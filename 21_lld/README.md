# Low-Level Design Problems

Object-oriented design problems of the kind asked in LLD or machine-coding rounds: design the classes for a parking lot, an elevator or a booking system, then extend the design when a new requirement arrives. The concepts (SOLID, class diagrams, patterns, concurrency) are in the [object-oriented design lessons](../00_docs/17_object_oriented_design/).

These differ from the [practical problems](../20_practical/), which are judged on a program that stays correct as a spec grows. Here the class model is what is judged, and the tests only check that a later stage does not break an earlier one.

## Format

Each problem folder has:

- `README.md`: the requirements in three stages, then the design. Read one stage at a time to practise as in an interview.
- `diagram.md`: the class diagram, in Mermaid.
- one Java file with the solution for every stage, and a `main` that runs checks grouped by stage. Run it with `java <Name>.java` (Java 21 or later, no build tool).

The stages follow the same shape in every problem:

1. **Core model.** The entities and the main flow.
2. **Extension.** A new type or rule that should need a new class, not edits across existing ones.
3. **Concurrency or a hard rule.** Two users acting at once, or a constraint that cuts across classes.

The tests call the public API only (park, book, split), not internal classes, so the design can be refactored without rewriting them. About three to six checks per stage is enough.

## README template

```markdown
# <Problem>

<One sentence: what the system does.>

## Stages

1. **Core model.** ...
2. **Extension.** ...
3. **Concurrency.** ...

## Entities

<Classes and what each one owns.>

## Patterns used

<Pattern, where, and which stage required it. Link to the lesson.>

## Design notes

<Choices and the alternative each one rejects, with its concrete failure.>
```

## Problems

Rate limiter, LRU cache and the key-value store with transactions are in [practical](../20_practical/) and are not repeated here.

Tier 1 is what Amazon-style LLD rounds ask most; tier 2 is next. Ride sharing, food delivery and online shopping are planned as tier 3.

| Problem | Tier | Level | Stages | Status |
|---|---|---|---|---|
| [Parking lot](parking_lot/) | 1 | Easy | spots and tickets, EV spots and weekend pricing, concurrent gates | solved |
| [Vending machine](vending_machine/) | 1 | Easy | selection and payment, states and change, restocking | solved |
| [Library management](library_management/) | 1 | Easy | books and loans, reservations and fines, member types | solved |
| [File search (Unix find)](file_search/) | 1 | Medium | basic filters, AND/OR/NOT, large trees | solved |
| [Amazon Locker](amazon_locker/) | 1 | Medium | slots and codes, expiry and locations, concurrent couriers | solved |
| [ATM](atm/) | 1 | Medium | session and PIN, note dispensing, debit/dispense consistency | solved |
| [Elevator system](elevator/) | 1 | Medium | one car (SCAN), several cars and dispatch, concurrent requests | solved |
| [Splitwise](splitwise/) | 1 | Medium | equal splits and balances, exact and percentage splits, simplifying debts | solved |
| [Movie booking](movie_booking/) | 1 | Hard | shows and seats, pricing and holds, two users on one seat | solved |
| [Tic-tac-toe](tic_tac_toe/) | 2 | Easy | board and turns, n×n and k in a row, undo and bots | solved |
| [Snake and ladder](snake_and_ladder/) | 2 | Medium | board and players, dice and rule variants, several games | solved |
| [Logging framework](logger/) | 2 | Medium | levels and appenders, hierarchy and formatters, async appender | solved |
| [Pub-sub](pub_sub/) | 2 | Medium | topics and subscribers, filters and offsets, concurrent delivery | solved |
| [Chess](chess/) | 2 | Hard | board and moves, check and checkmate, undo | solved |

When a problem is solved, its status becomes solved and its patterns are added to the "Used in" tables of the pattern lessons.
