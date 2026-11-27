# Palindromes

A **palindrome** reads the same forwards and backwards: `"racecar"`, `"abba"`. Palindrome problems usually come down to expanding around each centre in O(n²). Manacher's algorithm reuses earlier expansions to reach O(n), and problems about subsequences rather than substrings need a DP table. Every trace below was produced by running the code shown.

## Expanding from the centre

Every palindrome has a centre. An odd-length palindrome like `"aba"` is centred on a character. An even-length one like `"abba"` is centred between two characters. A string of length `n` has `n` character centres and `n - 1` gaps, `2n - 1` centres in total.

From each centre, move two pointers outwards while the characters match. Each step that succeeds finds one more palindrome around that centre.

### Longest Palindromic Substring (5)

This is the third solution in `13_dp_1D/5_longest_palindrome_substring/cpp/`:

```cpp
string longestPalindrome(string s) {
    int bestStart = 0, bestLen = 0;
    for (int i = 0; i < (int)s.size(); i++) {
        expand(s, i, i, bestStart, bestLen);       // odd, centred on s[i]
        expand(s, i, i + 1, bestStart, bestLen);   // even, between s[i] and s[i+1]
    }
    return s.substr(bestStart, bestLen);
}

void expand(const string &s, int l, int r, int &bestStart, int &bestLen) {
    while (l >= 0 && r < (int)s.size() && s[l] == s[r]) { l--; r++; }
    int len = r - l - 1;           // the loop overshot by one on each side
    if (len > bestLen) { bestLen = len; bestStart = l + 1; }
}
```

For `s = "babad"`, the odd centres give lengths `1, 3, 3, 1, 1` (around `b`, `a`, `b`, `a`, `d`), and no even centre has equal neighbours. The first length-3 palindrome is `"bab"`.

The time is O(n²): `2n - 1` centres, each expanding at most `n / 2` steps. The worst case is a string of one repeated letter, where every expansion runs to an edge. The extra space is O(1), against O(n²) for the DP table that the first two solutions in that folder use.

### Palindromic Substrings (647)

Count every palindromic substring. Each successful step of an expansion is one palindrome, so the counter goes inside the loop instead of comparing lengths:

```cpp
int count = 0;
while (l >= 0 && r < n && s[l] == s[r]) { count++; l--; r++; }
```

For `"aaa"` the odd centres 0, 1, 2 give 1, 2 and 1, and the gaps 0-1 and 1-2 give 1 each: 6 in total: `a, a, a, aa, aa, aaa`.

## Manacher's algorithm

Expanding from the centre repeats work. Inside a long palindrome, the left half is the mirror image of the right half, so the palindromes around a centre on the right are often the mirror of those already found on the left. **Manacher's algorithm** uses this to find the longest palindrome around every centre in O(n).

### Removing the odd and even cases

Insert a separator, usually `#`, between characters and at both ends. `"abaaba"` becomes `"#a#b#a#a#b#a#"`, of length `2n + 1`. Every palindrome in the new string has odd length and a centre on a character, and a gap centre of the original string becomes a `#` centre. One loop handles both cases.

For each position `i` of the transformed string `t`, `p[i]` is the **radius**: the largest `r` with `t[i-r .. i+r]` a palindrome. With this transformation, `p[i]` is exactly the length of the corresponding palindrome in the original string.

### The mirror trick

The algorithm keeps the palindrome found so far that reaches furthest right: centre `c` and right edge `r = c + p[c]`. For a position `i < r`, its mirror around `c` is `j = 2c - i`. Inside `[c - p[c], c + p[c]]` the string is symmetric, so what is around `j` also appears around `i`, as long as it stays inside the big palindrome:

```
p[i] >= min(r - i, p[2c - i])
```

The algorithm starts from that value, then expands with comparisons only past `r`:

```cpp
vector<int> manacher(const string &s) {
    string t = "#";
    for (char ch : s) { t += ch; t += '#'; }
    int n = t.size(), c = 0, r = 0;
    vector<int> p(n, 0);
    for (int i = 0; i < n; i++) {
        if (i < r) p[i] = min(r - i, p[2 * c - i]);
        while (i - p[i] - 1 >= 0 && i + p[i] + 1 < n
               && t[i - p[i] - 1] == t[i + p[i] + 1])
            p[i]++;
        if (i + p[i] > r) { c = i; r = i + p[i]; }
    }
    return p;
}
```

For `s = "abaaba"`:

```
i      0  1  2  3  4  5  6  7  8  9 10 11 12
t[i]   #  a  #  b  #  a  #  a  #  b  #  a  #
p[i]   0  1  0  3  0  1  6  1  0  3  0  1  0
```

`p[6] = 6`: the `#` between the two middle `a`s is the centre of `"abaaba"`, length 6. After it, `c = 6` and `r = 12`, and every later value starts from its mirror: `p[9]` copies `p[3] = 3`, and `p[11]` copies `p[1] = 1`. None of them needs an expansion.

The longest palindrome is at the largest `p[i]`, and it starts at index `(i - p[i]) / 2` in `s`. The number of palindromic substrings is the sum of `(p[i] + 1) / 2` over all `i`.

### Why it is linear

Each successful comparison in the `while` loop extends a palindrome past `r`, so `r` grows by one. `r` never decreases and stays below `n`, so there are at most `n` successful comparisons in total. Each `i` has at most one failed comparison. The total is O(n) for a transformed string of length `2n + 1`.

### Centre expansion or Manacher

For `n` up to a few thousand, as in problems 5 and 647, expanding from the centre is fast enough and much easier to get right. Manacher is for `n` around 10⁵ or more, or when the radius at every centre is needed as part of a larger algorithm.

## Palindromes and DP

Centre expansion and Manacher only work on **substrings**, which are contiguous. A **subsequence** keeps the order but can skip characters, so it has no centre to expand from. Those problems need a DP table over ranges.

### Longest Palindromic Subsequence (516)

`dp[i][j]` is the length of the longest palindromic subsequence of `s[i..j]`:

```
dp[i][i] = 1
dp[i][j] = dp[i+1][j-1] + 2              if s[i] == s[j]
dp[i][j] = max(dp[i+1][j], dp[i][j-1])   otherwise
```

If the two ends match, both belong to the palindrome around the best answer inside. If they do not, at least one end is not used. The table is filled with `i` going down and `j` going up, in O(n²) time.

For `s = "bbbab"`, the answer is 4, from `"bbbb"`. No substring of `"bbbab"` longer than 3 is a palindrome, which is why centre expansion cannot answer it.

The longest palindromic subsequence of `s` is also the [longest common subsequence](../12_dynamic_programming/12d_string_dp.md#longest-common-subsequence-1143) of `s` and `reverse(s)`. Range DP of this kind is covered in [interval DP](../12_dynamic_programming/12e_interval_dp.md).

### Other uses of the table

- **Palindrome Partitioning (131)** precomputes `isPal[i][j]` for all ranges in O(n²), then backtracks with O(1) checks.
- **Palindrome Partitioning II (132)** uses the same table with a 1D DP for the minimum number of cuts.
- **Minimum Insertion Steps to Make a String Palindrome (1312)** is `n - LPS(s)`: the characters outside the longest palindromic subsequence each need a partner.

## Common mistakes

- **Forgetting even-length centres.** Only expanding around characters misses `"abba"`.
- **Off by one after expanding.** The loop stops one step past the palindrome on each side, so its length is `r - l - 1` and it starts at `l + 1`.
- **Reading `s[l]` before checking `l >= 0`** in the loop condition.
- **Using centre expansion for subsequences.** Subsequences need the DP table.
- **Converting Manacher's indices.** The start in the original string is `(i - p[i]) / 2`, not `i - p[i]`.
