# Pub-Sub System

An in-process message broker: publishers send messages to topics, and every subscriber of a topic receives them.

## Stages

1. **Core model.** Create topics; subscribers subscribe and unsubscribe; `publish(topic, message)` delivers the message to every current subscriber of the topic, in publish order per subscriber. Publishing to an unknown topic fails.
2. **Filters and offsets.** A subscription can have a filter (only messages whose key matches). Topics keep their messages; a new subscriber can start from the beginning or from now, and can `reset` to an earlier offset to replay.
3. **Concurrency.** Many publishers publish at once; each subscriber is served by its own thread, so a slow subscriber does not delay others. Per subscriber, messages arrive in the topic's order, each exactly once.

## Entities

- `Broker`: the facade; topics by name.
- `Topic`: name, an append-only message list, its subscriptions.
- `Message`: record of key, payload, offset, timestamp.
- `Subscriber`: interface `onMessage(message)`.
- `Subscription`: subscriber, topic, filter, current offset, and (stage 3) its worker.
- `Filter`: predicate on a message.

## Class diagram

```
+-------------------------+
| Broker                  |
+-------------------------+
| + createTopic(name)     |
| + subscribe(t, s, f, o) |
| + publish(t, msg)       |
+-------------------------+
     ◆ 1
     | *
+-------------------------+  1   *  +-------------------------+     <<interface>>
| Topic                   |◆------->| Subscription            |---->  Subscriber
+-------------------------+         +-------------------------+
| - log: List<Message>    |         | - offset: long          |---->  Filter
+-------------------------+         | + deliverPending()      |
                                    +-------------------------+
```

## Patterns used

- **Observer**, with a broker in between: publishers and subscribers do not know each other. See [behavioral patterns](../../00_docs/16_object_oriented_design/16f_behavioral_patterns.md#observer).
- **Mediator**: the broker.
- **Producer-consumer** per subscription (stage 3).

## Design notes

- **A log with per-subscriber offsets** (like Kafka) instead of pushing copies into per-subscriber queues: replay is free, and ordering per subscriber is the order of the log.
- **Publishing appends under the topic's lock** (that fixes the order); delivery happens outside it, so publishers never wait for subscribers.
- **One worker per subscription** reads from its offset and waits on a condition when caught up; a slow or failing subscriber affects only its own worker.

## Compared with other solutions

<Fill in after solving.>

## Files

`PubSub.java`: the solution, with checks for each stage in `main`.
