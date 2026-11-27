# PL/SQL: Procedures, Functions, Packages and Exceptions

Anonymous blocks run once. Logic that is called again and again, by applications or by other PL/SQL, is stored in the database as **named** blocks: procedures, functions, and packages that group them. This page covers how to define and call them, how parameters work, and how errors are raised, handled and propagated, including what an unhandled error does to the transaction.

The examples run on Oracle Database Free (23.26), on the restaurant schema. `sqlplus` column output has been reformatted into tables; the values are unchanged.

## Procedures

A **procedure** is a named block that performs an action. It is created once, compiled, and stored:

```sql
CREATE OR REPLACE PROCEDURE book_table(
    p_email      IN  customers.email%TYPE,
    p_restaurant IN  restaurants.id%TYPE,
    p_at         IN  DATE,
    p_party      IN  NUMBER,
    p_booking_id OUT bookings.id%TYPE
) IS
    v_customer customers.id%TYPE;            -- declarations: no DECLARE keyword
    v_table    restaurant_tables.id%TYPE;
BEGIN
    SELECT id INTO v_customer FROM customers WHERE email = p_email;

    SELECT id INTO v_table FROM (             -- the smallest free table that fits
        SELECT t.id FROM restaurant_tables t
        WHERE t.restaurant_id = p_restaurant AND t.seats >= p_party
          AND NOT EXISTS (SELECT 1 FROM booking_tables bt JOIN bookings b ON b.id = bt.booking_id
                          WHERE bt.table_id = t.id AND b.starts_at = p_at AND b.status = 'CONFIRMED')
        ORDER BY t.seats, t.table_no)
    WHERE ROWNUM = 1;

    INSERT INTO bookings (customer_id, restaurant_id, starts_at, party_size)
    VALUES (v_customer, p_restaurant, p_at, p_party)
    RETURNING id INTO p_booking_id;

    INSERT INTO booking_tables VALUES (p_booking_id, v_table);
END;
/
```

If the body has errors, `CREATE` still stores the procedure, marked invalid, and prints `Warning: Procedure created with compilation errors`; `SHOW ERRORS` lists them. Calling an invalid procedure fails.

It is called from a block, or with `CALL` / `EXEC` from a SQL client:

```sql
DECLARE
    v_id NUMBER;
BEGIN
    book_table('cat@example.com', 1, TIMESTAMP '2026-12-19 20:00:00', 3, v_id);
    DBMS_OUTPUT.PUT_LINE('booked ' || v_id);
END;
/
```

```
booked 5
```

### Parameters

| Mode | Inside the procedure | The caller passes |
|---|---|---|
| `IN` (default) | read-only | any expression |
| `OUT` | write-only; starts as `NULL` | a variable, which receives the value at the end |
| `IN OUT` | read and write | a variable, whose value goes in and comes back changed |

- Parameter types have **no size**: `VARCHAR2`, not `VARCHAR2(100)`. The size comes from the value passed. `%TYPE` works and is the safer choice.
- Parameters can have defaults (`p_party NUMBER DEFAULT 2`), and callers can then omit them.
- Arguments can be passed by position or by name; named notation is clearer for long lists and does not break when parameters are reordered:

```sql
book_table(p_email => 'ada@example.com', p_restaurant => 1,
           p_at => TIMESTAMP '2026-12-05 20:00:00', p_party => 4, p_booking_id => v_id);
```

`RETURNING id INTO` reads the identity value of the inserted row in the same statement ([reading the generated id](../18_data_modeling_and_persistence/18a_entities_and_keys.md#reading-the-generated-id-in-java)).

## Functions

A **function** computes and returns a value. It can be called from PL/SQL and, if it does not change data, from SQL itself:

```sql
CREATE OR REPLACE FUNCTION free_seats(p_restaurant NUMBER, p_at DATE) RETURN NUMBER IS
    v_total NUMBER;
    v_taken NUMBER;
BEGIN
    SELECT NVL(SUM(seats), 0) INTO v_total
    FROM restaurant_tables WHERE restaurant_id = p_restaurant;

    SELECT NVL(SUM(t.seats), 0) INTO v_taken
    FROM bookings b
    JOIN booking_tables bt   ON bt.booking_id = b.id
    JOIN restaurant_tables t ON t.id = bt.table_id
    WHERE b.restaurant_id = p_restaurant AND b.starts_at = p_at AND b.status = 'CONFIRMED';

    RETURN v_total - v_taken;
END;
/

SELECT name, free_seats(id, TIMESTAMP '2026-12-05 20:00:00') AS free_at_20 FROM restaurants ORDER BY id;
```

| NAME | FREE_AT_20 |
|---|---|
| Trattoria | 0 |
| Sushi Bar | 2 |

Every path through a function must reach a `RETURN`; one that ends without it raises `ORA-06503: Function returned without value`.

A function called in a query runs **once per row**, and each call runs its own queries: the N+1 problem inside the database. For a report over thousands of rows, the same logic written as a join with `GROUP BY` is much faster. Functions in SQL suit cheap computations (formatting, a tier from a number of points) more than queries.

| | Procedure | Function |
|---|---|---|
| Purpose | perform an action | compute a value |
| Returns | through `OUT` parameters | a `RETURN` value (and `OUT` parameters, rarely) |
| Called from SQL | no | yes, if it changes no data |

## Packages

A **package** groups related procedures, functions, types, constants and variables under one name. It has two parts, compiled separately:

- the **specification**: what callers can use;
- the **body**: the implementation, and anything private.

```sql
CREATE OR REPLACE PACKAGE loyalty AS
    c_points_per_guest CONSTANT NUMBER := 5;
    PROCEDURE award(p_booking_id NUMBER);
    FUNCTION tier(p_points NUMBER) RETURN VARCHAR2;
    FUNCTION calls_in_session RETURN NUMBER;
END loyalty;
/

CREATE OR REPLACE PACKAGE BODY loyalty AS
    g_calls NUMBER := 0;                                   -- private, one copy per session

    FUNCTION tier(p_points NUMBER) RETURN VARCHAR2 IS
    BEGIN
        RETURN CASE WHEN p_points >= 100 THEN 'GOLD'
                    WHEN p_points >= 30  THEN 'SILVER'
                    ELSE 'BASIC' END;
    END;

    PROCEDURE award(p_booking_id NUMBER) IS
    BEGIN
        g_calls := g_calls + 1;
        UPDATE customers c
        SET points = points + c_points_per_guest *
                     (SELECT party_size FROM bookings b WHERE b.id = p_booking_id)
        WHERE c.id = (SELECT customer_id FROM bookings WHERE id = p_booking_id);
    END;

    FUNCTION calls_in_session RETURN NUMBER IS BEGIN RETURN g_calls; END;
END loyalty;
/
```

```sql
BEGIN
    loyalty.award(1);       -- Ada, party of 8
    loyalty.award(4);       -- Cat, party of 4
    DBMS_OUTPUT.PUT_LINE('calls=' || loyalty.calls_in_session);
END;
/
SELECT name, points, loyalty.tier(points) AS tier FROM customers ORDER BY id;
```

```
calls=2
```

| NAME | POINTS | TIER |
|---|---|---|
| Ada | 40 | SILVER |
| Bob | 0 | BASIC |
| Cat | 20 | BASIC |

What packages give:

- **Encapsulation.** Only what is in the specification is callable; `g_calls` cannot be read or changed from outside.
- **Fewer recompilations.** Changing the body does not invalidate the code that calls the package, as long as the specification is unchanged.
- **Session state.** Package variables keep their values for the whole session, across calls. Useful for caches and counters, but each connection of a pool has its own copy, so state set by one request is seen by whichever request gets that connection next.
- **A namespace**: `loyalty.award` cannot clash with another `award`.

`DBMS_OUTPUT`, `DBMS_ASSERT` and `DBMS_UTILITY` used on these pages are packages supplied by Oracle.

## Exceptions

An error at runtime raises an **exception**. Control jumps to the `EXCEPTION` section of the current block; if it has a matching handler, the handler runs and the block ends normally. If not, the exception **propagates** to the enclosing block, then to the caller, and finally to the client.

```sql
BEGIN
    ...
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        ...
    WHEN TOO_MANY_ROWS OR VALUE_ERROR THEN
        ...
    WHEN OTHERS THEN            -- anything else
        ...
END;
```

After a handler runs, execution does **not** return to the statement that failed: the block is over. To handle an error and continue, wrap the risky statement in its own nested block with its own handler.

### Predefined exceptions

Common Oracle errors have names:

| Exception | Oracle error | Raised when |
|---|---|---|
| `NO_DATA_FOUND` | ORA-01403 | `SELECT INTO` returns no rows |
| `TOO_MANY_ROWS` | ORA-01422 | `SELECT INTO` returns more than one row |
| `DUP_VAL_ON_INDEX` | ORA-00001 | an insert or update violates a unique constraint |
| `VALUE_ERROR` | ORA-06502 | a conversion or size error (a string too long for its variable) |
| `ZERO_DIVIDE` | ORA-01476 | division by zero |
| `INVALID_CURSOR` | ORA-01001 | fetching from a cursor that is not open |
| `CURSOR_ALREADY_OPEN` | ORA-06511 | opening a cursor twice |

Inside a handler, `SQLCODE` gives the error number and `SQLERRM` its message.

### User-defined exceptions and `RAISE_APPLICATION_ERROR`

Business rules raise their own errors. The complete `book_table` adds three:

```sql
    e_party_too_large EXCEPTION;                           -- in the declarations
BEGIN
    IF p_party > 12 THEN RAISE e_party_too_large; END IF;
    ...
EXCEPTION
    WHEN e_party_too_large THEN
        RAISE_APPLICATION_ERROR(-20001, 'party of ' || p_party || ' is too large to book online');
    WHEN NO_DATA_FOUND THEN
        IF v_customer IS NULL THEN
            RAISE_APPLICATION_ERROR(-20002, 'no customer ' || p_email);
        ELSE
            RAISE_APPLICATION_ERROR(-20003, 'no free table for ' || p_party
                                    || ' at ' || TO_CHAR(p_at, 'YYYY-MM-DD HH24:MI'));
        END IF;
END;
```

- `RAISE` raises a declared exception.
- `RAISE_APPLICATION_ERROR(code, message)` raises an error with a number between -20000 and -20999 and a message, and is how stored code reports a business error to the client. A Java caller receives it as an `SQLException` with that code.
- Both `SELECT INTO`s can raise `NO_DATA_FOUND`; the handler tells them apart by which variable is still `NULL`. Splitting each into its own nested block, with its own handler, is clearer when there are more than two.

```
ORA-20002: no customer nobody@example.com
ORA-20001: party of 30 is too large to book online
ORA-20003: no free table for 4 at 2026-12-05 20:00
```

A caller that wants to handle one of these by name gives the number a name with `PRAGMA EXCEPTION_INIT`:

```sql
DECLARE
    v_id NUMBER;
    e_no_table EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_no_table, -20003);
BEGIN
    book_table('ada@example.com', 1, TIMESTAMP '2026-12-05 20:00:00', 4, v_id);
EXCEPTION
    WHEN e_no_table THEN
        DBMS_OUTPUT.PUT_LINE('caught: ' || SQLCODE || ' ' || SQLERRM);
END;
/
```

```
caught: -20003 ORA-20003: no free table for 4 at 2026-12-05 20:00
```

### Unhandled exceptions undo the call's work

When an exception leaves a top-level call (an anonymous block sent by a client, or a procedure called from one) unhandled, Oracle rolls back **everything that call changed**, like a single statement that failed. The transaction itself stays open, and work done by earlier calls is kept.

Three bookings in one block, where the third finds no table:

```sql
SELECT count(*) FROM bookings;                -- 4

DECLARE v_id NUMBER;
BEGIN
    book_table('bob@example.com', 1, TIMESTAMP '2026-12-26 20:00:00', 2, v_id);   -- ok
    book_table('bob@example.com', 1, TIMESTAMP '2026-12-26 20:00:00', 6, v_id);   -- ok
    book_table('bob@example.com', 1, TIMESTAMP '2026-12-26 20:00:00', 6, v_id);   -- fails
END;
/

SELECT count(*) FROM bookings;                -- still 4
```

```
ORA-20003: no free table for 6 at 2026-12-26 20:00
```

The two bookings that succeeded were undone with the failed one: the block is all or nothing. The same happened to `booked 5` earlier, whose block failed on its second call: booking 5 does not exist.

### `WHEN OTHERS THEN NULL`

A handler that catches everything and does nothing turns every error into success:

```sql
DECLARE v_id NUMBER;
BEGIN
    book_table('bob@example.com',    1, TIMESTAMP '2026-12-27 20:00:00', 2, v_id);   -- ok
    book_table('nobody@example.com', 1, TIMESTAMP '2026-12-27 20:00:00', 2, v_id);   -- fails
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/
SELECT count(*) FROM bookings;                -- 5
```

The block reports success, the first booking is kept, and the failure of the second has disappeared without a trace. The caller believes both were booked. Oracle's compiler warns about it (`PLW-06009`) when warnings are enabled.

`WHEN OTHERS` is legitimate only when it logs the error and re-raises it:

```sql
EXCEPTION
    WHEN OTHERS THEN
        log_error(SQLCODE, SQLERRM, DBMS_UTILITY.FORMAT_ERROR_BACKTRACE);
        RAISE;                        -- re-raise the same exception
END;
```

### Where to commit

None of the procedures above commits. The caller decides whether `book_table` and `loyalty.award` belong to one transaction ([where the transaction belongs](../18_data_modeling_and_persistence/18g_transactions_in_code.md#where-the-transaction-belongs)). A procedure that commits ends the caller's transaction too, and makes it impossible to combine with other work. The exception is a top-level batch job that is itself the whole unit of work.

## Common mistakes

- **Sizes on parameter types** (`VARCHAR2(100)`), which do not compile.
- **Functions with queries called per row** in a large `SELECT`.
- **A function that can end without `RETURN`.**
- **`WHEN OTHERS THEN NULL`**, or a handler that prints and continues.
- **Error codes outside -20000 to -20999** in `RAISE_APPLICATION_ERROR`.
- **Package variables used as shared state** across users: each session has its own copy.
- **`COMMIT` in every procedure.**
