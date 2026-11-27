# Book Royalties

A publisher pays an author a percentage of the revenue of each book, by cumulative brackets: the first 30,000 of revenue since publication at 5%, from 30,001 to 175,000 at 15%, and so on.

```
books(id, title, author)
brackets(book, cum_from, cum_to, percent)
sales(book, sold_on, country, quantity, amount)
```

Write a function `royalties(p_book, p_year, p_previous)` that returns the royalties for a year, where `p_previous` is the revenue of all the previous years: the year's revenue fills the brackets starting where the previous years left off. Read only the brackets the calculation needs.

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

Brackets for AS45: 0–30,000 at 5%, 30,001–175,000 at 15%, 175,001–300,000 at 20%, 300,001–500,000 at 12%. Sales in 2012 total 50,000.

`royalties('AS45', 2012, 130000)`: revenue moves from 130,000 to 180,000, so 45,000 falls in the second bracket and 5,000 in the third: 45,000 × 15% + 5,000 × 20% = 7,750.

```
  45000 at 15%
  5000 at 20%
2012, after 130,000: 7750
  30000 at 5%
  20000 at 15%
2012, first year:    4500
```

## Approach

Compute the year's revenue, then treat the year as the interval (previous, previous + year] of cumulative revenue. A bracket contributes the length of its overlap with that interval:

```
amount = LEAST(cum_to, previous + year) - GREATEST(cum_from - 1, previous)
```

A cursor with parameters reads only the brackets that overlap (`cum_to > lo AND cum_from <= hi`), which is the "only the brackets needed" requirement: the first bracket, already exhausted, and the last, not reached, are never fetched.

The brackets are written with inclusive bounds (30,001 after 30,000), so `cum_from - 1` is the real lower edge; for the first bracket `GREATEST(..., 0)` keeps it at 0.

Lesson: [PL/SQL cursors](../../../00_docs/16_sql_databases/16k_plsql_cursors.md).
