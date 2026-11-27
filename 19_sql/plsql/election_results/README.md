# Election Results

An election assigns each district a number of seats, split between parties in proportion to their votes; each party's seats go to its candidates with the most preference votes.

```
parties(id, name, country)
districts(id, description, seats)
candidates(id, first_name, last_name, party, district, preferences)
votes(district, party, votes)
```

Write a procedure `elected(p_district)` that prints the elected candidates of a district. A party's seats are `ROUND(party votes / total votes × district seats)` (rounding problems can be ignored).

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

District 1 has 5 seats and 5,000 votes: Green 2,400 (2.4 → 2 seats), Blue 2,000 (2 seats), Red 600 (0.6 → 1 seat).

```
District 1: 5 seats, 5000 votes
Green: 2 seats
  Eve White (1200)
  Dan Green (1000)
Blue: 2 seats
  Ada Lovelace (900)
  Cat Black (700)
Red: 1 seats
  Gus Red (400)
```

## Approach

Read the district's seats and total votes once. Then a cursor over the parties, and a parameterized cursor over each party's candidates ordered by preferences, stopped after the party's seats with `EXIT WHEN c_candidates%ROWCOUNT > seats`. The `%ROWCOUNT` attribute counts the rows fetched so far, so no separate counter is needed.

This is a top-N per group, which a single query can also do with `ROW_NUMBER() OVER (PARTITION BY party ORDER BY preferences DESC)` compared with the party's seats ([window functions](../../../00_docs/16_sql_databases/16e_window_functions.md)).

Rounding each party separately can assign more or fewer seats than the district has (here 2 + 2 + 1 = 5, but 3 parties at 1/3 of 5 seats each would get 2 + 2 + 2). Real systems use a largest-remainder or D'Hondt method.

Lesson: [PL/SQL cursors](../../../00_docs/16_sql_databases/16k_plsql_cursors.md).
