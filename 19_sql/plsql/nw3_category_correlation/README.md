# Category Correlation

Write a function `category_correlation(p_cat1, p_cat2)` that measures how often products of two categories are bought together: the number of times a product of category `p_cat1` appears in an order together with a product of category `p_cat2`. When both categories are the same, each pair of products in an order counts once.

Uses the Northwind sample data: load [`../northwind/northwind.sql`](../northwind/) first, then run `solution.sql`.

## Example

```
category_correlation(1, 2) = 108
category_correlation(2, 1) = 108
category_correlation(1, 8) = 145
category_correlation(1, 1) = 55
```

## Approach

A self-join of `order_details` on `OrderID` pairs every line of an order with every other line of the same order; joining `products` on each side gives their categories, and `count(*)` counts the pairs.

For two different categories the function is symmetric, and each pair (A, B) appears once. For the same category, the join produces both (A, B) and (B, A), doubling the count (110 instead of 55). The condition `d1.ProductID < d2.ProductID`, applied only when the categories are equal, keeps one of the two and also excludes pairing a line with itself.

The cursor version (loop over the lines of category 1, count the matching lines of category 2 in the same order) is N+1 queries; one query is enough.

Lesson: [procedures and exceptions](../../../00_docs/16_sql_databases/16l_plsql_procedures_and_exceptions.md).
