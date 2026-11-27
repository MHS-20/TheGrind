# OOP Principles and SOLID

Encapsulation, abstraction, inheritance and polymorphism describe what a class can do; the five SOLID principles describe how classes should depend on each other so that a change stays local. Each principle below comes with a short Java example that violates it and the fixed version, both from the same parking-lot domain used in `21_lld`.

## The four OOP pillars

**Encapsulation**: a class hides its state and exposes operations that keep it valid. Callers cannot put the object in a bad state, because they never touch the fields.

```java
class Spot {
    private Vehicle parked;                 // nobody outside can set this to garbage

    boolean isFree() { return parked == null; }

    void park(Vehicle v) {
        if (!isFree()) throw new IllegalStateException("spot taken");
        parked = v;
    }
}
```

**Abstraction**: callers depend on *what* something does, not *how*. An interface is the usual tool.

```java
interface PricingStrategy {
    long fee(Ticket ticket, LocalDateTime exit);
}
```

**Inheritance**: a subclass reuses and specialises a parent. It also couples the subclass to every detail of the parent, which is why it is used sparingly.

**Polymorphism**: one call, different behaviour depending on the runtime type. It is what lets new behaviour be added without editing the caller.

```java
long fee = pricing.fee(ticket, now);   // HourlyPricing, FlatPricing, WeekendPricing...
```

### Composition over inheritance

Prefer giving a class a field of another type (has-a) over extending it (is-a). Composition can be changed at runtime and does not expose the parent's internals.

```java
// Inheritance: an ElectricCar "is a" Car, fine. But a "ChargingSpot extends Spot,
// ReservedSpot extends Spot, ReservedChargingSpot extends ...?" explodes.

// Composition: features are fields, combined freely.
class Spot {
    private final SpotSize size;
    private final Set<Feature> features;    // CHARGER, RESERVED, HANDICAP
}
```

Inheritance is the right tool when the relation is a true is-a **and** the subclass can be used anywhere the parent is expected without surprises (see Liskov below). `Car`, `Truck` and `Motorcycle` implementing `Vehicle` is fine; a `Stack extends ArrayList` that must refuse `add(index, x)` is not.

## Single responsibility

A class should have one reason to change. "Reason to change" means one group of people or one kind of requirement: pricing rules, ticket formatting, and spot allocation change for different reasons.

Violation:

```java
class ParkingLot {
    Ticket park(Vehicle v) { /* find spot */ }
    long unpark(Ticket t) {
        long hours = Duration.between(t.entry(), LocalDateTime.now()).toHours() + 1;
        long fee = hours * (t.vehicle().type() == VehicleType.TRUCK ? 50 : 20);
        System.out.println("Ticket " + t.id() + " fee " + fee);   // printing here too
        return fee;
    }
}
```

A change in pricing or in the receipt format both edit `ParkingLot`, and testing the fee needs a full lot.

Fix: each concern gets its own class, and `ParkingLot` coordinates them.

```java
class ParkingLot {
    private final PricingStrategy pricing;
    private final ReceiptPrinter printer;

    long unpark(Ticket t, LocalDateTime exit) {
        free(t.spot());
        long fee = pricing.fee(t, exit);
        printer.print(t, fee);
        return fee;
    }
}
```

## Open/closed

A module should be open for extension and closed for modification: new behaviour is added by writing new code, not by editing working code.

Violation: every new vehicle type or pricing rule is another branch.

```java
long fee(Ticket t, long hours) {
    switch (t.vehicle().type()) {
        case MOTORCYCLE: return hours * 10;
        case CAR:        return hours * 20;
        case TRUCK:      return hours * 50;
        // EV? weekend? each one edits this method, and every other switch like it
    }
    throw new IllegalArgumentException();
}
```

Fix: the varying part is an interface ([Strategy](17f_behavioral_patterns.md#strategy)); a new rule is a new class.

```java
interface PricingStrategy { long fee(Ticket t, LocalDateTime exit); }

class HourlyPricing implements PricingStrategy {
    private final Map<SpotSize, Long> ratePerHour;
    HourlyPricing(Map<SpotSize, Long> ratePerHour) { this.ratePerHour = ratePerHour; }

    public long fee(Ticket t, LocalDateTime exit) {
        long hours = Math.max(1, Duration.between(t.entry(), exit).toHours());
        return hours * ratePerHour.get(t.spot().size());
    }
}

class WeekendPricing implements PricingStrategy {
    private final PricingStrategy weekday;
    WeekendPricing(PricingStrategy weekday) { this.weekday = weekday; }

    public long fee(Ticket t, LocalDateTime exit) {
        DayOfWeek d = exit.getDayOfWeek();
        boolean weekend = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
        return weekend ? weekday.fee(t, exit) / 2 : weekday.fee(t, exit);
    }
}
```

A `switch` on an enum is not always wrong. If the set of cases is closed (the days of the week) and the logic is in one place, a `switch` is simpler. It becomes a problem when the same `switch` is repeated in several places and the set of cases grows.

## Liskov substitution

A subtype must be usable anywhere its parent is expected, without the caller noticing. Concretely: it may not demand more (stronger preconditions), promise less (weaker postconditions), or throw where the parent does not.

Violation:

```java
class Spot {
    void park(Vehicle v) { /* store it */ }
}

class OutOfServiceSpot extends Spot {
    @Override
    void park(Vehicle v) { throw new UnsupportedOperationException(); }
}
```

The concrete failure: `ParkingLot` loops over `List<Spot>`, finds one that `isFree()`, calls `park`, and crashes at a gate with a car waiting. Every caller now needs `instanceof OutOfServiceSpot` checks, which is exactly what polymorphism was meant to remove.

Fix: model the condition as state the parent contract already covers, so no subclass has to break it.

```java
class Spot {
    private boolean inService = true;
    private Vehicle parked;

    boolean canFit(Vehicle v) {
        return inService && parked == null && size.fits(v.type());
    }
}
```

The classic version of this mistake is `Square extends Rectangle`: `setWidth` on a square must also change the height, so code that sets width 5 and height 4 and expects area 20 gets 16.

## Interface segregation

Clients should not depend on methods they do not use. A fat interface forces every implementation to write stubs, and those stubs usually throw, which breaks Liskov too.

Violation:

```java
interface ParkingMachine {
    Ticket issueTicket(Vehicle v);
    long acceptPayment(Ticket t);
    void printReport();
}

class EntryGate implements ParkingMachine {
    public Ticket issueTicket(Vehicle v) { ... }
    public long acceptPayment(Ticket t) { throw new UnsupportedOperationException(); }
    public void printReport() { throw new UnsupportedOperationException(); }
}
```

Fix: small role interfaces; a class implements the roles it really has.

```java
interface TicketIssuer   { Ticket issueTicket(Vehicle v); }
interface PaymentAcceptor { long acceptPayment(Ticket t); }

class EntryGate implements TicketIssuer { ... }
class ExitGate  implements PaymentAcceptor { ... }
class Kiosk     implements TicketIssuer, PaymentAcceptor { ... }
```

## Dependency inversion

High-level policy should not depend on low-level details; both should depend on an abstraction, and the high-level module owns that abstraction.

Violation: the lot builds its own concrete gateway.

```java
class ParkingLot {
    private final StripeClient payments = new StripeClient("sk_live_...");
}
```

The lot cannot be tested without calling Stripe, and changing provider means editing `ParkingLot`.

Fix: the lot declares the interface it needs and receives an implementation through the constructor (**dependency injection**).

```java
interface PaymentGateway { boolean charge(String account, long amount); }

class ParkingLot {
    private final PaymentGateway payments;
    ParkingLot(PaymentGateway payments) { this.payments = payments; }
}

// production
new ParkingLot(new StripePaymentGateway(client));
// tests
new ParkingLot((account, amount) -> true);
```

This is what makes the stage tests in `21_lld` possible: every external dependency (clock, payment, notifier) is an interface passed in, so a test can replace it with a fake. Passing the time as a parameter (or injecting a `java.time.Clock`) is the same idea applied to `now()`.

## Sources

- R. C. Martin, *Design Principles and Design Patterns* (2000), the paper that collected the five principles; the SOLID acronym came later.
- B. Liskov, *Data Abstraction and Hierarchy*, OOPSLA 1987 keynote, origin of the substitution principle.
- E. Gamma, R. Helm, R. Johnson, J. Vlissides, *Design Patterns* (1994), for "favor object composition over class inheritance".
- [refactoring.guru](https://refactoring.guru/design-patterns), for readable pattern write-ups with Java examples.
