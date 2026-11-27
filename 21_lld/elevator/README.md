# Elevator System

A building with elevator cars that serve requests from floor buttons and from buttons inside the cars.

## Stages

1. **Core model.** One car, floors 0 to N. Requests come from inside the car (go to floor f) or from a floor (up or down button at floor f). Time advances in ticks with `step()`: each tick the car moves one floor or opens its doors at a requested floor. The car serves requests in its current direction before reversing (the SCAN or "elevator" algorithm). `state()` reports each car's floor, direction and pending stops.
2. **Several cars and scheduling.** The building has k cars. A floor request is assigned to one car by a dispatcher. The rule must be swappable: nearest idle car, or the car that will pass the floor soonest in the right direction. A car can be put in maintenance and gets no new requests.
3. **Concurrency.** Buttons are pressed from many threads while a clock thread calls `step()`. No request is lost or assigned twice, and `state()` never shows a half-updated car.

## Entities

- `ElevatorSystem`: the facade; receives requests, owns the cars and the dispatcher, advances time.
- `Car`: id, current floor, direction, status (moving, doors open, idle, maintenance), and its stops.
- `Request`: record of floor and direction (or a car-internal destination).
- `Dispatcher`: interface choosing a car for a floor request (stage 2).
- `Direction`: UP, DOWN, IDLE.

## Class diagram

See [diagram.md](diagram.md).

## Patterns used

- **Strategy** for the dispatcher (stage 2).
- **State** for the car's status, if its behaviour per status grows; an enum is enough at first.
- **Command**: each request is an object queued to a car.
- **Mediator**: the system sits between floor buttons and cars; buttons never talk to a car.

## Design notes

- **Two sorted sets per car** (stops above and below) make SCAN simple: going up, the next stop is `up.ceiling(floor)`; when it is empty, reverse.
- **Simulated time** (`step()`) instead of real threads per car makes stages 1 and 2 testable without sleeps.
- **Stage 3 locking**: one lock per car protects its stops and floor; the dispatcher reads a car's snapshot under that lock. Assigning a request takes the system lock briefly so two threads cannot assign the same floor call twice. `state()` returns immutable snapshots.

## Compared with other solutions

<Fill in after solving.>

## Files

`ElevatorSystem.java`: the solution, with checks for each stage in `main`.

`diagram.md`: the class diagram (Mermaid).
