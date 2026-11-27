import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

public class PubSub {

    // ---------- model ----------

    record Message(String key, String payload, long offset, Instant timestamp) {}

    interface Subscriber {
        void onMessage(Message m);
    }

    @FunctionalInterface
    interface Filter {
        boolean accepts(Message m);

        Filter ALL = m -> true;

        static Filter keyEquals(String key) { return m -> key.equals(m.key()); }
    }

    enum StartFrom { BEGINNING, NOW }

    // ---------- topic: an append-only log ----------

    static final class Topic {
        final String name;
        private final List<Message> log = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition appended = lock.newCondition();
        final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

        Topic(String name) { this.name = name; }

        /** Appending under the lock fixes the order every subscriber sees. */
        long append(String key, String payload) {
            lock.lock();
            try {
                Message m = new Message(key, payload, log.size(), Instant.now());
                log.add(m);
                appended.signalAll();
                return m.offset();
            } finally {
                lock.unlock();
            }
        }

        long size() {
            lock.lock();
            try { return log.size(); } finally { lock.unlock(); }
        }

        /** Blocks until a message exists at the cursor's current position, rereading it on every wake-up. */
        Message await(AtomicLong cursor) throws InterruptedException {
            lock.lock();
            try {
                while (cursor.get() >= log.size()) appended.await();
                return log.get((int) cursor.get());
            } finally {
                lock.unlock();
            }
        }

        /** Wakes waiting workers so they reread their cursor (after a reset). */
        void wake() {
            lock.lock();
            try { appended.signalAll(); } finally { lock.unlock(); }
        }
    }

    // ---------- subscription: an offset and its own worker ----------

    static final class Subscription {
        final Subscriber subscriber;
        final Topic topic;
        final Filter filter;
        private final AtomicLong offset;
        private final Thread worker;

        Subscription(Subscriber subscriber, Topic topic, Filter filter, long start) {
            this.subscriber = subscriber;
            this.topic = topic;
            this.filter = filter;
            this.offset = new AtomicLong(start);
            this.worker = Thread.ofVirtual().unstarted(this::run);
        }

        void start() { worker.start(); }

        void stop() {
            worker.interrupt();
            try { worker.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        long offset() { return offset.get(); }

        /** Replays from an earlier offset (or skips ahead). */
        void reset(long to) {
            if (to < 0 || to > topic.size()) throw new IllegalArgumentException("bad offset " + to);
            offset.set(to);
            topic.wake();
        }

        private void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Message m = topic.await(offset);
                    if (!offset.compareAndSet(m.offset(), m.offset() + 1)) continue; // reset meanwhile
                    if (filter.accepts(m)) {
                        try {
                            subscriber.onMessage(m);
                        } catch (RuntimeException e) {
                            // a failing subscriber only affects itself
                        }
                    }
                }
            } catch (InterruptedException e) {
                // unsubscribed
            }
        }
    }

    // ---------- broker ----------

    static final class Broker {
        private final Map<String, Topic> topics = new ConcurrentHashMap<>();

        void createTopic(String name) {
            if (topics.putIfAbsent(name, new Topic(name)) != null) throw new IllegalArgumentException("topic exists");
        }

        private Topic topic(String name) {
            Topic t = topics.get(name);
            if (t == null) throw new IllegalArgumentException("unknown topic " + name);
            return t;
        }

        Subscription subscribe(String topic, Subscriber s) { return subscribe(topic, s, Filter.ALL, StartFrom.NOW); }

        Subscription subscribe(String topic, Subscriber s, Filter filter, StartFrom from) {
            Topic t = topic(topic);
            Subscription sub = new Subscription(s, t, filter, from == StartFrom.BEGINNING ? 0 : t.size());
            t.subscriptions.add(sub);
            sub.start();
            return sub;
        }

        void unsubscribe(Subscription sub) {
            sub.topic.subscriptions.remove(sub);
            sub.stop();
        }

        long publish(String topic, String key, String payload) { return topic(topic).append(key, payload); }

        void shutdown() { topics.values().forEach(t -> t.subscriptions.forEach(this::unsubscribe)); }
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    static class Collector implements Subscriber {
        final List<Message> got = Collections.synchronizedList(new ArrayList<>());

        public void onMessage(Message m) { got.add(m); }

        List<String> payloads() {
            synchronized (got) { return got.stream().map(Message::payload).toList(); }
        }
    }

    static boolean eventually(BooleanSupplier cond) throws InterruptedException {
        for (int i = 0; i < 500; i++) {
            if (cond.getAsBoolean()) return true;
            Thread.sleep(10);
        }
        return cond.getAsBoolean();
    }

    /** Waits a little, for checks that something does NOT arrive. */
    static void settle() throws InterruptedException { Thread.sleep(100); }

    public static void main(String[] args) throws Exception {
        System.out.println("Stage 1: core model");
        {
            Broker b = new Broker();
            b.createTopic("orders");
            Collector a = new Collector(), c = new Collector();
            Subscription sa = b.subscribe("orders", a);
            b.subscribe("orders", c);
            b.publish("orders", "k", "1");
            b.publish("orders", "k", "2");
            check("every subscriber gets every message in order",
                    eventually(() -> a.payloads().equals(List.of("1", "2")) && c.payloads().equals(List.of("1", "2"))));
            b.unsubscribe(sa);
            b.publish("orders", "k", "3");
            check("unsubscribed gets nothing more",
                    eventually(() -> c.payloads().size() == 3) && a.payloads().size() == 2);
            boolean unknown = false;
            try { b.publish("nope", "k", "x"); } catch (IllegalArgumentException e) { unknown = true; }
            check("publish to unknown topic fails", unknown);
            b.shutdown();
        }

        System.out.println("Stage 2: filters and offsets");
        {
            Broker b = new Broker();
            b.createTopic("t");
            b.publish("t", "eu", "old-eu");
            b.publish("t", "us", "old-us");
            Collector fromStart = new Collector(), fromNow = new Collector(), euOnly = new Collector();
            b.subscribe("t", fromStart, Filter.ALL, StartFrom.BEGINNING);
            b.subscribe("t", fromNow, Filter.ALL, StartFrom.NOW);
            Subscription eu = b.subscribe("t", euOnly, Filter.keyEquals("eu"), StartFrom.BEGINNING);
            b.publish("t", "eu", "new-eu");
            check("from beginning sees history", eventually(() -> fromStart.payloads().size() == 3));
            check("from now sees only new", eventually(() -> fromNow.payloads().equals(List.of("new-eu"))));
            check("filter by key", eventually(() -> euOnly.payloads().equals(List.of("old-eu", "new-eu"))));
            eu.reset(0);
            check("reset replays", eventually(() -> euOnly.payloads().equals(List.of("old-eu", "new-eu", "old-eu", "new-eu"))));
            check("offsets are positions in the log", fromStart.got.get(2).offset() == 2);
            b.shutdown();
        }

        System.out.println("Stage 3: concurrency");
        {
            Broker b = new Broker();
            b.createTopic("t");
            Collector fast = new Collector();
            CountDownLatch slowGate = new CountDownLatch(1);
            Collector slow = new Collector() {
                @Override public void onMessage(Message m) {
                    try { slowGate.await(); } catch (InterruptedException ignored) {}
                    super.onMessage(m);
                }
            };
            b.subscribe("t", fast);
            b.subscribe("t", slow);
            int publishers = 8, each = 500;
            ExecutorService pool = Executors.newFixedThreadPool(publishers);
            CountDownLatch go = new CountDownLatch(1);
            for (int p = 0; p < publishers; p++) {
                int id = p;
                pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < each; i++) b.publish("t", "p" + id, id + ":" + i);
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            int total = publishers * each;
            check("a stuck subscriber does not delay others",
                    eventually(() -> fast.got.size() == total) && slow.got.isEmpty());
            slowGate.countDown();
            check("slow subscriber catches up", eventually(() -> slow.got.size() == total));
            settle();
            List<Long> fastOffsets, slowOffsets;
            synchronized (fast.got) { fastOffsets = fast.got.stream().map(Message::offset).toList(); }
            synchronized (slow.got) { slowOffsets = slow.got.stream().map(Message::offset).toList(); }
            List<Long> expected = java.util.stream.LongStream.range(0, total).boxed().toList();
            check("each message exactly once, in topic order", fastOffsets.equals(expected) && slowOffsets.equals(expected));
            b.shutdown();
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
