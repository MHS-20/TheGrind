# PL/SQL: Blocks, Variables and Control Flow

SQL says **what** data to get, not how to get it. Some tasks need steps, conditions and loops: "for every course that has enough students this week, assign them; otherwise cancel the course". **PL/SQL** is Oracle's procedural extension of SQL: a language with variables, `IF` and loops, whose code runs inside the database next to the data. Other databases have their own (PostgreSQL's PL/pgSQL, used in [triggers](16h_triggers.md); SQL Server's T-SQL). This page covers the basic unit of PL/SQL, the block, and the language inside it.

The examples run on Oracle Database Free (23.26) in Docker, with `sqlplus`, on the restaurant schema from [18b](../18_data_modeling_and_persistence/18b_relationships.md). The output shown is its output.

## Why run code in the database

The same logic can run in the application, sending SQL over JDBC, or in the database as PL/SQL. Running it in the database:

- **sends less data over the network.** A loop over every restaurant that queries its bookings costs one round trip per query from the application; inside the database it costs none.
- **keeps one copy of the logic**, shared by every application that calls it.
- **can be secured separately**: a user can be allowed to run a procedure without having rights on the tables it touches.

The costs: the logic is harder to test, version and debug than application code, and it ties the application to one database product. Most modern systems keep business rules in the application and use stored code for data-heavy batch work and for rules that must hold whatever application writes the data ([18e](../18_data_modeling_and_persistence/18e_data_access_patterns.md) covers the application side).

How it runs: a block is first **compiled** (syntax checked, then every table and column it names is checked to exist, then turned into code for the PL/SQL engine). The engine runs the procedural statements itself and passes each SQL statement to the SQL engine. Each switch between the two engines has a cost, which matters inside loops ([cursors](16k_plsql_cursors.md#bulk-collect-and-forall)).

## The block

A block has three sections:

```sql
DECLARE
    -- variables, constants, cursors (optional)
BEGIN
    -- statements (required)
EXCEPTION
    -- error handlers (optional)
END;
/
```

The `/` on its own line tells `sqlplus` (and SQL Developer's script mode) to run the block.

Blocks come in two kinds:

- **Anonymous** blocks have no name and run once, like a script. They are compiled every time they are sent.
- **Named** blocks are compiled once and stored in the database: procedures, functions, triggers and packages ([procedures and exceptions](16l_plsql_procedures_and_exceptions.md), [triggers](16m_plsql_triggers.md)). In their definitions, the `DECLARE` keyword is implicit.

An anonymous block that reads one restaurant:

```sql
SET SERVEROUTPUT ON

DECLARE
    v_name   restaurants.name%TYPE;
    v_tables NUMBER;
BEGIN
    SELECT r.name, count(t.id) INTO v_name, v_tables
    FROM restaurants r LEFT JOIN restaurant_tables t ON t.restaurant_id = r.id
    WHERE r.id = 1
    GROUP BY r.name;

    DBMS_OUTPUT.PUT_LINE(v_name || ' has ' || v_tables || ' tables');
END;
/
```

```
Trattoria has 3 tables
```

`DBMS_OUTPUT.PUT_LINE` writes a line to a buffer that the client prints after the block ends, if output is enabled (`SET SERVEROUTPUT ON` in `sqlplus`, the "DBMS Output" panel in SQL Developer). It is for debugging and exercises: a procedure called by an application returns values or writes rows, it does not print. `||` concatenates strings.

## Variables

```sql
identifier [CONSTANT] datatype [NOT NULL] [:= | DEFAULT expression];
```

```sql
DECLARE
    v_city        VARCHAR2(100);
    v_total_cents NUMBER(12) := 0;
    v_from        DATE := SYSDATE;
    v_found       BOOLEAN NOT NULL := FALSE;
    c_vat         CONSTANT NUMBER(4,2) := 0.10;
```

- Variables start as `NULL` unless initialised. `NOT NULL` variables must be initialised.
- Assignment is `:=`; `=` is comparison.
- Names are not case-sensitive. A variable with the same name as a column is ambiguous inside SQL statements: Oracle resolves the name to the column first, so `WHERE name = name` is always true. A prefix (`v_name`, `p_name` for parameters, `c_` for constants) avoids it.

The main scalar types:

| Type | Holds |
|---|---|
| `VARCHAR2(n)` | text up to n bytes (up to 32,767 in PL/SQL, 4,000 in a table column by default) |
| `NUMBER(p, s)` | decimal numbers: p digits, s after the point; `NUMBER(5,2)` holds 999.99 |
| `DATE` | date and time to the second |
| `TIMESTAMP` | date and time with fractional seconds |
| `BOOLEAN` | `TRUE`, `FALSE` or `NULL`; usable in PL/SQL, and in tables only from Oracle 23 |
| `PLS_INTEGER` | integers, faster than `NUMBER` for counters |

### Anchored types: `%TYPE` and `%ROWTYPE`

A variable that holds a column's value should have the column's type. Writing `VARCHAR2(100)` by hand breaks when the column becomes `VARCHAR2(200)`: the block still compiles, then fails at runtime on a long value. **Anchored** types take the type from the column:

```sql
v_name     restaurants.name%TYPE;     -- whatever type restaurants.name has
v_min_name v_name%TYPE;               -- same type as another variable
```

The failure that anchoring avoids, with a variable declared too small:

```sql
DECLARE
    v_short VARCHAR2(3);
BEGIN
    SELECT name INTO v_short FROM restaurants WHERE id = 1;
END;
/
```

```
ORA-06502: PL/SQL: value or conversion error: character string buffer too small
```

`%ROWTYPE` declares a **record** with one field per column of a table (or of a cursor), with the same names and types:

```sql
DECLARE
    r customers%ROWTYPE;
BEGIN
    SELECT * INTO r FROM customers WHERE email = 'ada@example.com';
    DBMS_OUTPUT.PUT_LINE(r.id || ' ' || r.name || ' points=' || r.points);
END;
/
```

```
1 Ada points=0
```

### Scope

Blocks can be nested anywhere a statement can go. A variable is visible in its block and in the blocks nested inside it, not outside. An inner declaration with the same name **hides** the outer one:

```sql
DECLARE
    v_count NUMBER := 1;
BEGIN
    DECLARE
        v_count NUMBER := 2;
    BEGIN
        DBMS_OUTPUT.PUT_LINE('inner sees v_count=' || v_count);
    END;
    DBMS_OUTPUT.PUT_LINE('outer sees v_count=' || v_count);
END;
/
```

```
inner sees v_count=2
outer sees v_count=1
```

A variable exists from the moment its block starts until the block's `END`; the next run of the block starts with fresh variables. Nested blocks are mostly used to give one part of the code its own exception handler ([exceptions](16l_plsql_procedures_and_exceptions.md#exceptions)).

## `SELECT INTO`

`SELECT ... INTO` reads one row into variables, one variable per selected column, in order. The query must return **exactly one** row. Zero rows and more than one row are both errors:

```sql
SELECT name INTO v_name FROM customers WHERE email = 'nobody@example.com';
```

```
ORA-01403: no data found
```

```sql
SELECT name INTO v_name FROM customers;
```

```
ORA-01422: exact fetch returned more than the requested number of rows (1)
```

These are the exceptions `NO_DATA_FOUND` and `TOO_MANY_ROWS`, and handling them is the usual way to treat "not found" ([exceptions](16l_plsql_procedures_and_exceptions.md#predefined-exceptions)). An aggregate without `GROUP BY` (`SELECT count(*) INTO ...`) always returns one row, so it never raises either. For queries that return many rows, use a cursor ([cursors](16k_plsql_cursors.md)).

## SQL inside PL/SQL

**Static SQL**: `SELECT INTO`, `INSERT`, `UPDATE`, `DELETE` and `MERGE` can be written directly in the block, with variables where values go. They are checked at compile time: a wrong column name is a compile error, not a runtime one. Variables are passed as bind values, so there is no SQL injection.

After a DML statement, the **implicit cursor** attributes describe what it did:

```sql
BEGIN
    UPDATE menu_items SET price_cents = price_cents * 1.1 WHERE restaurant_id = 1;
    DBMS_OUTPUT.PUT_LINE(SQL%ROWCOUNT || ' prices raised');
    ROLLBACK;
END;
/
```

```
2 prices raised
```

`SQL%ROWCOUNT` is the number of rows affected; `SQL%FOUND` and `SQL%NOTFOUND` say whether it was at least one. An `UPDATE` that matched nothing is not an error, so checking `SQL%ROWCOUNT` is how to notice it ([ignoring the row count](../18_data_modeling_and_persistence/18g_transactions_in_code.md#common-mistakes)).

DDL (`CREATE TABLE`) and statements whose text is only known at runtime need **dynamic SQL**: `EXECUTE IMMEDIATE` with a string.

```sql
DECLARE
    v_cnt   NUMBER;
    v_table VARCHAR2(30) := 'BOOKINGS';
BEGIN
    EXECUTE IMMEDIATE
        'SELECT count(*) FROM ' || DBMS_ASSERT.SIMPLE_SQL_NAME(v_table) || ' WHERE status = :s'
        INTO v_cnt
        USING 'CONFIRMED';
    DBMS_OUTPUT.PUT_LINE(v_table || ' confirmed: ' || v_cnt);
END;
/
```

```
BOOKINGS confirmed: 3
```

- Values go through placeholders (`:s`) and `USING`, never concatenation.
- Table and column names **cannot** be bind values, so they must be concatenated. Validate them first: `DBMS_ASSERT.SIMPLE_SQL_NAME` rejects anything that is not a plain name:

```sql
EXECUTE IMMEDIATE 'SELECT count(*) FROM ' || DBMS_ASSERT.SIMPLE_SQL_NAME('bookings; drop table customers') INTO v_cnt;
```

```
ORA-44003: invalid SQL name
```

- The string has no trailing `;`.

Prefer static SQL whenever the statement is known in advance: it is checked at compile time and cannot be injected.

## Control flow

### `IF` and `CASE`

```sql
FOR b IN (SELECT id, party_size FROM bookings ORDER BY id) LOOP
    v_label := CASE
                   WHEN b.party_size >= 8 THEN 'large'
                   WHEN b.party_size >= 4 THEN 'medium'
                   ELSE 'small'
               END;
    IF b.party_size > 6 THEN
        DBMS_OUTPUT.PUT_LINE('booking ' || b.id || ': ' || v_label || ', needs two tables');
    ELSIF b.party_size > 2 THEN
        DBMS_OUTPUT.PUT_LINE('booking ' || b.id || ': ' || v_label);
    ELSE
        DBMS_OUTPUT.PUT_LINE('booking ' || b.id || ': ' || v_label || ', any table');
    END IF;
END LOOP;
```

```
booking 1: large, needs two tables
booking 2: small, any table
booking 3: small, any table
booking 4: medium
```

The keyword is `ELSIF`, not `ELSEIF` or `ELSE IF`. A `CASE` expression returns a value; a `CASE` statement (`CASE ... WHEN ... THEN statement; END CASE;`) runs statements and raises `CASE_NOT_FOUND` if no branch matches and there is no `ELSE`.

Conditions follow SQL's three-valued logic ([NULL](16a_basics.md#null)): a comparison with `NULL` is `NULL`, and `IF` treats `NULL` like `FALSE`.

```sql
DECLARE
    v NUMBER;
BEGIN
    IF v = NULL THEN DBMS_OUTPUT.PUT_LINE('equal');
    ELSE DBMS_OUTPUT.PUT_LINE('v = NULL is not true');
    END IF;
    IF v IS NULL THEN DBMS_OUTPUT.PUT_LINE('v IS NULL is true'); END IF;
END;
/
```

```
v = NULL is not true
v IS NULL is true
```

### Loops

```sql
DECLARE
    i NUMBER := 0;
BEGIN
    LOOP                                 -- basic loop: exits only through EXIT
        i := i + 1;
        EXIT WHEN i >= 3;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('basic loop i=' || i);

    WHILE i < 5 LOOP                     -- condition checked before each pass
        i := i + 1;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('while i=' || i);

    FOR k IN REVERSE 1..3 LOOP           -- numeric FOR: k is declared by the loop
        DBMS_OUTPUT.PUT(k || ' ');
    END LOOP;
    DBMS_OUTPUT.NEW_LINE;
END;
/
```

```
basic loop i=3
while i=5
3 2 1
```

- The numeric `FOR` loop declares its own counter, which exists only inside the loop and cannot be assigned.
- `CONTINUE` / `CONTINUE WHEN` skips to the next iteration.
- The **cursor `FOR` loop** (`FOR b IN (SELECT ...) LOOP`, used above) runs once per row of a query, and is the most common loop in PL/SQL ([cursors](16k_plsql_cursors.md#cursor-for-loops)).

## Common mistakes

- **Variables named like columns**, so `WHERE name = name` compares the column with itself.
- **Hand-written types** instead of `%TYPE`, failing when the column changes.
- **`SELECT INTO` for a query that can return zero or many rows**, without handling the exception.
- **`= NULL`** instead of `IS NULL`.
- **Concatenating values into dynamic SQL** instead of binding them.
- **`DBMS_OUTPUT` as the way a procedure returns results.** Output is lost unless the client enabled it; return values with `OUT` parameters, functions, or rows.
- **`COMMIT` inside reusable code.** A block that commits ends the caller's transaction; leave transaction control to the caller ([18g](../18_data_modeling_and_persistence/18g_transactions_in_code.md#where-the-transaction-belongs)).
