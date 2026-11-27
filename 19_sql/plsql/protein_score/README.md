# Protein Score

A protein is divided into regions, each made of amino acids, each made of atoms. Regions and atoms have a potential of −1, 0 or +1.

```
proteins(code, name, function, class)
amino_acids(code, name, function)
atoms(code, name, potential)
regions(code, protein, position, potential)
atom_in_amino(amino, atom, position)
amino_in_region(region, amino, position)
```

Each atom of a region gets a weight from the region's potential and its own:

| region \ atom | − | 0 | + |
|---|---|---|---|
| − | +1 | −1 | −2 |
| 0 | −1 | +1 | −1 |
| + | −2 | −1 | +1 |

A region's score is the average weight of its atoms; the protein's score is the average of its regions' scores. Write a procedure `protein_score(p_protein)` that prints both.

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

```
region R1: 0.333
region R2: -1.000
region R3: 0.000
protein P1: -0.222
```

R1 (potential −) has atoms − − 0 − − 0, weights +1 +1 −1 +1 +1 −1: 2/6.

## Approach

The table has a simple rule: +1 when the potentials are equal, −2 when they are opposite (their difference is 2), −1 otherwise. That turns nine cases into one `CASE`.

The cursor query joins regions → amino acids → atoms (the amino acids are only the path to the atoms), groups by region, and averages the weights. The loop then averages the regions. The average of averages is deliberate: every region counts the same, whatever its number of atoms. A single `avg` over all atoms of the protein would weight big regions more and give a different number.

Lesson: [PL/SQL cursors](../../../00_docs/16_sql_databases/16k_plsql_cursors.md).
