# Loading Related Data

When a DAO reads a restaurant, it has to decide what to do with the restaurant's tables: load them now (**eager**), or load them only when the program asks for them (**lazy**). Each choice can waste work: eager loads data nobody uses, lazy can turn one query into hundreds. This page shows both with JDBC, implements lazy loading with a Proxy, measures the N+1 problem, and fixes it with a join.

The numbers on this page come from running the code against PostgreSQL 17 with 10 restaurants of 3 tables each, counting the SQL statements each version sends.

## The question

`Restaurant` has a `Set<RestaurantTable> tables` that is not a column ([mapping objects to tables](18d_mapping_objects_to_tables.md#1n-and-n1)). Filling it needs a second query:

```sql
SELECT id, restaurant_id, number, seats FROM restaurant_tables WHERE restaurant_id = ?
```

The only thing that changes between eager and lazy is **when** this query runs, and so only the DAO's read methods change. The beans and the other CRUD methods stay the same.

## Eager loading

The read method fills the collection before returning, by calling the other DAO's read-by-foreign-key method:

```java
private Restaurant map(ResultSet rs) throws SQLException {
    Restaurant r = new Restaurant();
    r.setId(rs.getLong("id"));
    r.setName(rs.getString("name"));
    r.setCity(rs.getString("city"));
    r.setTables(tableDao.readByRestaurant(r.getId()));     // the second query, now
    return r;
}
```

`readByRestaurant` is the method the eager version needs on the other DAO (a `readByFK`). The caller gets a complete object, and can use it after the DAO is gone. The cost: every read pays for the tables, even when the caller only wanted the name.

```
eager findAll: queries=11 tables of first=3
```

One query for the restaurants, and one more per restaurant.

## Lazy loading with a Proxy

The read method returns an object whose collection is filled on first access. The usual way to do that without changing the bean is a **Proxy**: a subclass of the bean, created only by the DAO, that overrides the getter ([proxy pattern](../17_object_oriented_design/17e_structural_patterns.md#proxy)).

```java
class RestaurantProxy extends Restaurant {
    private final RestaurantTableDAO tableDao;
    private boolean loaded;

    RestaurantProxy(RestaurantTableDAO tableDao) { this.tableDao = tableDao; }

    @Override
    Set<RestaurantTable> getTables() {
        if (!loaded) {
            tables = tableDao.readByRestaurant(getId());     // the second query, on first use
            loaded = true;
        }
        return tables;
    }
}
```

The DAO builds a proxy instead of a plain bean in its read methods, and leaves the set empty:

```java
private Restaurant map(ResultSet rs) throws SQLException {
    Restaurant r = new RestaurantProxy(tableDao);
    r.setId(rs.getLong("id"));
    r.setName(rs.getString("name"));
    r.setCity(rs.getString("city"));
    return r;                                             // tables not loaded yet
}
```

The caller declares `Restaurant` and cannot tell it got a proxy:

```
lazy findAll: queries=1 class=RestaurantProxy
after getTables twice on one: queries=2
```

The list cost one query. The first `getTables()` on one restaurant cost one more, and the second call cost nothing, because the proxy remembers it loaded.

What the proxy needs from the bean:

- The collection field must be accessible to the subclass (`protected`), or set through a setter.
- The getter must be overridable: not `final`, not `private`. A `final` bean class cannot be proxied at all, which is why JPA requires entities to be non-final.
- Every other method of the bean must go through the getter, not the field. A bean method that reads `tables.size()` directly sees the empty set.

### When lazy fails

A proxy holds a reference to the DAO, which needs a working `DataSource`. If the object is used after the persistence layer is closed, or sent to another process (serialized to JSON), the first access fails or the set is silently empty. ORMs report this as a `LazyInitializationException`. The rule: decide what the caller needs **while** the persistence context is open, and load it there.

## The N+1 problem

Lazy loading hides the second query inside a getter. Code that looks like one query can send many:

```java
int total = 0;
for (Restaurant r : restaurantDao.findAll())     // 1 query
    total += r.getTables().size();               // 1 query per restaurant
```

```
lazy, touching every restaurant: queries=11 tables=30
```

One query for the list plus N for the children: **N+1** queries. With 10 restaurants that is 11 round trips; with 2,000 it is 2,001, each paying the network latency. It is the most common performance bug in ORM-based code, because the loop looks innocent.

Eager loading does not fix it: as measured above, eager `findAll` also sends 11 queries. It just sends them up front.

### Fix 1: a join

Load parents and children in one query and group the rows in Java:

```java
public List<Restaurant> findAllWithTables() {
    String sql = """
        SELECT r.id, r.name, r.city, t.id AS t_id, t.number, t.seats
        FROM restaurants r
        LEFT JOIN restaurant_tables t ON t.restaurant_id = r.id
        ORDER BY r.id""";
    Map<Long, Restaurant> byId = new LinkedHashMap<>();
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            long id = rs.getLong("id");
            Restaurant r = byId.get(id);
            if (r == null) {
                r = new Restaurant();
                r.setId(id);
                r.setName(rs.getString("name"));
                r.setCity(rs.getString("city"));
                byId.put(id, r);
            }
            long tableId = rs.getLong("t_id");
            if (!rs.wasNull()) {                  // a restaurant with no tables gives one row of NULLs
                RestaurantTable t = new RestaurantTable(id, rs.getInt("number"), rs.getInt("seats"));
                t.setId(tableId);
                r.getTables().add(t);
            }
        }
    } catch (SQLException e) {
        throw new PersistenceException("cannot list restaurants", e);
    }
    return new ArrayList<>(byId.values());
}
```

```
join fetch: queries=1 restaurants=10 tables=30
```

Points in the code:

- `LEFT JOIN`, so restaurants with no tables are kept ([16b](../16_sql_databases/16b_joins.md#outer-joins-keeping-rows-that-match-nothing)).
- The map groups rows by parent id: the join returns one row per table, with the restaurant repeated.
- `wasNull()` tells a real table apart from the `NULL`s of an unmatched left join.

The cost: the parent columns are repeated in every row, and joining **two** collections (tables and bookings) multiplies the rows (3 tables × 50 bookings = 150 rows per restaurant). Join one collection at a time.

### Fix 2: batch loading

Two queries in total: the parents, then all their children with `IN` (or `= ANY` with an array), grouped in Java.

```sql
SELECT id, name, city FROM restaurants ORDER BY id;
SELECT id, restaurant_id, number, seats FROM restaurant_tables WHERE restaurant_id = ANY(?);
```

No repeated columns and no row multiplication, so it suits several collections at once. ORMs do this with a batch size setting.

### Which to use

| Situation | Loading |
|---|---|
| The children are almost always used with the parent, and are few | eager, or a join in the read method |
| The children are rarely used | lazy |
| A list screen shows parents with their children | a join or a batch query in a method made for that screen |
| A single parent with its children | either: 2 queries is fine |

The lesson is that loading is a property of the **use case**, not of the relationship. A DAO with `findAll()` (lazy) and `findAllWithTables()` (joined) lets each caller ask for what it needs.

## Identity map

Without care, loading the same row twice gives two objects. Two bookings at the same restaurant, each loaded with its restaurant, yield two `Restaurant` objects for row 1: changing the city on one does not change the other, and saving both writes whichever comes last.

An **identity map** is a `Map<id, object>` kept for the duration of a unit of work (one request, one transaction). Before building an object from a row, the DAO looks up the id; if it is there, it returns the existing object.

```java
class IdentityMap<T> {
    private final Map<Long, T> loaded = new HashMap<>();

    T get(long id, Supplier<T> load) {
        return loaded.computeIfAbsent(id, k -> load.get());
    }
}
```

It also works as a per-transaction cache: the second `read(1)` costs no query. It must not outlive the unit of work, or it serves stale data that other transactions have changed. JPA's persistence context is an identity map ([JPA and Hibernate](18h_jpa_and_hibernate.md#the-persistence-context)).

## Common mistakes

- **Making everything eager** "to be safe": every read loads the whole object graph.
- **Making everything lazy** and then looping over the results: N+1.
- **Accessing a lazy collection after the connection or session is closed.**
- **A `final` bean class or `final` getters**, which a proxy cannot override; the lazy load never happens.
- **Bean methods that use the field instead of the getter**, bypassing the proxy.
- **Joining several collections in one query**, multiplying the rows.
