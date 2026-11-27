# Indexes, Validation and Transactions

A document schema is more than the shape of its documents. Three features decide whether it performs and stays correct: indexes for the frequent queries, validation rules for the data the database must refuse, and transactions for the writes that span several documents. This page covers each, with the measurements MongoDB reports.

All examples were run on MongoDB 8.0, as a single-node replica set (transactions need one). The index examples use 100,000 bookings over 2,000 restaurants.

## Indexes

Without an index, a query reads every document in the collection (a **collection scan**). `explain("executionStats")` reports how much work a query did. Tonight's bookings at one restaurant:

```js
db.bookings.find({
  restaurant_id: "r7",
  starts_at: { $gte: ISODate("2026-12-08T00:00Z"), $lt: ISODate("2026-12-09T00:00Z") }
}).explain("executionStats")
```

```
no index         { stage: 'COLLSCAN', returned: 8, keysExamined: 0, docsExamined: 100000 }
```

100,000 documents examined to return 8. With a compound index on the two fields:

```js
db.bookings.createIndex({ restaurant_id: 1, starts_at: 1 })
```

```
compound index   { stage: 'IXSCAN', returned: 8, keysExamined: 8, docsExamined: 8 }
```

The ratio of documents examined to documents returned is the number to watch: 1 is ideal; thousands means a missing or wrong index.

MongoDB's indexes are B-trees, so the rules from [SQL indexes](../16_sql_databases/16i_query_planning_and_indexes.md) carry over.

**Leftmost prefix.** A compound index serves queries on its first field, or its first two, and so on, but not on a later field alone. The same index for a query on `starts_at` only:

```
prefix rule      { stage: 'COLLSCAN', returned: 3572, keysExamined: 0, docsExamined: 100000 }
```

**Sorting.** An index that matches the sort order avoids an in-memory sort. `find({ restaurant_id: "r7" }).sort({ starts_at: -1 })` on the index above has no `SORT` stage in its plan: the index is read backwards.

**Equality, sort, range.** For a compound index serving a filter and a sort, put the fields compared with equality first, then the sort field, then the range fields. An index on `{ restaurant_id: 1, starts_at: 1 }` serves "bookings of restaurant X from date D, newest first". Adding `party_size` as a range condition belongs after them.

### Multikey indexes

An index on an array field gets one entry per array element, called a **multikey** index. It is what makes `{ cuisines: "emilian" }` fast:

```js
db.restaurants.createIndex({ cuisines: 1 })
db.restaurants.find({ cuisines: "emilian" }).explain().queryPlanner.winningPlan.inputStage.isMultiKey
```

```
true
```

It is created the same way as any other index; MongoDB marks it multikey when it sees an array. Multikey indexes serve the N:M arrays of ids ([N:M](19b_embedding_vs_referencing.md#nm-arrays-of-ids)), the ancestors array of the tree pattern, and the key-value pairs of the attribute pattern. A compound index can contain at most one array field: two arrays would need an entry for every combination of their elements.

### Unique and partial indexes

A unique index enforces a natural key, as a `UNIQUE` constraint does ([natural and surrogate keys](../18_data_modeling_and_persistence/18a_entities_and_keys.md#natural-and-surrogate-keys)). A partial index covers only the documents that match a filter. Together they give uniqueness among live documents, the soft-delete case from [18i](../18_data_modeling_and_persistence/18i_schema_patterns.md#uniqueness-among-live-rows):

```js
db.customers.createIndex({ email: 1 }, { unique: true, partialFilterExpression: { deleted: false } })
```

Two deleted documents with the same email are allowed, because they are outside the index; a second live one is not:

```
E11000 duplicate key error collection: f.customers index: email_1
```

The partial filter accepts only some operators. `{ deleted_at: { $exists: false } }`, the natural translation of the SQL `WHERE deleted_at IS NULL`, is refused:

```
Expression not supported in partial index: $not
    deleted_at exists
```

so the documents carry an explicit `deleted: false` instead.

### What indexes cost

Every index is updated on every insert and on every update of its fields, and takes memory: the working set of indexes should fit in RAM. A collection with an index for every possible query writes slowly. Index the frequent queries from the workload table ([a method](19b_embedding_vs_referencing.md#a-method)), and check them with `explain`.

## Schema validation

A collection accepts any document by default. A **validator** makes the database refuse documents that break rules, with `$jsonSchema`:

```js
db.createCollection("reviews", {
  validator: { $jsonSchema: {
    bsonType: "object",
    required: ["restaurant_id", "stars", "at"],
    properties: {
      restaurant_id: { bsonType: "string" },
      stars: { bsonType: "int", minimum: 1, maximum: 5 },
      at:    { bsonType: "date" },
      text:  { bsonType: "string", maxLength: 2000 }
    }
  } },
  validationAction: "error"
})
```

A review with 6 stars is refused, and the error says which rule failed:

```
Document failed validation
{ propertyName: 'stars',
  details: [ { operatorName: 'maximum', specifiedAs: { maximum: 5 }, reason: 'comparison failed', consideredValue: 6 } ] }
```

and so is 4.5 stars, which is a double, not an int:

```
{ operatorName: 'bsonType', specifiedAs: { bsonType: 'int' }, reason: 'type did not match',
  consideredValue: 4.5, consideredType: 'double' }
```

Notes on using it:

- Validation covers types, required fields, ranges, lengths, patterns and allowed values (`enum`), including inside sub-documents and arrays. It is how the attribute pattern can still check that values are numbers ([attribute pattern](19d_structure_patterns.md#attribute-pattern)).
- It cannot check relationships between documents: there is no foreign key, and a review's `restaurant_id` can still name a restaurant that does not exist.
- `validationAction: "warn"` logs violations instead of refusing them, and `validationLevel: "moderate"` checks only documents that already pass. Both help when adding rules to a collection that already has bad data: fix the data, then switch to `error`.
- Fields not listed are allowed unless `additionalProperties: false` is set, which keeps the flexibility to add fields.

A validator is the document version of `NOT NULL`, `CHECK` and column types: rules that hold whatever code writes the data.

## Transactions

A write to one document is atomic ([atomicity](19a_documents_and_queries.md#atomicity-is-per-document)). A change that must update **several** documents together needs a multi-document transaction.

The two-way favourites from [N:M](19b_embedding_vs_referencing.md#nm-arrays-of-ids) went out of sync when only one side was updated. In a transaction, both sides change together:

```js
const s = db.getMongo().startSession();
const sdb = s.getDatabase("f");
s.startTransaction();
sdb.customers.updateOne({ _id: "bob" }, { $pull: { favourites: "trattoria" } });
sdb.restaurants.updateOne({ _id: "trattoria" }, { $pull: { fans: "bob" } });
s.commitTransaction();
```

Between the two updates, a reader outside the transaction still sees the old state (`favourites: [ 'trattoria' ]`); after the commit, both changes are visible at once:

```
after commit { bob: [], fans: [ 'ada' ] }
```

`abortTransaction()` undoes everything written in the transaction:

```
after abort []
```

### Write conflicts

Two transactions that write the same document conflict. The second one does not wait: it fails at once.

```js
s1.startTransaction(); s2.startTransaction();
s1...updateOne({ _id: "trattoria" }, { $push: { fans: "cat" } });
s2...updateOne({ _id: "trattoria" }, { $push: { fans: "dan" } });
```

```
conflict { code: 'WriteConflict', labels: [ 'TransientTransactionError' ] }
```

The `TransientTransactionError` label means "retry the whole transaction". Drivers have a helper (`session.withTransaction(...)`) that retries on it. After `s1` commits, Trattoria's fans include Cat and not Dan, until Dan's transaction is retried.

### When a design needs them

Transactions exist, but they cost more than single-document writes: they hold state on the server, are limited in duration (60 seconds by default), and conflict under contention. A document schema that needs a transaction for every frequent operation is usually a relational schema in disguise.

The usual approach:

- **Design so that the frequent writes touch one document**: embed what changes together.
- **Use transactions for the writes that really span documents**: two-way references, a booking and the slot counter it decrements, a transfer between two accounts.
- **Accept eventual consistency** where a short disagreement is harmless: a subset of reviews or a copied restaurant name ([relationship patterns](19c_relationship_patterns.md)) can be repaired by a retry or a background job.

Transactions also need a replica set (or a sharded cluster). A standalone server refuses them, which is why development setups often run a one-node replica set.

## Common mistakes

- **No index for the most frequent query**, discovered when `docsExamined` is the whole collection.
- **Compound index fields in the wrong order**, so the query uses only its first field, or none.
- **Indexing every field "just in case"**, slowing every write.
- **No validator**, so the first bad document is found by the code that reads it.
- **`stars: 4.5` stored as a double** when the code expects an int, or numbers stored as strings: types are not checked unless a validator checks them.
- **Ignoring `TransientTransactionError`** instead of retrying the transaction.
- **Transactions as the default** for every write, instead of designing documents so that most writes need none.
