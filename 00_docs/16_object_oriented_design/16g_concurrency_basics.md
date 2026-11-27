# Concurrency for LLD

LLD rounds often end with "now two users book the last seat at the same time". The answer needs a named shared state, the race on it, and the smallest lock or atomic operation that removes the race. This page covers the Java tools for that and the classic exercises that use them.

## Races and critical sections

A **race condition** is a bug whose result depends on how threads interleave. The usual shape is **check-then-act**: a thread checks a condition, then acts on it, and another thread changes the condition in between.

```java
class Show {
    private int seatsLeft = 1;

    boolean book() {
        if (seatsLeft > 0) {     // check
            seatsLeft--;         // act
            return true;
        }
        return false;
    }
}
```

Two users, one seat:

```
Thread A                      Thread B                     seatsLeft
--------                      --------                     ---------
reads seatsLeft = 1                                            1
                              reads seatsLeft = 1              1
seatsLeft-- (writes 0)                                         0
return true                                                    0
                              seatsLeft-- (writes -1)         -1
                              return true                     -1
```

Both users have a ticket for the same seat. Even `seatsLeft--` alone is a race: it is a read, a subtract, and a write, so two threads can both read 5 and both write 4. This is **read-modify-write**, the other common shape.

The **critical section** is the code that must run as one indivisible step: here, the check and the decrement together. The fix is to make it **atomic**: no other thread may see or change the state in the middle.

The first thing to say in an interview is *which state is shared and what the invariant is*: "the seat map is shared by all booking threads; a seat is never held by two bookings".

## Locks, synchronized and atomics

### synchronized

Every Java object has an intrinsic lock. A `synchronized` method or block holds it for the duration, so only one thread runs any code synchronized on the same object at a time. It also makes writes visible to the next thread that takes the lock.

```java
class Show {
    private int seatsLeft;

    synchronized boolean book() {
        if (seatsLeft == 0) return false;
        seatsLeft--;
        return true;
    }
}
```

Simple, and released automatically on exception. Limits: no timeout, no try-lock, cannot be interrupted while waiting.

### ReentrantLock

An explicit lock with those missing features. Always unlock in `finally`.

```java
private final ReentrantLock lock = new ReentrantLock();

boolean tryBook(String seat, long timeoutMs) throws InterruptedException {
    if (!lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) return false;   // give up instead of waiting forever
    try {
        return seats.reserve(seat);
    } finally {
        lock.unlock();
    }
}
```

Reach for it only when `tryLock`, timeouts, fairness, or several `Condition`s are needed; otherwise `synchronized` is shorter.

### Atomics and compare-and-set

`AtomicInteger`, `AtomicLong` and `AtomicReference` do read-modify-write on one variable without a lock, using the CPU's **compare-and-set** (CAS): "set to `new` only if it is still `expected`; tell me whether it worked".

```java
private final AtomicInteger seatsLeft = new AtomicInteger(100);

boolean book() {
    while (true) {
        int current = seatsLeft.get();
        if (current == 0) return false;
        if (seatsLeft.compareAndSet(current, current - 1)) return true;
        // another thread changed it between get and CAS: retry with the new value
    }
}
```

`incrementAndGet`, `getAndUpdate`, `updateAndGet` wrap this loop. Atomics fit counters, id generators and single-value flags. They do not help when the invariant spans **two** variables (move a seat from "free" to "booked by X"): that needs a lock, or one atomic reference to an immutable object holding both.

For maps, `ConcurrentHashMap` gives atomic per-key operations:

```java
ConcurrentHashMap<String, String> seatOwner = new ConcurrentHashMap<>();

boolean book(String seat, String user) {
    return seatOwner.putIfAbsent(seat, user) == null;     // atomic check-then-act per key
}
```

### Lock granularity

The larger the lock's scope, the simpler the reasoning and the less concurrency.

| Lock on | Concurrency | Risk |
|---------|-------------|------|
| the whole system | one booking at a time anywhere | simple, slow |
| one show | different shows book in parallel | usually the right answer |
| one seat | same show, different seats in parallel | multi-seat bookings need several locks: deadlock |

Booking seats A1 and A2 with per-seat locks: thread 1 locks A1 then A2, thread 2 locks A2 then A1, each waits for the other forever. That is a **deadlock**. The standard fix is a global lock order: always lock seats sorted by id.

```java
void bookAll(List<Seat> wanted, String user) {
    List<Seat> ordered = wanted.stream().sorted(Comparator.comparing(Seat::id)).toList();
    ordered.forEach(s -> s.lock().lock());
    try {
        if (ordered.stream().anyMatch(s -> !s.isFree())) throw new SeatTakenException();
        ordered.forEach(s -> s.assign(user));
    } finally {
        ordered.reversed().forEach(s -> s.lock().unlock());
    }
}
```

A good interview answer starts with a lock per show, then says per-seat locks would allow more concurrency at the cost of lock ordering.

## Bounded blocking queue and producer-consumer

Producers put work into a queue, consumers take it out. The queue has a capacity: producers wait when it is full, consumers wait when it is empty.

### With wait/notify

```java
class BoundedQueue<T> {
    private final Deque<T> items = new ArrayDeque<>();
    private final int capacity;

    BoundedQueue(int capacity) { this.capacity = capacity; }

    synchronized void put(T item) throws InterruptedException {
        while (items.size() == capacity) wait();     // while, not if
        items.addLast(item);
        notifyAll();
    }

    synchronized T take() throws InterruptedException {
        while (items.isEmpty()) wait();
        T item = items.removeFirst();
        notifyAll();
        return item;
    }
}
```

Two rules:

- **`while`, not `if`**: a thread can wake up without its condition being true (another thread took the item first, or a spurious wakeup). It must check again.
- **`notifyAll`, not `notify`**: producers and consumers wait on the same object. `notify` may wake a producer when only a consumer can make progress, and everyone sleeps.

### With Condition

A `ReentrantLock` can have separate conditions, so a `put` wakes only consumers and a `take` only producers:

```java
class BoundedQueue<T> {
    private final Deque<T> items = new ArrayDeque<>();
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    BoundedQueue(int capacity) { this.capacity = capacity; }

    void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (items.size() == capacity) notFull.await();
            items.addLast(item);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    T take() throws InterruptedException {
        lock.lock();
        try {
            while (items.isEmpty()) notEmpty.await();
            T item = items.removeFirst();
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }
}
```

### In production

Use `java.util.concurrent.ArrayBlockingQueue` (or `LinkedBlockingQueue`); it is this class, tested. Writing it by hand is an interview exercise to show the mechanism. An `ExecutorService` is a thread pool fed by such a queue:

```java
ExecutorService pool = Executors.newFixedThreadPool(4);
pool.submit(() -> notifier.send(user, msg));      // the observer's slow work, off the caller's thread
```

## Reader-writer locks

When reads far outnumber writes (a catalogue, a config, a cache), a plain lock makes readers wait for each other for no reason. A **read-write lock** lets any number of readers in at once, while a writer gets exclusive access.

```java
class Catalog {
    private final Map<String, Product> products = new HashMap<>();
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

    Product find(String sku) {
        rw.readLock().lock();
        try { return products.get(sku); }
        finally { rw.readLock().unlock(); }
    }

    void put(Product p) {
        rw.writeLock().lock();
        try { products.put(p.sku(), p); }
        finally { rw.writeLock().unlock(); }
    }
}
```

It is not free: the bookkeeping costs more than a plain lock, so it only helps when reads are frequent and hold the lock for a while. For a plain map, `ConcurrentHashMap` is simpler and faster. A read lock cannot be upgraded to a write lock in `ReentrantReadWriteLock`: trying it deadlocks.

## Testing concurrent code

A single-threaded test cannot find a race. The approach is to start many threads at the same instant against the shared object, then check the invariant.

```java
static void testNoDoubleBooking() throws Exception {
    var show = new Show(1);                         // one seat
    int threads = 50;
    var start = new CountDownLatch(1);
    var successes = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads);

    for (int i = 0; i < threads; i++) {
        pool.submit(() -> {
            start.await();                          // all threads wait here...
            if (show.book()) successes.incrementAndGet();
            return null;
        });
    }
    start.countDown();                              // ...and are released together
    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);

    check(successes.get() == 1, "exactly one booking succeeds");
}
```

- The `CountDownLatch` makes the threads hit the critical section together, which makes the race far more likely to show.
- Repeat the test in a loop (hundreds of runs) for the unsafe version to fail reliably.
- Count outcomes with an atomic, not a plain `int`, or the test itself races.

A passing run is evidence, not proof: the bad interleaving may simply not have happened. The argument for correctness is the reasoning about the lock, and the test is a check on that reasoning. Tools like jcstress exist for systematic testing but are beyond an interview.

## LeetCode concurrency problems

LeetCode has a Concurrency category with Java versions. They are short and drill the primitives above. Check each one's access on LeetCode, as some are Premium.

| # | Problem | Tool it drills |
|---|---------|----------------|
| 1114 | Print in Order | latches or semaphores to order threads |
| 1115 | Print FooBar Alternately | two semaphores taking turns |
| 1116 | Print Zero Even Odd | three-way hand-off |
| 1117 | Building H2O | semaphores plus a barrier |
| 1195 | Fizz Buzz Multithreaded | shared counter with conditions |
| 1226 | The Dining Philosophers | deadlock avoidance by lock ordering |
| 1188 | Design Bounded Blocking Queue | the queue above |
| 1242 | Web Crawler Multithreaded | thread pool, concurrent visited set |
| 1279 | Traffic Light Controlled Intersection | a single lock around state |

A semaphore, used by most of these, is a counter of permits: `acquire()` takes one and blocks at zero, `release()` gives one back. With 0 initial permits, it is a signal from one thread to another:

```java
class Foo {
    private final Semaphore firstDone = new Semaphore(0);
    private final Semaphore secondDone = new Semaphore(0);

    public void first(Runnable r) { r.run(); firstDone.release(); }
    public void second(Runnable r) throws InterruptedException { firstDone.acquire(); r.run(); secondDone.release(); }
    public void third(Runnable r) throws InterruptedException { secondDone.acquire(); r.run(); }
}
```
