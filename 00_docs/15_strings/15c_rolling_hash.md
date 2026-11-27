# Rolling Hash

A rolling hash gives every window of a string a number that can be updated in O(1) when the window slides, so comparing substrings becomes comparing numbers.

> Draft: the sections below are outlined, not written yet.

## Rabin-Karp

<!-- Polynomial hash, removing the first character and adding the next. -->

## Choosing the base and modulus

<!-- Base larger than the alphabet, a large prime modulus, overflow in each language (link to 13b_overflow). -->

## Collisions and double hashing

<!-- Verify on a hash match, or use two moduli; anti-hash inputs. Longest Duplicate Substring (1044), Repeated DNA Sequences (187). -->
