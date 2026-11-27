# Normalization

Normalization is a set of rules for splitting tables so that each fact is stored once. A fact stored twice can be updated in one place and not the other, and then the database contradicts itself. This page shows those failures on real rows, defines the normal forms that prevent them (1NF to BCNF), and covers when storing a fact twice on purpose is worth it.

## One table for everything

A spreadsheet of bookings, the way a restaurant might keep them before having a database:

```sql
CREATE TABLE booking_sheet (
    booking_id      int,
    customer_email  text,
    customer_name   text,
    restaurant      text,
    restaurant_city text,
    table_numbers   text,
    starts_at       timestamp
);
```

```
 booking_id | customer_email  | customer_name | restaurant | restaurant_city | table_numbers |      starts_at
------------+-----------------+---------------+------------+-----------------+---------------+---------------------
          1 | ada@example.com | Ada           | Trattoria  | Bologna         | 1,2           | 2026-12-05 20:00:00
          2 | bob@example.com | Bob           | Trattoria  | Bologna         | 3             | 2026-12-05 20:00:00
          3 | ada@example.com | Ada           | Sushi Bar  | Milano          | 1             | 2026-12-06 13:00:00
```

It is easy to read and to insert into. The problems appear when the data changes.

## The three anomalies

**Update anomaly.** Ada's name is stored once per booking. Correcting it in one row leaves the other one stale:

```sql
UPDATE booking_sheet SET customer_name = 'Ada Lovelace' WHERE booking_id = 1;
SELECT DISTINCT customer_email, customer_name FROM booking_sheet
WHERE customer_email = 'ada@example.com';
```

```
 customer_email  | customer_name
-----------------+---------------
 ada@example.com | Ada
 ada@example.com | Ada Lovelace
```

One email, two names. Which one is right cannot be known from the data.

**Delete anomaly.** The only row that mentions Sushi Bar is a booking. Cancelling it deletes the fact that the restaurant exists and is in Milano:

```sql
DELETE FROM booking_sheet WHERE booking_id = 3;
SELECT count(*) AS rows_mentioning_sushi_bar FROM booking_sheet WHERE restaurant = 'Sushi Bar';
```

```
 rows_mentioning_sushi_bar
---------------------------
                         0
```

**Insert anomaly.** A new restaurant with no bookings yet cannot be added without inventing a booking, or inserting a row full of `NULL`s.

All three come from the same cause: the table stores facts about several things (customers, restaurants, bookings) in one row, so a fact about one thing is tied to rows about another.

## Functional dependencies

The normal forms are stated in terms of **functional dependencies**. `A → B` ("A determines B") means: two rows with the same A always have the same B.

In `booking_sheet`:

```
booking_id     → customer_email, restaurant, table_numbers, starts_at
customer_email → customer_name
restaurant     → restaurant_city
```

`customer_email → customer_name` is a dependency whose left side is not a key of the table: many rows share an email. That is exactly the fact that got duplicated. Normalization amounts to this: **every dependency's left side should be a key of its table**. When it is not, move that dependency to a table where it is.

A **key** is a minimal set of columns that determines every other column. A **candidate key** is any such set; the primary key is the one chosen. A column that is part of some candidate key is called **prime**.

## First normal form (1NF)

Every column holds one atomic value: no lists, no repeating groups (`table1`, `table2`, `table3` columns).

`table_numbers = '1,2'` breaks it. Asking which bookings use table 1 needs string matching, and string matching is wrong:

```sql
INSERT INTO booking_sheet VALUES
    (4, 'cat@example.com', 'Cat', 'Trattoria', 'Bologna', '11', '2026-12-07 20:00');
SELECT booking_id FROM booking_sheet
WHERE restaurant = 'Trattoria' AND table_numbers LIKE '%1%' ORDER BY 1;
```

```
 booking_id
------------
          1
          4
```

Booking 4 uses table 11, not 1. No foreign key can check the values in the list either, and removing one table from a booking means editing a string. The fix is a row per (booking, table) pair: the join table `booking_tables` from [relationships](18b_relationships.md#nn-a-join-table).

"Atomic" depends on how the value is used. A timestamp is atomic even though it has a date and a time part, because the application never needs them separately as facts. PostgreSQL arrays and `jsonb` columns are allowed by the database, and are fine for values that are always read and written as a whole (a list of tags shown on a page), but not for values that are searched, joined or constrained one by one.

## Second normal form (2NF)

1NF, and no non-prime column depends on **part** of a composite key.

It only matters for tables with a composite key. In 1NF the sheet becomes one row per (booking, table):

```
booking_tables_flat(booking_id, table_number, customer_email, starts_at)
key: (booking_id, table_number)
```

`starts_at` depends on `booking_id` alone, so it is repeated for every table of the booking, and a booking moved to 21:00 must be updated in every row. The fix: columns that depend on `booking_id` alone go to `bookings`, and the join table keeps only the pair.

## Third normal form (3NF)

2NF, and no non-prime column depends on another non-prime column (no **transitive** dependency `key → A → B`).

Menu items with their category name:

```sql
CREATE TABLE items_flat (item_id int PRIMARY KEY, name text, category_id int, category_name text);
INSERT INTO items_flat VALUES (1, 'Tagliatelle', 2, 'Pasta'), (2, 'Lasagne', 2, 'Pasta'), (3, 'Margherita', 3, 'Pizza');
```

`item_id → category_id → category_name`. The category name is a fact about the category, stored once per item. Renaming it through one item splits it:

```sql
UPDATE items_flat SET category_name = 'Fresh pasta' WHERE item_id = 1;
SELECT category_id, array_agg(DISTINCT category_name) FROM items_flat GROUP BY category_id ORDER BY 1;
```

```
 category_id |       array_agg
-------------+-----------------------
           2 | {"Fresh pasta",Pasta}
           3 | {Pizza}
```

The fix is the `menu_categories` table, with `menu_items.category_id` as a foreign key. `customer_email → customer_name` and `restaurant → restaurant_city` in the sheet are the same problem, and become `customers` and `restaurants`.

The informal summary of 1NF to 3NF: every non-key column depends on **the key, the whole key, and nothing but the key**.

## Boyce-Codd normal form (BCNF)

3NF allows one case: a dependency whose right side is prime. BCNF removes it: for every dependency `A → B`, A must be a key.

The case is rare and needs overlapping candidate keys. Waiters work in areas of the room, and each area has supervisors:

```
shifts(waiter, area, supervisor)
- each supervisor supervises exactly one area:   supervisor → area
- a waiter has one supervisor per area:          (waiter, area) → supervisor
candidate keys: (waiter, area) and (waiter, supervisor)
```

Every column is prime, so the table is in 3NF. But `supervisor → area` has a left side that is not a key: the supervisor's area is repeated in every row of that supervisor, and moving a supervisor to the terrace means updating many rows. Splitting into `supervisors(supervisor, area)` and `shifts(waiter, supervisor)` fixes it.

The cost: the rule "a waiter has one supervisor per area" now spans two tables and cannot be a single `UNIQUE` constraint any more. BCNF sometimes loses a dependency that 3NF could enforce, which is why 3NF is the usual target and BCNF the check for tables with several overlapping keys.

Higher normal forms (4NF, 5NF) deal with independent multi-valued facts in one table, such as a chef's skills and a chef's languages stored as all combinations. The fix is the same idea: one table per independent fact.

## The normalized schema

The sheet becomes the schema from [relationships](18b_relationships.md):

```
booking_sheet  ──>  customers(id, email, name)
                    restaurants(id, name, city)
                    restaurant_tables(id, restaurant_id, number, seats)
                    bookings(id, customer_id, starts_at, party_size)
                    booking_tables(booking_id, table_id)
```

Each fact now has one place: a name change is one `UPDATE`, cancelling a booking does not delete a restaurant, and a restaurant can exist with no bookings.

Normalization in practice is rarely done by computing dependencies. Designing from entities ([entities and keys](18a_entities_and_keys.md)), with one table per entity and a join table per N:N, gives a 3NF schema almost automatically. The normal forms are the way to check a schema and to name what is wrong with it.

## Denormalizing on purpose

A normalized schema answers some questions with joins and aggregates. When a query runs very often and is too slow, storing a derived value is a legitimate choice. It is then a **copy**, and something has to keep it in step.

A restaurant with a stored count of its tables:

```sql
CREATE TABLE r (id int PRIMARY KEY, name text, table_count int);
CREATE TABLE t (id int PRIMARY KEY, r_id int REFERENCES r);
INSERT INTO r VALUES (1, 'Trattoria', 2);
INSERT INTO t VALUES (1, 1), (2, 1), (3, 1);

SELECT r.name, r.table_count AS stored,
       (SELECT count(*) FROM t WHERE t.r_id = r.id) AS actual
FROM r;
```

```
   name    | stored | actual
-----------+--------+--------
 Trattoria |      2 |      3
```

A code path added the third table and forgot the counter. Ways to keep a copy correct, from safest to riskiest:

| Way | Kept in step by |
|---|---|
| Don't store it: compute it, with an index | nothing to keep |
| A materialized view ([16f](../16_sql_databases/16f_views_and_virtual_tables.md#materialized-views)) | an explicit refresh, so it can be stale |
| A trigger ([16h](../16_sql_databases/16h_triggers.md#keeping-a-derived-value-in-step)) | the database, on every write |
| Application code, in the same transaction as the write | every code path that writes, which is the one in the example |

Not all repetition is denormalization. A value that looks duplicated can be a different fact: the price stored on an order line is the price **at the time of the order**, and must not follow later changes to the menu ([relationships](18b_relationships.md#a-join-table-with-its-own-attributes)). The test is whether the two copies must always be equal. If yes, it is denormalized; if they may legitimately differ, it is a separate fact and normalization does not apply.

Reporting databases (data warehouses) are denormalized on purpose, into wide "star schema" tables, because they are loaded in batches and only read. The anomalies above happen on writes, so a read-only copy does not suffer from them.

## Common mistakes

- **Lists in a column** (`'1,2'`, or a JSON array that is then searched). Use a join table.
- **Copying a name or a city into another table "to avoid a join"**. Joins on indexed keys are cheap; a stale copy is a bug report.
- **Normalizing a historical fact away**: pointing an invoice at the customer's current address, so old invoices change when the customer moves.
- **Splitting a table into 1:1 parts for no reason**, which costs joins and buys nothing. Normalization removes repeated facts, not columns.
- **Denormalizing before measuring.** Add the copy when a real query is too slow, together with the code that keeps it in step.
