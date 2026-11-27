# Transactions in Application Code

[16g](../16_sql_databases/16g_modifying_data_and_transactions.md#transactions) covers transactions in SQL: `BEGIN`, `COMMIT`, `ROLLBACK`, isolation levels. This page is about the code side: who opens and closes the transaction, how several DAOs share one, and how to stop two users from overwriting each other's changes with optimistic and pessimistic locking.

The examples were run with JDBC against PostgreSQL 17, on the schema from [relationships](18b_relationships.md).

## Autocommit: one transaction per statement

A new JDBC connection is in **autocommit** mode: every statement is its own transaction, committed as soon as it finishes. A DAO that opens its own connection per method therefore commits after each method.

Creating a booking is two writes: the booking row, then its links in `booking_tables`. In autocommit, with a table id that does not exist:

```java
try (Connection c = ds.getConnection()) {                       // autocommit
    createBooking(c, customerId, at, 10, List.of(1L, 999L));     // insert booking, then links
}
```

```
autocommit failed: ... violates foreign key constraint "booking_tables_table_id_fkey"
bookings without tables: 1
```

The booking was committed before the links failed. The database now has a booking for ten people with no tables, a state no rule of the domain allows.

## A transaction around the unit of work

Turn autocommit off, do all the writes on the **same connection**, then commit, or roll back on any failure:

```java
try (Connection c = ds.getConnection()) {
    c.setAutoCommit(false);
    try {
        createBooking(c, customerId, at, 10, List.of(1L, 999L));
        c.commit();
    } catch (SQLException e) {
        c.rollback();
        throw new PersistenceException("cannot create booking", e);
    }
}
```

```
transaction rolled back: ... violates foreign key constraint "booking_tables_table_id_fkey"
bookings after rollback: 0
```

Nothing is left behind. The id the rolled-back booking took is still used up ([ids have gaps](18a_entities_and_keys.md#ids-have-gaps)): the next booking got id 3, not 2.

Two requirements follow for the code:

- **The writes must share a connection.** A transaction belongs to a connection. If the booking DAO and the mapping DAO each call `ds.getConnection()`, they are in two transactions and the rollback undoes only one. That is why the mapping DAO methods in [data access patterns](18e_data_access_patterns.md#the-mapping-dao-for-nn) take a `Connection` parameter.
- **Restore the connection's state.** With a pool, the same connection is reused by the next request. Commit or roll back before returning it, or the next user inherits an open transaction.

## Where the transaction belongs

A DAO knows one table; a business operation often touches several. "Book a table" means: check the table is free, insert the booking, insert the links, add loyalty points. Only the **service** knows that these four writes form one unit, so the service decides where the transaction starts and ends:

```
BookingService.book(...)          <- begin
    tableDao.isFree(...)
    bookingDao.create(...)
    mappingDao.link(...)
    loyaltyDao.addPoints(...)
                                  <- commit, or rollback on any exception
```

With plain JDBC the service opens the connection, turns off autocommit, and passes the connection to each DAO call. Frameworks hide that plumbing: Spring's `@Transactional` on a service method binds a connection to the current thread, and every DAO or repository called inside the method uses it.

The opposite mistake is a transaction per DAO method: each method is safe, and the operation as a whole is not.

### Unit of work

A **unit of work** goes one step further: during a business operation, it records which objects were created, changed and deleted, and at commit time writes all of them in one transaction, in an order that satisfies the foreign keys (parents inserted before children, children deleted before parents). The code changes objects in memory and never calls `update` explicitly. JPA's persistence context is a unit of work ([JPA and Hibernate](18h_jpa_and_hibernate.md#the-persistence-context)); with hand-written DAOs, the service plays that role by hand.

## Lost updates

Transactions protect a group of writes from partial failure. They do not, at the default isolation level, stop two users from overwriting each other.

A waiter and a manager both open booking 3 (party of 4). The waiter changes it to 6 and saves; the manager, still looking at the old screen, changes it to 5 and saves. The manager's write silently replaces the waiter's: a **lost update**. Neither got an error, because each save was a correct transaction on its own. The problem is the time between reading the data and writing it back, which spans two transactions (or two HTTP requests).

There are two families of fixes.

## Optimistic locking: detect the conflict

Add a version number to the row. Every update checks that the version is the one it read, and increments it:

```sql
ALTER TABLE bookings ADD COLUMN version int NOT NULL DEFAULT 0;
```

```java
String sql = "UPDATE bookings SET party_size = ?, version = version + 1 WHERE id = ? AND version = ?";
ps.setInt(1, newSize);
ps.setLong(2, booking.getId());
ps.setInt(3, booking.getVersion());           // the version read when the screen was opened
int rows = ps.executeUpdate();
if (rows == 0) throw new ConcurrentModificationException("booking changed by someone else");
```

Both users read version 0. The waiter's update matches and moves the row to version 1; the manager's update finds no row with version 0:

```
waiter update, version 0: rows=1
manager update, version 0: rows=0
party_size now: 6, version 1
```

The manager's code sees 0 rows and reports a conflict, instead of overwriting. The application then reloads the booking and lets the user decide.

Optimistic locking takes no locks: it is a check at write time. It suits data that is rarely edited by two people at once, and edits that span user think-time (a form open for minutes), where holding a database lock would be unacceptable. JPA supports it with a `@Version` field.

## Pessimistic locking: prevent the conflict

Lock the row when reading it, so nobody else can lock or change it until the transaction ends. `SELECT ... FOR UPDATE` does it:

```java
c1.setAutoCommit(false);
c1.createStatement().executeQuery("SELECT * FROM restaurant_tables WHERE id = 3 FOR UPDATE");
// c1 now holds the row lock until commit or rollback

c2.setAutoCommit(false);
c2.createStatement().execute("SET lock_timeout = '500ms'");
c2.createStatement().executeQuery("SELECT * FROM restaurant_tables WHERE id = 3 FOR UPDATE");
```

```
c1 locked table 3
c2: ERROR: canceling statement due to lock timeout
```

Without the timeout, `c2` would wait until `c1` commits. Two variants are useful:

- `FOR UPDATE NOWAIT` fails at once instead of waiting.
- `FOR UPDATE SKIP LOCKED` returns only the rows nobody holds. Asking for tables 3 and 4 while 3 is locked:

```
c2 skip locked got: [4]
```

`SKIP LOCKED` is how a database table works as a job queue: each worker takes the next unlocked job, and no two workers take the same one.

Pessimistic locking fits short, contended operations inside one transaction: two people booking the last free table at 20:00. It is the database version of the `synchronized` block in [concurrency for LLD](../17_object_oriented_design/17g_concurrency_basics.md#locks-synchronized-and-atomics), with the same deadlock rule: lock rows in a fixed order (by id) when locking several.

## Choosing

| | Optimistic (version column) | Pessimistic (`FOR UPDATE`) |
|---|---|---|
| Conflict is | detected at write, one side fails | prevented, the other side waits |
| Locks held | none | until the transaction ends |
| Spans user think-time | yes | no: never hold a lock while waiting for a person |
| Contention | low: retries are rare | high: retries would keep failing |
| Cost when no conflict | one extra column and condition | lock management, possible waits |

A constraint can be better than either. "No two bookings on the same table at overlapping times" can be enforced by the database itself with an exclusion constraint, and then no application-level lock is needed: the second insert fails.

## Common mistakes

- **A connection per DAO call inside one business operation**, so the "transaction" is several, and failures leave half the data.
- **Catching an exception and committing anyway**, or forgetting `rollback()` before returning a connection to the pool.
- **Long transactions**: holding a transaction (and its locks) open across a remote call or user input.
- **Read, modify, write back** with no version check, on data several people edit: lost updates.
- **Ignoring the row count** of an `UPDATE`. Zero rows means the row is gone or was changed; treating it as success hides both.
- **Retrying on conflict blindly.** After an optimistic failure, reload and re-apply the user's change on fresh data, or ask the user.
