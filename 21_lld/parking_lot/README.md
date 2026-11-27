# Parking Lot

A multi-floor parking lot that assigns spots to vehicles at entry and charges a fee at exit.

## Stages

1. **Core model.** The lot has floors, each with spots of size SMALL, MEDIUM or LARGE. A motorcycle fits any spot, a car a MEDIUM or LARGE one, a truck only a LARGE one. `park(vehicle, time)` gives the vehicle the smallest free spot that fits (lowest floor first) and returns a ticket, or nothing if the lot is full for that vehicle. `unpark(ticket, time)` frees the spot and returns the fee: every started hour costs the spot size's hourly rate (10, 20, 50), minimum one hour. Unparking an unknown or already used ticket fails.
2. **Extension.** Some spots have an EV charger. An electric car must get a charging spot if one fits, otherwise a normal one. A charging spot adds a flat 30 to the fee. Weekend exits are charged at half the hourly rate. Neither rule may add a branch to `park` or `unpark`.
3. **Concurrency.** Several gates call `park` and `unpark` at the same time. No spot is ever given to two vehicles, and a ticket can be paid only once.

## Entities

- `ParkingLot`: the entry point. Owns the floors and the active tickets, and delegates the fee to a `PricingStrategy`.
- `Floor`: owns its spots, finds the best free one for a vehicle.
- `Spot`: size, features (charger), the vehicle parked in it. Decides `canFit(vehicle)`.
- `Vehicle`: record of plate and type (and whether it is electric).
- `Ticket`: record of id, vehicle, spot, entry time.
- `PricingStrategy`: computes the fee for a ticket and an exit time.
- `SpotSelector` (stage 2): orders the candidate spots for a vehicle.

## Patterns used

- **Strategy** for pricing (stage 1, pays off in stage 2): weekend pricing is a new class. See [behavioral patterns](../../00_docs/17_object_oriented_design/17f_behavioral_patterns.md#strategy).
- **Decorator** to combine pricing rules: `ChargingSurcharge(WeekendPricing(HourlyPricing))`. See [structural patterns](../../00_docs/17_object_oriented_design/17e_structural_patterns.md#decorator).
- **Facade**: `ParkingLot` is the only class the gates talk to.

## Design notes

- **Time is a parameter**, not `LocalDateTime.now()`, so the fee tests are deterministic.
- **The spot decides whether a vehicle fits** (tell, don't ask). A `switch` on vehicle type inside `ParkingLot` would be edited for every new type.
- **Lock granularity (stage 3).** A lock per floor lets gates on different floors work in parallel, but finding "the lowest floor with a free spot" then scans floors one by one and must claim the spot atomically (`compareAndSet` on the spot's occupant, or `synchronized` on the floor while choosing). One lock on the whole lot is simpler and is the first answer; per-floor locking is the follow-up.
- **Paying a ticket once**: remove it from a `ConcurrentHashMap` with `remove(id)`; only the thread that gets a non-null result frees the spot.
