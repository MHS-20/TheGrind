# Operator Rating

A call centre rates its operators. Every call has a problem with a difficulty in (0, 1], a result (1 solved, −1 not solved, 0 still open) and operations with a duration.

```
customers(id, name, contract)
operators(id, name, op_level)
problems(code, description, difficulty)
calls(id, customer, operator, problem, opened, result)
operations(call_id, done_at, minutes, description)
```

For each operator, from a date on, with minutes weighted by the problem's difficulty:

- VPos = weighted minutes spent on calls with result 1;
- VNeg = weighted minutes spent on calls with result −1;
- rating = (VPos − VNeg) / (VPos + VNeg).

Open calls do not count. Write a procedure `operator_rating(p_from)` that ranks the operators and marks those with a negative rating.

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

`operator_rating(DATE '2026-11-01')`:

```
Rita  0.765
Tom   0.000
Sam   -0.818  <- negative
```

Rita's bad call from October is outside the period, and Tom's 90-minute open call is ignored.

## Approach

Since `result` is 1 or −1 for closed calls, both sums come from one aggregate:

```
VPos − VNeg = Σ minutes × difficulty × result
VPos + VNeg = Σ minutes × difficulty
```

so one grouped query computes every rating, with `result <> 0` excluding open calls. The version with two queries per operator (one for VPos, one for VNeg) gives the same numbers with 2N queries.

Lesson: [a cursor loop vs one statement](../../../00_docs/16_sql_databases/16m_plsql_triggers.md#a-cursor-loop-vs-one-statement).
