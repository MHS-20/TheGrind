# Customer Orders

Write a procedure `customer_orders(p_customer)` that prints every order of a customer, in date order, each followed by its lines (product and quantity).

Uses the Northwind sample data: load [`../northwind/northwind.sql`](../northwind/) first, then run `solution.sql`.

## Example

`customer_orders('ANATR')`:

```
Order 10308 on 1996-09-18:
- product 69 quantity 1
- product 70 quantity 5
Order 10625 on 1997-08-08:
- product 14 quantity 3
- product 42 quantity 5
- product 60 quantity 10
Order 10759 on 1997-11-28:
- product 32 quantity 10
Order 10926 on 1998-03-04:
- product 11 quantity 2
- product 13 quantity 10
- product 19 quantity 7
- product 72 quantity 10
```

## Approach

Two cursors: one over the customer's orders, and a parameterized one over the lines of an order, opened once per order in a nested cursor `FOR` loop. A customer with no orders prints nothing, because a loop over an empty cursor runs zero times.

A single query joining orders and lines, printing the order header when the order id changes, does the same work in one query; the nested form is the one to know because it is the shape of most report procedures.

Lesson: [PL/SQL cursors](../../../00_docs/16_sql_databases/16k_plsql_cursors.md).
