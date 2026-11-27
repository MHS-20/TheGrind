# Pattern Matching

Finding a pattern `p` of length `m` in a text `t` of length `n` takes O(nm) with the naive method. KMP and the Z-function both do it in O(n + m), by reusing what earlier comparisons already proved about the text instead of comparing those characters again. Every trace below was produced by running the code shown.

## Naive search

Try every starting position, and compare the pattern character by character until a mismatch:

```cpp
int strStr(const string &t, const string &p) {
    int n = t.size(), m = p.size();
    for (int i = 0; i + m <= n; i++) {
        int j = 0;
        while (j < m && t[i + j] == p[j]) j++;
        if (j == m) return i;
    }
    return -1;
}
```

On random text most attempts fail at the first or second character, so it is close to O(n) in practice. The worst case is a text and pattern made of the same letter with a difference at the end:

```
t = "aaaaaaaaaab"   (n = 11)
p = "aaab"          (m = 4)
```

At each of the 8 starting positions, the first three `a`s match and the fourth comparison fails, except at the last position, which matches. That is 32 comparisons, `(n - m + 1) · m`. With `n = 10⁵` and `m = 5 · 10⁴` it is about 2.5 · 10⁹.

The waste: after matching `aaa` at position 0, the search knows `t[1..2] = "aa"`, yet the attempt at position 1 compares those characters again.

## The KMP prefix function

**Knuth-Morris-Pratt** never moves backwards in the text. When a mismatch happens after `k` characters matched, it already knows those `k` characters (they equal `p[0..k-1]`), so it can work out how much of the pattern still matches without reading the text again.

### The prefix function

For a string `s`, `pi[i]` is the length of the longest **proper** prefix of `s[0..i]` that is also a suffix of `s[0..i]`. Proper means shorter than `s[0..i]` itself, otherwise the answer would always be the whole string.

For `s = "aabaaab"`:

```
i      0  1  2  3  4  5  6
s[i]   a  a  b  a  a  a  b
pi[i]  0  1  0  1  2  2  3
```

- `pi[4] = 2`: `"aabaa"` starts and ends with `"aa"`.
- `pi[6] = 3`: `"aabaaab"` starts and ends with `"aab"`.

### Building it in O(m)

```cpp
vector<int> prefixFunction(const string &s) {
    int m = s.size();
    vector<int> pi(m, 0);
    for (int i = 1; i < m; i++) {
        int k = pi[i - 1];                  // longest border of s[0..i-1]
        while (k > 0 && s[i] != s[k])
            k = pi[k - 1];                  // fall back to the next shorter border
        if (s[i] == s[k]) k++;
        pi[i] = k;
    }
    return pi;
}
```

**Why it works.** A border of `s[0..i]` is a border of `s[0..i-1]` extended by `s[i]`. So the candidates are the borders of `s[0..i-1]`, longest first. The longest is `pi[i-1]`. The next longest is a border of that border, `pi[k-1]`, and so on. The loop tries each until `s[k]` equals `s[i]`, or `k` reaches 0.

For `i = 5` in `"aabaaab"`: `k = pi[4] = 2`, and `s[2] = 'b' != s[5] = 'a'`. Fall back: `k = pi[1] = 1`, and `s[1] = 'a' == s[5]`, so `pi[5] = 2`.

**Why it is O(m).** `k` grows by at most 1 per value of `i`, and every pass of the `while` loop makes it smaller. It cannot shrink more than it grew in total, so the `while` loop runs at most `m` times over the whole build. The argument is the same as for the [variable sliding window](../04_sliding_window/4b_variable_size.md#why-the-cost-is-on).

### Matching with it

The matcher runs the same loop over the text, with `k` the number of pattern characters matched so far:

```cpp
vector<int> kmpSearch(const string &t, const string &p) {
    vector<int> pi = prefixFunction(p), found;
    int k = 0;
    for (int i = 0; i < (int)t.size(); i++) {
        while (k > 0 && t[i] != p[k]) k = pi[k - 1];
        if (t[i] == p[k]) k++;
        if (k == (int)p.size()) {
            found.push_back(i - k + 1);
            k = pi[k - 1];                  // keep going, matches can overlap
        }
    }
    return found;
}
```

For `t = "aaaaab"`, `p = "aab"` (`pi = [0, 1, 0]`):

```
i   t[i]   k before   action                    k after
0    a        0       match                        1
1    a        1       match                        2
2    a        2       p[2]='b' ≠ 'a' → k = pi[1]=1, then match   2
3    a        2       same                         2
4    a        2       same                         2
5    b        2       match → k = 3 = m, found at 3, k = pi[2]=0
```

`i` never decreases, so each text character is read once plus the fallbacks, and the total is O(n + m).

An equivalent and common form runs `prefixFunction` on `p + '#' + t`, with `#` a character in neither string. Every position where `pi` equals `m` ends a match.

### Find the Index of the First Occurrence in a String (28)

`kmpSearch` stopped at the first match. O(n + m) time and O(m) space for `pi`.

### Shortest Palindrome (214)

The task: add the fewest characters to the **front** of `s` to make it a palindrome. That means finding the longest prefix of `s` that is a palindrome, then putting the reverse of the rest in front.

A prefix of `s` is a palindrome exactly when it equals a suffix of `reverse(s)`. So the answer is the longest prefix of `s` that is a suffix of `reverse(s)`, which is the last value of the prefix function of `s + '#' + reverse(s)`. The `#` stops a border from running past the middle.

For `s = "aacecaaa"`, `reverse(s) = "aaacecaa"`, and the prefix function of `"aacecaaa#aaacecaa"` ends with 7: the prefix `"aacecaa"` is a palindrome. The remaining `"a"` is reversed and put in front, giving `"aaacecaaa"`.

## The Z-function

For a string `s`, `z[i]` is the length of the longest common prefix of `s` and `s[i..]`. By convention `z[0] = 0` (or `n`).

For `s = "aabxaab"`:

```
i      0  1  2  3  4  5  6
s[i]   a  a  b  x  a  a  b
z[i]   0  1  0  0  3  1  0
```

`z[4] = 3`: `s[4..] = "aab"` matches the first 3 characters of `s`.

### Computing it in O(n)

The algorithm keeps the **Z-box** `[l, r)`: the match `s[l..r-1] = s[0..r-l-1]` that reaches furthest right so far. For `i` inside the box, `s[i..r-1]` equals `s[i-l .. r-l-1]`, so `z[i]` is at least `min(r - i, z[i - l])` without comparing anything. Only characters past `r` are compared.

```cpp
vector<int> zFunction(const string &s) {
    int n = s.size(), l = 0, r = 0;
    vector<int> z(n, 0);
    for (int i = 1; i < n; i++) {
        if (i < r) z[i] = min(r - i, z[i - l]);
        while (i + z[i] < n && s[z[i]] == s[i + z[i]]) z[i]++;
        if (i + z[i] > r) { l = i; r = i + z[i]; }
    }
    return z;
}
```

Every successful comparison in the `while` loop moves `r` to the right, and `r` never moves left, so there are at most `n` of them in total.

### Matching with it

Compute the Z-function of `p + '$' + t`, with `$` in neither string. A position `i` in the text part with `z[i] == m` is a match. The separator keeps `z` from exceeding `m`.

For `p = "ab"`, `t = "xabab"`: the Z-function of `"ab$xabab"` is `[0, 0, 0, 0, 2, 0, 2, 0]`. Positions 4 and 6 have `z = 2 = m`, which are text positions `4 - 3 = 1` and `6 - 3 = 3`.

### KMP or Z

Both are O(n + m) and solve the same matching problems. They answer different questions about a single string:

- **Prefix function**: at each end position, the longest border. It suits streaming (the text can be read one character at a time) and questions about periods: a string of length `n` is a repetition of a block of length `n - pi[n-1]` when that length divides `n`.
- **Z-function**: at each start position, how far it matches the start of the string. It is often easier to reason about for "how much of the prefix appears here" questions.

The code of either is short enough to write from memory. Pick the one whose definition fits the question.

## Common mistakes

- **Moving the text pointer back** after a mismatch. In KMP only `k` moves back.
- **Forgetting the separator** in `p + '#' + t`. Without it a border can be longer than `p`.
- **Resetting `k` to 0** after a full match, which misses overlapping matches. Set `k = pi[m-1]`.
- **Using `pi[k]` instead of `pi[k - 1]`** when falling back. `k` is a length, and the border of the first `k` characters is at index `k - 1`.
