# File Search (Unix find)

An API like the Unix `find` command: search a directory tree for files that match a set of criteria.

## Stages

1. **Core model.** An in-memory file system of directories and files (name, size, extension). `find(root, filter)` returns all files under `root`, recursively, matching the filter. Filters: size greater than n, extension equals x, name matches a pattern.
2. **Combining filters.** Filters combine with AND, OR and NOT, nested to any depth (`size > 5MB AND (ext = xml OR ext = json)`). Adding a new filter type (owner, modified after a date) must not change the search code.
3. **Large trees.** `find` can stop after the first k results, skips directories it has no permission to read, and does not overflow the stack on very deep trees.

## Entities

- `FsNode`: sealed interface; `File` (name, size, owner, modified) and `Directory` (name, children).
- `Filter`: interface `matches(File)`.
- `SizeFilter`, `ExtensionFilter`, `NameFilter`; `And`, `Or`, `Not` (stage 2).
- `FileSearch`: walks the tree and applies a filter.

## Class diagram

```
<<interface>> FsNode            <<interface>> Filter
    △        △                  + matches(File): boolean
  File    Directory ◆---* FsNode    △     △      △       △    △    △
                                  Size  Ext  Name   And   Or   Not
                                                     ◆     ◆    ◆
                                                     | 2..*|    | 1
                                                   Filter Filter Filter
+--------------------------+
| FileSearch               |
+--------------------------+
| + find(root, filter, k)  |
+--------------------------+
```

## Patterns used

- **Composite** twice: the file tree, and the filter tree (`And` holds filters and is a filter). See [structural patterns](../../00_docs/16_object_oriented_design/16e_structural_patterns.md#composite).
- **Strategy**: each filter is a strategy for "does this file match".
- **Iterator** if results are produced lazily (stage 3), so the caller can stop early.

## Design notes

- **This is an Amazon favourite** because the extension in stage 2 tests Open/closed directly: the first version often has `if (criteria.size != null && ...)` in the walker.
- **Filters are pure functions of a file**, so they combine freely and can be tested alone.
- **Iterative DFS with an explicit stack** (stage 3) avoids recursion depth limits; stopping after k results is a check in the loop.
- **Builder or small factory methods** (`Filter.size(gt(5_000_000)).and(ext("xml"))`) make combined filters readable; optional.

## Compared with other solutions

<Fill in after solving.>

## Files

`FileSearch.java`: the solution, with checks for each stage in `main`.
