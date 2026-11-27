# Rolling Hash

A rolling hash gives every window of a string a number that can be updated in O(1) when the window slides, so comparing substrings becomes comparing numbers. Equal strings always get equal hashes, but different strings can also get equal hashes, so the method needs a plan for collisions. Every value below was produced by running the code shown.

## Rabin-Karp

### The polynomial hash

Treat the string as a number in base `B`, with each character a digit, reduced modulo `M`:

```
hash(s[0..k-1]) = (s[0]·B^(k-1) + s[1]·B^(k-2) + ... + s[k-1]·B^0) mod M
```

It is computed left to right, like reading a decimal number:

```cpp
long long h = 0;
for (char c : s) h = (h * B + (c - 'a' + 1)) % M;
```

### Rolling the window

Moving a window of length `k` one step to the right removes the leading digit and appends a new one:

```
new = ((old - out · B^(k-1)) · B + in) mod M
```

`B^(k-1) mod M` is computed once before the loop. Each step is then O(1), and hashing all `n - k + 1` windows is O(n).

### Example

With `B = 26`, `M = 10⁹ + 7`, and `a = 0, b = 1, c = 2` (digits starting at 0 for readability), the windows of length 3 in `"abcab"` are:

```
window   digits     hash
"abc"    0 1 2      0·676 + 1·26 + 2 =   28
"bca"    1 2 0      (28  - 0·676)·26 + 0 =  728
"cab"    2 0 1      (728 - 1·676)·26 + 1 = 1353
```

Starting digits at 0 has a flaw: `"a"`, `"aa"` and `"aaa"` all hash to 0, so strings of different lengths can collide. For windows of one fixed length it does not matter. When lengths can differ, map `a` to 1, as in the code above.

### Searching for a pattern

**Rabin-Karp** hashes the pattern once, rolls a window of the same length over the text, and compares the strings character by character only when the hashes are equal:

```cpp
int rabinKarp(const string &t, const string &p) {
    const long long B = 131, M = 1'000'000'007;
    int n = t.size(), m = p.size();
    if (m > n) return -1;
    long long hp = 0, ht = 0, pow = 1;       // pow = B^(m-1) mod M
    for (int i = 0; i < m; i++) {
        hp = (hp * B + p[i]) % M;
        ht = (ht * B + t[i]) % M;
        if (i < m - 1) pow = pow * B % M;
    }
    for (int i = 0; ; i++) {
        if (hp == ht && t.compare(i, m, p) == 0) return i;
        if (i + m == n) return -1;
        ht = (ht - t[i] * pow % M + M) % M;  // remove t[i]
        ht = (ht * B + t[i + m]) % M;        // add t[i+m]
    }
}
```

The expected time is O(n + m). With an adversarial input that makes every window collide, every check runs the O(m) comparison and it degrades to O(nm), like naive search.

Where Rabin-Karp beats KMP is **many substrings at once**: the hashes of all windows go into a hash set, and checking any later substring is O(1).

## Choosing the base and modulus

**Base.** `B` must be larger than the largest digit, otherwise two different strings can produce the same number before any reduction. With digits 1 to 26, `B = 27` or `31` works. With raw ASCII codes, use `B = 131` or `B = 257`. A random base chosen at run time makes it hard for anyone to prepare an input that collides on purpose.

**Modulus.** `M` should be a large prime, usually `10⁹ + 7` or `10⁹ + 9`. A prime spreads values evenly, and the chance that two particular different strings collide is about `1 / M`.

**Overflow.** `h * B` must fit before the `% M`. With `h < M ≈ 10⁹` and `B < 300`, the product is below 3 · 10¹¹, so a 64-bit integer is required and enough. What happens when it does not fit differs by language, see [overflow](../13_bit_manipulation/13b_overflow.md):

- C, C++, Java, Go, Rust: use 64-bit types (`long long`, `long`, `int64`, `i64` or `u64`).
- Python: integers never overflow, but `% M` must still be applied each step, or the numbers grow to thousands of digits and get slow.
- JavaScript: numbers are doubles and lose precision above 2⁵³ ≈ 9 · 10¹⁵. `h * B` with `h ≈ 10⁹` is safe, but multiplying two values near 10⁹ (for example `ht * pow`) is not. Use `BigInt` or a modulus below about 9 · 10⁷ for products of two hashes.

**Subtraction.** `old - out · pow` can be negative. Add `M` before the final `%` in C, C++, Java, Go and JavaScript, as in [modular arithmetic](../14_math_and_geometry/14c_number_theory.md#modular-arithmetic).

**Natural overflow.** Some solutions skip `% M` and let an unsigned 64-bit integer wrap, which is reducing modulo 2⁶⁴. It is fast, but there is a known family of strings (Thue-Morse strings) that collide for every base under modulo 2⁶⁴. Use a prime.

## Collisions and double hashing

A collision is two different strings with the same hash. With a small modulus they are easy to find. With `B = 31` and `M = 101`, searching strings of `a` and `b` of length 8 quickly finds

```
hash("aaaabbba") = hash("aaabaaab") = 49
```

### The birthday bound

With one pair of strings, the chance of a collision is about `1 / M`. With `N` hashes in a set, there are about `N² / 2` pairs, and a collision becomes likely once `N` approaches `√M`. For `M ≈ 10⁹`, that is about 3 · 10⁴ hashes, which is far fewer than the 10⁵ windows a typical problem produces.

There are two ways to deal with it:

1. **Verify on a match.** When two hashes are equal, compare the strings. The answer is always correct, and the comparison costs O(k) only when the hashes match.
2. **Double hashing.** Compute two hashes with different moduli (or different bases), and use the pair as the key. The effective modulus becomes about `M₁ · M₂ ≈ 10¹⁸`, and the birthday bound rises to about 10⁹ hashes. The pair can be packed into one 64-bit key as `h1 * M2 + h2` when both are below 2³².

### Repeated DNA Sequences (187)

Find every 10-letter sequence that appears more than once in a DNA string. With an alphabet of 4 letters, each letter fits in 2 bits, and a 10-letter window fits in 20 bits. The "hash" is the window itself encoded as an integer, so it cannot collide:

```cpp
int code(char c) { return c == 'A' ? 0 : c == 'C' ? 1 : c == 'G' ? 2 : 3; }

vector<string> findRepeatedDnaSequences(string s) {
    unordered_set<int> seen, added;
    vector<string> out;
    int h = 0, mask = (1 << 20) - 1;
    for (int i = 0; i < (int)s.size(); i++) {
        h = ((h << 2) | code(s[i])) & mask;   // shift in 2 bits, drop the oldest 2
        if (i >= 9 && !seen.insert(h).second && added.insert(h).second)
            out.push_back(s.substr(i - 9, 10));
    }
    return out;
}
```

This is a rolling hash with `B = 4` and `M = 2²⁰`, where the modulus is the mask. Because `B^10 = M`, the old leading digit falls off automatically.

### Longest Duplicate Substring (1044)

Find the longest substring that appears at least twice. If a duplicate of length `L` exists, a duplicate of every shorter length exists too (take a prefix of it). So the answer can be found by [binary search](../05_sorting_and_searching/5c_binary_search.md) on `L`, and each step asks "is there a duplicate of length `L`?":

1. Hash every window of length `L` with a rolling hash, O(n).
2. Put the hashes in a set. A hash seen twice is a candidate.
3. Confirm the candidate by comparing the two substrings, or use double hashing.

The total is O(n log n) expected time. With `n` up to 3 · 10⁴ windows per step, a single modulus near 10⁹ is at the birthday bound, which is why this problem is known for failing solutions that use one hash without checking.

## Common mistakes

- **A base smaller than the alphabet**, or digits starting at 0 when lengths can differ.
- **Forgetting `+ M`** after removing the outgoing character.
- **Overflow in `h * B`** with 32-bit integers, or precision loss in JavaScript.
- **Trusting a hash match** without verifying or double hashing, when many hashes are stored.
- **Modulo 2⁶⁴ by natural overflow**, which has inputs that always collide.
