# Embedding vs Referencing

In a relational schema every relationship is a foreign key. In a document schema each relationship is a choice: put the related data **inside** the document (embedding), or store it in its own collection and keep its id (referencing). This page gives the questions that decide it, works through each cardinality on the restaurant domain, and ends with a method for designing a whole schema.

Examples were run on MongoDB 8.0.

## The two options

**Referencing** stores the related data in another collection and links to it by `_id`, like a foreign key without the constraint:

```js
// restaurants
{ _id: "trattoria", name: "Trattoria", city: "Bologna" }

// reviews
{ _id: 111, restaurant_id: "trattoria", author: "Ada", stars: 5, text: "Best tagliatelle..." }
{ _id: 112, restaurant_id: "trattoria", author: "Bob", stars: 4, text: "Long wait but..." }
```

**Embedding** stores the related data as sub-documents of the main one:

```js
{ _id: "trattoria", name: "Trattoria", city: "Bologna",
  reviews: [
    { author: "Ada", stars: 5, text: "Best tagliatelle..." },
    { author: "Bob", stars: 4, text: "Long wait but..." } ] }
```

The embedded model is denormalized: the reviews have no existence outside the restaurant.

| | Embedding | Referencing |
|---|---|---|
| Reading parent and children | one read | a second query, or a `$lookup` |
| Updating parent and children together | one atomic write ([atomicity](19a_documents_and_queries.md#atomicity-is-per-document)) | several writes, atomic only in a transaction |
| Document size | grows with the children, up to 16 MB | stays small |
| Data used rarely | read with the parent every time | read only when needed |
| Shared data (one child, many parents) | copied into each parent | stored once |
| Querying children alone ("all 1-star reviews") | possible, but returns whole parents | a normal query on the child collection |

## The questions

The cardinality on the ER diagram is not enough. Four questions decide:

1. **What is the real cardinality?** Not just "many", but how many: 3, 300, or 3 million.
2. **How is the data accessed, and starting from which entity?** If tables are always read with their restaurant, they belong inside it. If reviews are mostly read on their own pages, they need their own collection.
3. **Is the embedded data queried on its own?** Searching across all reviews is easier when reviews are documents.
4. **How often does the embedded data change?** Embedded data that is also copied elsewhere must be updated everywhere it was copied.

MongoDB's guideline summarises it as: **data that is accessed together should be stored together**. Favour embedding, and reference when there is a reason.

## Cardinalities

The relational 1:N covers very different cases in a document store, so the cardinalities are finer:

| Cardinality | Restaurant example | Usual choice |
|---|---|---|
| 1:1 | customer and loyalty card | embed |
| 1:few | restaurant and its tables | embed an array |
| 1:many | restaurant and its reviews (hundreds) | reference from the child, or a pattern |
| 1:squillions | restaurant and its order events (millions) | reference from the child only |
| N:M | customers and favourite restaurants | arrays of ids, one way or both |
| few:few | waiters and the sections they cover | embed on one or both sides |

### 1:1, embed

A loyalty card belongs to one customer and is read with it:

```js
{ _id: "ada@example.com", name: "Ada",
  loyalty: { points: 120, tier: "gold" } }
```

The card could even be flattened into the customer (`loyalty_points: 120`); a sub-document just groups the fields. Reference instead only if the second part is large and rarely read (a profile photo), or accessed alone under different rules.

### 1:few, embed an array

A restaurant has a handful of tables, always shown with it, and never searched across restaurants:

```js
{ _id: "trattoria", name: "Trattoria",
  tables: [ { number: 1, seats: 4 }, { number: 2, seats: 4 }, { number: 3, seats: 6 } ] }
```

One read returns everything, and adding a table is one atomic `$push`. In the relational schema this needed a second table and a join ([1:N](../18_data_modeling_and_persistence/18b_relationships.md#1n-a-foreign-key-on-the-many-side)).

### 1:many, reference from the child

A restaurant collects hundreds or thousands of reviews. Embedding them all makes the restaurant document large, and every read of the restaurant's name loads every review. The child holds the parent's id, as in a relational schema:

```js
// reviews
{ _id: ObjectId("..."), restaurant_id: "trattoria", author: "Ada", stars: 5, at: ISODate("2026-11-02") }
```

with an index on `restaurant_id`. The alternative is **child referencing**: the parent keeps an array of the children's ids (`review_ids: [111, 112, ...]`). It allows reading the ids with the parent, but the array grows with every review and must be kept in step with the reviews.

The common middle ground is to embed the few reviews shown on the restaurant page and reference the rest: the [subset pattern](19c_relationship_patterns.md#subset-pattern).

### 1:squillions, reference from the child only

Every order event of a busy restaurant (an item ordered, served, paid) adds up to millions of documents. Any array in the parent would hit the 16 MB limit, so only the child points to the parent:

```js
// order_events
{ restaurant_id: "trattoria", at: ISODate("2026-12-05T20:04Z"), type: "item_served" }
```

```js
db.order_events.createIndex({ restaurant_id: 1, at: -1 })
db.order_events.find({ restaurant_id: "trattoria" }, { _id: 0, at: 1 }).sort({ at: -1 }).limit(2)
```

```
[ { at: ISODate('2026-12-05T20:04:00.000Z') }, { at: ISODate('2026-12-05T20:03:00.000Z') } ]
```

A document per event has its own overhead (each one has an `_id` and an index entry). Grouping events into one document per restaurant per hour is the [bucket pattern](19c_relationship_patterns.md#bucket-pattern).

### N:M, arrays of ids

Customers mark restaurants as favourites; a customer has several favourites, a restaurant has many fans. There is no join table: each side can keep an array of the other's ids.

**One-way**: only customers hold `favourites`. Resolving the ids is a `$lookup` whose `localField` is an array:

```js
db.customers.aggregate([
  { $match: { _id: "ada" } },
  { $lookup: { from: "restaurants", localField: "favourites", foreignField: "_id", as: "fav" } },
  { $project: { name: 1, fav: "$fav.name" } }
])
```

```
[ { _id: 'ada', name: 'Ada', fav: [ 'Sushi Bar', 'Trattoria' ] } ]
```

Ada's array is `["trattoria", "sushi"]`, but the result came back in another order: `$lookup` does not keep the order of the ids. Sort explicitly if the order matters.

**Two-way**: restaurants also hold `fans`. Both directions are fast to read, but every change must update two documents, and nothing keeps them consistent. Bob removes Trattoria from his favourites, and only his side is updated:

```js
db.customers.updateOne({ _id: "bob" }, { $pull: { favourites: "trattoria" } })
```

```
{ bob_favourites: [], trattoria_fans: [ 'ada', 'bob' ] }
```

Trattoria still lists Bob as a fan. Updating both sides needs a transaction, or code that tolerates the two sides disagreeing for a while.

Choose two-way only when both directions are frequent queries, and when one side's array stays small: a restaurant with 50,000 fans cannot hold them all in an array. Then keep the array on the "few" side only (customers) and find fans with an index on `favourites`, which MongoDB builds as a **multikey index** over array elements.

When the link has attributes (when Ada marked it, a note), the array holds sub-documents (`favourites: [ { id: "trattoria", since: ISODate(...) } ]`). With two-way references, the attribute is usually stored on one side only.

### Few:few

Waiters cover a few sections, and a section has a few waiters. Both sides are small, so embedding short arrays of names or ids on one side, or both, is fine.

## Copies of embedded data

Embedding data from another entity is denormalization: a copy. If the restaurant's name is embedded in each customer's profile ("your usual restaurant"), renaming the restaurant means updating every copy:

```js
db.customers.updateMany({ "restaurant.id": "trattoria" }, { $set: { "restaurant.name": "Trattoria da Ada" } })
```

```
2
```

Two documents here; potentially millions in production, and not atomic together without a transaction. That is acceptable when the copied fields rarely change (a name, a city) or should not change (the price on a past order, which is a different fact from today's price, as in [18b](../18_data_modeling_and_persistence/18b_relationships.md#a-join-table-with-its-own-attributes)). It is a problem for data that changes often: copy those by reference instead.

## A method

There is no normalization algorithm for documents. A workable method is iterative, in three steps.

**1. Conceptual model.** An ER diagram, with the real cardinalities of each relationship (1:few or 1:squillions, not just 1:N).

**2. Workload.** Estimate the data and the operations, now and in the future:

- a **volume table**: number of documents per entity, and their average size;
- an **operations table**: each query and write, how often it runs, and which entity it starts from.

For the restaurant system:

| Entity | Documents | Avg size (bytes) |
|---|---|---|
| Restaurant | 2,000 | 400 |
| Table | 30,000 (15 per restaurant) | 40 |
| Customer | 500,000 | 200 |
| Booking | 10,000,000 (20 per customer) | 150 |
| Review | 600,000 (300 per restaurant) | 500 |

| Operation | Per day | Starts from |
|---|---|---|
| Show a restaurant page with tables and latest reviews | 200,000 | restaurant |
| Create a booking | 30,000 | customer, restaurant |
| List a customer's bookings | 50,000 | customer |
| Tonight's bookings at a restaurant | 10,000 | restaurant |
| Write a review | 2,000 | customer, restaurant |

**3. Schema per collection.** For each relationship, choose embedding, referencing, or a pattern ([relationship patterns](19c_relationship_patterns.md), [structure patterns](19d_structure_patterns.md)), driven by the most frequent operations. The output is the list of collections, their estimated size ([sizing and cost](19e_sizing_and_cost.md)), and the queries and indexes.

Applied here:

- Tables are 1:few and always shown with the restaurant: **embed** them in `restaurants`.
- Reviews are 1:many, and the restaurant page shows only the latest few: **reference** them from `reviews`, and embed the last 10 in the restaurant (subset pattern).
- Bookings are created and listed per customer and per restaurant, are many, and change state: their own `bookings` collection, **referencing** both, with the restaurant's name and the customer's name copied in for display (extended reference pattern).
- The loyalty card is 1:1 and read with the customer: **embed** it.

The result is checked against the workload: the most frequent operation (the restaurant page) is one read of one document. If the workload changes (the app adds a "all my reviews" page), the design is revisited: that is why the method is iterative.

## Common mistakes

- **Translating tables to collections one to one**, then joining everything with `$lookup`. That is a relational schema without the constraints.
- **Unbounded arrays**: embedding something that grows forever (bookings in a restaurant) until it hits 16 MB, or long before that makes every read slow.
- **Embedding data that changes often and is copied in many places.**
- **Two-way references without a plan** for keeping both sides in step.
- **`$elemMatch` forgotten** on queries with several conditions on embedded arrays ([several conditions on an array](19a_documents_and_queries.md#several-conditions-on-an-array-of-documents)).
- **Designing without the workload.** The same entities give different schemas for different access patterns, and that is intended.
