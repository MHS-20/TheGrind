# Schema Patterns and Anti-Patterns

Some requirements come up in almost every schema: keeping deleted data, knowing when a row changed, storing a fixed set of statuses, attaching one kind of row to several kinds of parent, letting users add their own fields, and changing the schema of a live database. Each has a standard solution and a tempting wrong one. This page shows both on PostgreSQL 17, with the output of the wrong ones failing.

## Soft delete

Deleting a row loses it for good, and `ON DELETE` rules may fail or cascade ([what happens on delete](18b_relationships.md#what-happens-on-delete)). A **soft delete** keeps the row and marks it as deleted:

```sql
CREATE TABLE customers (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      text NOT NULL,
    name       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz                     -- NULL = live
);

UPDATE customers SET deleted_at = now() WHERE email = 'ada@example.com';   -- "delete"
```

A timestamp is better than a boolean: it records when, at no extra cost.

### Uniqueness among live rows

A plain `UNIQUE (email)` would stop Ada from registering again after deleting her account, because her deleted row still holds the email. A **partial unique index** applies the constraint only to live rows:

```sql
CREATE UNIQUE INDEX customers_email_live ON customers (email) WHERE deleted_at IS NULL;

INSERT INTO customers (email, name) VALUES ('ada@example.com', 'Ada (new account)');   -- allowed
INSERT INTO customers (email, name) VALUES ('bob@example.com', 'Bob again');           -- Bob is live
```

```
ERROR:  duplicate key value violates unique constraint "customers_email_live"
DETAIL:  Key (email)=(bob@example.com) already exists.
```

```
 id |      email      |       name        | deleted
----+-----------------+-------------------+---------
  1 | ada@example.com | Ada               | t
  2 | bob@example.com | Bob               | f
  3 | ada@example.com | Ada (new account) | f
```

### The cost: every query must filter

The deleted rows are still in the table, so every query that forgets `WHERE deleted_at IS NULL` returns them:

```sql
SELECT count(*) AS all_rows, count(*) FILTER (WHERE deleted_at IS NULL) AS live FROM customers;
```

```
 all_rows | live
----------+------
        3 |    2
```

Ways to make the filter hard to forget: a view of live rows that the application reads from (`CREATE VIEW live_customers AS SELECT * FROM customers WHERE deleted_at IS NULL`), a filter in the data access layer applied to every query (Hibernate's `@SQLRestriction("deleted_at IS NULL")`), or row-level security.

Foreign keys do not know about soft deletes either: a booking can still point to a deleted customer, which is usually what is wanted (history keeps its references), but new bookings for deleted customers must be blocked in code.

Alternatives, when soft deletes would be read rarely:

- **An archive table**: move deleted rows to `customers_archive` in the same transaction. Live queries need no filter.
- **A status column** when "deleted" is one state among several (a booking that is pending, confirmed or cancelled is not deleted when cancelled: it is cancelled).
- **A real delete**, when the law requires the data to be gone (personal data under GDPR), which soft delete does not satisfy.

## Audit columns

`created_at` and `updated_at` on every table answer "when did this change?", which comes up in every support request. `created_at` is a default. `updated_at` must change on every update, which application code forgets; a `BEFORE UPDATE` trigger ([16h](../16_sql_databases/16h_triggers.md#cleaning-and-rejecting-rows-before)) cannot be forgotten:

```sql
CREATE FUNCTION touch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END $$;

CREATE TRIGGER customers_touch BEFORE UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION touch();
```

Even an update that tries to set `updated_at` to an old date gets the current time:

```sql
UPDATE customers SET created_at = '2026-01-01', updated_at = '2026-01-01' WHERE email = 'bob@example.com';
SELECT email, created_at::date, updated_at::date = now()::date AS updated_today FROM customers WHERE email = 'bob@example.com';
```

```
      email      | created_at | updated_today
-----------------+------------+---------------
 bob@example.com | 2026-01-01 | t
```

Use `timestamptz`, not `timestamp`: it stores an absolute instant, so rows written from servers in different time zones compare correctly.

`created_by` and `updated_by` need the application user, which the database does not know; the application sets them (JPA has `@CreatedBy` in Spring Data, or entity listeners). For a full history of **what** changed, not just when, use an audit table filled by a trigger ([an audit log](../16_sql_databases/16h_triggers.md#an-audit-log-after-update)).

## Enumerations

A booking's status is one of a fixed set. Three ways to store it:

**A `CHECK` constraint** on a text column:

```sql
CREATE TABLE bookings (
    id     int PRIMARY KEY,
    status text NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);
INSERT INTO bookings VALUES (2, 'confirmed');
```

```
ERROR:  new row for relation "bookings" violates check constraint "bookings_status_check"
DETAIL:  Failing row contains (2, confirmed).
```

**A lookup table** with a foreign key:

```sql
CREATE TABLE booking_statuses (code text PRIMARY KEY, label text NOT NULL, is_final boolean NOT NULL);
INSERT INTO booking_statuses VALUES
    ('PENDING', 'Waiting for confirmation', false), ('CONFIRMED', 'Confirmed', false),
    ('CANCELLED', 'Cancelled', true), ('NO_SHOW', 'Did not show up', true);

CREATE TABLE bookings2 (id int PRIMARY KEY, status text NOT NULL REFERENCES booking_statuses(code));
INSERT INTO bookings2 VALUES (1, 'NO_SHOW');
SELECT b.id, s.label, s.is_final FROM bookings2 b JOIN booking_statuses s ON s.code = b.status;
```

```
 id |      label      | is_final
----+-----------------+----------
  1 | Did not show up | t
```

**A database enum type** (`CREATE TYPE booking_status AS ENUM (...)`): compact, but values cannot be removed or renamed easily, and it is PostgreSQL-specific.

| | `CHECK` | Lookup table | `ENUM` type |
|---|---|---|---|
| Adding a value | migration | `INSERT` | `ALTER TYPE ... ADD VALUE` |
| Removing a value | migration | `DELETE` (fails if in use) | not supported directly |
| Extra data per value (label, order, flags) | no | yes | no |
| Values readable in raw rows | yes | yes (with text codes) | yes |

Use a `CHECK` when the values are fixed and owned by the code (a Java enum with behaviour). Use a lookup table when values have their own data, or change without a release. Store readable codes, not numbers: `status = 3` means nothing in a query, and JPA's default `@Enumerated(EnumType.ORDINAL)` stores the Java enum's position, which silently changes meaning when someone inserts a constant in the middle. Use `EnumType.STRING`.

## Polymorphic associations

Reviews can be about a restaurant or about a menu item. The tempting design is one pair of columns pointing to "any" table:

```sql
CREATE TABLE reviews_poly (
    id          int PRIMARY KEY,
    target_type text NOT NULL,     -- 'restaurant' or 'menu_item'
    target_id   int  NOT NULL,     -- id in that table
    stars       int
);
INSERT INTO reviews_poly VALUES (1, 'restaurant', 1, 5), (2, 'menu_item', 1, 4), (3, 'restaurant', 42, 1);
DELETE FROM menu_items WHERE id = 1;
```

`target_id` cannot have a foreign key, because a foreign key points to one table. So the database accepted a review of restaurant 42, which does not exist, and kept the review of the menu item after the item was deleted:

```sql
SELECT r.id, r.target_type, r.target_id FROM reviews_poly r
WHERE NOT EXISTS (SELECT 1 FROM restaurants x WHERE r.target_type = 'restaurant' AND x.id = r.target_id)
  AND NOT EXISTS (SELECT 1 FROM menu_items  x WHERE r.target_type = 'menu_item'  AND x.id = r.target_id);
```

```
 id | target_type | target_id
----+-------------+-----------
  2 | menu_item   |         1
  3 | restaurant  |        42
```

Frameworks like Rails make this pattern easy, which is why it is common.

The alternative that keeps foreign keys is an **exclusive arc**: one nullable foreign key per possible parent, and a `CHECK` that exactly one is set:

```sql
CREATE TABLE reviews (
    id            int PRIMARY KEY,
    restaurant_id int REFERENCES restaurants(id),
    menu_item_id  int REFERENCES menu_items(id),
    stars         int NOT NULL,
    CHECK (num_nonnulls(restaurant_id, menu_item_id) = 1)
);
INSERT INTO reviews VALUES (3, 42, NULL, 1);
INSERT INTO reviews VALUES (4, 1, 1, 3);
```

```
ERROR:  insert or update on table "reviews" violates foreign key constraint "reviews_restaurant_id_fkey"
DETAIL:  Key (restaurant_id)=(42) is not present in table "restaurants".
ERROR:  new row for relation "reviews" violates check constraint "reviews_check"
DETAIL:  Failing row contains (4, 1, 1, 3).
```

Other correct designs: a separate table per parent (`restaurant_reviews`, `menu_item_reviews`), or a common supertype table (`reviewables`) that both restaurants and menu items reference, and that reviews point to. The exclusive arc fits a small, fixed number of parent types.

## Entity-attribute-value

Restaurants want to add their own properties to menu items: spicy, calories, vegan. The tempting design avoids schema changes by storing each property as a row:

```sql
CREATE TABLE item_attrs (item_id int, attr text, value text, PRIMARY KEY (item_id, attr));
INSERT INTO item_attrs VALUES (1, 'spicy', 'no'), (1, 'calories', '650'), (1, 'vegan', 'maybe?');
```

This is **entity-attribute-value** (EAV). Every value is text, so the database checks nothing: `vegan = 'maybe?'` is accepted, and so is a calorie count that is not a number. Queries then fail on the bad data:

```sql
INSERT INTO item_attrs VALUES (2, 'calories', 'about 500');
SELECT item_id FROM item_attrs WHERE attr = 'calories' AND value::int < 700;
```

```
ERROR:  invalid input syntax for type integer: "about 500"
```

EAV also loses `NOT NULL` (an item without calories is just a missing row), foreign keys on values, and readable queries: reading one item with five attributes is five rows to pivot back into one.

Better options:

- **Real columns** for properties every item has. Adding a column is a cheap migration.
- **A `jsonb` column** for properties that really vary by restaurant. It keeps per-item data in one place, can be indexed (a GIN index), and can still be checked with `CHECK (jsonb_typeof(extra -> 'calories') = 'number')` where it matters.
- EAV only when the attributes are defined by users at runtime **and** stored with a type column and typed value columns, accepting the query cost.

## Migrations

A schema changes over the life of an application. Changing it by hand on each server makes the environments drift apart. **Migrations** are numbered SQL scripts kept in the repository, applied in order by a tool (Flyway, Liquibase) that records in a table which ones a database has already run:

```
db/migration/
  V1__create_restaurants.sql
  V2__create_bookings.sql
  V3__add_bookings_version.sql
  V4__soft_delete_customers.sql
```

Rules that keep them safe:

- **A migration that has run is never edited.** Fixing a mistake is a new migration. Tools checksum the applied scripts and refuse to start if one changed.
- **Every environment runs the same scripts**, from an empty database in tests to production.
- **The ORM validates, it does not generate**: Hibernate's schema generation is for prototypes ([schema generation](18h_jpa_and_hibernate.md#schema-generation)).

### Changing a live schema

During a deployment, old and new application versions run side by side for a while, against one database. A change that breaks the old code (renaming a column, adding a `NOT NULL` column without a default) fails the requests still served by the old version. Such changes are split into **expand and contract** steps:

1. **Expand**: add the new column, nullable. Deploy code that writes both the old and the new column.
2. **Migrate**: copy existing data to the new column, in batches on large tables.
3. Deploy code that reads only the new column.
4. **Contract**: add `NOT NULL`, then drop the old column in a later migration.

Each step works with both the code before it and the code after it.

Some statements also lock a table while they run. Adding a column with a constant default is instant in PostgreSQL, but creating an index blocks writes for as long as the build takes; `CREATE INDEX CONCURRENTLY` avoids that. On a large table in production, check what lock a statement takes before running it.

## Common mistakes

- **Soft delete without a partial unique index**, so deleted rows block new ones.
- **Soft delete without a single place that filters**, so deleted rows leak into totals.
- **`updated_at` maintained by application code** in some paths and not others.
- **Enum ordinals in the database**, which change meaning when the enum changes.
- **`target_type` + `target_id` columns**, with no foreign key behind them.
- **EAV for properties that are known in advance.**
- **Editing an applied migration**, or changing the production schema by hand.
- **Renaming a column in one step** while the old code is still running.
