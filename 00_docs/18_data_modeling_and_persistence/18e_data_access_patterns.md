# Data Access Patterns

Code that runs SQL should not be spread across the classes that implement business rules. If it is, changing a table means searching the whole program, and the business rules cannot be tested without a database. Data access patterns put persistence behind an interface. This page covers DTOs, the DAO pattern with a factory per database, the mapping DAO for N:N relationships, Repository, Active Record and Data Mapper, and the fixed skeleton every JDBC method follows.

All the code on this page was run with JDBC against PostgreSQL 17, on the restaurant schema from [relationships](18b_relationships.md).

## The layers

```
+-----------------------------+
| service / business logic    |   BookingService: rules, transactions
+-----------------------------+
              | uses interfaces only
+-----------------------------+
| DAO interfaces              |   RestaurantDAO, RestaurantTableDAO, ...
+-----------------------------+
              | implemented by
+-----------------------------+
| DAO implementations (JDBC)  |   PgRestaurantDAO, ... : SQL lives only here
+-----------------------------+
              | DataSource
+-----------------------------+
| database                    |
+-----------------------------+
```

The service passes beans in and gets beans back. It never sees a `Connection`, a `ResultSet` or an SQL string.

## Support classes

Three small classes are shared by every DAO.

**`DataSource`**: the standard JDBC interface for getting connections. A DAO asks it for a connection per operation and closes it at the end. In production the `DataSource` is a connection pool, so "closing" returns the connection to the pool instead of opening a new TCP connection every time.

```java
PGSimpleDataSource ds = new PGSimpleDataSource();     // a pool (HikariCP) in production
ds.setURL("jdbc:postgresql://localhost:5432/restaurants");
ds.setUser("app");
ds.setPassword(secret);
```

**`PersistenceException`**: an unchecked exception that wraps `SQLException`. Callers above the DAO cannot do anything useful with a vendor SQL error code, and `SQLException` is checked, so without wrapping every service method would have to declare it. The wrapper keeps the cause for logging:

```java
class PersistenceException extends RuntimeException {
    PersistenceException(String message, Throwable cause) { super(message, cause); }
    PersistenceException(String message) { super(message); }
}
```

**IdBroker** (optional): generates surrogate ids when the database does not ([entities and keys](18a_entities_and_keys.md#a-key-table-idbroker)). With an identity column, the id is read back after the insert instead.

## DTO: one bean per table

A **DTO** (data transfer object) is a plain Java bean that carries one row's data: fields, a no-argument constructor, getters and setters, no logic and no SQL. There is one DTO class per entity, and each instance represents one row. The beans are the ones from [mapping objects to tables](18d_mapping_objects_to_tables.md):

```java
class Restaurant {
    private Long id;
    private String name;
    private String city;
    private Set<RestaurantTable> tables = new HashSet<>();
    // getters and setters
}
```

The name "DTO" comes from passing data between layers or processes. In course material and in JDBC code, "DTO" and "bean" mean the same thing here: the object the DAO reads and writes.

## DAO: an interface per DTO

A **DAO** (data access object) offers the persistence operations for one DTO: create, read, update, delete (CRUD), plus whatever queries the application needs.

```java
interface RestaurantDAO {
    void create(Restaurant r);              // sets r's id
    Restaurant read(long id);               // null if missing
    void update(Restaurant r);
    void delete(long id);
    List<Restaurant> findAll();
}

interface RestaurantTableDAO {
    void create(RestaurantTable t);
    Set<RestaurantTable> readByRestaurant(long restaurantId);    // read by foreign key
}
```

The structure of the pattern:

- **one DTO** per entity;
- **one DAO interface** per DTO;
- **one implementation** of each interface per database product (`PgRestaurantDAO`, `Db2RestaurantDAO`);
- **one DAO factory** per database product, which creates all the implementations for that product.

Beyond the four CRUD methods, a DAO has the queries its callers need, so DAOs for different tables do not have the same methods. The table DAO has `readByRestaurant` because a restaurant needs to load its tables; a booking DAO may have `findByCustomer` or `findOverlapping(table, from, to)`. A DAO with dozens of methods is a sign that callers ask for many slightly different queries; often a few methods with parameters, or a separate query class for reporting, is cleaner.

Some conventions on what CRUD methods receive and return:

- `create` receives a **complete** object, including its foreign keys (a table already carries its `restaurantId`), and sets the generated id on it before returning.
- `read` returns `null` (or an `Optional`) when the row does not exist. A missing row is a normal result, not an error.
- `update` and `delete` fail loudly when no row matched, if the caller expected one.

## The DAO factory

The factory is the [abstract factory](../17_object_oriented_design/17d_creational_patterns.md#abstract-factory) pattern: one family of DAOs per database product, picked once at startup.

```java
abstract class DAOFactory {
    abstract RestaurantDAO restaurants();
    abstract RestaurantTableDAO tables();

    static DAOFactory postgres(DataSource ds) { return new PgDAOFactory(ds); }
}

class PgDAOFactory extends DAOFactory {
    private final DataSource ds;
    PgDAOFactory(DataSource ds) { this.ds = ds; }

    RestaurantTableDAO tables()  { return new PgRestaurantTableDAO(ds); }
    RestaurantDAO restaurants() { return new PgRestaurantDAO(ds, tables()); }
}

// at startup
DAOFactory daos = DAOFactory.postgres(ds);
var service = new BookingService(daos.restaurants(), daos.tables());
```

The service depends on the interfaces only ([dependency inversion](../17_object_oriented_design/17b_oop_and_solid.md#dependency-inversion)): switching to DB2 is a new factory and new implementations, and tests can pass in-memory fakes.

In practice, a program rarely switches database product. The factory's real value today is the second one: the seam where tests replace the database.

## The skeleton of a DAO method

Every JDBC method has the same steps. Written out, a method:

1. declares a variable for its result;
2. checks the arguments it received;
3. opens a connection;
4. prepares the SQL statement, sets its parameters, executes it, and reads the result;
5. handles exceptions;
6. **always** releases the connection, whatever happened;
7. returns the result (or signals failure).

And the SQL part of step 4:

1. create the statement, or prepare it if it has parameters;
2. set the parameters;
3. execute it, and get the result set if one is expected;
4. loop over the result set, building the result from each row;
5. close the result set;
6. close the statement.

With try-with-resources, steps 6, 5 and 6 of the second list are written once, in the `try (...)` header: every resource declared there is closed in reverse order when the block ends, normally or by an exception. Before Java 7 this was a `finally` block with nested `try`/`catch` for each `close()`, and forgetting one leaked connections until the pool ran dry.

```java
public Restaurant read(long id) {
    Restaurant result = null;                                         // 1. the result
    String sql = "SELECT id, name, city FROM restaurants WHERE id = ?";
    try (Connection c = ds.getConnection();                           // 3. connection
         PreparedStatement ps = c.prepareStatement(sql)) {            //    prepared statement
        ps.setLong(1, id);                                            //    parameters
        try (ResultSet rs = ps.executeQuery()) {                      //    execute
            if (rs.next()) result = map(rs);                          //    read the row
        }                                                             //    result set closed
    } catch (SQLException e) {                                        // 5. exceptions
        throw new PersistenceException("cannot read restaurant " + id, e);
    }                                                                 // 6. statement and connection closed
    return result;                                                    // 7. the result
}

private Restaurant map(ResultSet rs) throws SQLException {
    Restaurant r = new Restaurant();
    r.setId(rs.getLong("id"));
    r.setName(rs.getString("name"));
    r.setCity(rs.getString("city"));
    return r;
}
```

```
read missing: null
```

Step 2 matters most in `create`, where an object that already has an id is a bug in the caller:

```java
public void create(Restaurant r) {
    if (r == null || r.getId() != null) throw new IllegalArgumentException("new restaurant expected");
    String sql = "INSERT INTO restaurants (name, city) VALUES (?, ?)";
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql, new String[] {"id"})) {
        ps.setString(1, r.getName());
        ps.setString(2, r.getCity());
        ps.executeUpdate();
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            r.setId(keys.getLong(1));                 // the bean now matches its row
        }
    } catch (SQLException e) {
        throw new PersistenceException("cannot create restaurant " + r.getName(), e);
    }
}
```

### Reading results

A `ResultSet` is a cursor over the rows, not a collection: building a `Set` or `List` means iterating it with `while (rs.next())` and mapping each row.

```java
public Set<RestaurantTable> readByRestaurant(long restaurantId) {
    Set<RestaurantTable> result = new HashSet<>();
    String sql = "SELECT id, restaurant_id, number, seats FROM restaurant_tables WHERE restaurant_id = ?";
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setLong(1, restaurantId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        }
    } catch (SQLException e) {
        throw new PersistenceException("cannot read tables of restaurant " + restaurantId, e);
    }
    return result;
}
```

A query expected to return one row uses the same `ResultSet`, with `if (rs.next())` instead of `while`. JDBC has no "unique result" method (ORMs do, like Hibernate's `uniqueResult()` or JPA's `getSingleResult()`); if more than one row would be a bug, check `rs.next()` again and fail.

### Always prepared statements

Parameters go through `?` and `setXxx`, never by concatenating strings into the SQL. `"... WHERE name = '" + name + "'"` with the name `x' OR '1'='1` returns every row: **SQL injection**. A prepared statement sends the SQL and the values separately, so a value can never change the query's structure.

### Errors keep their cause

The wrapped exception carries the database's message. Creating a table number that already exists in the restaurant:

```
duplicate: cannot create table / cause: ERROR: duplicate key value violates unique constraint "restaurant_tables_restaurant_id_number_key"
```

To report a constraint violation to a user ("table 1 already exists"), check the SQL state (`23505` is a unique violation in PostgreSQL) inside the DAO, and throw a specific exception for it. The service can catch `DuplicateException` without knowing about SQL states.

## The mapping DAO for N:N

The join table of an N:N relationship has no DTO ([mapping objects to tables](18d_mapping_objects_to_tables.md#nn)), but its rows still have to be read and written. A **mapping DAO** does it. It has no public DTO and is usually not exposed outside the persistence layer: it exists to serve the DAOs of the two entities.

```java
class BookingTableMapping {
    private final DataSource ds;
    BookingTableMapping(DataSource ds) { this.ds = ds; }

    void link(Connection c, long bookingId, long tableId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO booking_tables (booking_id, table_id) VALUES (?, ?)")) {
            ps.setLong(1, bookingId);
            ps.setLong(2, tableId);
            ps.executeUpdate();
        }
    }

    void unlinkAll(Connection c, long bookingId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM booking_tables WHERE booking_id = ?")) {
            ps.setLong(1, bookingId);
            ps.executeUpdate();
        }
    }

    // one-way navigation: only booking -> tables
    Set<Long> tableIdsOf(long bookingId) { ... }
}
```

The entity DAO calls the mapping DAO in **every** CRUD operation that touches the relationship, so the join table always matches the objects:

| Booking DAO method | Calls on the mapping |
|---|---|
| `create(booking)` | insert the booking, then `link` each of its tables |
| `read(id)` | read the booking, then `tableIdsOf(id)` to fill its set (now or lazily) |
| `update(booking)` | update the booking, then `unlinkAll` and `link` the current set (or link/unlink the difference) |
| `delete(id)` | `unlinkAll`, then delete the booking (unless `ON DELETE CASCADE` does it) |

The order follows the foreign keys: create the row before linking it, unlink before deleting it ([what happens on delete](18b_relationships.md#what-happens-on-delete)). The calls take the `Connection` as a parameter so the booking and its links are written in **one transaction**; with a connection per method, a crash between the two leaves a booking with no tables ([transactions in code](18g_transactions_in_code.md)).

For a one-way N:N the mapping has one navigation method. For a two-way one it has two, `tableIdsOf(bookingId)` and `bookingIdsOf(tableId)`.

## Repository

A **Repository** looks like a DAO, and in many codebases the two names are used for the same thing: one class per bean with CRUD methods. The distinction, when people make one:

| DAO | Repository |
|---|---|
| one per table | one per aggregate (an entity and the parts it owns: a booking and its table links) |
| speaks in rows and queries (`readByFK`) | speaks in domain terms (`bookingsFor(customer, day)`), like a collection of objects |
| part of the persistence layer | its interface belongs to the domain, its implementation to persistence |

In practice, a repository is often implemented with DAOs underneath, or with an ORM. Spring Data's `JpaRepository` generates repositories from interfaces, which is why "repository" is the common word in Java jobs.

A repository can also own the table's creation script and constraints (a `createTable()` method that runs the `CREATE TABLE` with its `UNIQUE` constraints), which is common in course projects. In production, schema changes are kept in versioned migration files instead ([schema patterns](18i_schema_patterns.md#migrations)).

## Active Record and Data Mapper

Two broader styles decide **where** the persistence code lives.

**Active Record**: the object saves itself. Each class has `save()`, `delete()` and static finders, and wraps one row.

```java
Restaurant r = Restaurant.find(7);
r.setCity("Modena");
r.save();
```

Short and convenient (Ruby on Rails, Django, Laravel), but the domain class depends on the database, and testing its logic needs one.

**Data Mapper**: the domain objects know nothing about persistence, and a separate layer moves data between them and the tables. DAOs and repositories are data mappers, and so is JPA ([JPA and Hibernate](18h_jpa_and_hibernate.md)).

Data Mapper is the usual choice in Java, and the one interviewers expect for an LLD round with persistence: it keeps the domain classes testable and the SQL in one place.

## Common mistakes

- **SQL in the service or in the beans.** The point of the layer is that only DAOs contain SQL.
- **String concatenation in SQL.** Injection, and the statement cannot be reused.
- **Connections not closed** on the exception path. Use try-with-resources for the connection, the statement and the result set.
- **Swallowing `SQLException`** (`catch (SQLException e) {}`), so a failed insert looks like a success.
- **Forgetting to set the generated id** on the bean after `create`, so the next insert that needs it as a foreign key uses `null`.
- **Updating an N:N in two separate connections**, so the entity and its links can disagree after a failure.
- **Printing from DAOs.** A DAO returns data or throws; printing is the caller's job, and a test in `main` (or a test class) is what exercises the DAO.
