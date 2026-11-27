# 176. Second Highest Salary

Return the second highest distinct salary as `SecondHighestSalary`, or `NULL` if there is none.

Wrapping the query in a scalar subquery turns "no row" into one row with `NULL`. `DISTINCT` makes two equal top salaries count once.

Lesson: [subqueries and CTEs](../../00_docs/16_sql_databases/16d_subqueries_and_ctes.md).
