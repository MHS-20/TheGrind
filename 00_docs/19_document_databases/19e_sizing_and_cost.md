# Sizing and Cost Estimation

The design method ([embedding vs referencing](19b_embedding_vs_referencing.md#a-method)) ends with an output: the collections, their sizes, and what the frequent queries cost. These are estimates made on paper, before any data exists, from the volume table and the operations. They are what makes two candidate designs comparable: "embedding the tables makes the restaurant page one page read instead of nineteen" is an argument; "embedding feels right" is not.

This page gives the notation and formulas used for that, applies them to the restaurant schema, and works through an exercise in the style of the course's exams.

## Notation

An **aggregate** is a document type with what it embeds. `A ≺ B` means "B is embedded in A". Nesting and lists use brackets:

```
Restaurant ≺ (Table, Review)                       tables and some reviews embedded in each restaurant
Booking                                            a booking on its own, with references
[ Auto ≺ Cliente, Lavoro ≺ (Ricambi ≺ Parte, Officina) ]
                                                   two collections: cars with their customer;
                                                   jobs with their parts used (each with its part data) and their garage
```

The symbols in the formulas:

| Symbol | Meaning |
|---|---|
| ND | number of documents in a collection |
| len | average length of a document, in bytes |
| len(P) | length of a reference (pointer) to another document |
| D | page (block) size, in bytes |
| u | fill factor: the fraction of each page actually used |
| NP | number of pages a collection occupies |

## Document length

The length of an aggregate is the length of its own fields, plus, for each embedded entity, **how many** of it a document holds on average times its length, plus the length of each reference it keeps:

```
len(A ≺ B) = len(A) + avg_B_per_A × len(B) + refs × len(P)
```

The average count comes from the volume table: if there are 40,000 jobs and 80,000 parts-used records, each job holds on average 80,000 / 40,000 = 2 of them.

## Pages

Documents are stored in fixed-size pages, filled only up to `u`. The pages a collection needs:

```
NP = ⌈ ND × len / (D × u) ⌉
```

With D = 4096 bytes and u = 0.69, each page holds 4096 × 0.69 ≈ 2826 bytes of documents.

## Query cost

The cost of an operation is counted in documents and pages touched:

- **ED**: estimated documents read (or written).
- **EP**: estimated pages read (or written).

When documents are reached by key through an index, each one is assumed to be on a different page, so EP = ED. A collection scan reads every page instead: EP = NP. The access to the index itself is left out, as in the course's solutions; the point is to compare designs, and both designs pay it.

## The restaurant schema

The volume table from [the method](19b_embedding_vs_referencing.md#a-method):

| Entity | ND | len |
|---|---|---|
| Restaurant | 2,000 | 400 |
| Table | 30,000 | 40 |
| Customer (with loyalty card, 30 bytes) | 500,000 | 230 |
| Booking | 10,000,000 | 150 |
| Review | 600,000 | 500 |

Averages: 15 tables per restaurant, 300 reviews per restaurant, 20 bookings per customer, 5,000 bookings per restaurant.

Two candidate designs, with D = 4096, u = 0.69 and len(P) = 10.

### Design A: embedded

```
[ Restaurant ≺ (Table, Review₃), Customer, Booking, Review ]
```

- Restaurants embed their 15 tables and the latest 3 reviews (subset pattern).
- Bookings reference the customer and the restaurant, and copy the customer's name and phone (40 bytes) and the restaurant's name (30 bytes) (extended reference).
- Reviews reference their restaurant and author.

| Collection | ND | len | NP |
|---|---|---|---|
| Restaurant | 2,000 | 400 + 15 × 40 + 3 × 500 = 2,500 | ⌈2,000 × 2,500 / 2,826⌉ = 1,770 |
| Customer | 500,000 | 230 | 40,691 |
| Booking | 10,000,000 | 150 + 2 × 10 + 40 + 30 = 240 | 849,185 |
| Review | 600,000 | 500 + 2 × 10 = 520 | 110,395 |
| **Total** | | | **1,002,041** |

### Design B: everything referenced

```
[ Restaurant, Table, Customer, Booking, Review ]
```

| Collection | ND | len | NP |
|---|---|---|---|
| Restaurant | 2,000 | 400 | 284 |
| Table | 30,000 | 40 + 10 = 50 | 531 |
| Customer | 500,000 | 230 | 40,691 |
| Booking | 10,000,000 | 150 + 3 × 10 = 180 | 636,889 |
| Review | 600,000 | 520 | 110,395 |
| **Total** | | | **788,790** |

Design B is about 21% smaller: the copies in design A (review subset, booking extended reference) cost 213,000 pages, mostly in bookings, where 70 extra bytes are multiplied by ten million documents.

### Operations

| Operation | Design A: ED / EP | Design B: ED / EP |
|---|---|---|
| Restaurant page with tables and 3 latest reviews | 1 / 1 | 1 restaurant + 15 tables + 3 reviews = 19 / 19 |
| Tonight's bookings at a restaurant (30) with customer name and phone | 30 / 30 | 30 bookings + 30 customers = 60 / 60 |
| A customer changes phone | 1 customer + 20 bookings = 21 / 21 | 1 / 1 |
| Write a review | 1 review + 1 restaurant (subset) = 2 / 2 | 1 / 1 |

Weighted by frequency (200,000 restaurant pages, 10,000 bookings lists, 2,000 reviews and, say, 500 phone changes a day):

- Design A: 200,000 × 1 + 10,000 × 30 + 500 × 21 + 2,000 × 2 = **514,500** pages a day.
- Design B: 200,000 × 19 + 10,000 × 60 + 500 × 1 + 2,000 × 1 = **4,402,500** pages a day.

Design A uses more space and makes rare writes more expensive, and in exchange reads about 8.5 times fewer pages on this workload. That is the usual shape of the trade in document design, and the numbers say how large it is for this particular workload. With a different one (phones changing constantly, restaurant pages rarely viewed), the same calculation could favour design B.

## An exam-style exercise

The course's self-assessment uses a car-repair database. The volume table:

| Collection | ND | len |
|---|---|---|
| Officina (garage) | 10 | 150 |
| Lavoro (job) | 40,000 | 120 |
| Auto (car) | 10,000 | 100 |
| Cliente (customer) | 5,000 | 160 |
| Ricambi (part used in a job) | 80,000 | 90 |
| Parte (part catalogue entry) | 800 | 100 |

with D = 4096, len(P) = 10, u = 0.69, and the aggregate design

```
[ Auto ≺ Cliente, Lavoro ≺ (Ricambi ≺ Parte, Officina) ]
```

**Sizes.** A car embeds its one customer. A job embeds its parts used (80,000 / 40,000 = 2 per job), each with its catalogue part, and its garage, and references its car:

```
len(Auto)   = 100 + 160                        = 260
len(Lavoro) = 120 + 2 × (90 + 100) + 150 + 10  = 660

NP(Auto)    = ⌈ 10,000 × 260 / (4096 × 0.69) ⌉ = 920
NP(Lavoro)  = ⌈ 40,000 × 660 / (4096 × 0.69) ⌉ = 9,342
total                                            10,262 pages
```

**Query (a): given a car, list its jobs.** Read the car (1 document), then its jobs: 40,000 jobs / 10,000 cars = 4 per car.

```
ED = 1 + 4 = 5        EP = 5
```

**Query (b): update a customer's phone.** The customer is embedded in each of their cars: 10,000 cars / 5,000 customers = 2 cars per customer, and both copies must be updated.

```
ED = 2        EP = 2
```

These match the official solution. Query (b) shows the cost of embedding Cliente: a customer update touches every car document that copied it.

The collection `S.Auto` (cars with their customer embedded) can be built from referenced collections with an aggregation that writes its result:

```js
db.auto.aggregate([
  { $lookup: { from: "cliente", localField: "cliente_id", foreignField: "_id", as: "cliente" } },
  { $unwind: "$cliente" },
  { $out: "s_auto" }
])
```

and query (a) on the referenced design is a `$match` on the plate followed by a `$lookup` into `lavoro` on `auto_id`.

## What the estimate leaves out

- **Index pages.** Every index has its own pages, and an index lookup reads a few of them. They are left out above because both designs need similar indexes; for a design that adds indexes (the attribute pattern), count them.
- **Caching.** Frequently read documents stay in memory, so their real cost is far below one page read. The estimate ranks designs; it does not predict latency.
- **Document growth.** Documents that grow (an array that keeps receiving elements) may need to be moved or rewritten; a design with bounded arrays avoids this.
- **Variance.** Averages hide outliers: "300 reviews per restaurant" may mean most have 20 and a few have 20,000, which is the case for the [outlier pattern](19c_relationship_patterns.md#outlier-pattern).
