# Customer Tiers

Write a procedure `customer_tiers(p_min, p_max)` that lists every customer with their number of orders and a tier: **high** if they placed more than `p_max` orders, **medium** from `p_min` to `p_max`, **low** below `p_min`. Most orders first, ties by name.

Uses the Northwind sample data: load [`../northwind/northwind.sql`](../northwind/) first, then run `solution.sql`.

## Example

`customer_tiers(5, 15)`, first and last rows:

```
Save-a-lot Markets: 31 orders, high
Ernst Handel: 30 orders, high
QUICK-Stop: 28 orders, high
...
Bon app': 17 orders, high
Frankenversand: 15 orders, medium
Lehmanns Marktstand: 15 orders, medium
...
Lazy K Kountry Store: 2 orders, low
Centro comercial Moctezuma: 1 orders, low
FISSA Fabrica Inter. Salchichas S.A.: 0 orders, low
Paris spécialités: 0 orders, low
```

## Approach

One grouped query does the counting and the ordering; the loop only formats. The tier is a `CASE` on the count.

The boundaries need care: 15 orders with `p_max = 15` is medium, so the test for high is `> p_max` and for medium `>= p_min`.

`LEFT JOIN` keeps the two customers who never ordered, with a count of 0. With an inner join they disappear from the list, which is wrong for a report that is supposed to list every customer. `count(o.OrderID)` (not `count(*)`) is what makes their count 0 instead of 1.

Lesson: [grouping](../../../00_docs/16_sql_databases/16c_grouping.md), [PL/SQL cursors](../../../00_docs/16_sql_databases/16k_plsql_cursors.md).
