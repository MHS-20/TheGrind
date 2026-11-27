# Costliest Hospital Stay

A hospital records stays and the treatments given during them.

```
patients(tax_code, first_name, last_name, sex, birth_date)
treatments(code, name, cost)
stays(patient, start_date, end_date)                 -- end_date NULL: still in hospital
stay_treatments(patient, stay_start, treatment)
```

Write a procedure `costliest_stay(p_from, p_to)` that finds the stay within the period with the highest cost F, where `days = end_date - start_date` and

- F = 20 + sum of treatment costs − days × 2, if the stay lasted less than 7 days;
- F = 15 + sum of treatment costs − days × 1.5 otherwise.

Print the patient, the stay and its treatments. Stays not yet finished do not count.

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

`costliest_stay(DATE '2026-01-01', DATE '2026-06-30')`. The data has a 3-day stay with F = 384, a 10-day stay with F = 1,240, a more expensive stay of a patient still in hospital, and a stay outside the period:

```
Patient: Laura Bianchi (BNCLRA75B41F205X)
Stay: 2026-04-10 to 2026-04-20, 10 days, F = 1240
  Surgery: 1200
  Blood test: 20
  Blood test: 20
```

## Approach

Compute F in the cursor's query: join stays to their treatments, group by stay, and write the formula as a `CASE` on the duration. Ordering by F descending puts the answer in the first row, so the procedure fetches **one** row and closes the cursor, instead of looping over every stay and keeping a running maximum.

Only then, for that one stay, read the patient and list the treatments. Joining patient data into the main query would carry it for every stay just to use it once.

`end_date IS NOT NULL` excludes the open stay; without it, `end_date - start_date` is `NULL`, and so is F, which sorts first in a descending Oracle sort.

Lesson: [PL/SQL cursors](../../../00_docs/16_sql_databases/16k_plsql_cursors.md).
