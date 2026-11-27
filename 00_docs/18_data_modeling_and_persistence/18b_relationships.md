# Relationships in Tables

Entities are related: a restaurant has tables, a customer makes bookings, a booking uses tables. In a relational schema every relationship is stored as a **foreign key**, a column whose value must be a key of another table. Where the foreign key goes depends on the relationship's cardinality: one-to-one, one-to-many, or many-to-many. This page builds the restaurant schema one relationship at a time and shows what each choice allows and forbids.

## Cardinality

The cardinality of a relationship says how many rows on each side can be connected to one row on the other.

| Cardinality | Example | Stored as |
|---|---|---|
| 1:1 | customer, loyalty card | a foreign key that is also unique (or is the primary key) |
| 1:N | restaurant, its tables | a foreign key on the N side |
| N:N | bookings, tables | a separate join table with two foreign keys |

The second question is **optionality**: can a row exist without the other side? A table always belongs to a restaurant (`NOT NULL` foreign key); a customer may have no loyalty card (the card row simply does not exist).

## 1:N, a foreign key on the many side

A restaurant has many tables, and each table belongs to exactly one restaurant. The foreign key goes on the **many** side, in `restaurant_tables`:

```sql
CREATE TABLE restaurants (
    id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name text NOT NULL,
    city text NOT NULL
);

CREATE TABLE restaurant_tables (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    restaurant_id bigint NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    number        int    NOT NULL,
    seats         int    NOT NULL CHECK (seats > 0),
    UNIQUE (restaurant_id, number)
);
```

It cannot go on the one side: a row has one value per column, and a restaurant has many tables. A column `table_ids` holding a list would break the first normal form ([normalization](18c_normalization.md)), and the database could not check each id.

- `NOT NULL` makes the relationship mandatory: no table without a restaurant.
- `UNIQUE (restaurant_id, number)` says table numbers repeat across restaurants but not within one. It is the natural key of a table, next to the surrogate `id`.

Reading the relationship is a join on the foreign key:

```sql
SELECT r.name, count(t.id) AS tables, sum(t.seats) AS seats
FROM restaurants r
LEFT JOIN restaurant_tables t ON t.restaurant_id = r.id
GROUP BY r.id, r.name
ORDER BY r.id;
```

```
   name    | tables | seats
-----------+--------+-------
 Trattoria |      3 |    14
 Sushi Bar |      1 |     2
```

The same rule gives `bookings.customer_id` (a customer has many bookings) and `orders.booking_id` (a booking has many orders).

### Index the foreign key

PostgreSQL creates an index for every primary key and unique constraint, but **not** for a foreign key column. The only foreign key on `restaurant_tables` is covered by accident, because `UNIQUE (restaurant_id, number)` starts with `restaurant_id` ([leftmost prefix](../16_sql_databases/16i_query_planning_and_indexes.md#composite-indexes-and-the-leftmost-prefix)). `bookings.customer_id` has no such luck:

```sql
CREATE INDEX ON bookings (customer_id);
```

Without it, "all bookings of a customer" scans the whole table, and so does every delete of a customer, because the database must check that no booking still points to it.

## 1:1, two ways

A customer has at most one loyalty card, and a card belongs to exactly one customer. Two layouts are common.

### Shared primary key

The card's primary key **is** the customer's id:

```sql
CREATE TABLE loyalty_cards (
    customer_id bigint PRIMARY KEY REFERENCES customers(id) ON DELETE CASCADE,
    points      int    NOT NULL DEFAULT 0
);
```

The primary key makes a second card for the same customer impossible:

```sql
INSERT INTO loyalty_cards VALUES (1, 120), (3, 40);
INSERT INTO loyalty_cards VALUES (1, 0);
```

```
ERROR:  duplicate key value violates unique constraint "loyalty_cards_pkey"
DETAIL:  Key (customer_id)=(1) already exists.
```

A customer without a card simply has no row, which a left join shows as `NULL`:

```sql
SELECT c.name, l.points
FROM customers c
LEFT JOIN loyalty_cards l ON l.customer_id = c.id
ORDER BY c.id;
```

```
 name | points
------+--------
 Ada  |    120
 Bob  |   NULL
 Cat  |     40
```

### Unique foreign key

The other table has its own id, and a foreign key with `UNIQUE`:

```sql
CREATE TABLE addresses (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
    street      text   NOT NULL
);
```

Without `UNIQUE`, this is a 1:N relationship. The constraint is the only thing that makes it 1:1.

### Which side, and whether to split at all

| Layout | Use it when |
|---|---|
| Same table (columns on `customers`) | the data is always there and always read together |
| Shared primary key | the dependent row cannot exist without its owner and has no identity of its own |
| Unique foreign key | the dependent row may later be shared or become 1:N, or other tables refer to it by its own id |

Put the foreign key on the side that is **optional**: every card has a customer, but not every customer has a card, so `loyalty_cards` points to `customers`. The other way round, `customers.card_id` would be `NULL` for most rows.

Reasons to split a 1:1 at all: the second part is optional and has several columns (no rows full of `NULL`), it is large and rarely read (a photo, a long description), or it has different access rules (payment details).

## N:N, a join table

A booking can use several tables (a party of ten gets tables 1 and 2), and a table is used by many bookings over time. Neither side can hold the foreign key, because each side has many of the other. The relationship becomes its own table, called a **join table** (also link, junction, mapping or associative table):

```sql
CREATE TABLE bookings (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    starts_at   timestamp NOT NULL,
    party_size  int NOT NULL
);

CREATE TABLE booking_tables (
    booking_id bigint REFERENCES bookings(id) ON DELETE CASCADE,
    table_id   bigint REFERENCES restaurant_tables(id) ON DELETE RESTRICT,
    PRIMARY KEY (booking_id, table_id)
);
```

Each row says "this booking uses this table". An N:N relationship always takes three tables: the two entities and the join table. The entity tables have no foreign key to each other.

The composite primary key `(booking_id, table_id)` is a natural key that does two jobs: it forbids the same pair twice, and its index serves lookups by booking.

```sql
INSERT INTO booking_tables VALUES (1, 1), (1, 2), (2, 3);
INSERT INTO booking_tables VALUES (1, 1);
```

```
ERROR:  duplicate key value violates unique constraint "booking_tables_pkey"
DETAIL:  Key (booking_id, table_id)=(1, 1) already exists.
```

Lookups by table ("which bookings use table 3?") cannot use that index, because `table_id` is its second column. Add one in the other direction if that query is needed:

```sql
CREATE INDEX ON booking_tables (table_id, booking_id);
```

Reading the relationship takes two joins, through the join table:

```sql
SELECT b.id AS booking, c.name,
       string_agg(t.number::text, ', ' ORDER BY t.number) AS tables
FROM bookings b
JOIN customers c          ON c.id = b.customer_id
JOIN booking_tables bt    ON bt.booking_id = b.id
JOIN restaurant_tables t  ON t.id = bt.table_id
GROUP BY b.id, c.name
ORDER BY b.id;
```

```
 booking | name | tables
---------+------+--------
       1 | Ada  | 1, 2
       2 | Bob  | 3
```

A surrogate `id` on the join table is optional. It adds nothing for a pure link, and if it is added, the pair still needs a `UNIQUE (booking_id, table_id)`, or duplicates come back.

The schema says nothing about **direction**. The same join table answers "tables of a booking" and "bookings of a table". Whether the Java side navigates one way or both is a decision for the code ([mapping objects to tables](18d_mapping_objects_to_tables.md)).

## A join table with its own attributes

An order contains menu items, and an item appears in many orders: N:N. But the link carries data of its own, the quantity and the price charged:

```sql
CREATE TABLE orders (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES bookings(id) ON DELETE CASCADE
);

CREATE TABLE order_lines (
    order_id    bigint REFERENCES orders(id) ON DELETE CASCADE,
    item_id     bigint REFERENCES menu_items(id) ON DELETE RESTRICT,
    quantity    int    NOT NULL CHECK (quantity > 0),
    price_cents int    NOT NULL,
    PRIMARY KEY (order_id, item_id)
);
```

```sql
SELECT i.name, ol.quantity, ol.price_cents, ol.quantity * ol.price_cents AS total
FROM order_lines ol JOIN menu_items i ON i.id = ol.item_id
ORDER BY i.id;
```

```
    name     | quantity | price_cents | total
-------------+----------+-------------+-------
 Tagliatelle |        4 |        1400 |  5600
 Margherita  |        6 |         900 |  5400
 Water       |       10 |         200 |  2000
```

`order_lines` is now an entity in its own right, often called an **association entity**: in Java it becomes a class (`OrderLine`), not just a list on each side.

Why copy the price into the line, when `menu_items` already has it? Because the menu price changes and the bill must not:

```sql
UPDATE menu_items SET price_cents = 1600 WHERE name = 'Tagliatelle';

SELECT i.name, i.price_cents AS today, ol.price_cents AS charged
FROM order_lines ol JOIN menu_items i ON i.id = ol.item_id
WHERE i.name = 'Tagliatelle';
```

```
    name     | today | charged
-------------+-------+---------
 Tagliatelle |  1600 |    1400
```

This looks like duplication but is not: the line stores *the price at the time of the order*, a different fact from *the price today*. The same reasoning applies to an address on an invoice or a tax rate on a payment.

A test for when a link needs attributes: if a sentence about the relationship needs a number or a date ("Ada ordered **4** tagliatelle", "the table was booked **from 20:00**"), that value belongs on the link.

## Self-references

A menu category can contain sub-categories: Food has Pasta and Pizza. This is a 1:N relationship between rows of the **same** table, stored as a foreign key to its own primary key:

```sql
CREATE TABLE menu_categories (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id bigint REFERENCES menu_categories(id) ON DELETE SET NULL,
    name      text NOT NULL
);
INSERT INTO menu_categories (parent_id, name)
VALUES (NULL, 'Food'), (1, 'Pasta'), (1, 'Pizza'), (NULL, 'Drinks');
```

`parent_id` is nullable: the top-level categories have no parent. One level is a self-join ([16b](../16_sql_databases/16b_joins.md#a-self-join)); a whole tree of any depth needs a recursive CTE ([16d](../16_sql_databases/16d_subqueries_and_ctes.md#recursive-ctes)):

```sql
WITH RECURSIVE tree AS (
    SELECT id, name, name AS path FROM menu_categories WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.name, t.path || ' > ' || c.name
    FROM menu_categories c JOIN tree t ON c.parent_id = t.id
)
SELECT path FROM tree ORDER BY path;
```

```
     path
--------------
 Drinks
 Food
 Food > Pasta
 Food > Pizza
```

The same shape models employees and managers, replies to a comment, and folders. A self-referencing N:N (users following users) is a join table whose two foreign keys point to the same table: `follows(follower_id, followed_id)`.

The foreign key does not stop a cycle (Pasta's parent is Pizza, Pizza's parent is Pasta). If cycles are possible through the application, check them in code before updating `parent_id`.

## What happens on delete

A foreign key also decides what happens to the rows that point to a row being deleted. The `ON DELETE` clause picks one of:

| Option | Effect | Use it for |
|---|---|---|
| `RESTRICT` / `NO ACTION` (default) | the delete fails while references exist | data that must not disappear silently: a customer with bookings |
| `CASCADE` | referencing rows are deleted too | parts that cannot exist without the whole: a booking's table links, an order's lines |
| `SET NULL` | the foreign key becomes `NULL` | optional links: a sub-category whose parent is removed |
| `SET DEFAULT` | the foreign key gets its default | rare: reassign to an "unknown" row |

`RESTRICT` and `NO ACTION` differ only in timing: `NO ACTION` can be deferred to the end of the transaction.

Deleting a customer who has a booking:

```sql
DELETE FROM customers WHERE name = 'Ada';
```

```
ERROR:  update or delete on table "customers" violates foreign key constraint "bookings_customer_id_fkey" on table "bookings"
DETAIL:  Key (id)=(1) is still referenced from table "bookings".
```

Deleting a table that a booking uses fails the same way, through `booking_tables.table_id`. That is the intent: a restaurant should not lose a table that has future bookings without someone deciding what to do with them.

Deleting the Food category sets its children's parent to `NULL`:

```sql
DELETE FROM menu_categories WHERE name = 'Food';
SELECT id, parent_id, name FROM menu_categories ORDER BY id;
```

```
 id | parent_id |  name
----+-----------+--------
  2 |      NULL | Pasta
  3 |      NULL | Pizza
  4 |      NULL | Drinks
```

Deleting booking 1 cascades to its table links, its order, and the order's lines (two levels):

```sql
DELETE FROM bookings WHERE id = 1;
SELECT (SELECT count(*) FROM booking_tables) AS booking_tables,
       (SELECT count(*) FROM orders)         AS orders,
       (SELECT count(*) FROM order_lines)    AS order_lines;
```

```
 booking_tables | orders | order_lines
----------------+--------+-------------
              1 |      0 |           0
```

Only booking 2's link to table 3 is left.

### Choosing the option

Follow ownership:

- **Composition** (the part makes no sense alone, [17c](../17_object_oriented_design/17c_class_diagrams.md#relations)): `CASCADE`. Order lines without an order are garbage.
- **Association** (both sides exist on their own): `RESTRICT`, so the application decides. Or `SET NULL` when the link is optional.
- **A join table**: `CASCADE` from both sides is common, since a link without one of its ends is meaningless. Here `booking_tables.table_id` is `RESTRICT` on purpose, to protect bookings.

Cascades are invisible in the code that runs the `DELETE`: one statement can remove thousands of rows in other tables. Many teams use `RESTRICT` almost everywhere and delete children explicitly, or never delete at all and mark rows as deleted ([schema patterns](18i_schema_patterns.md)).

When the application has to clean up links itself (no cascade, or a tool that does not generate them), the order is always: **delete or disconnect the links first, then the row**. Creating is the reverse: create the row, then connect it. The order matches what the foreign keys allow at each step.

## The full schema

```
restaurants 1───* restaurant_tables 1───* booking_tables *───1 bookings
                                                                  │ *
customers 1───0..1 loyalty_cards                                  │
customers 1───* bookings ─────────────────────────────────────────┘
bookings 1───* orders 1───* order_lines *───1 menu_items *───0..1 menu_categories
                                                     menu_categories 0..1───* menu_categories
```

## Common mistakes

- **A comma-separated list of ids in a column** (`table_ids = '1,2'`). No foreign key can check it, no index can search it, and removing one id needs string editing. That is a join table.
- **A foreign key on both sides of a 1:1**: two columns that must agree, and nothing makes them.
- **N:N without a primary key on the join table**, so the same link can be inserted twice and every count doubles.
- **Unindexed foreign keys**: slow joins, and deletes on the parent that scan the child table.
- **`CASCADE` on an association**: deleting a menu item would delete every past order line that contains it, rewriting history.
- **Modelling an N:N as two 1:N** without noticing the join table is an entity (order lines with a quantity), and then adding the quantity to one of the entity tables.
