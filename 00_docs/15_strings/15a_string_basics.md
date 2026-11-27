# String Basics

Strings look like arrays of characters, but most languages make them immutable, so building one in a loop can cost O(n²). This page covers what each operation costs in the repository's languages, how to compare and convert characters, and counting with a fixed array, which most string problems start with.

## Immutable strings and building them

A string is **immutable** when no operation can change it in place. Every "change" makes a new string and copies the old characters into it. Java, Python, Go, JavaScript and C# strings are immutable. C++ `std::string` and Rust `String` are mutable buffers.

### Why `s += c` in a loop can be quadratic

With an immutable string, `s += c` allocates a string of length `len(s) + 1` and copies `s` into it. Building a string of length `n` one character at a time copies

```
1 + 2 + 3 + ... + n = n(n + 1) / 2
```

characters, which is O(n²). For `n = 10⁵` that is about 5 · 10⁹ character copies, too slow for any judge.

A **builder** is a growable buffer, like a dynamic array of characters. Appending writes into spare capacity, and when the buffer is full it doubles. Each character is copied O(1) times on average (amortised), so building the whole string is O(n). Converting the builder to a string at the end costs one more O(n) copy.

| Language   | Type of a string     | Build it with                                   | `s += c` in a loop               |
|------------|----------------------|-------------------------------------------------|----------------------------------|
| C++        | `std::string`, mutable | `s += c` or `s.push_back(c)`                  | amortised O(1) per append        |
| Rust       | `String`, mutable    | `s.push(c)`, `s.push_str(t)`                    | amortised O(1) per append        |
| Java       | `String`, immutable  | `StringBuilder.append`, then `toString()`       | O(len) per append                |
| Go         | `string`, immutable  | `strings.Builder.WriteByte/WriteString`, or a `[]byte` | O(len) per append         |
| Python     | `str`, immutable     | collect parts in a list, then `"".join(parts)`  | O(len) per append in general     |
| JavaScript | `string`, immutable  | push parts into an array, then `parts.join("")` | usually fast in V8 (see below)   |
| C          | `char[]`             | write into a buffer of known size, track the length | `strcat` rescans the string: O(len) |

Two exceptions that are implementation details, not guarantees:

- **CPython** resizes a `str` in place for `s += c` when nothing else refers to `s`. It often runs in linear time, but it is not part of the language and other interpreters do not do it. `"".join` is the reliable form.
- **V8** (Node, Chrome) represents a concatenation as a *rope*, a tree of the two parts, and flattens it only when the string is read. Repeated `+=` is usually fast. `join` is still the safe default.

In C, `strcat(dst, src)` first walks `dst` to find its terminating `'\0'`, so appending in a loop is O(n²) even though the buffer is mutable. Keeping the current length in a variable and writing at `dst[len]` avoids it.

### Other operations that copy

- **Slicing** copies in Java (`substring`, since Java 7), Python (`s[i:j]`) and C++ (`substr`). Taking a slice inside a loop over `i` adds an O(length) factor. Go slices and Rust `&s[i..j]` share the original bytes and cost O(1). C++17 `std::string_view` does the same.
- **Comparing** two strings with `==` costs O(length), not O(1). Hashing a string to use it as a map key also reads every character.
- **Reversing** a Java or Python string makes a new one. When a problem asks to reverse "in place", the input is a `char[]` or a list, not a string.

## Comparing characters

### Characters are small integers

In ASCII, every character is a number from 0 to 127. Letters and digits are contiguous:

```
'0'..'9'   48..57
'A'..'Z'   65..90
'a'..'z'   97..122
```

So arithmetic on characters works:

```cpp
int idx   = c - 'a';          // 'a' → 0, 'z' → 25
int digit = c - '0';          // '7' → 7
char up   = c - 'a' + 'A';    // 'q' → 'Q'
bool isLower = c >= 'a' && c <= 'z';
```

Upper and lower case differ by 32, which is the single bit `0x20`: `'a' ^ 32 == 'A'`. The standard functions (`tolower`, `Character.toLowerCase`, `str.lower`) are clearer, and they also handle characters outside ASCII.

Python and JavaScript have no character type. Python uses `ord(c) - ord('a')` and `chr(i + ord('a'))`. JavaScript uses `s.charCodeAt(i) - 97` and `String.fromCharCode(i + 97)`.

### Bytes, code points and Unicode

A string holds text in some encoding, and "the i-th character" means different things per language:

- **C and C++** `char` is one byte. A UTF-8 letter like `é` takes two bytes, so `s.size()` counts bytes.
- **Java and JavaScript** store UTF-16. `charAt(i)` returns one 16-bit unit. Characters outside the Basic Multilingual Plane, like most emoji, take two units.
- **Python 3** indexes by code point: `len("é") == 1`.
- **Go** `s[i]` is a byte, and `for _, r := range s` decodes runes (code points).
- **Rust** `String` is UTF-8 and cannot be indexed with `s[i]` at all. `s.as_bytes()[i]` gives a byte and `s.chars()` gives code points. For problems guaranteed to be ASCII, converting once with `s.as_bytes()` or `s.chars().collect::<Vec<char>>()` gives O(1) indexing.

LeetCode inputs are almost always ASCII, so byte indexing is correct. When the input can be any Unicode text, indexing by byte can split a character in half.

## Counting with a fixed array

Anagram, permutation and frequency problems count how many times each character appears. When the alphabet is small and known, an array indexed by the character replaces a hash map:

```cpp
array<int, 26> count{};        // lowercase letters only
for (char c : s) count[c - 'a']++;

array<int, 128> count{};       // any ASCII character
for (char c : s) count[c]++;
```

Compared with a hash map, the array:

- does no hashing, so each update is one memory access;
- has no allocation per key;
- compares with another array in O(26) or O(128), a constant, with `==` in C++ and Python, and `Arrays.equals` in Java;
- can be used as a hash key itself, which groups anagrams: Python `tuple(count)`, or a string built from the counts in other languages.

### Example: Valid Anagram (242)

`s` and `t` are anagrams when every letter appears the same number of times in both. One array is enough: count up for `s`, down for `t`, and check that everything is back to 0.

```cpp
bool isAnagram(string s, string t) {
    if (s.size() != t.size()) return false;
    array<int, 26> count{};
    for (int i = 0; i < (int)s.size(); i++) {
        count[s[i] - 'a']++;
        count[t[i] - 'a']--;
    }
    for (int c : count)
        if (c != 0) return false;
    return true;
}
```

For `s = "anagram"`, `t = "nagaram"`, both count `a: 3, g: 1, m: 1, n: 1, r: 1`, so every entry ends at 0. The time is O(n) and the extra space is O(26) = O(1).

The same array drives the fixed sliding windows in [Permutation in String (567)](../04_sliding_window/4a_fixed_size.md#permutation-in-string-567).

### When a map is needed

Use a hash map when the alphabet is not known in advance: Unicode input, or "characters" that are really words. An array of size 2¹⁶ or larger is mostly empty, and clearing or comparing it costs its full size every time.

## Common mistakes

- **Building a string with `+=` in a loop** in Java or Go. Use `StringBuilder` or `strings.Builder`.
- **Slicing inside a loop** in Java, Python or C++, which hides an O(length) copy.
- **Indexing a Rust `String` with `s[i]`.** It does not compile. Convert to bytes or a `Vec<char>` first.
- **Using `count[c]` with `c` a signed `char` above 127** in C or C++. The index is negative. Cast to `unsigned char` first.
- **Assuming `length()` counts letters.** It counts bytes in C++, Go and Rust, and UTF-16 units in Java and JavaScript.
