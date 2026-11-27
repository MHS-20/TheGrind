# Chess

A two-player chess game that validates moves and detects the end of the game.

## Stages

1. **Core model.** An 8×8 board with the standard starting position. `move(from, to)` in algebraic squares (`e2`, `e4`) fails if it is not the mover's turn, the piece cannot move that way, or the path is blocked. Each piece type moves by its own rule; captures remove the opponent's piece.
2. **Check and the end.** A move that leaves one's own king in check is illegal. After each move, report check, checkmate (no legal move and in check) or stalemate (no legal move, not in check). Add castling and pawn promotion to a queen.
3. **Undo and history.** `undo()` reverts moves one by one, including captures, castling and promotion. The game produces its move list in algebraic notation.

## Entities

- `Game`: board, side to move, status, move history.
- `Board`: 8×8 squares, `pieceAt`, and a way to apply and revert a move.
- `Piece`: colour and type; abstract with `candidateMoves(board, from)`.
- `King`, `Queen`, `Rook`, `Bishop`, `Knight`, `Pawn`.
- `Move`: from, to, the piece, the captured piece, and flags (castling, promotion), so it can be undone.
- `Square`: record of file and rank.

## Class diagram

See [diagram.md](diagram.md).

## Patterns used

- **Polymorphism over piece types**: each piece knows its own movement; the game asks, it does not `switch`.
- **Command** for moves (stage 3): a move stores what it needs to revert itself. See [behavioral patterns](../../00_docs/17_object_oriented_design/17f_behavioral_patterns.md#command).
- **Template method** for sliding pieces: rook, bishop and queen share "walk in these directions until blocked", with only the direction list differing.
- **Factory** for the starting position.

## Design notes

- **Pseudo-legal then legal**: pieces generate moves ignoring check; the game filters out moves that leave the king attacked by applying, testing, and reverting each. Simple and correct; fast enough for an interview.
- **Undo is easier than copying the board** once `Move` records the captured piece and flags.
- **Scope**: say en passant, threefold repetition and the 50-move rule are out unless asked.

## Compared with other solutions

<Fill in after solving.>

## Files

`Chess.java`: the solution, with checks for each stage in `main`.

`diagram.md`: the class diagram (Mermaid).
