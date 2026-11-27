# ATM

A cash machine that authenticates a card, shows a balance, and dispenses cash from a bank account.

## Stages

1. **Core model.** The user inserts a card, enters a PIN, then checks the balance, withdraws or deposits, and ejects the card. Three wrong PINs block the card. The bank is an interface the ATM calls; withdrawals fail on insufficient funds.
2. **Cash dispensing.** The ATM holds notes of 100, 50 and 20 in finite numbers. A withdrawal must be payable with the notes available (largest first, falling back when greedy fails); if not, it is refused and the account is not debited. A daily withdrawal limit per card applies.
3. **Consistency.** The debit at the bank and the cash leaving the machine must agree: if dispensing fails after the bank debited the account, the debit is reversed. Two ATMs withdrawing from the same account at once never take it below zero.

## Entities

- `Atm`: the facade; holds the session state and the cash dispenser.
- `AtmState`: `Idle`, `CardInserted`, `Authenticated` (and `Blocked` card handling).
- `Bank`: interface (`verifyPin`, `balance`, `debit`, `credit`, `reverse`).
- `Account`: balance, guarded by its own lock (inside the in-memory bank).
- `CashDispenser`: chain of note handlers over the note inventory.
- `Card`: record of number; the bank tracks failed attempts and blocks.

## Class diagram

```
+-------------------+  state  +------------------+
| Atm               |-------->| <<interface>>    |
+-------------------+         | AtmState         |
| + insert(card)    |         +------------------+
| + enterPin(pin)   |          △      △        △
| + withdraw(amt)   |        Idle CardInserted Authenticated
| + deposit(amt)    |
| + eject()         |  1   1  +------------------+
+-------------------+-------->| <<interface>>    |
     ◆ 1                      | Bank             |
     |                        +------------------+
+-------------------+                 △
| CashDispenser     |                 :
+-------------------+          InMemoryBank ◆--- * Account
| 100 -> 50 -> 20   |
+-------------------+
```

## Patterns used

- **State** for the session (stage 1).
- **Chain of responsibility** for notes (stage 2). See [behavioral patterns](../../00_docs/17_object_oriented_design/17f_behavioral_patterns.md#chain-of-responsibility).
- **Adapter**: a real bank client would sit behind the `Bank` interface.

## Design notes

- **Plan, then debit, then dispense.** Compute the notes first without removing them; debit the bank; then take the notes. If the plan is impossible, nothing was debited. This ordering makes stage 3's reversal rare.
- **Compensation instead of a transaction**: the ATM and the bank are separate systems, so a failed dispense calls `reverse(txId)`. The transaction id makes the reversal idempotent.
- **Per-account lock** in the bank: `debit` checks and subtracts under the account's lock, so two ATMs cannot both see enough money.

## Compared with other solutions

<Fill in after solving.>

## Files

`Atm.java`: the solution, with checks for each stage in `main`.
