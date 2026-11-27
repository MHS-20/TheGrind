# Structure Patterns

The [relationship patterns](19c_relationship_patterns.md) decide how two entities are combined. The patterns on this page are about the shape of the documents themselves: entities of different kinds in one collection, fields that vary from document to document, hierarchies, and documents whose shape changes over time.

All examples were run on MongoDB 8.0.

## Polymorphic pattern

**Problem.** The menu has dishes, wines and fixed menus. They share a name and a price, but each kind has its own fields: allergens and preparation time for a dish, vintage and region for a wine, a list of courses for a menu. A relational schema needs one of the inheritance mappings ([18d](../18_data_modeling_and_persistence/18d_mapping_objects_to_tables.md#inheritance)) and joins or nullable columns.

**Solution.** Store all of them in one collection, each document with the fields of its kind, and a `type` field saying which kind it is:

```js
db.menu_items.insertMany([
  { type: "dish", name: "Tagliatelle",  price: 1400, allergens: ["gluten", "egg"], prep_minutes: 12 },
  { type: "wine", name: "Sangiovese",   price: 2800, vintage: 2021, region: "Romagna", glass_price: 700 },
  { type: "menu", name: "Tasting menu", price: 6500, courses: ["Tagliatelle", "Tiramisu"] }
])
```

Queries on the shared fields cover every kind at once; queries on kind-specific fields add the type:

```js
db.menu_items.find({ price: { $lt: 3000 } }, { _id: 0, type: 1, name: 1 })
db.menu_items.find({ type: "wine", region: "Romagna" }, { _id: 0, name: 1 })
```

```
[ { type: 'dish', name: 'Tagliatelle' }, { type: 'wine', name: 'Sangiovese' } ]
[ { name: 'Sangiovese' } ]
```

This is the single-table inheritance mapping without the `NULL` columns: absent fields simply do not exist. The application maps `type` to a class (in Java, a sealed interface with a record per kind, or Spring Data's type alias).

- **Gains**: one collection, one query for "the whole menu", no joins.
- **Guidelines**: use it when the kinds are mostly queried together and share most of their important fields. If they share little and are never listed together, separate collections are simpler.

## Attribute pattern

**Problem.** Dishes have many optional properties: calories, protein, spicy, vegan, gluten-free, and restaurants keep adding new ones. As separate fields (`calories: 650, spicy: false, ...`), searching on any of them needs an index per field, and a new property means a new index.

**Solution.** Move the properties into an array of key-value sub-documents, and index the pair once:

```js
db.dishes.insertMany([
  { name: "Tagliatelle", props: [ { k: "calories", v: 650, u: "kcal" }, { k: "spicy", v: false }, { k: "protein", v: 22, u: "g" } ] },
  { name: "Arrabbiata",  props: [ { k: "calories", v: 540, u: "kcal" }, { k: "spicy", v: true } ] },
  { name: "Salad",       props: [ { k: "calories", v: 210, u: "kcal" }, { k: "vegan", v: true } ] }
])
db.dishes.createIndex({ "props.k": 1, "props.v": 1 })
```

Every property query has the same shape, with `$elemMatch` so that key and value are matched in the same element ([several conditions on an array](19a_documents_and_queries.md#several-conditions-on-an-array-of-documents)):

```js
db.dishes.find({ props: { $elemMatch: { k: "calories", v: { $lt: 600 } } } }, { _id: 0, name: 1 })
db.dishes.find({ props: { $elemMatch: { k: "spicy", v: true } } }, { _id: 0, name: 1 })
```

```
[ { name: 'Salad' }, { name: 'Arrabbiata' } ]
[ { name: 'Arrabbiata' } ]
```

Both use the single index, and so does a query on `vegan`, a property added without creating anything: its plan is an index scan.

```
[ '"stage":"FETCH"', '"stage":"IXSCAN"' ]
```

The third field `u` carries the unit, which plain fields could not.

- **Gains**: one index for any number of properties; new properties need no schema or index change.
- **Guidelines**: use it for many similar, sparse, searchable properties: product specs, translations of a name (`{ k: "it", v: "Tagliatelle" }`), dates of different events.

This looks like the entity-attribute-value anti-pattern ([18i](../18_data_modeling_and_persistence/18i_schema_patterns.md#entity-attribute-value)), and it shares its weakness: nothing checks that `calories` is a number. The differences are that the properties stay inside their document (no join to rebuild an item), and that `v` keeps its type (a number stays a number), so a range query does not fail on text. A validator can add checks ([indexes, validation and transactions](19f_indexes_validation_transactions.md#schema-validation)).

## Tree patterns

Menu categories form a tree:

```
Menu
├── Food
│   ├── Pasta
│   │   └── Fresh pasta
│   └── Pizza
└── Drinks
    └── Wine
```

The relational schema stored a parent id and needed a recursive CTE to walk it ([self-references](../18_data_modeling_and_persistence/18b_relationships.md#self-references)). In documents there are four common layouts, each making different questions cheap.

### Parent references

Each node stores its parent:

```js
{ _id: "Menu", parent: null }, { _id: "Food", parent: "Menu" }, { _id: "Pasta", parent: "Food" },
{ _id: "Pizza", parent: "Food" }, { _id: "Fresh pasta", parent: "Pasta" }, ...
```

Children of a node are one query (`find({ parent: "Food" })` → `Pasta, Pizza`), and moving a subtree is one update of one document. Ancestors and descendants need a walk, which `$graphLookup` does recursively inside the database:

```js
db.categories.aggregate([
  { $match: { _id: "Fresh pasta" } },
  { $graphLookup: { from: "categories", startWith: "$parent",
                    connectFromField: "parent", connectToField: "_id",
                    as: "ancestors", depthField: "depth" } }
])
```

```
ancestors (by depth): [ 'Pasta', 'Food', 'Menu' ]
```

### Child references

Each node stores the list of its children:

```js
{ _id: "Menu", children: ["Food", "Drinks"] }, { _id: "Food", children: ["Pasta", "Pizza"] }, ...
```

Children are read with the node itself. The parent is a query on the array (`findOne({ children: "Pizza" })` → `Food`), with a multikey index. It suits trees that are read top-down and rarely change, and graphs where a node can have several parents.

### Array of ancestors

Each node stores the full path from the root:

```js
{ _id: "Pasta",       ancestors: ["Menu", "Food"] }
{ _id: "Fresh pasta", ancestors: ["Menu", "Food", "Pasta"] }
```

With an index on `ancestors`, both directions are one query and no recursion:

```js
db.categories.find({ ancestors: "Food" })            // the whole subtree of Food
db.categories.findOne({ _id: "Fresh pasta" }).ancestors
```

```
[ 'Pasta', 'Pizza', 'Fresh pasta' ]
[ 'Menu', 'Food', 'Pasta' ]
```

Ancestors are read with the node (breadcrumbs for free), and subtrees are an index lookup. The cost is on moves: moving Pasta under Drinks must rewrite the ancestors of Pasta and of every node below it:

```js
db.categories.updateMany({ $or: [{ _id: "Pasta" }, { ancestors: "Pasta" }] }, [
  { $set: { ancestors: { $concatArrays: [ ["Menu", "Drinks"],
      { $slice: ["$ancestors", { $add: [{ $indexOfArray: ["$ancestors", "Food"] }, 1] }, 100] } ] } } }
])
```

```
{ modified: 2, fresh: [ 'Menu', 'Drinks', 'Pasta' ] }
```

The course recommends this layout for working with subtrees.

### Materialized path

The same information as one string:

```js
{ _id: "Pasta",       path: "/Menu/Food/Pasta" }
{ _id: "Fresh pasta", path: "/Menu/Food/Pasta/Fresh pasta" }
```

A subtree is a prefix match, which can use an index on `path` when the regular expression is anchored with `^`:

```js
db.categories.find({ path: /^\/Menu\/Food\// }).sort({ path: 1 })
```

```
[ 'Pasta', 'Fresh pasta', 'Pizza' ]
```

Sorting by path also returns the nodes in depth-first order, ready to print as an indented tree. Websites store pages this way: the URL path is the materialized path.

### Choosing

| Question | Parent refs | Child refs | Ancestors array | Path |
|---|---|---|---|---|
| Children of a node | 1 query | in the node | 1 query (with `parent` too) | regex on one level |
| Ancestors of a node | `$graphLookup` | repeated queries | in the node | parse the string |
| Whole subtree | `$graphLookup` | repeated queries | 1 indexed query | 1 indexed prefix query |
| Move a subtree | 1 update | 2 updates | update every descendant | update every descendant |

Layouts combine: storing `parent` **and** `ancestors` gives cheap children, cheap subtrees and breadcrumbs, at the cost of maintaining both. Copying a product's full category path into the product document (an extended reference) avoids looking the tree up at all on the product page, at the cost of updating products when a category moves.

## Schema versioning pattern

**Problem.** A customer used to have one `phone` field; now customers can have several contacts of different kinds. In a relational database, a migration changes every row at once ([migrations](../18_data_modeling_and_persistence/18i_schema_patterns.md#migrations)). With millions of documents and no fixed schema, rewriting every document at deployment is slow and risky.

**Solution.** Add a `schema_version` field to documents written in the new shape, and make the code read both shapes:

```js
{ _id: "ada", name: "Ada", phone: "333 111" }                                    // version 1 (no field)
{ _id: "bob", schema_version: 2, name: "Bob",
  contacts: [ { kind: "phone", value: "333 222" }, { kind: "email", value: "bob@example.com" } ] }
```

```js
function phoneOf(c) {
  return (c.schema_version ?? 1) >= 2
    ? (c.contacts.find(x => x.kind === "phone") || {}).value
    : c.phone;
}
```

```
[ 'ada: 333 111', 'bob: 333 222' ]
```

Old documents are upgraded when they are next written, or by a background job, and once none are left the version-1 branch is deleted. This is the expand-and-contract migration ([changing a live schema](../18_data_modeling_and_persistence/18i_schema_patterns.md#changing-a-live-schema)) done document by document.

- **Gains**: no downtime and no big-bang migration.
- **Costs**: the code handles several shapes for a while, and queries on the changed field must cover both (`$or: [{ phone: x }, { "contacts.value": x }]`) until the migration finishes.

## Common mistakes

- **One collection per kind** for items that are always listed together, then merging them with `$unionWith` on every page.
- **The attribute pattern without `$elemMatch`**, so `k: "calories"` and `v: { $lt: 600 }` match different elements.
- **A tree layout chosen without the queries**: parent references for an app that mostly shows subtrees, or an ancestors array for a tree that is reorganized every day.
- **Unanchored path regexes** (`/Food/`), which cannot use the index.
- **Changing a field's shape without a version marker**, leaving code to guess which shape a document has.
