# Pattern Matching

Finding a pattern of length m in a text of length n takes O(nm) with the naive method; KMP and the Z-function do it in O(n + m) by reusing what earlier comparisons already proved.

> Draft: the sections below are outlined, not written yet.

## Naive search

<!-- The double loop, and the input that makes it O(nm) ("aaaa...ab"). -->

## The KMP prefix function

<!-- pi[i] = longest proper prefix that is also a suffix; building it; matching with it. Find the Index of the First Occurrence (28), Shortest Palindrome (214). -->

## The Z-function

<!-- z[i] = longest common prefix of s and s[i:]; pattern + '$' + text. Compare with KMP. -->
