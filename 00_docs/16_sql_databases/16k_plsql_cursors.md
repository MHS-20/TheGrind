# PL/SQL: Cursors

`SELECT INTO` reads exactly one row ([blocks](16j_plsql_blocks.md#select-into)). To process a query that returns many rows one at a time, PL/SQL uses a **cursor**: a named query whose result is read row by row, like an iterator over a result set. This page covers explicit cursors and their attributes, the cursor `FOR` loop, parameters, updating the current row, returning a cursor to the caller, and why row-by-row processing is slow and how bulk operations fix it.

The examples run on Oracle Database Free (23.26), on the restaurant schema.

## Implicit and explicit cursors

Every SQL statement in PL/SQL runs through a cursor. For `SELECT INTO`, `INSERT`, `UPDATE` and `DELETE`, Oracle manages an **implicit cursor**, and its attributes are read through `SQL%` (`SQL%ROWCOUNT` after an update). An **explicit cursor** is declared by name in the `DECLARE` section, for a query whose rows the code walks through.

## Explicit cursors

The lifecycle is declare, open, fetch until there are no more rows, close:

```sql
DECLARE
    CURSOR c_tables IS
        SELECT table_no, seats FROM restaurant_tables WHERE restaurant_id = 1 ORDER BY table_no;
    v_row c_tables%ROWTYPE;                 -- a record shaped like one row of the cursor
BEGIN
    OPEN c_tables;                          -- runs the query
    LOOP
        FETCH c_tables INTO v_row;          -- next row into the record
        EXIT WHEN c_tables%NOTFOUND;        -- the last fetch found nothing
        DBMS_OUTPUT.PUT_LINE('table ' || v_row.table_no || ': ' || v_row.seats
                             || ' seats (row ' || c_tables%ROWCOUNT || ')');
    END LOOP;
    CLOSE c_tables;                         -- frees the cursor
END;
/
```

```
table 1: 4 seats (row 1)
table 2: 4 seats (row 2)
table 3: 6 seats (row 3)
```

The attributes of a cursor `c`:

| Attribute | Value |
|---|---|
| `c%FOUND` | `TRUE` if the last fetch returned a row |
| `c%NOTFOUND` | the opposite |
| `c%ROWCOUNT` | rows fetched so far |
| `c%ISOPEN` | whether the cursor is open |

### The exit test goes right after the fetch

When there are no more rows, `FETCH` does not raise an error: it leaves the variables unchanged and sets `%NOTFOUND`. If the code uses the variables before testing, it processes the last row twice:

```sql
LOOP
    FETCH c INTO v;
    DBMS_OUTPUT.PUT_LINE('processing ' || v);
    EXIT WHEN c%NOTFOUND;                   -- too late
END LOOP;
```

```
processing 1
processing 2
processing 3
processing 3
```

Opening a cursor that is already open raises `CURSOR_ALREADY_OPEN`, and a cursor left open stays open until the session ends. Both are reasons to prefer the `FOR` loop below.

## Cursor `FOR` loops

The cursor `FOR` loop does the open, fetch, exit test and close, and declares the loop record itself:

```sql
FOR r IN c_tables LOOP
    DBMS_OUTPUT.PUT_LINE(r.table_no || ' ' || r.seats);
END LOOP;
```

The query can also be written inline, with no declaration at all:

```sql
BEGIN
    FOR rest IN (SELECT id, name FROM restaurants ORDER BY id) LOOP
        DBMS_OUTPUT.PUT_LINE(rest.name);
        FOR t IN (SELECT table_no, seats FROM restaurant_tables
                  WHERE restaurant_id = rest.id ORDER BY table_no) LOOP
            DBMS_OUTPUT.PUT_LINE('  table ' || t.table_no || ' (' || t.seats || ')');
        END LOOP;
    END LOOP;
END;
/
```

```
Trattoria
  table 1 (4)
  table 2 (4)
  table 3 (6)
Sushi Bar
  table 1 (2)
```

The cursor is closed even when the loop is left by an exception. Use the explicit `OPEN`/`FETCH`/`CLOSE` form only when the code needs control the loop does not give: fetching from two cursors alternately, or stopping and resuming.

(The inner loop runs one query per restaurant: the [N+1 pattern](../18_data_modeling_and_persistence/18f_loading_related_data.md#the-n1-problem) again. Inside the database each query is cheap, since there is no network round trip, but a single join is still less work.)

## Cursors with parameters

A cursor can take parameters, used in its query like variables. One declaration then serves different inputs, and each `OPEN` (or `FOR`) passes values:

```sql
DECLARE
    CURSOR c_bookings(p_restaurant NUMBER, p_day DATE) IS
        SELECT b.id, c.name, b.party_size
        FROM bookings b JOIN customers c ON c.id = b.customer_id
        WHERE b.restaurant_id = p_restaurant
          AND TRUNC(b.starts_at) = p_day
          AND b.status = 'CONFIRMED'
        ORDER BY b.id;
BEGIN
    FOR r IN c_bookings(1, DATE '2026-12-05') LOOP
        DBMS_OUTPUT.PUT_LINE(r.id || ' ' || r.name || ' x' || r.party_size);
    END LOOP;
    FOR r IN c_bookings(2, DATE '2026-12-06') LOOP
        DBMS_OUTPUT.PUT_LINE('never printed: cancelled');
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('done');
END;
/
```

```
1 Ada x8
2 Bob x2
done
```

A loop over an empty result simply runs zero times. Parameter types are written without a size (`NUMBER`, not `NUMBER(10)`). The common use is an outer loop over parents and an inner parameterized cursor over each parent's children, as in the course's exercises.

## Updating the row being read

To update the rows a cursor reads, declare it `FOR UPDATE`, which locks them when the cursor opens ([pessimistic locking](../18_data_modeling_and_persistence/18g_transactions_in_code.md#pessimistic-locking-prevent-the-conflict)), and refer to the current row with `WHERE CURRENT OF`:

```sql
DECLARE
    CURSOR c IS SELECT id, points FROM customers FOR UPDATE OF points;
BEGIN
    FOR r IN c LOOP
        UPDATE customers SET points = points + 10 WHERE CURRENT OF c;
    END LOOP;
END;
/
```

```
Ada 10
Bob 10
Cat 10
```

The locks are held until the transaction ends. A `COMMIT` inside the loop releases them and invalidates the cursor: the next fetch fails with `ORA-01002: fetch out of sequence`.

This example is also something that should not be a loop: `UPDATE customers SET points = points + 10` does the same in one statement. Loops are for logic that one statement cannot express ([a cursor loop vs one statement](16m_plsql_triggers.md#a-cursor-loop-vs-one-statement)).

## Returning a cursor: `SYS_REFCURSOR`

A **cursor variable** (`SYS_REFCURSOR`) points to an open cursor and can be passed around. A function can open a query and return it; the caller fetches the rows. This is how a stored function hands a result set to a Java program, which reads it as a `ResultSet`:

```sql
CREATE OR REPLACE FUNCTION bookings_of(p_email VARCHAR2) RETURN SYS_REFCURSOR IS
    rc SYS_REFCURSOR;
BEGIN
    OPEN rc FOR
        SELECT b.id, b.starts_at, b.party_size
        FROM bookings b JOIN customers c ON c.id = b.customer_id
        WHERE c.email = p_email
        ORDER BY b.starts_at;
    RETURN rc;
END;
/
```

```sql
DECLARE
    rc SYS_REFCURSOR;
    v_id NUMBER; v_at DATE; v_size NUMBER;
BEGIN
    rc := bookings_of('ada@example.com');
    LOOP
        FETCH rc INTO v_id, v_at, v_size;
        EXIT WHEN rc%NOTFOUND;
        DBMS_OUTPUT.PUT_LINE(v_id || ' ' || TO_CHAR(v_at, 'YYYY-MM-DD HH24:MI') || ' x' || v_size);
    END LOOP;
    CLOSE rc;
END;
/
```

```
1 2026-12-05 20:00 x8
3 2026-12-06 13:00 x2
```

The caller must close it. A cursor variable can also be opened with a dynamic query string (`OPEN rc FOR v_sql USING ...`).

## `BULK COLLECT` and `FORALL`

Each SQL statement in a PL/SQL loop is a **context switch** from the PL/SQL engine to the SQL engine and back ([why run code in the database](16j_plsql_blocks.md#why-run-code-in-the-database)). Copying 100,000 rows with a loop does 100,000 inserts, each with its switch.

**Bulk** operations move whole collections across in one switch:

- `SELECT ... BULK COLLECT INTO collection` fetches all rows (or a batch with `LIMIT`) into collections.
- `FORALL i IN 1 .. n` sends one DML statement with all the values at once.

```sql
DECLARE
    TYPE t_ids     IS TABLE OF events.id%TYPE;
    TYPE t_amounts IS TABLE OF events.amount%TYPE;
    v_ids     t_ids;
    v_amounts t_amounts;
BEGIN
    SELECT id, amount BULK COLLECT INTO v_ids, v_amounts FROM events;
    FORALL i IN 1 .. v_ids.COUNT
        INSERT INTO events_copy VALUES (v_ids(i), v_amounts(i));
END;
/
```

Copying 100,000 rows three ways, three runs each:

```
row by row 760 ms | forall 160 ms | insert-select 10 ms
row by row 710 ms | forall 160 ms | insert-select 10 ms
row by row 780 ms | forall 160 ms | insert-select 10 ms
```

- The row-by-row loop is about 4.5 times slower than `FORALL`.
- A single `INSERT INTO events_copy SELECT ... FROM events` is 16 times faster again: no PL/SQL at all, and the SQL engine can optimise the whole operation.

The order of preference that follows:

1. **One SQL statement**, when the logic fits in SQL.
2. **Bulk operations**, when the logic needs PL/SQL between reading and writing.
3. **A row-by-row loop**, for small row counts or logic that really is per row (calling a procedure per row, stopping at the first error).

`BULK COLLECT` without `LIMIT` loads every row into memory. For large tables, fetch in batches:

```sql
OPEN c;
LOOP
    FETCH c BULK COLLECT INTO v_ids, v_amounts LIMIT 1000;
    EXIT WHEN v_ids.COUNT = 0;
    FORALL i IN 1 .. v_ids.COUNT
        INSERT INTO events_copy VALUES (v_ids(i), v_amounts(i));
END LOOP;
CLOSE c;
```

With `LIMIT`, the exit test is `COUNT = 0`, not `%NOTFOUND`: the last batch is usually partial and sets `%NOTFOUND` while still holding rows to process.

## Common mistakes

- **Testing `%NOTFOUND` after using the fetched values**, processing the last row twice.
- **Explicit cursors left open** on an error path; use the `FOR` loop or close in the exception handler.
- **A loop of single-row DML** where one `UPDATE` or `INSERT ... SELECT` would do.
- **`COMMIT` inside a `FOR UPDATE` loop.**
- **`BULK COLLECT` of a whole large table** into memory, without `LIMIT`.
- **`EXIT WHEN c%NOTFOUND` with `BULK COLLECT ... LIMIT`**, skipping the last partial batch.
