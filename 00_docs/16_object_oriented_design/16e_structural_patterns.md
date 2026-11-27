# Structural Patterns

Structural patterns combine objects into larger ones without changing the classes being combined. In LLD problems they appear when a feature is layered on top of an existing class, such as toppings on an order or an extra check on a payment.

Decorator, Adapter and Proxy look alike in code: each is a class that holds another object and forwards calls to it. They differ in intent.

| Pattern | Wrapper's interface vs wrapped | Purpose |
|---------|--------------------------------|---------|
| Decorator | same | add behaviour, stackable |
| Proxy | same | control access (lazy, cache, permission, rate limit) |
| Adapter | different | make an incompatible class fit an expected interface |
| Facade | new, simpler | one entry point over several classes |

## Decorator

A decorator wraps an object that implements an interface, implements the same interface, and adds something before or after forwarding. Decorators stack, so features combine at runtime.

The problem it solves: a coffee with milk, sugar, and an extra shot. With subclasses, every combination is a class (`CoffeeWithMilk`, `CoffeeWithMilkAndSugar`, ...): 2ⁿ classes for n add-ons. With decorators, it is n classes.

```java
interface Beverage {
    String description();
    long cost();              // in cents
}

record Espresso() implements Beverage {
    public String description() { return "espresso"; }
    public long cost() { return 200; }
}

abstract class AddOn implements Beverage {
    protected final Beverage inner;
    AddOn(Beverage inner) { this.inner = inner; }
}

class Milk extends AddOn {
    Milk(Beverage inner) { super(inner); }
    public String description() { return inner.description() + " + milk"; }
    public long cost() { return inner.cost() + 50; }
}

class ExtraShot extends AddOn {
    ExtraShot(Beverage inner) { super(inner); }
    public String description() { return inner.description() + " + shot"; }
    public long cost() { return inner.cost() + 80; }
}

Beverage b = new Milk(new ExtraShot(new ExtraShot(new Espresso())));
b.description();   // "espresso + shot + shot + milk"
b.cost();          // 410
```

Decorators are also how cross-cutting behaviour is added to a service without touching it: logging, timing, retries, caching.

```java
class RetryingPayment implements PaymentGateway {
    private final PaymentGateway inner;
    private final int attempts;

    RetryingPayment(PaymentGateway inner, int attempts) { this.inner = inner; this.attempts = attempts; }

    public boolean charge(String account, long amount) {
        for (int i = 0; i < attempts; i++) {
            if (inner.charge(account, amount)) return true;
        }
        return false;
    }
}
```

`java.io` is the standard library's example: `new BufferedReader(new InputStreamReader(new FileInputStream(f)))`.

One limit: order matters. A 10% discount decorator applied before or after a fixed add-on gives different prices. If the order of rules is part of the requirement, say so, or use a list of rules applied in a defined order instead.

## Adapter

An adapter makes a class with the wrong interface usable where the system expects its own interface. The usual case is a third-party library whose API the system cannot change.

```java
// The system's own interface, designed for its needs.
interface PaymentGateway {
    boolean charge(String account, long amountCents);
}

// A vendor SDK with a different shape: amounts in decimal, exceptions on failure.
class LegacyBankClient {
    String makeTransfer(String iban, BigDecimal euros) throws BankException { ... }
}

class LegacyBankAdapter implements PaymentGateway {
    private final LegacyBankClient client;
    LegacyBankAdapter(LegacyBankClient client) { this.client = client; }

    public boolean charge(String account, long amountCents) {
        try {
            client.makeTransfer(account, BigDecimal.valueOf(amountCents, 2));
            return true;
        } catch (BankException e) {
            return false;
        }
    }
}
```

The rest of the system only knows `PaymentGateway`. Switching vendors means writing another adapter; nothing else changes. This is [dependency inversion](16b_oop_and_solid.md#dependency-inversion) in practice: the system owns the interface, and the adapter is the boundary.

`Arrays.asList` (array to `List`) and `InputStreamReader` (bytes to chars) are adapters in the JDK.

## Facade

A facade gives one simple entry point over several classes that would otherwise have to be called in the right order by every client.

```java
class BookingService {                        // the facade
    private final ShowCatalog catalog;
    private final SeatLocker locker;
    private final PaymentGateway payments;
    private final Notifier notifier;

    BookingService(ShowCatalog c, SeatLocker l, PaymentGateway p, Notifier n) {
        catalog = c; locker = l; payments = p; notifier = n;
    }

    Booking book(String userId, String showId, List<String> seats, String account) {
        Show show = catalog.find(showId);
        locker.lock(show, seats, userId);
        try {
            if (!payments.charge(account, show.price() * seats.size())) {
                throw new PaymentFailedException();
            }
            Booking booking = locker.confirm(show, seats, userId);
            notifier.send(userId, "Booked " + seats);
            return booking;
        } finally {
            locker.release(show, seats, userId);     // no-op if already confirmed
        }
    }
}
```

The client calls `book(...)` and does not need to know that seats are locked before payment and released on failure. The subsystems stay usable directly where a client needs finer control.

A facade is the natural shape of the top-level class in most LLD problems (`ParkingLot`, `BookingService`, `Splitwise`). The risk is that it grows into a god class: it should coordinate, not compute. If it starts holding fee logic or seat maps, move them out.

## Composite

A composite lets a tree of objects be treated the same way as a single object. Leaves and containers implement one interface; a container forwards the call to its children and combines the results.

```java
sealed interface FsNode permits FileNode, Directory {
    String name();
    long size();
}

record FileNode(String name, long size) implements FsNode {}

final class Directory implements FsNode {
    private final String name;
    private final Map<String, FsNode> children = new TreeMap<>();

    Directory(String name) { this.name = name; }

    public String name() { return name; }

    public long size() {
        long total = 0;
        for (FsNode child : children.values()) total += child.size();   // leaf or directory, same call
        return total;
    }

    void add(FsNode node) { children.put(node.name(), node); }
}

var root = new Directory("/");
var docs = new Directory("docs");
docs.add(new FileNode("a.txt", 100));
docs.add(new FileNode("b.txt", 250));
root.add(docs);
root.add(new FileNode("readme", 50));
root.size();   // 400
```

Typical problems: a file system (compare [`20_practical/storage/file_system_tree`](../../20_practical/)), a menu with sub-menus, an org chart (total salary of a department), a product bundle priced as the sum of its items.

The design choice to mention: whether `add` lives on the shared interface (every node looks the same, but `add` on a file must throw) or only on the container (type-safe, as above, but the client must know it has a directory). The second keeps [Liskov](16b_oop_and_solid.md#liskov-substitution) intact and is the usual choice in Java.

## Proxy

A proxy has the same interface as the real object and controls access to it. The client cannot tell it is talking to a proxy.

Common kinds:

- **Virtual proxy**: create the expensive object only when first used.
- **Caching proxy**: return a stored result for repeated calls.
- **Protection proxy**: check permissions before forwarding.
- **Rate-limiting proxy**: refuse calls over a quota.

A caching proxy:

```java
interface ProductCatalog {
    Product find(String sku);
}

class DbProductCatalog implements ProductCatalog {
    public Product find(String sku) { /* slow query */ }
}

class CachingCatalog implements ProductCatalog {
    private final ProductCatalog real;
    private final Map<String, Product> cache = new ConcurrentHashMap<>();

    CachingCatalog(ProductCatalog real) { this.real = real; }

    public Product find(String sku) {
        return cache.computeIfAbsent(sku, real::find);
    }
}
```

A protection proxy:

```java
class AdminOnlyCatalog implements ProductAdmin {
    private final ProductAdmin real;
    private final Supplier<User> currentUser;

    AdminOnlyCatalog(ProductAdmin real, Supplier<User> currentUser) {
        this.real = real; this.currentUser = currentUser;
    }

    public void delete(String sku) {
        if (!currentUser.get().isAdmin()) throw new SecurityException("admin only");
        real.delete(sku);
    }
}
```

Proxy vs decorator: the code is the same shape. A decorator adds a feature the client asked for, and is stacked freely. A proxy manages the real object's lifecycle or access, and the client usually does not choose it. Saying this distinction is usually enough in an interview.

## Used in

Filled as problems are solved in [`21_lld`](../../21_lld/).

| Pattern | Problems |
|---------|----------|
| Decorator | coffee_machine / pizza pricing, logging around services |
| Adapter | payment gateways in parking_lot, movie_booking |
| Facade | the top-level service of every problem |
| Composite | file_system, menus |
| Proxy | lru_cache in front of a slow store, rate_limiter |
