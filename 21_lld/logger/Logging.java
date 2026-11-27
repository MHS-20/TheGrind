import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Logging {

    // ---------- records and formatting ----------

    enum Level { DEBUG, INFO, WARN, ERROR }

    record LogRecord(Instant time, Level level, String logger, String message, String thread) {}

    interface Formatter {
        String format(LogRecord r);
    }

    static final class PlainFormatter implements Formatter {
        public String format(LogRecord r) {
            return r.time() + " " + r.level() + " [" + r.thread() + "] " + r.logger() + " - " + r.message();
        }
    }

    static final class JsonFormatter implements Formatter {
        public String format(LogRecord r) {
            return "{\"time\":\"" + r.time() + "\",\"level\":\"" + r.level() + "\",\"logger\":\"" + escape(r.logger())
                    + "\",\"thread\":\"" + escape(r.thread()) + "\",\"message\":\"" + escape(r.message()) + "\"}";
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }
    }

    // ---------- appenders ----------

    interface Appender {
        void append(LogRecord r);

        default void close() {}
    }

    /** Level filter and formatting outside the lock; one whole line written under it. */
    abstract static class LineAppender implements Appender {
        private final Level minLevel;
        private final Formatter formatter;

        LineAppender(Level minLevel, Formatter formatter) {
            this.minLevel = minLevel;
            this.formatter = formatter;
        }

        public final void append(LogRecord r) {
            if (r.level().compareTo(minLevel) < 0) return;
            String line = formatter.format(r);
            synchronized (this) { write(line); }
        }

        protected abstract void write(String line);
    }

    static final class ConsoleAppender extends LineAppender {
        private final PrintStream out;

        ConsoleAppender(Level minLevel, Formatter formatter, PrintStream out) {
            super(minLevel, formatter);
            this.out = out;
        }

        protected void write(String line) { out.println(line); }
    }

    static final class MemoryAppender extends LineAppender {
        final List<String> lines = Collections.synchronizedList(new ArrayList<>());

        MemoryAppender(Level minLevel, Formatter formatter) { super(minLevel, formatter); }

        protected void write(String line) { lines.add(line); }
    }

    enum Overflow { BLOCK, DROP_DEBUG }

    /** Decorator: hands records to a background thread through a bounded queue. */
    static final class AsyncAppender implements Appender {
        private static final LogRecord POISON = new LogRecord(Instant.EPOCH, Level.ERROR, "", "", "");

        private final Appender delegate;
        private final BlockingQueue<LogRecord> queue;
        private final Overflow overflow;
        private final Thread worker;
        final AtomicInteger dropped = new AtomicInteger();
        private volatile boolean closed;

        AsyncAppender(Appender delegate, int capacity, Overflow overflow) {
            this.delegate = delegate;
            this.queue = new ArrayBlockingQueue<>(capacity);
            this.overflow = overflow;
            this.worker = new Thread(this::drain, "async-appender");
            worker.setDaemon(true);
            worker.start();
        }

        private void drain() {
            try {
                for (LogRecord r = queue.take(); r != POISON; r = queue.take()) delegate.append(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void append(LogRecord r) {
            if (closed) throw new IllegalStateException("appender closed");
            if (overflow == Overflow.DROP_DEBUG && r.level() == Level.DEBUG) {
                if (!queue.offer(r)) dropped.incrementAndGet();
                return;
            }
            try {
                queue.put(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** Flushes everything queued before it, then stops the worker. */
        public void close() {
            closed = true;
            try {
                queue.put(POISON);
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            delegate.close();
        }
    }

    // ---------- loggers ----------

    record LoggerConfig(Level level, List<Appender> appenders) {}

    static final class Logger {
        final String name;
        private final LogManager manager;

        Logger(String name, LogManager manager) {
            this.name = name;
            this.manager = manager;
        }

        boolean isEnabled(Level level) { return level.compareTo(manager.effectiveConfig(name).level()) >= 0; }

        void log(Level level, String message) { log(level, () -> message); }

        /** The message is built only when the level is enabled. */
        void log(Level level, Supplier<String> message) {
            LoggerConfig cfg = manager.effectiveConfig(name);
            if (level.compareTo(cfg.level()) < 0) return;
            LogRecord r = new LogRecord(manager.clock.get(), level, name, message.get(), Thread.currentThread().getName());
            cfg.appenders().forEach(a -> a.append(r));
        }

        void debug(String m) { log(Level.DEBUG, m); }
        void info(String m) { log(Level.INFO, m); }
        void warn(String m) { log(Level.WARN, m); }
        void error(String m) { log(Level.ERROR, m); }
    }

    /** One injected instance rather than a global singleton. */
    static final class LogManager {
        static final String ROOT = "";

        private final Map<String, LoggerConfig> configs = new ConcurrentHashMap<>();
        private final Map<String, Logger> loggers = new ConcurrentHashMap<>();
        final Supplier<Instant> clock;

        LogManager(Supplier<Instant> clock) {
            this.clock = clock;
            configs.put(ROOT, new LoggerConfig(Level.INFO, List.of()));
        }

        Logger getLogger(String name) { return loggers.computeIfAbsent(name, n -> new Logger(n, this)); }

        void configure(String name, Level level, Appender... appenders) {
            configs.put(name, new LoggerConfig(level, List.of(appenders)));
        }

        /** The nearest configured ancestor: app.db.pool -> app.db -> app -> root. */
        LoggerConfig effectiveConfig(String name) {
            for (String n = name; ; n = parent(n)) {
                LoggerConfig c = configs.get(n);
                if (c != null) return c;
            }
        }

        private static String parent(String name) {
            int dot = name.lastIndexOf('.');
            return dot < 0 ? ROOT : name.substring(0, dot);
        }

        void shutdown() {
            Set<Appender> all = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            configs.values().forEach(c -> all.addAll(c.appenders()));
            all.forEach(Appender::close);
        }
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    static final Instant T = Instant.parse("2026-11-27T10:00:00Z");

    public static void main(String[] args) throws Exception {
        System.out.println("Stage 1: core model");
        {
            LogManager lm = new LogManager(() -> T);
            MemoryAppender mem = new MemoryAppender(Level.DEBUG, new PlainFormatter());
            lm.configure("app", Level.INFO, mem);
            Logger log = lm.getLogger("app");
            log.debug("hidden");
            log.info("started");
            log.error("boom");
            check("messages below the logger level are dropped", mem.lines.size() == 2);
            check("record has time, level, logger, message",
                    mem.lines.get(0).equals("2026-11-27T10:00:00Z INFO [main] app - started"));
            AtomicInteger built = new AtomicInteger();
            log.log(Level.DEBUG, () -> "expensive " + built.incrementAndGet());
            check("disabled message is never built", built.get() == 0);
            check("same name gives the same logger", lm.getLogger("app") == log);
        }

        System.out.println("Stage 2: several appenders and hierarchy");
        {
            LogManager lm = new LogManager(() -> T);
            MemoryAppender all = new MemoryAppender(Level.DEBUG, new PlainFormatter());
            MemoryAppender errors = new MemoryAppender(Level.ERROR, new JsonFormatter());
            lm.configure("app", Level.DEBUG, all, errors);
            lm.configure("app.http", Level.WARN, all);
            lm.getLogger("app.db").debug("query");
            lm.getLogger("app.db").error("say \"no\"");
            check("each appender applies its own level", all.lines.size() == 2 && errors.lines.size() == 1);
            check("JSON formatter escapes quotes", errors.lines.get(0).equals(
                    "{\"time\":\"2026-11-27T10:00:00Z\",\"level\":\"ERROR\",\"logger\":\"app.db\","
                            + "\"thread\":\"main\",\"message\":\"say \\\"no\\\"\"}"));
            check("unconfigured app.db.pool inherits app",
                    lm.getLogger("app.db.pool").isEnabled(Level.DEBUG));
            check("app.http has its own config", !lm.getLogger("app.http").isEnabled(Level.INFO));
            check("unknown tree falls back to root (INFO)",
                    !lm.getLogger("other").isEnabled(Level.DEBUG) && lm.getLogger("other").isEnabled(Level.INFO));
        }

        System.out.println("Stage 3: concurrency and async");
        {
            LogManager lm = new LogManager(Instant::now);
            MemoryAppender mem = new MemoryAppender(Level.DEBUG, new PlainFormatter());
            lm.configure("app", Level.DEBUG, mem);
            Logger log = lm.getLogger("app");
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch go = new CountDownLatch(1);
            for (int t = 0; t < 8; t++) {
                int id = t;
                pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < 500; i++) log.info("t" + id + "-" + i);
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            check("every line from 8 threads arrives whole",
                    mem.lines.size() == 4000 && mem.lines.stream().allMatch(l -> l.matches(".* app - t\\d-\\d+")));

            LogManager alm = new LogManager(() -> T);
            MemoryAppender sink = new MemoryAppender(Level.DEBUG, new PlainFormatter());
            AsyncAppender async = new AsyncAppender(sink, 16, Overflow.BLOCK);
            alm.configure("app", Level.DEBUG, async);
            for (int i = 0; i < 1000; i++) alm.getLogger("app").info("m" + i);
            alm.shutdown();
            check("async BLOCK loses nothing and shutdown flushes", sink.lines.size() == 1000);
            check("order kept through the queue", sink.lines.get(999).endsWith("m999"));
            check("logging after shutdown fails", throwsISE(() -> alm.getLogger("app").info("late")));

            // a slow sink makes the queue fill up
            List<String> slowLines = Collections.synchronizedList(new ArrayList<>());
            LineAppender slowSink = new LineAppender(Level.DEBUG, new PlainFormatter()) {
                @Override protected void write(String line) {
                    try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                    slowLines.add(line);
                }
            };
            AsyncAppender dropping = new AsyncAppender(slowSink, 4, Overflow.DROP_DEBUG);
            LogManager dlm = new LogManager(() -> T);
            dlm.configure("app", Level.DEBUG, dropping);
            for (int i = 0; i < 200; i++) dlm.getLogger("app").debug("d" + i);
            for (int i = 0; i < 20; i++) dlm.getLogger("app").warn("w" + i);
            dlm.shutdown();
            long warns = slowLines.stream().filter(l -> l.contains("WARN")).count();
            check("DROP_DEBUG drops debug when full", dropping.dropped.get() > 0);
            check("DROP_DEBUG never drops higher levels", warns == 20);
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }

    private static boolean throwsISE(Runnable r) {
        try { r.run(); return false; } catch (IllegalStateException e) { return true; }
    }
}
