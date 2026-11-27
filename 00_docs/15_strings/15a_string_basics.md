# String Basics

Strings look like arrays of characters, but most languages make them immutable, so building one in a loop can cost O(n²). This page covers what each operation costs in the repository's languages and the counting tricks most string problems start from.

> Draft: the sections below are outlined, not written yet.

## Immutable strings and building them

<!-- Java StringBuilder, Python join, Go strings.Builder, C++ std::string +=, Rust String::push_str. Why s += c in a loop is quadratic in Java/Python. -->

## Comparing characters

<!-- char arithmetic (c - 'a'), case, Unicode vs bytes; Python ord/chr, Rust bytes vs chars. -->

## Counting with a fixed array

<!-- int[26] or int[128] instead of a hash map: anagrams, frequency checks; when a map is needed (Unicode). -->
