# Restock Report

Write a procedure `restock_report(p_supplier)` that lists the products of a supplier whose units in stock are below their reorder level **and** that appear in at least one order not yet shipped, with the number of such orders.

Uses the Northwind sample data: load [`../northwind/northwind.sql`](../northwind/) first, then run `solution.sql`.

## Example

`restock_report(1)`:

```
Products to reorder from Exotic Liquids:
  Chang (17 in stock vs reorder level 25, 4 unshipped orders)
  Aniseed Syrup (13 in stock vs reorder level 25, 1 unshipped orders)
```

## Approach

Join products to their order lines and orders, filter on the supplier, `UnitsInStock < ReorderLevel` and `ShippedDate IS NULL`, and group by product to count the pending orders. The inner joins do the "at least one unshipped order" part: a product with none produces no rows.

An unknown supplier makes the first `SELECT INTO` fail with `NO_DATA_FOUND`, reported as a message instead of an error.

Lesson: [procedures and exceptions](../../../00_docs/16_sql_databases/16l_plsql_procedures_and_exceptions.md).
