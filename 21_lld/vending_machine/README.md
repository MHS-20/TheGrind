# Vending Machine

A machine that sells products from slots, accepts coins, and returns change.

## Stages

1. **Core model.** Slots have a code (`A1`), a product, a price and a quantity. The user inserts coins (5, 10, 25, 100 cents), selects a code, and gets the product and the change. Selecting with too little credit, an unknown code or an empty slot fails and keeps the credit. `cancel()` returns the credit.
2. **States and change.** The machine has states: idle, has money, dispensing, out of service. Each action is legal only in some states (inserting a coin while dispensing fails). The machine holds a finite stock of each coin: if it cannot make the exact change, the sale is refused and the credit stays. Change uses as few coins as possible.
3. **Restocking.** An operator puts the machine out of service, refills products and coins, and puts it back. User actions fail while it is out of service, and the operator cannot enter maintenance while a user has credit in the machine.

## Entities

- `VendingMachine`: the facade. Holds the current state, the credit, the inventory and the coin box.
- `VendingState` and one class per state: `Idle`, `HasMoney`, `Dispensing`, `OutOfService`.
- `Inventory`: slots by code. `Slot`: product, price, quantity.
- `CoinBox`: count per denomination; `makeChange(amount)` returns the coins or nothing.
- `Sale`: record of the product and the change returned.

## Patterns used

- **State** (stage 2): one class per state, default methods reject everything. See [behavioral patterns](../../00_docs/17_object_oriented_design/17f_behavioral_patterns.md#state).
- **Chain of responsibility** for making change, largest coin first, with a fallback when greedy fails on the available coins.

## Design notes

- **Credit is kept on failure.** A failed select must not consume coins; the state does not change.
- **Greedy change is not always possible with a limited coin stock** (owing 30 with one 25 and three 10s: greedy takes 25 and is stuck, three 10s work). With these four coin values a small search over counts is enough; say so instead of assuming greedy.
- **Enum vs classes.** Four states with little data each could be an enum with overridden methods. Classes are used to show the pattern; the table above is the real spec either way.
