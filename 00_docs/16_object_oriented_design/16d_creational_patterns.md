# Creational Patterns

Creational patterns decide which class creates an object and which concrete type it gets, so that callers depend on an interface instead of a constructor. Only the patterns that show up in LLD problems are covered here; refactoring.guru has the full catalogue.

| Pattern | Use it when | Interview example |
|---------|-------------|-------------------|
| Simple factory | the concrete type depends on input | vehicle or notification from a type |
| Factory method | a subclass decides what to create | each game creates its own board |
| Abstract factory | several related objects must match | UI widgets per theme |
| Builder | many optional fields | an order, a booking, an HTTP request |
| Singleton | exactly one instance must exist | rarely needed; prefer injection |

## Factory method and simple factory

A **simple factory** is one method that hides `new` behind a decision. Callers ask for "a vehicle of this type" and get an interface back.

```java
sealed interface Vehicle permits Car, Truck, Motorcycle {
    String plate();
    SpotSize minSpot();
}
record Car(String plate) implements Vehicle        { public SpotSize minSpot() { return SpotSize.MEDIUM; } }
record Truck(String plate) implements Vehicle      { public SpotSize minSpot() { return SpotSize.LARGE; } }
record Motorcycle(String plate) implements Vehicle { public SpotSize minSpot() { return SpotSize.SMALL; } }

final class VehicleFactory {
    private VehicleFactory() {}

    static Vehicle create(VehicleType type, String plate) {
        return switch (type) {
            case CAR        -> new Car(plate);
            case TRUCK      -> new Truck(plate);
            case MOTORCYCLE -> new Motorcycle(plate);
        };
    }
}
```

The `switch` is still there, but it is in **one** place: adding a type edits the factory and nothing else. With a sealed interface and a switch expression, the compiler reports a missing case.

When types must be added without editing the factory (plugins, configuration), use a registry of constructors instead:

```java
final class NotifierFactory {
    private final Map<String, Supplier<Notifier>> registry = new HashMap<>();

    void register(String channel, Supplier<Notifier> ctor) { registry.put(channel, ctor); }

    Notifier create(String channel) {
        Supplier<Notifier> ctor = registry.get(channel);
        if (ctor == null) throw new IllegalArgumentException("unknown channel " + channel);
        return ctor.get();
    }
}

factory.register("email", EmailNotifier::new);
factory.register("sms",   SmsNotifier::new);
```

The GoF **factory method** is a different shape: a base class defines the algorithm and leaves one creation step to an abstract method that subclasses override. It is the [Template method](16f_behavioral_patterns.md#template-method) pattern applied to creation.

```java
abstract class BoardGame {
    abstract Board createBoard();            // the factory method

    void start() {
        Board board = createBoard();
        board.reset();
        // ... the same game loop for every game
    }
}

class Chess extends BoardGame    { Board createBoard() { return new Board(8, 8); } }
class TicTacToe extends BoardGame { Board createBoard() { return new Board(3, 3); } }
```

In interviews, "factory" almost always means the simple factory. Name it precisely if asked.

## Abstract factory

An abstract factory creates a **family** of related objects that must be used together. The client picks one factory and every object it gets from it matches.

```java
interface Button   { String render(); }
interface Checkbox { String render(); }

interface UiFactory {
    Button button();
    Checkbox checkbox();
}

class DarkFactory implements UiFactory {
    public Button button()     { return () -> "[dark button]"; }
    public Checkbox checkbox() { return () -> "[dark checkbox]"; }
}

class LightFactory implements UiFactory {
    public Button button()     { return () -> "[light button]"; }
    public Checkbox checkbox() { return () -> "[light checkbox]"; }
}

class Settings {
    private final UiFactory ui;
    Settings(UiFactory ui) { this.ui = ui; }       // can never mix dark buttons with light checkboxes
    String render() { return ui.button().render() + ui.checkbox().render(); }
}
```

It is rare in LLD rounds. Mention it when the problem has variants that come in sets (themes, platforms, regional rules: a `TaxRules` and a `ShippingRules` per country).

## Builder

A builder constructs an object step by step when the constructor would have many parameters, most of them optional. It replaces the telescoping constructor `new Order(id, user, items, null, null, true, 0, null)`, where nobody can tell which `null` is which.

```java
final class Order {
    private final String id;
    private final String userId;
    private final List<String> items;
    private final String coupon;          // optional
    private final String deliveryNote;    // optional
    private final boolean giftWrap;       // optional

    private Order(Builder b) {
        this.id = b.id;
        this.userId = b.userId;
        this.items = List.copyOf(b.items);
        this.coupon = b.coupon;
        this.deliveryNote = b.deliveryNote;
        this.giftWrap = b.giftWrap;
    }

    static Builder builder(String id, String userId) { return new Builder(id, userId); }

    static final class Builder {
        private final String id;
        private final String userId;
        private final List<String> items = new ArrayList<>();
        private String coupon;
        private String deliveryNote;
        private boolean giftWrap;

        private Builder(String id, String userId) {   // required fields up front
            this.id = Objects.requireNonNull(id);
            this.userId = Objects.requireNonNull(userId);
        }

        Builder item(String sku)          { items.add(sku); return this; }
        Builder coupon(String code)       { this.coupon = code; return this; }
        Builder deliveryNote(String note) { this.deliveryNote = note; return this; }
        Builder giftWrap()                { this.giftWrap = true; return this; }

        Order build() {
            if (items.isEmpty()) throw new IllegalStateException("order without items");
            return new Order(this);
        }
    }
}

Order o = Order.builder("o1", "u42")
        .item("book")
        .item("pen")
        .giftWrap()
        .build();
```

Three points to say in an interview:

- Required fields go in the builder's constructor, so they cannot be forgotten.
- `build()` validates the combination of fields and is the only way to get an `Order`, so an invalid one never exists.
- The result is immutable (`final` fields, `List.copyOf`), so it is safe to share between threads.

If an object has three or four fields and no optional ones, a record is simpler than a builder.

## Singleton

A singleton guarantees one instance of a class and gives global access to it. Two thread-safe versions are worth knowing in Java.

The enum version. The JVM creates the instance once, safely, and it also survives serialization and reflection:

```java
enum IdGenerator {
    INSTANCE;

    private final AtomicLong next = new AtomicLong();

    long nextId() { return next.incrementAndGet(); }
}

long id = IdGenerator.INSTANCE.nextId();
```

The holder-class version, lazy without locks. The nested class is only loaded on the first call to `get()`, and class loading is thread-safe:

```java
final class Config {
    private Config() {}

    private static final class Holder {
        static final Config INSTANCE = new Config();
    }

    static Config get() { return Holder.INSTANCE; }
}
```

The version to recognise as **broken** is the lazy check without synchronization: two threads can both see `null` and both create an instance.

```java
static Config get() {
    if (instance == null) instance = new Config();   // race
    return instance;
}
```

Double-checked locking fixes it only if the field is `volatile`; the holder class is shorter and correct.

### Why injection is usually better

A singleton is a global variable with a class around it. It hides dependencies (the constructor of `ParkingLot` does not show that it uses `Config`), and its state outlives each test: if one stage test parks a car in a singleton lot, the next test finds the spot taken.

The alternative is to create **one** instance at startup and pass it to whoever needs it:

```java
var ids = new IdGenerator();                 // a normal class now
var lot = new ParkingLot(floors, pricing, ids);
var bookings = new BookingService(ids);
```

There is still one instance, but it is visible in constructors and each test makes its own. In an interview, if a singleton comes up, use the enum or holder version and mention this trade-off.

## Used in

Filled as problems are solved in [`21_lld`](../../21_lld/).

| Pattern | Problems |
|---------|----------|
| Simple factory | parking_lot (vehicles), notification channels |
| Builder | movie_booking, food_delivery (orders) |
| Singleton | logger (and why injection was chosen instead) |
