# Tic-Tac-Toe

A game of tic-tac-toe between two players, generalised to bigger boards.

## Stages

1. **Core model.** A 3×3 board, two players X and O taking turns. `move(player, row, col)` fails if it is not that player's turn, the cell is taken or out of range, or the game is over. After each move the game reports a win, a draw, or in progress.
2. **Generalise.** An n×n board where k in a row wins (row, column, either diagonal), and any number of players with their own symbols. Checking a win must not scan the whole board after every move.
3. **Undo and players.** `undo()` takes back the last move (repeatedly, back to the empty board). Players can be human (moves come from the caller) or a bot with a pluggable strategy (random free cell, or win-if-possible-else-block).

## Entities

- `Game`: board, players, turn index, status, move history.
- `Board`: n×n cells, `place`, `clear`, and win detection from the last move.
- `Player`: name, symbol, and a `MoveStrategy` for bots.
- `Move`: record of player, row, col.
- `GameStatus`: IN_PROGRESS, WON(player), DRAW.

## Class diagram

See [diagram.md](diagram.md).

## Patterns used

- **Command** for moves, so undo pops the history (stage 3).
- **Strategy** for bots (stage 3).
- **Template method** if this and snake-and-ladder share a `Game` skeleton; optional.

## Design notes

- **Win check from the last move only**: count consecutive symbols in 4 directions through the placed cell; O(k) per move instead of O(n²). The LeetCode version (348, Design Tic-Tac-Toe) keeps row/column/diagonal counters for O(1), which works only for k = n.
- **The turn advances in `Game`, not in `Board`**: the board knows cells, the game knows rules.

## Compared with other solutions

<Fill in after solving.>

## Files

`TicTacToe.java`: the solution, with checks for each stage in `main`.

`diagram.md`: the class diagram (Mermaid).
