# PL/SQL: Triggers, and Loops vs Set-Based SQL

A trigger is a block that the database runs automatically when a table is changed. [Triggers](16h_triggers.md) covers the idea and its uses on PostgreSQL; this page covers Oracle's syntax and the error that most Oracle trigger code runs into, the mutating table. It ends with the question the course asks about every PL/SQL solution: should this be a loop at all, or one SQL statement?

The examples run on Oracle Database Free (23.26), on the restaurant schema.

## Syntax

```sql
CREATE OR REPLACE TRIGGER name
{BEFORE | AFTER | INSTEAD OF} {INSERT | UPDATE [OF column, ...] | DELETE} [OR ...]
ON table
[FOR EACH ROW]
[WHEN (condition)]
[DECLARE ...]
BEGIN
    ...
END;
/
```

The differences from PostgreSQL:

- The body is written inline: there is no separate trigger function.
- In a row trigger, the old and new row are `:OLD` and `:NEW`, with a colon. In the `WHEN` clause they are written without the colon (`NEW.price_cents`).
- `INSERTING`, `UPDATING` and `DELETING` are true inside the body depending on which event fired it, so one trigger can handle several events.
- `UPDATE OF column` fires only when that column is in the `SET` list.

## `BEFORE` row triggers: changing or rejecting the row

A `BEFORE` row trigger runs before the row is written, and can change `:NEW` or reject the row by raising an error. Normalising emails, so that `' Dan@Example.COM '` and `'dan@example.com'` cannot both be registered:

```sql
CREATE OR REPLACE TRIGGER customers_clean
BEFORE INSERT OR UPDATE OF email ON customers
FOR EACH ROW
BEGIN
    :NEW.email := LOWER(TRIM(:NEW.email));
    IF :NEW.email NOT LIKE '%_@_%' THEN
        RAISE_APPLICATION_ERROR(-20010, 'invalid email ' || :NEW.email);
    END IF;
END;
/

INSERT INTO customers (email, name) VALUES ('  Dan@Example.COM ', 'Dan');
SELECT email FROM customers WHERE name = 'Dan';
```

```
dan@example.com
```

```sql
INSERT INTO customers (email, name) VALUES ('not-an-email', 'Eve');
```

```
ORA-20010: invalid email not-an-email
ORA-06512: at "APP.CUSTOMERS_CLEAN", line 4
ORA-04088: error during execution of trigger 'APP.CUSTOMERS_CLEAN'
```

The error fails the `INSERT` statement, and the row is not written.

## `AFTER` row triggers: logging changes

The course's lab logs every price change. An `AFTER UPDATE` trigger writes the old and new price to a log table, with a `WHEN` clause so that updates that do not change the price are skipped:

```sql
CREATE TABLE price_log (
    item_id NUMBER, old_cents NUMBER, new_cents NUMBER,
    changed_by VARCHAR2(128), changed_at DATE
);

CREATE OR REPLACE TRIGGER menu_price_log
AFTER UPDATE OF price_cents ON menu_items
FOR EACH ROW
WHEN (NEW.price_cents <> OLD.price_cents)
BEGIN
    INSERT INTO price_log VALUES (:OLD.id, :OLD.price_cents, :NEW.price_cents, USER, SYSDATE);
END;
/

UPDATE menu_items SET price_cents = price_cents + 100 WHERE restaurant_id = 1;   -- two items change
UPDATE menu_items SET price_cents = price_cents WHERE restaurant_id = 2;          -- no real change
SELECT item_id, old_cents, new_cents, changed_by FROM price_log ORDER BY item_id;
```

| ITEM_ID | OLD_CENTS | NEW_CENTS | CHANGED_BY |
|---|---|---|---|
| 1 | 1400 | 1500 | APP |
| 2 | 900 | 1000 | APP |

The second update matched an item but changed nothing, and the `WHEN` clause kept it out of the log. The log row is written in the same transaction as the update, so a rollback removes both. `USER` is the database user; in an application that connects with one shared account, the real end user has to be passed in by the application (for example with `DBMS_SESSION.SET_IDENTIFIER`).

## Row and statement triggers

Without `FOR EACH ROW`, a trigger is a **statement trigger**: it fires once per statement, however many rows the statement changes, including zero. With both on one table:

```sql
UPDATE menu_items SET price_cents = price_cents;      -- three rows
```

```
row trigger for item 1
row trigger for item 2
row trigger for item 3
statement trigger fired once
```

Statement triggers cannot see `:OLD` and `:NEW`. They suit checks and actions about the statement as a whole: refusing changes outside business hours, refreshing a summary once after a bulk load.

Row triggers cost time per row. An update of a million rows runs a row trigger a million times, which is the same per-row switching cost as a cursor loop ([bulk collect and forall](16k_plsql_cursors.md#bulk-collect-and-forall)).

## The mutating table error

A restaurant holds at most 20 guests at a time. A row trigger that checks it by summing the bookings already in the table:

```sql
CREATE OR REPLACE TRIGGER bookings_capacity
BEFORE INSERT ON bookings
FOR EACH ROW
DECLARE
    v_guests NUMBER;
BEGIN
    SELECT NVL(SUM(party_size), 0) INTO v_guests FROM bookings
    WHERE restaurant_id = :NEW.restaurant_id AND starts_at = :NEW.starts_at AND status = 'CONFIRMED';
    IF v_guests + :NEW.party_size > 20 THEN
        RAISE_APPLICATION_ERROR(-20020, 'restaurant full at that time');
    END IF;
END;
/
```

A single-row `INSERT ... VALUES` works. An insert of two rows in one statement fails:

```sql
INSERT INTO bookings (customer_id, restaurant_id, starts_at, party_size)
SELECT 3, 1, TIMESTAMP '2026-12-05 20:00:00', 2 FROM dual
UNION ALL
SELECT 2, 1, TIMESTAMP '2026-12-05 20:00:00', 2 FROM dual;
```

```
ORA-04091: table APP.BOOKINGS is mutating, trigger/function may not see it
ORA-06512: at "APP.BOOKINGS_CAPACITY", line 4
```

**Why.** While a statement is changing a table, the table is **mutating**: some of the statement's rows are written and some are not. A row trigger that queries the same table would see a state that depends on the order in which the rows happen to be processed: the first row's check sees no new rows, the second sees one. Oracle refuses rather than give an answer that depends on that order. A single-row `INSERT ... VALUES` is exempt, because there is only one row and no partial state; that is why the bug hides in tests that insert one row at a time, and appears with the first bulk insert.

**The fix** is to check after the statement has finished, when the table is complete. A **compound trigger** has sections for several timing points in one trigger, with variables shared between them: collect the affected keys in the row section, check them in the statement section.

```sql
CREATE OR REPLACE TRIGGER bookings_capacity
FOR INSERT ON bookings
COMPOUND TRIGGER
    TYPE t_keys IS TABLE OF VARCHAR2(60) INDEX BY PLS_INTEGER;
    g_keys t_keys;                                     -- shared by the sections below

    AFTER EACH ROW IS
    BEGIN
        g_keys(g_keys.COUNT + 1) := :NEW.restaurant_id || '|' || TO_CHAR(:NEW.starts_at, 'YYYYMMDDHH24MI');
    END AFTER EACH ROW;

    AFTER STATEMENT IS
        v_guests NUMBER;
    BEGIN
        FOR i IN 1 .. g_keys.COUNT LOOP
            SELECT NVL(SUM(party_size), 0) INTO v_guests FROM bookings
            WHERE restaurant_id = TO_NUMBER(SUBSTR(g_keys(i), 1, INSTR(g_keys(i), '|') - 1))
              AND starts_at = TO_DATE(SUBSTR(g_keys(i), INSTR(g_keys(i), '|') + 1), 'YYYYMMDDHH24MI')
              AND status = 'CONFIRMED';
            IF v_guests > 20 THEN
                RAISE_APPLICATION_ERROR(-20020, 'restaurant full at that time (' || v_guests || ' guests)');
            END IF;
        END LOOP;
    END AFTER STATEMENT;
END bookings_capacity;
/
```

The two-row insert now succeeds (14 guests at 20:00), and a booking that would bring the total to 23 is refused:

```
ORA-20020: restaurant full at that time (23 guests)
```

The error in the `AFTER STATEMENT` section fails the whole statement, so the rows it had inserted are undone.

### A trigger is not a lock

The check has a race the trigger cannot see. Two sessions each insert a booking of 4 when 14 guests are booked: each trigger sees its own row and the committed 14, but not the other session's uncommitted row, so both see 18 and both commit, leaving 22. This is the check-then-act race from [concurrency](../17_object_oriented_design/17g_concurrency_basics.md#races-and-critical-sections) and [lost updates](../18_data_modeling_and_persistence/18g_transactions_in_code.md#lost-updates). Rules across rows need a lock (a `SELECT ... FOR UPDATE` on a per-slot row, so the bookings for one slot are serialized) or a design where a constraint can enforce them. Triggers enforce rules within one session's view of the data.

## A cursor loop vs one statement

The course's lab asks the same question of each exercise: is the PL/SQL solution the right one, or can one SQL statement do it? Three ways to write a monthly loyalty award: every customer with at least three confirmed bookings in the month gets 10 points per guest of those bookings.

**From the application**, over JDBC: read the customers, then for each customer run a count query, then read their bookings and send an update per booking. The cost is round trips: one per customer for the count, one per customer for the bookings, one per booking for the update, and every row crosses the network.

**A stored procedure with nested cursors** does the same steps inside the database:

```sql
CREATE OR REPLACE PROCEDURE award_loop(p_month DATE) IS
    CURSOR c_customers IS SELECT id FROM customers;
    CURSOR c_bookings(p_customer NUMBER) IS
        SELECT party_size FROM bookings
        WHERE customer_id = p_customer AND status = 'CONFIRMED'
          AND starts_at >= p_month AND starts_at < ADD_MONTHS(p_month, 1);
    v_count NUMBER;
BEGIN
    FOR c IN c_customers LOOP
        SELECT count(*) INTO v_count FROM bookings
        WHERE customer_id = c.id AND status = 'CONFIRMED'
          AND starts_at >= p_month AND starts_at < ADD_MONTHS(p_month, 1);
        IF v_count >= 3 THEN
            FOR b IN c_bookings(c.id) LOOP
                UPDATE customers SET points = points + 10 * b.party_size WHERE id = c.id;
            END LOOP;
        END IF;
    END LOOP;
END;
/
```

No network traffic, but still one query per customer and one update per booking.

**One statement**: aggregate the month's bookings per customer, keep the groups with at least three, and apply them:

```sql
CREATE OR REPLACE PROCEDURE award_merge(p_month DATE) IS
BEGIN
    MERGE INTO customers c
    USING (SELECT customer_id, 10 * SUM(party_size) AS pts
           FROM bookings
           WHERE status = 'CONFIRMED' AND starts_at >= p_month AND starts_at < ADD_MONTHS(p_month, 1)
           GROUP BY customer_id
           HAVING count(*) >= 3) s
    ON (c.id = s.customer_id)
    WHEN MATCHED THEN UPDATE SET c.points = c.points + s.pts;
END;
/
```

An `UPDATE` with two correlated subqueries (one for the points, one for the `>= 3` condition) is the other one-statement form.

On 20,000 customers and 200,000 bookings (in this data, every customer qualified), three runs each:

| Version | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| Nested cursor loops | 2,060 ms | 1,720 ms | 1,940 ms |
| `UPDATE` with correlated subqueries | 310 ms | 370 ms | 300 ms |
| `MERGE` with `GROUP BY` | 250 ms | 220 ms | 220 ms |

All three gave the same result (6,444,430 points in total). The loop is about 8 times slower than the `MERGE`, which reads the bookings once and lets the optimizer choose how to group and join.

How to choose:

- **Write it as one statement when the logic is a query**: filter, group, join, compute, write. `MERGE`, `INSERT ... SELECT`, `UPDATE` with subqueries, and window functions ([16e](16e_window_functions.md)) cover a lot.
- **Use PL/SQL when the logic is really step by step**: each step depends on the previous one (assigning tables one booking at a time, each assignment changing what is free for the next), per-row error handling, calling other procedures per row, or limits like "stop after the first N" that depend on running state, as in the course's elected-candidates exercise.
- **When it must be a loop, make it a bulk loop** ([bulk collect and forall](16k_plsql_cursors.md#bulk-collect-and-forall)).

## Common mistakes

- **Querying the triggering table from a row trigger**, which works in single-row tests and fails with `ORA-04091` on the first multi-row statement.
- **Business processes in triggers**: sending emails or calling services from a trigger runs them even when the transaction later rolls back, and hides them from anyone reading the application code.
- **Relying on a trigger to enforce a rule across concurrent sessions.**
- **Row triggers on tables that receive bulk loads**, slowing every row.
- **A cursor loop for something one SQL statement does.**
- **Committing in a trigger**, which Oracle forbids (`ORA-04092`) unless the trigger runs as an autonomous transaction, which then commits independently of the statement that fired it.
