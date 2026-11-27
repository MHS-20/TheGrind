# Five-a-Side Teams

Friends play five-a-side football and want balanced teams.

```
players(id, name, technical, athletic, role)      -- role: ATTACKER, DEFENDER, GOALKEEPER
matches(id, played_on)
availability(match_id, player_id)
lineups(match_id, player_id, team)                -- team: 1 or 2
```

Write a procedure `generate_teams(p_match)` that splits the players available for a match into two teams:

- at least 10 players must be available, otherwise raise an error with a clear message;
- within each role, players are ordered by `technical * 1.2 + athletic`, best first, and assigned alternately to team 1 and team 2;
- the team that picks first changes from one role to the next (if attackers start with team 1, defenders start with team 2, goalkeepers with team 1 again).

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

Match 1 has 10 players available:

```
TEAM ROLE       NAME  SCORE
---- ---------- ----- -----
   1 ATTACKER   Ada    17.8
   1 ATTACKER   Dan      15
   1 DEFENDER   Fay    16.2
   1 DEFENDER   Gus     9.8
   1 GOALKEEPER Ivy    15.6
   2 ATTACKER   Bob    16.4
   2 ATTACKER   Cat    13.2
   2 DEFENDER   Eve    17.6
   2 DEFENDER   Hal    12.4
   2 GOALKEEPER Jon    14.2
```

Match 2 has 8:

```
ORA-20001: only 8 players available for match 2, at least 10 needed
```

## Approach

This one really is sequential: each player's team depends on the previous assignment, so a cursor loop is the right tool.

- One cursor over the available players, ordered by role and then score: the role changes mark where alternation restarts.
- Two variables carry the state: the current role, and the team that picked first for it. On a role change, flip the first team (`3 - team` turns 1 into 2 and back); after each insert, flip the current team. The same code serves both teams.
- The availability check runs before anything is written, and a user-defined exception turned into `RAISE_APPLICATION_ERROR` reports it.
- Deleting the match's old lineup first makes the procedure safe to run twice.

Lesson: [PL/SQL cursors](../../../00_docs/16_sql_databases/16k_plsql_cursors.md), [procedures and exceptions](../../../00_docs/16_sql_databases/16l_plsql_procedures_and_exceptions.md).
