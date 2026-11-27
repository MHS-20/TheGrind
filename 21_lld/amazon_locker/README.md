# Amazon Locker

Self-service lockers where a courier drops a package and the customer picks it up with a code.

## Stages

1. **Core model.** A locker location has slots of size SMALL, MEDIUM, LARGE. A package has a size and an order id. `assign(package)` reserves the smallest free slot that fits and returns it, or fails if none fits. The courier `deposit`s the package; the customer gets a 6-digit code and `pickup(code)` opens the slot and frees it. A wrong code fails.
2. **Expiry and returns.** A package not picked up in 3 days expires: its slot is marked for return, the courier collects it, and the slot is freed. A code works only once and not after expiry. Several locker locations exist; `assign` picks the nearest location with a fitting slot to the customer's address.
3. **Concurrency.** Many couriers assign and deposit at once, and customers pick up at the same time. A slot is never assigned to two packages, and a code never opens a slot twice.

## Entities

- `LockerService`: the facade; locations, codes, assignment.
- `LockerLocation`: id, position, slots.
- `Slot`: id, size, status (free, reserved, occupied, to return), the package in it.
- `Package`: order id, size.
- `PickupCode`: code, slot, expiry, used flag.
- `CodeGenerator`, `Clock`: injected, so tests control codes and time.
- `LocationSelector` (stage 2): chooses a location for an address.

## Patterns used

- **Strategy** for choosing the location (stage 2).
- **State** for the slot status, as an enum with allowed transitions checked in one method.
- **Facade**: `LockerService`.

## Design notes

- **Same "smallest fitting spot" logic as the parking lot.** Say so in the interview; the difference is the code and expiry lifecycle.
- **Codes are unguessable and unique among live codes**: generate random 6 digits and retry on collision. The generator is injected so tests are deterministic.
- **Expiry is checked lazily on pickup and by an `expire(now)` sweep**, instead of a timer thread per package.
- **Atomic claim**: a slot goes free → reserved with a compare-and-set (or under the location's lock); a code is consumed with `remove` from a `ConcurrentHashMap`, so only one pickup succeeds.
