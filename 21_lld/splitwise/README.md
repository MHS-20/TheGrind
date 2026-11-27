# Splitwise

An expense-sharing system: users add expenses paid by one person for a group, and the system tracks who owes whom.

## Stages

1. **Core model.** Users and groups. An expense has a payer, an amount in cents, and participants; it is split equally (leftover cents go to the first participants). `balances(user)` lists, for each other user, the net amount owed in either direction. Amounts never use floating point.
2. **Split types.** Expenses can also be split by exact amounts (must sum to the total) or by percentages (must sum to 100; the last participant absorbs rounding). An invalid split is rejected and changes nothing. Adding a new split type must not edit the expense code.
3. **Simplify debts.** `settleUp(group)` returns a list of payments that clears every balance in the group with as few payments as practical (greedy: largest debtor pays largest creditor). Recording a payment updates balances.

## Entities

- `ExpenseService`: the facade.
- `User`, `Group` (members, expenses).
- `Expense`: id, payer, amount, the computed shares.
- `SplitStrategy`: `EqualSplit`, `ExactSplit`, `PercentSplit`; validates input and returns shares.
- `BalanceSheet`: net amount per pair of users (or per user within a group).
- `Payment`: record of from, to, amount.

## Class diagram

See [diagram.md](diagram.md).

## Patterns used

- **Strategy** for splits (stage 2): the main point of the problem. See [behavioral patterns](../../00_docs/17_object_oriented_design/17f_behavioral_patterns.md#strategy).
- **Factory** to create a strategy from a type name, if the input is text.
- **Observer** if a notification on new expenses is asked for.

## Design notes

- **Money in `long` cents.** `double` cannot represent 0.10 exactly; splits of 1000 among 3 must give 334, 333, 333 and sum back to 1000.
- **Net balances per user** (`+` is owed, `-` owes) make settling simple: the sum over a group is always 0, which is the invariant to check.
- **Minimum number of payments is NP-hard** in general; the greedy largest-debtor-to-largest-creditor gives at most n−1 payments. Say this rather than claim it is optimal.

## Compared with other solutions

<Fill in after solving.>

## Files

`ExpenseService.java`: the solution, with checks for each stage in `main`.

`diagram.md`: the class diagram (Mermaid).
