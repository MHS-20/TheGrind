# Region Report

Write a procedure `region_report(p_region)` that, for the customers of a region, prints the shipper used for the most orders and the 10 products bought in the largest quantity.

Uses the Northwind sample data: load [`../northwind/northwind.sql`](../northwind/) first, then run `solution.sql`.

## Example

`region_report('AK')`:

```
Most used shipper in AK: Federal Shipping (5 orders)
Top products in AK:
  Pavlova (120)
  Lakkalikööri (50)
  Schoggi Schokolade (50)
  Gnocchi di nonna Alice (44)
  Singaporean Hokkien Fried Mee (40)
  Gorgonzola Telino (34)
  Chartreuse verte (30)
  Escargots de Bourgogne (30)
  Carnarvon Tigers (25)
  Thüringer Rostbratwurst (25)
```

## Approach

Two grouped queries. The shipper is the first row of orders grouped by shipper, ordered by count, read with `SELECT INTO` from a subquery filtered by `ROWNUM = 1`. The top products use `FETCH FIRST 10 ROWS ONLY` on quantities grouped by product.

Ties (two products at 50, two at 25) have no defined order unless the query gives one; ordering by quantity and then name makes the output repeatable. A region with no orders makes the first `SELECT INTO` raise `NO_DATA_FOUND`, handled with a message.

`ROWNUM = 1` must be applied **outside** the subquery that sorts: `ROWNUM` is assigned before `ORDER BY` in the same query block.

Lesson: [PL/SQL blocks](../../../00_docs/16_sql_databases/16j_plsql_blocks.md), [procedures and exceptions](../../../00_docs/16_sql_databases/16l_plsql_procedures_and_exceptions.md).
