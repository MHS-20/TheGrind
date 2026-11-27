# Mapping Objects to Tables

A Java program holds objects that point to each other; a relational database holds rows that refer to each other through key values. Moving data between the two means deciding, for each class and each relationship, what the table looks like and what the object looks like. This page goes from a UML class diagram to Java beans and tables: which side gets a foreign key, which side gets a collection, what the direction of navigation changes, and how class hierarchies are stored.

## Two models of the same data

The differences between objects and tables are often called the **object-relational impedance mismatch**:

| Objects | Tables |
|---|---|
| a reference to another object | a foreign key value |
| a collection (`Set<Table>`) on the "one" side | nothing on the "one" side: the foreign key is on the "many" side |
| navigation has a direction (`booking.getTables()`) | a join works in both directions |
| N:N is two collections, or one | N:N needs a third table |
| identity is the reference (`==`) | identity is the primary key |
| inheritance | no inheritance |

Everything on this page is a way of bridging one of those rows.

## From the class diagram

The starting point is a UML class diagram of the domain ([17c](../17_object_oriented_design/17c_class_diagrams.md)). The basic rules:

- Each class in the diagram normally becomes one Java class (a **bean**: private fields, a no-argument constructor, getters and setters) and one table.
- Each attribute of the class becomes a field and a column.
- Each surrogate key becomes an `id` field, even though the UML diagram does not show it ([entities and keys](18a_entities_and_keys.md)).
- Relationships do not become new classes, except N:N ones in some cases (below). They become fields: a foreign key or a collection.
- A line with **no arrows** in the diagram is navigable in both directions; an arrow means only that direction is navigable.

## 1:N and N:1

A restaurant has many tables. In the **table**, the foreign key goes on the many side ([relationships](18b_relationships.md#1n-a-foreign-key-on-the-many-side)). In the **objects**, each side gets what it needs to navigate:

- the "many" side (`RestaurantTable`) stores the foreign key as a plain id, the same as the column;
- the "one" side (`Restaurant`) holds a collection of the other class. The cardinality N becomes a collection.

```java
class Restaurant {
    private Long id;
    private String name;
    private String city;
    private Set<RestaurantTable> tables = new HashSet<>();   // the N side, as a collection
    // getters and setters
}

class RestaurantTable {
    private Long id;
    private long restaurantId;     // the 1 side, as a foreign key: same as the column
    private int number;
    private int seats;
    // getters and setters
}
```

```
restaurants(id, name, city)
restaurant_tables(id, restaurant_id → restaurants, number, seats)
```

No extra class is needed: a 1:N relationship adds a field on each side and nothing else.

The collection is **not** a column. Nothing in `restaurants` lists its tables. To fill `Restaurant.tables`, the restaurant's data access code has to ask the table's data access code for "all tables whose `restaurant_id` is this id", a read by foreign key. When that happens (immediately, or on first use) is the subject of [loading related data](18f_loading_related_data.md).

Why an id instead of a `Restaurant` reference on the many side? Both are used. An id is simple and matches the row exactly, which is the usual choice with plain JDBC and DAOs. A reference (`private Restaurant restaurant`) is what an ORM uses ([JPA and Hibernate](18h_jpa_and_hibernate.md)), because it can load the object for you. With hand-written code, a reference means loading the restaurant every time a table is loaded.

### 1:1

The same rules with a single object instead of a collection: the side whose table holds the foreign key ([relationships](18b_relationships.md#11-two-ways)) holds the id, and the other side holds a field of the other class, which may be `null`.

```java
class Customer {
    private Long id;
    private String email;
    private LoyaltyCard card;        // may be null: not every customer has one
}

class LoyaltyCard {
    private long customerId;         // shared primary key: also the foreign key
    private int points;
}
```

## N:N

A booking uses several tables and a table is used by many bookings. In the database this is three tables, with the join table holding the pairs ([relationships](18b_relationships.md#nn-a-join-table)). In the objects:

- **No foreign keys** in either bean. Neither table has a foreign key to the other, so neither class has one.
- **A collection on each side that is navigable**, and only there.
- The join table is **not a bean**. It holds no data of its own, only pairs of keys, so no class represents its rows. Its rows are read and written by a dedicated piece of data access code, the **mapping DAO** ([data access patterns](18e_data_access_patterns.md#the-mapping-dao-for-nn)).

### Navigation direction

Whether an N:N is navigable one way or both is a design choice, made from the use cases. "Which tables does this booking use?" is needed to seat guests. "Which bookings use table 3?" is needed only to check availability, and that is better answered by a query than by loading every booking a table ever had.

**One-way** (booking → tables):

```java
class Booking {
    private Long id;
    private long customerId;
    private LocalDateTime startsAt;
    private int partySize;
    private Set<RestaurantTable> tables = new HashSet<>();   // navigable direction
}

class RestaurantTable {
    // no Set<Booking>: this side does not navigate the relationship
}
```

The class that does not navigate has neither a foreign key nor a collection: the join table holds everything. The mapping DAO then needs only one navigation method, in the navigable direction (`tablesOf(bookingId)`), besides the methods that add and remove pairs.

**Two-way**: both classes have a collection, and the mapping DAO has a method per direction (`tablesOf(bookingId)` and `bookingsOf(tableId)`). Two collections must be kept consistent: adding a table to a booking in memory must also add the booking to the table, or the two objects disagree until the next reload. A helper method on one side does both:

```java
void addTable(RestaurantTable t) {
    tables.add(t);
    t.getBookings().add(this);
}
```

Prefer one-way unless both directions are really used. Every two-way relationship is twice the code to keep in step.

### When the join table becomes a class

If the link has attributes of its own, it is an entity, not just a pair: an order line has a quantity and the price charged ([relationships](18b_relationships.md#a-join-table-with-its-own-attributes)). It becomes a bean like any other, and the N:N becomes two 1:N relationships through it:

```java
class Order {
    private Long id;
    private long bookingId;
    private Set<OrderLine> lines = new HashSet<>();   // 1:N to the association class
}

class OrderLine {
    private long orderId;
    private long itemId;          // or a MenuItem reference
    private int quantity;
    private int priceCents;
}
```

The general option: a class can be made for **any** join table, with or without attributes, and the N:N treated as two 1:N. That costs a class and buys uniformity. The mapping-DAO approach keeps the join table hidden, which is simpler when the link really is just a pair.

## Identity and equality

Two `Restaurant` objects loaded separately for row 7 are two objects. With the default `equals` (reference identity), a `Set<RestaurantTable>` can hold "table 7" twice. Base `equals` and `hashCode` on the key:

```java
@Override public boolean equals(Object o) {
    return o instanceof RestaurantTable t && id != null && id.equals(t.id);
}
@Override public int hashCode() { return Objects.hashCode(id); }
```

The catch is new objects: before the insert, `id` is `null`, so all new tables are unequal to everything, and the hash code changes after the insert. A new object put into a `HashSet` before saving cannot be found in it afterwards. Either save before adding to sets, or base equality on the natural key (`restaurant_id`, `number`), which does not change on insert.

## Inheritance

The domain has staff of several kinds: waiters (with a section), chefs (with a station), managers. Relational tables have no inheritance, so a hierarchy is stored with one of three layouts.

```java
abstract class Staff  { Long id; String name; LocalDate hiredOn; }
class Waiter  extends Staff { String section; }
class Chef    extends Staff { String station; }
class Manager extends Staff { }
```

**Single table**: one table for the whole hierarchy, a **discriminator** column saying which class a row is, and the subclass columns nullable.

```sql
CREATE TABLE staff (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind     text NOT NULL CHECK (kind IN ('WAITER', 'CHEF', 'MANAGER')),
    name     text NOT NULL,
    hired_on date NOT NULL,
    section  text,               -- waiters only
    station  text,               -- chefs only
    CHECK (kind <> 'WAITER' OR section IS NOT NULL),
    CHECK (kind <> 'CHEF'   OR station IS NOT NULL)
);
```

**Joined** (table per class): a table for the base class and one per subclass, sharing the primary key.

```sql
CREATE TABLE staff   (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name text NOT NULL, hired_on date NOT NULL);
CREATE TABLE waiters (id bigint PRIMARY KEY REFERENCES staff(id) ON DELETE CASCADE, section text NOT NULL);
CREATE TABLE chefs   (id bigint PRIMARY KEY REFERENCES staff(id) ON DELETE CASCADE, station text NOT NULL);
```

**Table per concrete class**: one complete table per non-abstract class, each repeating the base columns, and no table for `Staff`.

| | Single table | Joined | Per concrete class |
|---|---|---|---|
| Read one object | 1 table | join base + subclass | 1 table |
| "All staff" query | 1 table | base table (plus joins for details) | `UNION` of every table |
| `NOT NULL` on subclass columns | only through `CHECK` | yes | yes |
| Foreign key to "any staff" | yes | yes, to the base table | no single table to point to |
| Adding a subclass | add nullable columns | add a table | add a table |
| Wasted space | many `NULL`s | none | none |

Single table is the usual default: simple and fast, and the `CHECK` constraints recover the lost `NOT NULL`s. Joined fits when subclasses have many fields of their own, or other tables must point to a specific subclass (a section assigned to a waiter). Per concrete class fits when the classes are never queried together.

Often the best mapping is none: if the subclasses differ only in data, not in behaviour, a `role` column on one `staff` table and no Java hierarchy at all is simpler. Use inheritance in the model only where behaviour really differs ([composition over inheritance](../17_object_oriented_design/17b_oop_and_solid.md#composition-over-inheritance)).

## Summary

| Relationship | Tables | Java |
|---|---|---|
| 1:N | foreign key on the N side | id on the N side, collection on the 1 side |
| 1:1 | foreign key (unique, or the primary key) on the optional side | id on that side, a single field on the other |
| N:N | join table, no foreign keys in the two entity tables | collections only on the navigable sides, no foreign keys; join table read through a mapping DAO |
| N:N with attributes | join table with extra columns | an association class, and two 1:N |
| inheritance | single table, joined, or per concrete class | the class hierarchy |
