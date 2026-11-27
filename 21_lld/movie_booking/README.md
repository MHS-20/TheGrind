# Movie Ticket Booking

A system for theatres, shows and seats, where users choose seats and pay for a booking.

## Stages

1. **Core model.** Cities have theatres, theatres have screens with a seat layout, and a show is a movie on a screen at a time. Users search shows by movie and city, see free seats, and book a set of seats. Booking a taken seat fails and books nothing (all or nothing). A booking can be cancelled, which frees its seats.
2. **Pricing and holds.** Seats have categories (regular, premium, recliner) with different prices; evening shows cost 20% more. Booking is two steps: `hold(seats)` reserves them for 5 minutes, `confirm(hold, payment)` pays and books. An expired hold frees its seats. Payment is an interface and may fail.
3. **Concurrency.** Many users hold seats on the same show at once. A seat is never held or booked by two users, and holding seats on different shows does not block each other.

## Entities

- `BookingService`: the facade.
- `Movie`, `City`, `Theatre`, `Screen`, `Seat` (row, number, category).
- `Show`: movie, screen, start time, and the state of each seat (free, held by X until T, booked).
- `Hold`: id, show, seats, user, expiry. `Booking`: id, show, seats, user, amount.
- `PricingStrategy` (stage 2), `PaymentGateway`, `Clock`.

## Patterns used

- **Facade**: `BookingService` over catalogue, seats, payment.
- **Strategy** for pricing; **Decorator** for the evening surcharge on top of category prices.
- **Builder** for a booking request with optional fields, if it grows.
- **Adapter** around a payment provider.

## Design notes

- **All or nothing**: check every requested seat, then mark them, inside one critical section; never mark seat by seat with a check in between.
- **Lock per show** (stage 3) is the answer to give first: shows are independent, and seats of one show are booked together. Per-seat locks need sorted lock order to avoid deadlock; see [concurrency](../../00_docs/17_object_oriented_design/17g_concurrency_basics.md#lock-granularity).
- **Expired holds are checked lazily**: a seat held until a past time counts as free. No background thread is needed, and the injected clock keeps tests deterministic.
- **Payment outside the lock**: confirming takes the lock to validate the hold, releases it to call payment, then takes it again to book. The hold guarantees the seats meanwhile; if it expired during payment, refund.
