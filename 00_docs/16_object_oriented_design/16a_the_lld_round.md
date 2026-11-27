# The LLD Round

A low-level design round asks for the classes behind a small system, such as a parking lot or an elevator, and usually for working code in 45 to 90 minutes. The interviewer judges the model of the domain and how it absorbs a new requirement, not the algorithm. This page gives an order of work for the round, so that the first 10 minutes produce requirements and entities instead of code.

A rough budget for a 60-minute round:

```
 0-10  requirements, written down, agreed
10-20  entities, responsibilities, class diagram
20-50  code: interfaces, one happy path, then edge cases
50-60  extension or concurrency follow-up, trade-offs
```

## Clarifying requirements

The prompt is vague on purpose ("design a parking lot"). Asking questions is part of what is graded: it shows that scope is chosen, not assumed.

Split the questions in two groups.

- **Functional**: what the system does. Who are the actors, what can each one do, what does the system return.
- **Non-functional**: how it behaves. Number of concurrent users, whether state survives a restart, whether it is one process or distributed. In an LLD round the answer is almost always "one process, in memory, thread-safe", but say it.

Then say what is **out of scope**: payment processing, a database, a UI, authentication. This protects the time budget.

For a parking lot, a good set of questions:

1. Which vehicle types exist (motorcycle, car, truck)? Can a small vehicle use a bigger spot?
2. How many floors? Is a spot assigned at entry, or does the driver choose?
3. How is the fee computed: flat, per hour, per vehicle type? Is it likely to change?
4. Several entry and exit gates at the same time? (This is the concurrency hint.)
5. What happens when the lot is full?

Write the agreed list where the interviewer can see it, then design against it:

```
- vehicles: MOTORCYCLE, CAR, TRUCK; spot sizes SMALL, MEDIUM, LARGE
- a vehicle takes the smallest free spot that fits
- park() returns a ticket, or fails if full
- unpark(ticket) frees the spot and returns the fee (hourly, rate per size)
- several gates call park/unpark concurrently
- out of scope: payment, persistence, reservations
```

## Finding entities and responsibilities

Read the requirement list and mark nouns and verbs. Nouns are candidate classes, verbs are candidate methods. Not every noun becomes a class: a noun with no behaviour and no identity is a field or an enum.

| Noun | Becomes |
|------|---------|
| parking lot | `ParkingLot`, the entry point |
| floor | `Floor`, owns its spots |
| spot | `Spot`, knows its size and whether it is free |
| vehicle | `Vehicle` record with a `VehicleType` |
| ticket | `Ticket` record: vehicle, spot, entry time |
| fee | `PricingStrategy` interface, because the rule may change |

For each class, decide what state it **owns**. The rule is that the class holding the data makes the decisions about it ("tell, don't ask"). A `Spot` decides whether a vehicle fits it; `ParkingLot` does not read the spot's size and compare it itself.

```java
// Ask: the caller pulls data out and decides.
if (spot.getSize().ordinal() >= vehicle.type().minSize().ordinal() && spot.isFree()) { ... }

// Tell: the object that owns the data decides.
if (spot.canFit(vehicle)) { ... }
```

## From class diagram to code

1. **Sketch the diagram** ([class diagrams](16c_class_diagrams.md)): classes, the relations, the multiplicities. Ask "does this match what you had in mind?" before coding.
2. **Write the interfaces and records first.** They are the contract, they are short, and the interviewer can review them in a minute.
3. **One happy path end to end**: park a car, unpark it, get a fee. Running code early beats a complete design with nothing running.
4. **Edge cases next**: lot full, unknown ticket, unpark twice.
5. **Concurrency last**, unless the prompt leads with it ([concurrency for LLD](16g_concurrency_basics.md)).

A small `main` with checks is worth more than claims that the code works:

```java
public static void main(String[] args) {
    var lot = new ParkingLot(List.of(new Floor(2, 1, 0)), new HourlyPricing());
    Ticket t = lot.park(new Vehicle("AB123", VehicleType.CAR)).orElseThrow();
    check(lot.park(new Vehicle("CD456", VehicleType.CAR)).isEmpty(), "full for cars");
    check(lot.unpark(t, t.entry().plusHours(2)) == 40, "2h at 20/h");
}
```

## Handling an extension

The second half of most rounds adds a requirement: "add electric spots with charging", "weekend pricing", "notify the driver when the lot has space". This is where the design is tested.

A good design absorbs it by **adding a class**. A weak one absorbs it by **editing many classes** (another `case` in each `switch`). This is the [Open/closed principle](16b_oop_and_solid.md#openclosed).

Say the effect out loud: "weekend pricing is a new `PricingStrategy`. `ParkingLot` does not change, because it only knows the interface." If the change does touch several classes, say so too, and explain what would have avoided it. Interviewers value an honest trade-off over a pretend-perfect design.

## Common mistakes

- **God class.** A `ParkingLotManager` that holds every list, computes fees, prints tickets and checks sizes. Nothing can be changed or tested alone. Split by responsibility ([SRP](16b_oop_and_solid.md#single-responsibility)).
- **Anaemic model.** Entities that are only getters and setters, with all the logic in a manager. This is the god class again, with extra classes around it. Put behaviour next to the data it uses.
- **Inheritance for code reuse.** `Truck extends Car` because both have a plate. Inherit only for a real is-a whose behaviour can be substituted; otherwise use composition or a shared field.
- **Patterns without a requirement.** A factory of factories for three vehicle types. Each pattern should answer a requirement that was named ("pricing will change" justifies Strategy). If the interviewer asks "why is this here?" there must be an answer.
- **Coding before agreeing.** Twenty minutes of code for a requirement the interviewer did not want.
- **Silence.** The interviewer can only grade what they hear. Say the trade-off being made while making it.
