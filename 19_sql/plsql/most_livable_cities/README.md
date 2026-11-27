# Most Livable Cities

An environmental agency stores measurements per city as named parameters with a type.

```
regions(name, population)
districts(name, description)
cities(name, region, population, district)
parameters(city, region, parameter, parameter_type, description, value)
livability(city, region, value)
```

Write a procedure `most_livable(p_region)` that writes into `livability` the 5 most livable cities of a region:

1. take the 10 cities of the region with the lowest average of their parameters of type `Pollution`;
2. among those, rank by `GreenArea / population × (1 + CulturalCentres / 10)`, where `GreenArea` and `CulturalCentres` are parameters.

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

The region has 12 cities. The most polluted one has the largest green area by far and would win step 2, but step 1 excludes it.

```
CITY       VALUE
-------- -------
C1           570
C3       245.333
C5         183.6
C2            99
C4            78
```

## Approach

One `INSERT ... SELECT` with two CTEs, one per step:

- `cleanest`: group the pollution parameters by city, order by their average, `FETCH FIRST 10 ROWS ONLY`;
- `scored`: for those cities only, pivot the two named parameters into columns with `max(CASE WHEN parameter = ... THEN value END)` and apply the formula.

The final `ORDER BY ... FETCH FIRST 5` does the ranking. A city with no `CulturalCentres` parameter counts as 0 (`NVL`); one with no `GreenArea` gets `NULL` and sorts last.

The order of the steps is the point of the problem: ranking all cities by the formula and then filtering by pollution gives a different answer.

Lesson: [a cursor loop vs one statement](../../../00_docs/16_sql_databases/16m_plsql_triggers.md#a-cursor-loop-vs-one-statement).
