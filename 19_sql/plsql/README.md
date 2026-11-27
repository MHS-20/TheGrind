# PL/SQL problems

Problems solved with Oracle PL/SQL: procedures and functions over a small schema, each with example data and the expected output. Every solution was run on Oracle Database Free 23.26.

Each folder has a `README.md` (the problem, an example, the approach), a `solution.sql`, and either a `schema.sql` with its own tables and data, or a dependency on the shared [Northwind](northwind/) data. The lessons are [16j–16m](../../00_docs/16_sql_databases/16j_plsql_blocks.md).

| Problem | Technique |
|---|---|
| [Customer Orders](nw1_customer_orders/) | nested cursors, parameterized cursor |
| [Customer Tiers](nw2_customer_tiers/) | grouped count, `LEFT JOIN` for zero counts, `CASE` |
| [Category Correlation](nw3_category_correlation/) | self-join on order lines, counting unordered pairs |
| [Employee Productivity](nw4_employee_productivity/) | ratios to period totals in one grouped query |
| [Region Report](nw5_region_report/) | top-1 with `ROWNUM`, top-10 with `FETCH FIRST` |
| [Restock Report](nw6_restock_report/) | joins as filters, `NO_DATA_FOUND` |
| [Ski School Assignment](ski_school_assignment/) | nested cursors vs one `INSERT ... SELECT` with `HAVING` |
| [Five-a-Side Teams](five_a_side_teams/) | stateful cursor loop, user-defined exception |
| [Costliest Hospital Stay](costliest_hospital_stay/) | cost formula in the cursor, fetch only the first row |
| [Operator Rating](operator_rating/) | two sums folded into one with a ±1 factor |
| [Most Livable Cities](most_livable_cities/) | two-step ranking with CTEs, pivot with `CASE` |
| [Comics Correlation](comics_correlation/) | `IN` subquery vs a multiplying join |
| [Protein Score](protein_score/) | weight table as one `CASE`, average of averages |
| [Book Royalties](book_royalties/) | interval overlap, cursor with parameters |
| [Election Results](election_results/) | top-N per group with `%ROWCOUNT` |
