# Comics Correlation

A comic shop's database records which characters appear in which adventures.

```
comics(name, artist, publisher, price)
issues(comic, issue, year, pages)
adventures(comic, issue, title, description, pages)
characters(name, description)
appears_in(character, comic, issue, title, panels)
```

Write a function `comics_correlation(p_comic1, p_comic2)` that returns how many times characters of `p_comic1` appear in adventures of `p_comic2`.

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

Mouse and Dog appear in Mouse Tales. In Duck City, Mouse appears in 2 adventures and Dog in 1; Duck and Uncle appear only in Duck City.

```
comics_correlation('Mouse Tales', 'Duck City') = 3
comics_correlation('Duck City', 'Mouse Tales') = 3
```

## Approach

Count the appearances in `p_comic2` whose character is in the set of characters of `p_comic1`: a `count(*)` with an `IN` subquery.

The `IN` handles a character who appears many times in the first comic: it is counted once as a member of the set. A plain join of the two appearance lists would multiply instead (Mouse's 2 appearances in Mouse Tales × 2 in Duck City = 4). The loop version (a cursor over the characters of the first comic, one count query per character, summed) gives the same result with one query per character.

Lesson: [procedures and exceptions](../../../00_docs/16_sql_databases/16l_plsql_procedures_and_exceptions.md).
