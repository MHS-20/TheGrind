# Relationship Patterns

Pure embedding and pure referencing ([embedding vs referencing](19b_embedding_vs_referencing.md)) are the two ends of a range. Most real relationships sit in between: some fields of the other entity are needed often, the rest rarely; most parents have a few children, a few have thousands. The patterns on this page are standard mixes of the two. Each one trades some redundancy, or some extra code, for faster frequent queries.

A **design pattern** here means what it means for classes ([17d](../17_object_oriented_design/17d_creational_patterns.md)): a generic, reusable solution to a recurring design problem, not a rule. Each pattern is introduced by the problem it solves.

All examples were run on MongoDB 8.0.

| Pattern | Problem | Mix |
|---|---|---|
| Extended reference | a join on every read, for a few fields | reference + a copy of the frequently used fields |
| Subset | a large array, of which only a part is shown | reference all + embed the most used few |
| Outlier | embedding works, except for a few huge parents | embed + overflow documents for the outliers |
| Computed | the same aggregate recomputed on every read | store the result, update it on write |
| Bucket | millions of tiny documents | group them into documents of N |

## Extended reference pattern

**Problem.** The list of tonight's bookings at a restaurant shows each customer's name and phone. Bookings reference customers by id, so every list needs a `$lookup` into `customers`: a second read per booking, for two fields.

**Solution.** Keep the reference, and also copy into the booking the few customer fields that the frequent query needs. The rest of the customer (birth date, address, preferences) stays only in `customers`.

```js
// customers: the full record
{ _id: "ada", name: "Ada", phone: "333 111", birth: ISODate("1990-01-01"), address: { street: "Via Roma 1" } }

// bookings: reference + copied fields
{ _id: 1,
  customer:   { id: "ada", name: "Ada", phone: "333 111" },
  restaurant: { id: "trattoria", name: "Trattoria" },
  starts_at: ISODate("2026-12-05T20:00Z"), party_size: 4 }
```

```js
db.bookings.find({ "restaurant.id": "trattoria" }, { _id: 0, "customer.name": 1, "customer.phone": 1, party_size: 1 })
```

```
[ { customer: { name: 'Ada', phone: '333 111' }, party_size: 4 } ]
```

No `$lookup`.

- **Gains**: faster reads, fewer joins.
- **Costs**: the copied fields are duplicated, and when Ada changes her phone, every booking that copied it must be updated ([copies of embedded data](19b_embedding_vs_referencing.md#copies-of-embedded-data)), or show a stale number.
- **Guidelines**: copy only fields that are read often and change rarely. Decide for each copied field whether it must follow changes (a phone: yes, update the copies) or must not (the name on a past invoice: no, it is history).

This is the same trade as denormalizing a relational schema ([18c](../18_data_modeling_and_persistence/18c_normalization.md#denormalizing-on-purpose)), done on purpose for one query.

## Subset pattern

**Problem.** A restaurant page shows the latest reviews. Embedding all reviews makes the document grow without bound and loads hundreds of reviews to show three. Referencing all of them needs a second query on every page view.

**Solution.** Keep every review in the `reviews` collection, and **also** embed the most recent few in the restaurant. The embedded subset is a copy kept for the frequent query; the full list is read only on the "all reviews" page.

```js
function addReview(rid, author, stars, at) {
  db.reviews.insertOne({ restaurant_id: rid, author, stars, at });
  db.restaurants.updateOne({ _id: rid }, {
    $push: { recent_reviews: { $each: [{ author, stars, at }], $sort: { at: -1 }, $slice: 3 } }
  });
}
```

`$push` with `$sort` and `$slice` adds the review, keeps the array ordered newest first, and cuts it to 3 elements, in one atomic update. After five reviews (Ada, Bob, Cat, Dan, Eve, in that order):

```
recent_reviews: [ 'Eve 2', 'Dan 5', 'Cat 3' ]
```

```
all_reviews: 5
```

- **Gains**: the frequent read is one small document; its size is bounded.
- **Costs**: the subset is a duplicate to maintain. Every write of a review updates two documents (not atomic together without a transaction), and deleting or editing a review must also fix the subset if the review is in it.
- **Guidelines**: use it when a large collection of children is mostly accessed through a small, well-defined part (the newest, the most popular). Choose the subset size from what the page shows.

## Outlier pattern

**Problem.** Embedding works for almost every parent, but a few have far more children than the rest. A restaurant's private events have guest lists of 10 to 30 names, embedded in the event, except for the New Year gala with 400 guests. Designing every event around the gala (a separate guest collection, a `$lookup` everywhere) slows down the common case for one outlier.

**Solution.** Embed, as for the normal case. When a parent's array reaches a limit, flag the document and put the excess in one or more **overflow** documents that reference it. The code reads the overflow only for flagged documents.

```js
function addGuest(eventId, guest, cap) {
  // append only if the array has fewer than `cap` elements
  const r = db.events.updateOne(
    { _id: eventId, [`guests.${cap - 1}`]: { $exists: false } },
    { $push: { guests: guest } });
  if (r.modifiedCount === 0) {
    db.events.updateOne({ _id: eventId }, { $set: { has_overflow: true } });
    db.event_overflow.updateOne(
      { event_id: eventId, count: { $lt: cap } },
      { $push: { guests: guest }, $inc: { count: 1 } },
      { upsert: true });
  }
}
```

The filter `guests.4: { $exists: false }` is true while the array has fewer than 5 elements, so the check and the push are one atomic step. Starting with 3 guests and adding 9 more, with a cap of 5:

```
outlier main     { embedded: 5, has_overflow: true }
outlier overflow [ { count: 5 }, { count: 2 } ]
```

The event holds 5 guests; the other 7 are in two overflow documents of at most 5.

- **Gains**: the common case keeps the simplicity and speed of embedding.
- **Costs**: an ad-hoc design. Every piece of code that reads guests must remember `has_overflow`, and queries over all guests must look in two places.
- **Guidelines**: use it when the outliers are rare and known to be rare; if many parents overflow, the relationship is really 1:many and should be referenced. (The caps here are tiny to keep the example short; a real cap would be hundreds or thousands.)

## Computed pattern

**Problem.** The restaurant page shows the average rating and the number of reviews. Computing them reads every review on every page view:

```js
db.reviews.aggregate([{ $group: { _id: "$restaurant_id", avg: { $avg: "$stars" } } }])
```

```
[ { _id: 'trattoria', avg: 3.8 } ]
```

With 200,000 page views a day and 2,000 reviews written, the same number is computed 100 times for each time it changes.

**Solution.** Store the derived values, and update them when the data they depend on changes. Add to the restaurant a count and a sum, and increment them in the same update that maintains the subset:

```js
db.restaurants.updateOne({ _id: rid }, {
  $push: { recent_reviews: { $each: [{ author, stars, at }], $sort: { at: -1 }, $slice: 3 } },
  $inc:  { review_count: 1, stars_sum: stars }
});
```

```
{ review_count: 5, stars_sum: 19, avg: 3.8, all_reviews: 5 }
```

Storing the sum and the count, rather than the average, keeps the update a plain `$inc`: the average is `stars_sum / review_count`, computed on read in constant time. An average cannot be updated incrementally without knowing the count anyway.

- **Gains**: reads pay nothing for the aggregate.
- **Costs**: the derived value can drift if some write path forgets to update it ([the stored counter that drifted](../18_data_modeling_and_persistence/18c_normalization.md#denormalizing-on-purpose)). Deleting a review must decrement both fields.
- **Guidelines**: use it when reads far outnumber writes. When exactness is not required, the value can also be recomputed periodically by a batch job instead of on every write.

The relational version is the same idea: a derived column (`trans_count`, `sum_temperature` in the course's sensor example) kept by a trigger or by the writing code.

## Bucket pattern

**Problem.** Sensors under each table report whether it is occupied, once a minute. A document per reading means 1,440 documents per table per day: millions of tiny documents, each with its own `_id` and index entries that can be larger than the reading itself. Embedding all readings in the table document hits 16 MB.

**Solution.** Group readings into **buckets**: one document per table per hour, holding an array of that hour's readings, with a cap on its size.

```js
function record(table, at, seated) {
  const hour = new Date(at); hour.setUTCMinutes(0, 0, 0);
  db.occupancy.updateOne(
    { table, hour, n: { $lt: 60 } },                        // the open bucket for this hour
    { $push: { readings: { at, seated } },
      $inc:  { n: 1, seated_sum: seated },                   // computed values per bucket
      $min:  { first: at }, $max: { last: at } },
    { upsert: true });                                       // create the bucket if missing
}
```

Two hours of readings for one table:

```
[ { hour: ISODate('2026-12-05T19:00:00.000Z'), n: 60, seated_sum: 174 },
  { hour: ISODate('2026-12-05T20:00:00.000Z'), n: 60, seated_sum: 183 } ]
```

120 readings became 2 documents. The `n: { $lt: 60 }` condition closes a bucket when it is full: the next reading finds no open bucket, and the upsert starts a new one. Each bucket also carries its own computed values (count, sum, first and last time), so an hourly report reads one document per hour instead of 60 readings.

- **Gains**: far fewer documents and index entries; reads by time range touch a few buckets; per-bucket summaries for free.
- **Costs**: queries on a single reading must look inside arrays; the bucket boundaries (an hour, 60 readings) are fixed into the code.
- **Guidelines**: size buckets from the queries (a report per hour → hourly buckets), and cap them by count as well as time, so a burst of data cannot make one bucket huge. Typical uses: time series, IoT, event streams, bank transactions, and splitting a long list into pages (one bucket = one page).

MongoDB has native **time series collections** (`db.createCollection("occupancy", { timeseries: { timeField: "at", metaField: "table" } })`) that apply this pattern internally. The hand-written version shows what they do and still fits data that is not a plain time series.

## Choosing

Starting from embedding vs referencing, the patterns answer specific complaints:

| Symptom | Pattern |
|---|---|
| Every read does a `$lookup` for the same two or three fields | extended reference |
| A document embeds a long list but pages show only the start of it | subset |
| One or two parents break the size of an otherwise fine embedded design | outlier |
| The same aggregate is computed on every read | computed |
| Millions of tiny documents, read by range | bucket |

Every pattern on this page adds a copy or a derived value. Each one needs an answer to "what updates it, and what happens if that update fails?". When the answer is "nothing and nothing", the pattern is a future data bug.
