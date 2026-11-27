# Class Diagrams

A class diagram shows the classes of a design, their main fields and methods, and the relations between them. In an interview it is drawn before code, so that the interviewer can correct the model while it is still cheap to change. Only a small part of UML is needed: classes, interfaces, and five kinds of relation.

## Classes and interfaces

A class is a box with three parts: name, fields, methods. Visibility is a one-character prefix.

```
+---------------------------+
| Spot                      |
+---------------------------+
| - id: String              |
| - size: SpotSize          |
| - parked: Vehicle         |
+---------------------------+
| + canFit(v): boolean      |
| + park(v): void           |
| + free(): Vehicle         |
+---------------------------+
```

| Marker | Visibility |
|--------|------------|
| `+` | public |
| `-` | private |
| `#` | protected |
| `~` | package-private |

An interface gets `<<interface>>` above its name and usually only methods. An abstract class gets `<<abstract>>` or an italic name. Enums get `<<enum>>` and list their values.

What to leave out: getters, setters, constructors, `toString`, and any field that is only an implementation detail. The diagram is for the model, not a copy of the code. In an interview, a box with just a name and two key methods is often enough.

## Relations

```
A ───────> B     association      A has a reference to B
A ◇─────── B     aggregation      A has Bs, Bs can exist without A
A ◆─────── B     composition      A owns Bs, Bs die with A
A ──────|> B     inheritance      A extends B          (solid line, hollow triangle)
A - - - |> B     implementation   A implements B       (dashed line, hollow triangle)
A - - - -> B     dependency       A uses B briefly (parameter, local variable)
```

The diamond sits on the side of the whole.

**Association**: a long-lived reference. A `Ticket` refers to the `Spot` it was issued for.

```java
record Ticket(String id, Vehicle vehicle, Spot spot, LocalDateTime entry) {}
```

**Aggregation**: a whole holding parts that have a life of their own. A `Team` has `Player`s; delete the team, the players still exist and can join another one.

```java
class Team { private final List<Player> players; }   // players are passed in, shared
```

**Composition**: the whole creates and owns its parts; they make no sense alone. A `Floor` creates its `Spot`s, and a spot never moves to another floor.

```java
class Floor {
    private final List<Spot> spots = new ArrayList<>();
    Floor(int small, int medium, int large) {
        for (int i = 0; i < small; i++) spots.add(new Spot(SpotSize.SMALL));
        ...
    }
}
```

The difference between aggregation and composition matters when deciding **who creates and who deletes**: with composition, the owner's constructor builds the parts and no outside code holds them. In an interview, if unsure, draw a plain association; nobody fails a round on a diamond.

**Inheritance and implementation**: `Car ──|> Vehicle` if `Vehicle` is an abstract class, `HourlyPricing - - |> PricingStrategy` for an interface.

**Dependency**: the weakest relation. `ParkingLot.unpark` takes a `LocalDateTime`; the lot does not store it.

## Multiplicity

Numbers at the ends of a relation say how many objects take part.

| Multiplicity | Meaning | Java field |
|--------------|---------|------------|
| `1` | exactly one | `final Spot spot` (non-null) |
| `0..1` | optional | nullable field, or `Optional<T>` as a return type |
| `*` or `0..*` | any number | `List<T>`, `Set<T>`, `Map<K, T>` |
| `1..*` | at least one | collection, checked non-empty in the constructor |

The collection type carries meaning too: `List` when order matters, `Set` when duplicates are impossible, `Map` when objects are looked up by key (tickets by id).

## A full example

The parking lot from [the LLD round](16a_the_lld_round.md):

```
              +--------------------+      1  +------------------------+
              | ParkingLot         |◇------->| <<interface>>          |
              +--------------------+         | PricingStrategy        |
              | + park(v): Ticket  |         +------------------------+
              | + unpark(t): long  |         | + fee(t, exit): long   |
              +--------------------+         +------------------------+
                  ◆ 1                              △         △
                  |                                :         :
                  | 1..*                 HourlyPricing     WeekendPricing
              +--------------------+
              | Floor              |
              +--------------------+
                  ◆ 1
                  | 1..*
              +--------------------+  1     0..1  +-------------------+
              | Spot               |------------->| Vehicle           |
              +--------------------+              +-------------------+
              | - size: SpotSize   |              | - plate: String   |
              | + canFit(v)        |              | - type: VehicleType|
              +--------------------+              +-------------------+
```

## Drawing them in this repo

Diagrams in these pages are plain text in a fenced block, as above: they render the same on GitHub, in the editor, and on the site, and they are quick to edit.

Mermaid has a `classDiagram` syntax that looks nicer, but MkDocs Material only renders it when `pymdownx.superfences` has a `mermaid` custom fence in `mkdocs.yml`. Check that config before writing a mermaid block, or it will show up as raw text.
