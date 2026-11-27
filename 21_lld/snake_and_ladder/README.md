# Snake and Ladder

The board game: players roll a die and move along a board of cells with snakes and ladders.

## Stages

1. **Core model.** A board of 100 cells with snakes (head → tail, down) and ladders (bottom → top, up), validated at creation (no cycles, no snake on cell 100, no two jumps starting on one cell). Players take turns; `playTurn()` rolls, moves, applies a jump, and reports the new position. A roll that would pass 100 leaves the player in place. The first to reach exactly 100 wins.
2. **Rules and dice.** The die is injected (tests use a fixed sequence). Variants: two dice, and "a six gives another roll, three sixes in a row lose the turn". New special cells (a trap that skips the next turn) must not change the turn loop.
3. **Several games.** A service runs many games at once, each with its own state; players can be in one game at a time. A finished game reports the ranking of all players.

## Entities

- `Game`: board, players in turn order, positions, die, status.
- `Board`: size, and a map from cell to `Cell` effect.
- `CellEffect`: `Snake`, `Ladder`, `Trap` (stage 2); `apply(player, position)`.
- `Dice`: interface `roll()`; `RandomDice`, `FixedDice`.
- `Player`: id, name.
- `GameService` (stage 3).

## Class diagram

See [diagram.md](diagram.md).

## Patterns used

- **Strategy** for dice and turn rules.
- **Polymorphism over cell types** (a small Strategy/State): each effect is a class, so a new cell type is a new class.
- **Template method** for the turn: roll → move → apply effect → check win, with hooks for the variants.

## Design notes

- **Inject the randomness.** A game with `new Random()` inside cannot be tested.
- **Validate the board once at creation** so the turn loop never meets an invalid jump.
- **Games share nothing mutable** (stage 3), so a lock per game is enough; the service's map of games is a `ConcurrentHashMap`.

## Compared with other solutions

<Fill in after solving.>

## Files

`SnakeAndLadder.java`: the solution, with checks for each stage in `main`.

`diagram.md`: the class diagram (Mermaid).
