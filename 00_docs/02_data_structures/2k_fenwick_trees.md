# Fenwick Trees

A [prefix sum array](2c_prefix_sums.md) answers "sum of `a[l..r]`" in O(1), but changing one element means rebuilding every prefix after it, which is O(n). A **Fenwick tree** (also called a **binary indexed tree**, or BIT) does both in O(log n): a point update and a prefix sum. It is an array of `n + 1` numbers and two loops of three lines each. Every value below was produced by running the code shown.

## The idea

Each entry `bit[i]` stores the sum of a block of the array that **ends at `i`**. The block's length is the lowest set bit of `i`, `i & -i` ([isolating the lowest set bit](../13_bit_manipulation/13e_tricks.md#isolating-the-lowest-set-bit-x-x)):

```
bit[i] = a[i - (i & -i) + 1] + ... + a[i]
```

Indices start at 1, because `0 & -0 = 0` would give a block of length 0.

For `n = 8`:

```
i   binary   i & -i   block covered
1    0001       1      a[1]
2    0010       2      a[1..2]
3    0011       1      a[3]
4    0100       4      a[1..4]
5    0101       1      a[5]
6    0110       2      a[5..6]
7    0111       1      a[7]
8    1000       8      a[1..8]
```

Odd indices cover one element, and powers of two cover the whole prefix.

## Prefix sum: remove the lowest bit

To sum `a[1..i]`, take `bit[i]`, which covers the block ending at `i`. The block before it ends at `i - (i & -i)`. Repeat until the index reaches 0:

```cpp
long long prefix(int i) const {            // a[1] + ... + a[i]
    long long s = 0;
    for (; i > 0; i -= i & -i) s += bit[i];
    return s;
}
```

Each step clears the lowest set bit of `i`, so there are at most as many steps as `i` has set bits: O(log n).

For `a = [3, 2, -1, 6, 5, 4, -3, 3]` (positions 1 to 8), the tree is

```
i        1  2   3   4  5  6   7   8
bit[i]   3  5  -1  10  5  9  -3  19
```

and `prefix(7)` reads

```
i = 7 (0111)   bit[7] = -3    covers a[7]
i = 6 (0110)   bit[6] =  9    covers a[5..6]
i = 4 (0100)   bit[4] = 10    covers a[1..4]
i = 0          stop           total 16
```

which is `3 + 2 - 1 + 6 + 5 + 4 - 3 = 16`. A range sum is two prefix sums: `sum(l, r) = prefix(r) - prefix(l - 1)`. `sum(3, 6)` is `-1 + 6 + 5 + 4 = 14`.

## Point update: add the lowest bit

Changing `a[i]` changes every block that contains position `i`. Those are `bit[i]`, then the next index up whose block reaches back over `i`, which is `i + (i & -i)`, and so on until past `n`:

```cpp
void add(int i, long long delta) {         // a[i] += delta
    for (; i < (int)bit.size(); i += i & -i) bit[i] += delta;
}
```

Adding `delta` to `a[3]` updates `bit[3]`, `bit[4]` and `bit[8]`, exactly the rows of the table whose block contains 3. Adding the lowest bit carries it into a higher bit, so the index at least doubles its lowest bit each step: O(log n).

The update adds a **difference**. To set `a[i] = v`, keep a copy of the array and call `add(i, v - a[i])`.

## The whole structure

```cpp
struct Fenwick {
    vector<long long> bit;
    Fenwick(int n) : bit(n + 1, 0) {}
    void add(int i, long long delta) {
        for (; i < (int)bit.size(); i += i & -i) bit[i] += delta;
    }
    long long prefix(int i) const {
        long long s = 0;
        for (; i > 0; i -= i & -i) s += bit[i];
        return s;
    }
    long long range(int l, int r) const { return prefix(r) - prefix(l - 1); }
};
```

Building it with `n` calls to `add` is O(n log n). There is also an O(n) build: set `bit[i] = a[i]`, then for each `i` in increasing order add `bit[i]` into `bit[i + (i & -i)]` if that index exists.

| Operation       | Prefix sum array | Fenwick tree |
|-----------------|------------------|--------------|
| Build           | O(n)             | O(n) or O(n log n) |
| Range sum       | O(1)             | O(log n)     |
| Point update    | O(n)             | O(log n)     |
| Memory          | n + 1            | n + 1        |

The Fenwick tree needs the operation to be **invertible**, because a range is computed as `prefix(r) - prefix(l - 1)`. Sums and XOR work. Minimum and maximum do not, since there is no way to "subtract" a minimum. Those need a [segment tree](2l_segment_trees.md).

## Range updates with a difference array

A **difference array** `d` stores `d[i] = a[i] - a[i-1]`, so `a[i]` is the prefix sum of `d` up to `i`. Adding `x` to every element of `a[l..r]` changes only two entries of `d`: `d[l] += x` and `d[r+1] -= x`.

Keeping `d` in a Fenwick tree gives **range update, point query**, each in O(log n):

```cpp
void addRange(int l, int r, long long x) { f.add(l, x); f.add(r + 1, -x); }
long long get(int i) { return f.prefix(i); }
```

### Range update and range sum

For range updates **and** range sums, two Fenwick trees are needed. The prefix sum of `a` up to `i` is

```
a[1] + ... + a[i] = Σ_{j≤i} d[j] · (i - j + 1) = i · Σ d[j]  -  Σ d[j] · (j - 1)
```

So one tree `b1` stores `d[j]`, and a second `b2` stores `d[j] · (j - 1)`:

```cpp
struct RangeFenwick {
    Fenwick b1, b2;
    RangeFenwick(int n) : b1(n + 1), b2(n + 1) {}
    void addRange(int l, int r, long long x) {
        b1.add(l, x);           b1.add(r + 1, -x);
        b2.add(l, x * (l - 1)); b2.add(r + 1, -x * r);
    }
    long long prefix(int i) const { return b1.prefix(i) * i - b2.prefix(i); }
};
```

On an array of 5 zeros, adding 10 to `[2, 4]` and then 1 to `[1, 2]` gives `[1, 11, 10, 10, 0]`, and `prefix(5) = 32`. A [segment tree with lazy propagation](2l_segment_trees.md#lazy-propagation) does the same and more, at the cost of more code.

## Range Sum Query - Mutable (307)

`NumArray` gets an array, then a mix of `update(index, val)` and `sumRange(left, right)` calls, up to 3 · 10⁴ of them on 3 · 10⁴ elements. With a plain prefix array, updates cost O(n), and recomputing each sum costs O(n), so either way the worst case is about 9 · 10⁸ operations. The Fenwick tree makes both O(log n):

```cpp
class NumArray {
    vector<int> a;
    Fenwick f;
public:
    NumArray(vector<int> &nums) : a(nums), f(nums.size()) {
        for (int i = 0; i < (int)a.size(); i++) f.add(i + 1, a[i]);
    }
    void update(int i, int val) { f.add(i + 1, val - a[i]); a[i] = val; }
    int sumRange(int l, int r) { return f.range(l + 1, r + 1); }
};
```

The problem uses 0-based indices, so every call shifts by 1.

## Count of Smaller Numbers After Self (315)

For each `nums[i]`, count the elements to its right that are smaller. The brute force is O(n²), too slow for `n = 10⁵`.

Walk the array **from the right**, and keep a Fenwick tree indexed by **value**, where `bit` counts how many of each value have been seen. For each element, the answer is the prefix count of values strictly smaller, then the element itself is added.

Values go up to 10⁴ in absolute value and can be negative, so they are first replaced by their **rank** among the distinct values (coordinate compression): 1 for the smallest, 2 for the next, and so on. The tree then has one slot per distinct value.

```cpp
vector<int> countSmaller(vector<int> &nums) {
    vector<int> sorted = nums;
    sort(sorted.begin(), sorted.end());
    sorted.erase(unique(sorted.begin(), sorted.end()), sorted.end());
    Fenwick f(sorted.size());
    vector<int> out(nums.size());
    for (int i = (int)nums.size() - 1; i >= 0; i--) {
        int rank = lower_bound(sorted.begin(), sorted.end(), nums[i]) - sorted.begin() + 1;
        out[i] = f.prefix(rank - 1);           // values strictly smaller
        f.add(rank, 1);
    }
    return out;
}
```

For `nums = [5, 2, 6, 1]`, the ranks are `5 → 3, 2 → 2, 6 → 4, 1 → 1`:

```
i   value   rank   prefix(rank - 1)   seen after
3     1       1          0             {1}
2     6       4          1             {1, 6}
1     2       2          1             {1, 2, 6}
0     5       3          2             {1, 2, 5, 6}
```

The output is `[2, 1, 1, 0]`. The time is O(n log n). The same pattern counts **inversions** (pairs `i < j` with `a[i] > a[j]`). A merge sort that counts while merging is the other standard solution.

## Common mistakes

- **Index 0.** `i & -i` is 0 for `i = 0`, so `add(0, x)` loops forever. Shift 0-based indices by 1.
- **Setting instead of adding.** `add` takes a difference. Keep the old values to compute `val - a[i]`.
- **Using a Fenwick tree for min or max over arbitrary ranges.** The operation must be invertible.
- **Forgetting coordinate compression** when the tree is indexed by value and values are large or negative.
- **`int` sums** that overflow. Use `long long` for the tree.
