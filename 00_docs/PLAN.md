# Plan: docs expansion for NeetCode 250

The topics below are what the docs need to cover the 100 problems that NeetCode 250 adds to NeetCode 150, plus three topics outside the list: string algorithms, segment and Fenwick trees, and language specifics. System design and concurrency are out of scope.

mkdocs: add mkdocs for easier navigation of solutions and lessons

## 1. New lessons and sections

Each item gives the page it belongs in: an existing page to extend, or a new page.

### Arrays and hashing (`02_data_structures`)

- [ ] **Boyer-Moore majority voting**, for one majority element (> n/2) and for up to two (> n/3). Majority Element, Majority Element II. Goes in `2b_array_problems`.
- [ ] **Dutch national flag**, three-way partitioning in one pass. Sort Colors. Goes in `2b` or `5a_sorting`.
- [ ] **Indices as a hash set**, cyclic sort, marking with the sign, with O(1) extra space. First Missing Positive. Goes in `2b`.
- [ ] **Building a hash table**: chaining vs open addressing, load factor, resizing. Design HashSet, Design HashMap. Goes in `2a_hashing`.

### Two pointers (`03_two_pointers`)

- [ ] **Rotating an array by reversing it three times.** Rotate Array.
- [ ] **k-Sum as recursion down to 2Sum.** 4Sum.
- [ ] **Merging in place from the back.** Merge Sorted Array.

### Stacks and design (`02_data_structures`)

- [ ] **A stack of partial results for nested input.** Decode String, Simplify Path, Asteroid Collision. Goes in `2d_stacks`.
- [ ] **A queue from two stacks, with amortised O(1); a stack from queues.** Goes in `2f_queues_and_deques`.
- [ ] **Ring buffer.** Design Circular Queue. Goes in `2f`.
- [ ] **A stack per frequency.** Maximum Frequency Stack. Goes in `2d`.
- [ ] **Monotonic stack for spans:** each entry stores its span, and popped spans are added to it. Online Stock Span. Goes in `2e_monotonic_stack`.

### Binary search (`05_sorting_and_searching/5c_binary_search`)

- [ ] **Rotated arrays with duplicates**, and why the worst case becomes O(n). Search in Rotated Sorted Array II.
- [ ] **Mountain arrays:** find the peak, then search each side. Find in Mountain Array.
- [ ] Integer square root as a last-true search. Sqrt(x).

### Linked lists (`06_linked_lists`)

- [ ] **Reversing a sub-range.** Reverse Linked List II. Goes in `6b_reversal`.
- [ ] **LFU cache:** a list per frequency plus a minimum-frequency pointer. Goes in `6d_copy_and_cache`.

### Trees (`07_trees`)

- [ ] **Iterative traversals with an explicit stack** (inorder, preorder, postorder), and Morris traversal for O(1) space. Goes in `7a_basics_and_traversals`.
- [ ] **BST insert and delete**, including the node with two children. Goes in `7c_binary_search_trees`.
- [ ] **Tree DP returning a pair**, take or skip. House Robber III. Goes in `7b_recursion_patterns`.
- [ ] **Quad trees** and building a tree by dividing the grid. Construct Quad Tree.
- [ ] Deleting nodes after the recursion returns (postorder). Delete Leaves With a Given Value.

### Heaps (`02_data_structures/2h_heap_problems`)

- [ ] **Simulation driven by time with a heap.** Single-Threaded CPU, IPO, Meeting Rooms III.
- [ ] **Greedy choice by highest remaining count.** Reorganize String, Longest Happy String.
- [ ] Car Pooling: heap vs difference array.

### Backtracking (`08_backtracking`)

- [ ] **Filling buckets and pruning:** sort descending, skip identical empty buckets. Matchsticks to Square, Partition to K Equal Sum Subsets. Goes in `8c_constraints`.
- [ ] Backtracking with memoization. Word Break II.

### Tries (`02_data_structures/2j_trie_problems`)

- [ ] **Trie plus DP over suffixes.** Extra Characters in a String.

### Graphs (`11_graphs`)

- [ ] **BFS over states**, where nodes are configurations. Open the Lock. Goes in `11a`.
- [ ] **Union-Find with weights**, storing the ratio to the parent. Evaluate Division. Goes in `11c`.
- [ ] **Union-Find over shared keys:** emails (Accounts Merge) and prime factors (GCD Traversal). Goes in `11c`.
- [ ] **Trimming leaves layer by layer to find the centre.** Minimum Height Trees. Goes in `11b`.
- [ ] **Reachability for every pair:** Floyd-Warshall, or BFS from each node. Course Schedule IV. New page `11h_floyd_warshall`.
- [ ] **Two topological sorts, one per axis.** Build a Matrix With Conditions. Goes in `11b`.
- [ ] **Dijkstra where the cost is the path's largest edge**, and when binary search plus BFS does the same job. Path With Minimum Effort. Goes in `11d`.
- [ ] **Critical and pseudo-critical MST edges**, by forcing an edge in or leaving it out. Goes in `11f`.
- [ ] In-degree and out-degree counting. Find the Town Judge.

### Dynamic programming (`12_dynamic_programming`)

- [ ] **Game DP and minimax:** score difference, the opponent playing optimally. Stone Game I, II and III. New page `12f_game_dp`.
- [ ] **Turning a problem into a knapsack.** Last Stone Weight II (split into two groups with the smallest difference). Goes in `12c_knapsack`.
- [ ] **Combinations vs permutations in unbounded knapsack** (loop order). Combination Sum IV vs Coin Change II. Goes in `12c`.
- [ ] Perfect Squares and Integer Break as unbounded knapsack, plus the math shortcut for Integer Break.

### Greedy (`09_greedy/9b_problems`)

- [ ] **Circular Kadane:** the total minus the minimum subarray, and the case where every number is negative. Maximum Sum Circular Subarray.
- [ ] **Two passes, left then right.** Candy.
- [ ] **Greedy with queues**, taking turns. Dota2 Senate.
- [ ] Jump Game VII: reachability with a sliding window or a prefix count.
- [ ] Longest Turbulent Subarray as a variant of Kadane.

### Math and bits (`14_math_and_geometry`, `13_bit_manipulation`)

- [ ] **Base 26 with no zero digit.** Excel Sheet Column Title. Goes in `14b_integer_formulas`.
- [ ] **GCD of strings:** concatenation commutes exactly when there is a common base. Goes in `14c_number_theory`.
- [ ] Roman numerals: subtract when a smaller value comes before a larger one.
- [ ] **Bitwise AND of a range** is the common binary prefix. Goes in `13e_tricks`.
- [ ] **Placing bits into the zero positions of a mask.** Minimum Array End. Goes in `13d_masking`.
- [ ] Adding binary strings with a carry. Add Binary.

## 2. Topics outside the list

### String algorithms (new folder `15_strings`)

- [ ] `15a_string_basics`: immutable strings and what building one costs in each language (StringBuilder, `join`, `strings.Builder`); comparing characters; counting with a fixed array of 26 or 128 entries.
- [ ] `15b_pattern_matching`: naive search; the KMP prefix function; the Z-function.
- [ ] `15c_rolling_hash`: Rabin-Karp, choosing the base and modulus, collisions, double hashing.
- [ ] `15d_palindromes`: expanding from the centre (already used in 150), and Manacher in O(n).

### Segment trees and Fenwick trees (`02_data_structures`, new pages)

- [ ] `2k_fenwick_trees`: prefix sums with point updates, `i & -i` (links to `13e`), range updates via difference arrays.
- [ ] `2l_segment_trees`: range queries with point updates, lazy propagation, and when a Fenwick tree is enough.
- [ ] Practice problems: Range Sum Query Mutable (307), Count of Smaller Numbers After Self (315), My Calendar (729/731).

### PL/SQL (`16_sql_databases`, new pages)

Oracle's procedural language, run on Oracle Database Free in Docker. Examples use the restaurant schema from `18`.

- [x] `16j_plsql_blocks`: anonymous and named blocks, variables, `%TYPE` and `%ROWTYPE`, `SELECT INTO`, control flow, `DBMS_OUTPUT`.
- [x] `16k_plsql_cursors`: implicit and explicit cursors, cursor `FOR` loops, parameters, `BULK COLLECT` and `FORALL`.
- [x] `16l_plsql_procedures_and_exceptions`: procedures, functions, packages, predefined and user-defined exceptions, `RAISE_APPLICATION_ERROR`.
- [x] `16m_plsql_triggers`: row and statement triggers, `:NEW` and `:OLD`, the mutating-table error, and a cursor loop vs one set-based statement.
- [ ] Practice in `19_sql`: the course exercises (hospital stay fee, operator rating, elected candidates).

### Document databases (new folder `19_document_databases`)

MongoDB schema design, run on MongoDB 8 in Docker, with the restaurant domain so each design can be compared with the relational schema in `18`.

- [x] `19a_documents_and_queries`: BSON, `_id`, CRUD, update operators and single-document atomicity, the aggregation pipeline, `$lookup`.
- [x] `19b_embedding_vs_referencing`: the cardinalities (1:1, 1:few, 1:many, 1:squillions, N:M), two-way referencing, the 16 MB limit, the design method.
- [x] `19c_relationship_patterns`: extended reference, subset, outlier, computed, bucket.
- [x] `19d_structure_patterns`: polymorphic, attribute, trees (parent, children, ancestors, path), schema versioning.
- [x] `19e_sizing_and_cost`: aggregate notation, document and collection sizes, documents and pages read per query.
- [x] `19f_indexes_validation_transactions`: indexes on embedded fields and arrays, `$jsonSchema`, multi-document transactions.

### Language specifics (new folder `20_language_specifics`)

One page per language, covering the same points:

- [ ] Standard containers and their costs: ordered vs hashed maps, heaps, deques.
- [ ] The heap's direction, and how to get the other one (for example, `heapq` is a min-heap only).
- [ ] Custom comparators and sort stability.
- [ ] Integer sizes and overflow (link to `13b_overflow`), division and modulo of negatives (link to `14b`).
- [ ] Building strings and what it costs.
- [ ] Recursion depth limits (Python's default is 1000) and when to switch to iteration.
- [ ] Pitfalls: Java `Integer` and `==`; C++ `map[key]` inserting a missing key; Go maps iterating in random order; Rust ownership in graph and tree code; JavaScript's default `sort` comparing as strings.

Pages: `20a_cpp`, `20b_c`, `20c_go`, `20d_java`, `20e_javascript`, `20f_python`, `20g_rust`.


### Data modeling and persistence (new folder `18_data_modeling_and_persistence`)

Designing relational schemas and the Java code that reads and writes them. It sits between `16_sql_databases` (querying) and `17_object_oriented_design` (classes and patterns). One running domain on every page: a restaurant booking system (restaurants, tables, customers, loyalty cards, bookings, menu categories and items, orders, staff). SQL is run on PostgreSQL 17 with real output; Java uses plain JDBC except in `18h`.

- [x] `18a_entities_and_keys`: entities to tables; natural vs surrogate keys; generating ids (identity, sequence, UUID, a key table, hi-lo); reading the generated id back after an insert.
- [x] `18b_relationships`: 1:1 (shared primary key vs unique foreign key), 1:N, N:N with a join table, a join table with its own attributes, self-references, `ON DELETE` options.
- [x] `18c_normalization`: 1NF to 3NF and BCNF with the anomalies shown on rows; denormalizing on purpose.
- [x] `18d_mapping_objects_to_tables`: the object-relational mismatch; foreign key in the table vs collection in the object; one-way and two-way navigation; a class for the join table or not; inheritance mapping (single table, joined, table per class).
- [x] `18e_data_access_patterns`: DTO, DAO with a DAO factory per DBMS, Repository, Active Record vs Data Mapper; the JDBC method skeleton with try-with-resources; `DataSource`; wrapping `SQLException`.
- [x] `18f_loading_related_data`: eager vs lazy; lazy loading with a Proxy subclass; the N+1 problem and its fixes; identity map.
- [x] `18g_transactions_in_code`: transaction boundaries in the service layer; unit of work; autocommit; optimistic (version column) vs pessimistic (`SELECT ... FOR UPDATE`) locking; keeping join tables consistent.
- [x] `18h_jpa_and_hibernate`: modern annotations only (`@Entity`, `@OneToMany(mappedBy)`, `@ManyToMany`, `@JoinTable`, cascade, orphan removal, fetch defaults); `EntityManager` and transactions; N+1 in Hibernate and `JOIN FETCH`. Run with a Maven Docker image.
- [x] `18i_schema_patterns`: soft delete, audit columns, enum tables vs check constraints, polymorphic associations, entity-attribute-value, migrations.

## 3. Problems NeetCode 250 adds to NeetCode 150

Arrays and Hashing (13)

- 1929. Concatenation of Array
-  1. Longest Common Prefix
-  1. Remove Element
-   1. Majority Element
-   1. Design HashSet
-   1. Design HashMap
-   1. Sort an Array
-  1. Sort Colors
-   1. Range Sum Query 2D Immutable
-   1. Best Time to Buy and Sell Stock II
-   1. Majority Element II
-   1. Subarray Sum Equals K
-  1. First Missing Positive

Two Pointers (8)

- 344. Reverse String
-   1. Valid Palindrome II
-    1. Merge Strings Alternately
-  1. Merge Sorted Array
-  1. Remove Duplicates from Sorted Array
-  1. 4Sum
-   1. Rotate Array
-   1. Boats to Save People

Sliding Window (3)

- 219. Contains Duplicate II
-   1. Minimum Size Subarray Sum
-   1. Find K Closest Elements

Stack (8)

- 682. Baseball Game
-   1. Implement Stack using Queues
-   1. Implement Queue using Stacks
-   1. Asteroid Collision
-   1. Online Stock Span
-  1. Simplify Path
-   1. Decode String
-   1. Maximum Frequency Stack

Binary Search (7)

- 35. Search Insert Position
-   1. Guess Number Higher or Lower
-  1. Sqrt(x)
-    1. Capacity to Ship Packages Within D Days
-  1. Search in Rotated Sorted Array II
-   1. Split Array Largest Sum
-    1. Find in Mountain Array

Linked List (3)

- 92. Reverse Linked List II
-   1. Design Circular Queue
-   1. LFU Cache

Trees (8)

- 94. Binary Tree Inorder Traversal
-   1. Binary Tree Preorder Traversal
-   1. Binary Tree Postorder Traversal
-   1. Insert into a Binary Search Tree
-   1. Delete Node in a BST
-   1. Construct Quad Tree
-   1. House Robber III
-    1. Delete Leaves With a Given Value

Heap / Priority Queue (5)

- 1834. Single-Threaded CPU
-   1. Reorganize String
-    1. Longest Happy String
-    1. Car Pooling
-   1. IPO

Backtracking (7)

- 1863. Sum of All Subset XOR Totals
-  1. Combinations
-  1. Permutations II
-   1. Matchsticks to Square
-   1. Partition to K Equal Sum Subsets
-  1. N-Queens II
-   1. Word Break II

Tries (1)

- 2707. Extra Characters in a String

Graphs (8)

- 463. Island Perimeter
-   1. Verifying an Alien Dictionary
-   1. Find the Town Judge
-   1. Open the Lock
-    1. Course Schedule IV
-   1. Accounts Merge
-   1. Evaluate Division
-   1. Minimum Height Trees

Advanced Graphs (4)

- 1631. Path With Minimum Effort
-    1. Find Critical and Pseudo-Critical Edges in Minimum Spanning Tree
-    1. Build a Matrix With Conditions
-    1. Greatest Common Divisor Traversal

1-D Dynamic Programming (5)

- 1137. N-th Tribonacci Number
-   1. Combination Sum IV
-   1. Perfect Squares
-   1. Integer Break
-    1. Stone Game III

2-D Dynamic Programming (5)

- 63. Unique Paths II
-  1. Minimum Path Sum
-    1. Last Stone Weight II
-   1. Stone Game
-    1. Stone Game II

Greedy (6)

- 860. Lemonade Change
-   1. Maximum Sum Circular Subarray
-   1. Longest Turbulent Subarray
-    1. Jump Game VII
-   1. Dota2 Senate
-   1. Candy

Intervals (1)

- 2402. Meeting Rooms III

Math and Geometry (5)

- 168. Excel Sheet Column Title
-    1. Greatest Common Divisor of Strings
-    1. Insert Greatest Common Divisors in Linked List
-   1. Transpose Matrix
-  1. Roman to Integer

Bit Manipulation (3)

- 67. Add Binary
-   1. Bitwise AND of Numbers Range
-    1. Minimum Array End

Total: 100.
