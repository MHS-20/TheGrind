# Segment Trees

A **segment tree** answers a query over any range `a[l..r]` and applies an update in O(log n), for any operation that combines two halves into a whole: sum, minimum, maximum, GCD, count. A [Fenwick tree](2k_fenwick_trees.md) is shorter but only works for operations that can be undone, like sums. With **lazy propagation**, a segment tree also updates a whole range in O(log n). Every value below was produced by running the code shown.

## The structure

The root covers the whole array `[0, n-1]`. Each node covering `[l, r]` with `l < r` has two children, covering `[l, m]` and `[m+1, r]` with `m = (l + r) / 2`. The leaves cover one element. Each node stores the answer for its range: here, the sum.

For `a = [2, 1, 5, 3]`:

```
                 [0,3] 11
               /          \
        [0,1] 3            [2,3] 8
        /     \            /     \
   [0,0] 2  [1,1] 1   [2,2] 5  [3,3] 3
```

The tree is stored in an array, like a [heap](2g_heaps_and_priority_queues.md): the root is at index 1, and the children of node `k` are `2k` and `2k + 1`. The example stores `t = [_, 11, 3, 8, 2, 1, 5, 3]`. When `n` is not a power of two, the tree is not complete, and an array of size `4n` is always enough.

The depth is `⌈log₂ n⌉`, and there are fewer than `2n` nodes.

## Build, query, update

```cpp
struct SegTree {
    int n;
    vector<long long> t;
    SegTree(const vector<int> &a) : n(a.size()), t(4 * a.size()) { build(1, 0, n - 1, a); }

    void build(int node, int l, int r, const vector<int> &a) {
        if (l == r) { t[node] = a[l]; return; }
        int m = (l + r) / 2;
        build(2 * node, l, m, a);
        build(2 * node + 1, m + 1, r, a);
        t[node] = t[2 * node] + t[2 * node + 1];
    }

    void update(int node, int l, int r, int i, int v) {     // a[i] = v
        if (l == r) { t[node] = v; return; }
        int m = (l + r) / 2;
        if (i <= m) update(2 * node, l, m, i, v);
        else        update(2 * node + 1, m + 1, r, i, v);
        t[node] = t[2 * node] + t[2 * node + 1];
    }

    long long query(int node, int l, int r, int ql, int qr) { // sum of a[ql..qr]
        if (qr < l || r < ql) return 0;                       // no overlap
        if (ql <= l && r <= qr) return t[node];               // fully inside
        int m = (l + r) / 2;
        return query(2 * node, l, m, ql, qr) + query(2 * node + 1, m + 1, r, ql, qr);
    }
};
```

Calls start at the root: `query(1, 0, n - 1, ql, qr)`.

- **Build** visits every node once: O(n).
- **Update** walks one path from the root to a leaf and recomputes each node on the way back: O(log n).
- **Query** has three cases. A node outside the range returns the **identity** (0 for a sum). A node fully inside returns its stored value without going deeper. Only a node that partly overlaps splits.

### Why a query is O(log n)

At each depth, at most two nodes partly overlap the query: the one containing `ql` and the one containing `qr`. Every node between them is fully inside and returns immediately, so at most 4 nodes are visited per level, and there are O(log n) levels.

In the example, `query(1..3)` visits `[0,3]` (partial), `[0,1]` (partial), `[0,0]` (outside, 0), `[1,1]` (inside, 1) and `[2,3]` (inside, 8), giving 9. After `update(2, 0)`, which changes `[2,2]`, `[2,3]` and `[0,3]`, the same query gives 4.

### Other operations

Replace the three places that say "sum":

| Operation | Combine         | Identity for "outside" |
|-----------|-----------------|------------------------|
| Sum       | `a + b`         | `0`                    |
| Minimum   | `min(a, b)`     | `+∞` (`LLONG_MAX`)     |
| Maximum   | `max(a, b)`     | `-∞`                   |
| GCD       | `gcd(a, b)`     | `0`                    |

The combine must be **associative**, so that the order of grouping does not matter. It does not need to be invertible, which is what a Fenwick tree requires.

## Lazy propagation

"Add `x` to every element of `a[l..r]`" with point updates costs O(n log n). **Lazy propagation** handles it in O(log n) by not going down into a node that is fully covered. The node takes the update itself, and remembers in a second array `lz` that its children still owe it:

```cpp
struct LazySeg {
    int n;
    vector<long long> t, lz;
    LazySeg(int n) : n(n), t(4 * n), lz(4 * n) {}

    void apply(int node, int l, int r, long long x) {   // add x to all of [l, r]
        t[node] += x * (r - l + 1);
        lz[node] += x;
    }
    void push(int node, int l, int r) {                 // hand the pending add to the children
        if (lz[node] == 0) return;
        int m = (l + r) / 2;
        apply(2 * node, l, m, lz[node]);
        apply(2 * node + 1, m + 1, r, lz[node]);
        lz[node] = 0;
    }
    void add(int node, int l, int r, int ql, int qr, long long x) {
        if (qr < l || r < ql) return;
        if (ql <= l && r <= qr) { apply(node, l, r, x); return; }
        push(node, l, r);
        int m = (l + r) / 2;
        add(2 * node, l, m, ql, qr, x);
        add(2 * node + 1, m + 1, r, ql, qr, x);
        t[node] = t[2 * node] + t[2 * node + 1];
    }
    long long query(int node, int l, int r, int ql, int qr) {
        if (qr < l || r < ql) return 0;
        if (ql <= l && r <= qr) return t[node];
        push(node, l, r);
        int m = (l + r) / 2;
        return query(2 * node, l, m, ql, qr) + query(2 * node + 1, m + 1, r, ql, qr);
    }
};
```

The rule: **before going below a node, push its pending value down.** A node's own `t` is always correct, because `apply` updates it immediately. Only its descendants may be stale, and nobody reads them without passing through it first.

On 5 zeros, `add(1..3, 10)` then `add(0..1, 1)` gives `[1, 11, 10, 10, 0]`. `query(0..4) = 32` and `query(2..4) = 20`.

The update and query visit the same nodes as a plain query, so both are O(log n). For "assign `x` to a range" instead of "add", `apply` sets the value instead of adding, and `lz` needs a separate "nothing pending" marker, since 0 can be a real value.

## When a Fenwick tree is enough

| Need                                        | Fenwick | Segment tree |
|---------------------------------------------|:-------:|:------------:|
| Point update, range sum (or XOR)            | yes     | yes          |
| Range add, point query                      | yes (difference array) | yes |
| Range add, range sum                        | yes (two trees) | yes (lazy) |
| Range min, max or GCD with updates          | no      | yes          |
| Range assign, or combined updates           | no      | yes (lazy)   |
| Finding the first index where a condition holds, by walking down | limited | yes |

When both fit, the Fenwick tree is about 10 lines, uses `n + 1` memory instead of `4n`, and is faster by a constant factor. Use the segment tree when the operation cannot be undone or the updates are on ranges of a kind the Fenwick tricks do not cover.

Without any updates, neither is needed: a [prefix sum](2c_prefix_sums.md) answers sums in O(1), and a sparse table answers min or max in O(1) after O(n log n) preprocessing.

## Practice problems

### Range Sum Query - Mutable (307)

The plain segment tree above, with `update` and `query`. The [Fenwick solution](2k_fenwick_trees.md#range-sum-query-mutable-307) is shorter.

### Count of Smaller Numbers After Self (315)

A segment tree over compressed values, counting how many of each have been seen, works the same way as the [Fenwick solution](2k_fenwick_trees.md#count-of-smaller-numbers-after-self-315).

### My Calendar I (729)

`book(start, end)` adds the half-open interval `[start, end)` if it does not overlap any booked one. The coordinates go up to 10⁹, so an array-based tree would need compression, and the bookings are not known in advance. With at most 1000 calls, an ordered map from start to end is enough: only the booking just before `start` and the one just after can overlap.

```cpp
class MyCalendar {
    map<int, int> booked;                          // start -> end
public:
    bool book(int start, int end) {
        auto next = booked.lower_bound(start);     // first booking starting at or after start
        if (next != booked.end() && next->first < end) return false;
        if (next != booked.begin() && prev(next)->second > start) return false;
        booked[start] = end;
        return true;
    }
};
```

`book(10, 20)` succeeds, `book(15, 25)` fails, and `book(20, 30)` succeeds because the ends are half-open. Each call is O(log n).

### My Calendar II (731)

Now a booking fails only if it would create a **triple** booking. A sweep over a map of `+1` at each start and `-1` at each end counts how many bookings are active at every point. The new booking is added, the sweep runs, and if the count reaches 3 anywhere, the booking is removed again:

```cpp
class MyCalendarTwo {
    map<int, int> delta;
public:
    bool book(int start, int end) {
        delta[start]++; delta[end]--;
        int active = 0;
        for (auto &[t, d] : delta) {
            active += d;
            if (active >= 3) { delta[start]--; delta[end]++; return false; }
        }
        return true;
    }
};
```

For the bookings `[10,20), [50,60), [10,40), [5,15), [5,10), [25,55)`, the results are `true, true, true, false, true, true`. Each call is O(n), O(n²) for all of them, fine for 1000 calls. This is the [difference array](2k_fenwick_trees.md#range-updates-with-a-difference-array) idea on a map.

The segment tree version answers each call in O(log C) for coordinates up to `C`: range "add 1" and range "max" with lazy propagation, on a **dynamic segment tree** that creates nodes only when a path first reaches them, so 10⁹ coordinates need only O(calls · log C) nodes. Book only if the maximum over `[start, end - 1]` is below 2 (for 731) or below 1 (for 729), then add 1 to that range. It is the solution that scales, and the one My Calendar III (732) needs.

## Common mistakes

- **An array of size `2n`** for a tree where `n` is not a power of two. Use `4n`.
- **Returning the wrong identity** for nodes outside the query, such as 0 for a minimum.
- **Forgetting `push`** in `query` or `add` with lazy propagation, which reads stale children.
- **Forgetting to recompute the parent** (`t[node] = combine(children)`) after updating a child.
- **Mixing inclusive and half-open ranges.** Calendar intervals are `[start, end)`, so the segment tree range is `[start, end - 1]`.
