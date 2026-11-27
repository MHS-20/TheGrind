# Logging Framework

A small logging library: application code logs messages at a level, and the framework formats and writes them to one or more destinations.

## Stages

1. **Core model.** `log(level, message)` with levels DEBUG < INFO < WARN < ERROR. A logger has a minimum level and drops messages below it. Each record has a timestamp, level, logger name and message, and is written by a formatter to an appender (console, or an in-memory list for tests).
2. **Several appenders.** A logger writes to many appenders, each with its own minimum level and formatter (plain text, JSON). Loggers are named hierarchically (`app.db`): a logger without its own configuration inherits its parent's.
3. **Concurrency and async.** Many threads log at once; lines are never interleaved. An async appender puts records on a bounded queue drained by a background thread; when the queue is full, it either blocks or drops DEBUG records (configurable). `shutdown()` flushes everything.

## Entities

- `LoggerFactory` / `LogManager`: creates and caches named loggers, resolves inherited config.
- `Logger`: name, level, appenders.
- `LogRecord`: record of time, level, logger, message, thread.
- `Appender`: interface `append(record)`; `ConsoleAppender`, `MemoryAppender`, `AsyncAppender` (wraps another).
- `Formatter`: interface `format(record)`; `PlainFormatter`, `JsonFormatter`.
- `Level`: enum with ordering.

## Class diagram

```
LogManager ◆---* Logger(name, level) ---*--> <<interface>> Appender
                    | parent                   △          △            △
                    v                     Console     Memory    AsyncAppender ◆--1 Appender
                  Logger                                         (queue + thread)
                                          Appender ---1--> <<interface>> Formatter
                                                             △            △
                                                           Plain         Json
```

## Patterns used

- **Chain of responsibility**: a record goes up the logger hierarchy (stage 2), and level checks filter it at each step.
- **Strategy** for formatters; **Decorator** for `AsyncAppender` wrapping a real appender.
- **Observer**: appenders subscribe to a logger's records.
- **Singleton** is the textbook answer for `LogManager`; injecting one instance is the better one. See [creational patterns](../../00_docs/17_object_oriented_design/17d_creational_patterns.md#singleton).
- **Producer-consumer** for the async appender. See [concurrency](../../00_docs/17_object_oriented_design/17g_concurrency_basics.md#bounded-blocking-queue-and-producer-consumer).

## Design notes

- **Cheap when disabled**: check the level before building the message (`isDebugEnabled`, or a `Supplier<String>`).
- **No interleaving**: each appender writes a whole line under its own lock; formatting happens outside the lock.
- **Shutdown flushes**: a poison-pill record tells the background thread to drain and stop.

## Compared with other solutions

<Fill in after solving.>

## Files

`Logging.java`: the solution, with checks for each stage in `main`.
