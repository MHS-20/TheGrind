# Employee Productivity

Write a procedure `employee_productivity(p_from, p_to)` that ranks employees by a fitness score over a period, F = F1 + F2 + F3:

- F1 = customers the employee served / customers who ordered in the period;
- F2 = days the employee handled at least one order / days with at least one order;
- F3 = the employee's orders / all orders in the period.

Print each employee with F and its three parts, best first.

Uses the Northwind sample data: load [`../northwind/northwind.sql`](../northwind/) first, then run `solution.sql`.

## Example

`employee_productivity(DATE '1996-01-29', DATE '1997-05-29')`:

```
Peacock Margaret F=0.985 (0.543, 0.242, 0.200)
Callahan Laura F=0.771 (0.444, 0.182, 0.144)
Leverling Janet F=0.756 (0.407, 0.195, 0.154)
Davolio Nancy F=0.710 (0.407, 0.165, 0.138)
Fuller Andrew F=0.559 (0.333, 0.127, 0.098)
King Robert F=0.540 (0.333, 0.114, 0.092)
Suyama Michael F=0.475 (0.272, 0.114, 0.089)
Buchanan Steven F=0.293 (0.173, 0.068, 0.052)
Dodsworth Anne F=0.199 (0.123, 0.042, 0.033)
```

## Approach

Every part is a ratio of a per-employee count to the same count over the whole period, so one query computes both: a CTE `p` with the orders in the period, a one-row CTE `tot` with the three totals, and a `GROUP BY` employee over `p` cross-joined with `tot`. Ordering by `f1 + f2 + f3` puts the ranking in SQL.

The denominators are **distinct** counts: `count(DISTINCT CustomerID)` and `count(DISTINCT OrderDate)`, not the number of orders. Computing them once, instead of in a query per employee, is the main difference from a cursor-per-employee solution.

Lesson: [grouping](../../../00_docs/16_sql_databases/16c_grouping.md), [a cursor loop vs one statement](../../../00_docs/16_sql_databases/16m_plsql_triggers.md#a-cursor-loop-vs-one-statement).
