import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Chess {

    // ---------- basics ----------

    enum Color {
        WHITE, BLACK;

        Color opposite() { return this == WHITE ? BLACK : WHITE; }
    }

    enum Status { IN_PROGRESS, CHECK, CHECKMATE, STALEMATE }

    record Square(int file, int rank) {
        static Square of(String s) {
            if (s.length() != 2) throw new IllegalArgumentException("bad square " + s);
            Square sq = new Square(s.charAt(0) - 'a', s.charAt(1) - '1');
            if (!sq.onBoard()) throw new IllegalArgumentException("bad square " + s);
            return sq;
        }

        boolean onBoard() { return file >= 0 && file < 8 && rank >= 0 && rank < 8; }

        Square plus(int df, int dr) { return new Square(file + df, rank + dr); }

        @Override public String toString() { return "" + (char) ('a' + file) + (char) ('1' + rank); }
    }

    // ---------- pieces ----------

    /** Each piece knows how it moves, ignoring check (pseudo-legal). */
    abstract static class Piece {
        final Color color;
        int moveCount; // castling needs "has never moved"

        Piece(Color color) { this.color = color; }

        abstract List<Square> targets(Board b, Square from);

        /** Letter used in notation; empty for pawns. */
        abstract String letter();

        /** Squares this piece attacks; differs from targets only for pawns. */
        List<Square> attacks(Board b, Square from) { return targets(b, from); }

        /** Step once in each direction (king, knight). */
        protected List<Square> steps(Board b, Square from, int[][] deltas) {
            List<Square> out = new ArrayList<>();
            for (int[] d : deltas) {
                Square to = from.plus(d[0], d[1]);
                if (to.onBoard() && !b.isOwn(to, color)) out.add(to);
            }
            return out;
        }
    }

    /** Template for rook, bishop and queen: walk each direction until blocked. */
    abstract static class SlidingPiece extends Piece {
        SlidingPiece(Color color) { super(color); }

        abstract int[][] directions();

        List<Square> targets(Board b, Square from) {
            List<Square> out = new ArrayList<>();
            for (int[] d : directions()) {
                for (Square to = from.plus(d[0], d[1]); to.onBoard(); to = to.plus(d[0], d[1])) {
                    if (b.isOwn(to, color)) break;
                    out.add(to);
                    if (b.pieceAt(to) != null) break; // capture stops the slide
                }
            }
            return out;
        }
    }

    static final int[][] STRAIGHT = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    static final int[][] ALL_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    static final class Rook extends SlidingPiece {
        Rook(Color c) { super(c); }
        int[][] directions() { return STRAIGHT; }
        String letter() { return "R"; }
    }

    static final class Bishop extends SlidingPiece {
        Bishop(Color c) { super(c); }
        int[][] directions() { return DIAGONAL; }
        String letter() { return "B"; }
    }

    static final class Queen extends SlidingPiece {
        Queen(Color c) { super(c); }
        int[][] directions() { return ALL_DIRECTIONS; }
        String letter() { return "Q"; }
    }

    static final class Knight extends Piece {
        private static final int[][] JUMPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};

        Knight(Color c) { super(c); }
        List<Square> targets(Board b, Square from) { return steps(b, from, JUMPS); }
        String letter() { return "N"; }
    }

    /** Castling is added by the game, which knows about attacked squares. */
    static final class King extends Piece {
        King(Color c) { super(c); }
        List<Square> targets(Board b, Square from) { return steps(b, from, ALL_DIRECTIONS); }
        String letter() { return "K"; }
    }

    static final class Pawn extends Piece {
        Pawn(Color c) { super(c); }

        int forward() { return color == Color.WHITE ? 1 : -1; }

        int lastRank() { return color == Color.WHITE ? 7 : 0; }

        List<Square> targets(Board b, Square from) {
            List<Square> out = new ArrayList<>();
            Square one = from.plus(0, forward());
            if (one.onBoard() && b.pieceAt(one) == null) {
                out.add(one);
                Square two = one.plus(0, forward());
                if (moveCount == 0 && two.onBoard() && b.pieceAt(two) == null) out.add(two);
            }
            for (Square s : attacks(b, from)) if (b.pieceAt(s) != null && !b.isOwn(s, color)) out.add(s);
            return out;
        }

        List<Square> attacks(Board b, Square from) {
            List<Square> out = new ArrayList<>();
            for (int df : new int[] {-1, 1}) {
                Square s = from.plus(df, forward());
                if (s.onBoard()) out.add(s);
            }
            return out;
        }

        String letter() { return ""; }
    }

    // ---------- moves ----------

    /** Everything needed to apply the move and to revert it (command). */
    record Move(Square from, Square to, Piece piece, Piece captured, Piece promotion,
                Square rookFrom, Square rookTo) {
        boolean isCastling() { return rookFrom != null; }
    }

    static final class Board {
        private final Piece[][] grid = new Piece[8][8];

        Piece pieceAt(Square s) { return grid[s.file()][s.rank()]; }

        void put(Square s, Piece p) { grid[s.file()][s.rank()] = p; }

        boolean isOwn(Square s, Color c) {
            Piece p = pieceAt(s);
            return p != null && p.color == c;
        }

        void apply(Move m) {
            put(m.from(), null);
            put(m.to(), m.promotion() != null ? m.promotion() : m.piece());
            m.piece().moveCount++;
            if (m.isCastling()) {
                Piece rook = pieceAt(m.rookFrom());
                put(m.rookFrom(), null);
                put(m.rookTo(), rook);
                rook.moveCount++;
            }
        }

        void revert(Move m) {
            if (m.isCastling()) {
                Piece rook = pieceAt(m.rookTo());
                put(m.rookTo(), null);
                put(m.rookFrom(), rook);
                rook.moveCount--;
            }
            put(m.to(), m.captured());
            put(m.from(), m.piece());
            m.piece().moveCount--;
        }

        boolean isAttacked(Square target, Color by) {
            for (int f = 0; f < 8; f++) {
                for (int r = 0; r < 8; r++) {
                    Piece p = grid[f][r];
                    if (p != null && p.color == by && p.attacks(this, new Square(f, r)).contains(target)) return true;
                }
            }
            return false;
        }

        Square kingOf(Color c) {
            for (int f = 0; f < 8; f++) {
                for (int r = 0; r < 8; r++) if (grid[f][r] instanceof King k && k.color == c) return new Square(f, r);
            }
            throw new IllegalStateException("no " + c + " king");
        }

        List<Square> squaresOf(Color c) {
            List<Square> out = new ArrayList<>();
            for (int f = 0; f < 8; f++) {
                for (int r = 0; r < 8; r++) if (grid[f][r] != null && grid[f][r].color == c) out.add(new Square(f, r));
            }
            return out;
        }

        /** Factory for the standard starting position. */
        static Board standard() {
            Board b = new Board();
            for (Color c : Color.values()) {
                int back = c == Color.WHITE ? 0 : 7, pawns = c == Color.WHITE ? 1 : 6;
                Piece[] row = {new Rook(c), new Knight(c), new Bishop(c), new Queen(c), new King(c),
                        new Bishop(c), new Knight(c), new Rook(c)};
                for (int f = 0; f < 8; f++) {
                    b.put(new Square(f, back), row[f]);
                    b.put(new Square(f, pawns), new Pawn(c));
                }
            }
            return b;
        }
    }

    // ---------- game ----------

    static final class Game {
        final Board board;
        private Color toMove = Color.WHITE;
        private Status status = Status.IN_PROGRESS;
        private final Deque<Move> history = new ArrayDeque<>();
        private final Deque<String> notation = new ArrayDeque<>();

        Game() { this(Board.standard()); }

        Game(Board board) { this.board = board; }

        Color toMove() { return toMove; }

        Status status() { return status; }

        Status move(String from, String to) {
            if (status == Status.CHECKMATE || status == Status.STALEMATE) throw new IllegalStateException("game over");
            Square f = Square.of(from), t = Square.of(to);
            Piece p = board.pieceAt(f);
            if (p == null) throw new IllegalArgumentException("no piece on " + from);
            if (p.color != toMove) throw new IllegalStateException("not " + p.color + "'s turn");
            Move m = legalMoves(toMove).stream()
                    .filter(x -> x.from().equals(f) && x.to().equals(t))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("illegal move " + from + to));
            String san = describe(m);
            board.apply(m);
            history.push(m);
            toMove = toMove.opposite();
            status = evaluate();
            notation.push(san + (status == Status.CHECKMATE ? "#" : status == Status.CHECK ? "+" : ""));
            return status;
        }

        void undo() {
            Move m = history.poll();
            if (m == null) throw new IllegalStateException("nothing to undo");
            board.revert(m);
            notation.poll();
            toMove = toMove.opposite();
            status = evaluate();
        }

        List<String> moveList() { return new ArrayList<>(notation).reversed(); }

        private Status evaluate() {
            boolean inCheck = board.isAttacked(board.kingOf(toMove), toMove.opposite());
            boolean canMove = !legalMoves(toMove).isEmpty();
            if (canMove) return inCheck ? Status.CHECK : Status.IN_PROGRESS;
            return inCheck ? Status.CHECKMATE : Status.STALEMATE;
        }

        /** Pseudo-legal moves, kept only if the mover's king is not attacked afterwards. */
        List<Move> legalMoves(Color c) {
            List<Move> out = new ArrayList<>();
            for (Move m : pseudoLegal(c)) {
                board.apply(m);
                if (!board.isAttacked(board.kingOf(c), c.opposite())) out.add(m);
                board.revert(m);
            }
            return out;
        }

        private List<Move> pseudoLegal(Color c) {
            List<Move> out = new ArrayList<>();
            for (Square from : board.squaresOf(c)) {
                Piece p = board.pieceAt(from);
                for (Square to : p.targets(board, from)) {
                    Piece promo = p instanceof Pawn pawn && to.rank() == pawn.lastRank() ? new Queen(c) : null;
                    out.add(new Move(from, to, p, board.pieceAt(to), promo, null, null));
                }
                if (p instanceof King) out.addAll(castlings(from, c));
            }
            return out;
        }

        private List<Move> castlings(Square kingSq, Color c) {
            List<Move> out = new ArrayList<>();
            Piece king = board.pieceAt(kingSq);
            if (king.moveCount > 0 || board.isAttacked(kingSq, c.opposite())) return out;
            for (int side : new int[] {1, -1}) { // king side, queen side
                Square rookSq = new Square(side == 1 ? 7 : 0, kingSq.rank());
                if (!(board.pieceAt(rookSq) instanceof Rook rook) || rook.color != c || rook.moveCount > 0) continue;
                boolean clear = true;
                for (int f = kingSq.file() + side; f != rookSq.file(); f += side) {
                    if (board.pieceAt(new Square(f, kingSq.rank())) != null) clear = false;
                }
                Square pass = kingSq.plus(side, 0), dest = kingSq.plus(2 * side, 0);
                if (clear && !board.isAttacked(pass, c.opposite())) { // dest is checked by legalMoves
                    out.add(new Move(kingSq, dest, king, null, null, rookSq, pass));
                }
            }
            return out;
        }

        /** Short algebraic notation, with file/rank disambiguation when needed. */
        private String describe(Move m) {
            if (m.isCastling()) return m.rookFrom().file() == 7 ? "O-O" : "O-O-O";
            Piece p = m.piece();
            StringBuilder s = new StringBuilder(p.letter());
            if (p instanceof Pawn) {
                if (m.captured() != null) s.append((char) ('a' + m.from().file()));
            } else {
                List<Square> rivals = legalMoves(p.color).stream()
                        .filter(x -> x.to().equals(m.to()) && !x.from().equals(m.from())
                                && x.piece().getClass() == p.getClass())
                        .map(Move::from).toList();
                if (!rivals.isEmpty()) {
                    boolean sameFile = rivals.stream().anyMatch(r -> r.file() == m.from().file());
                    s.append(sameFile ? String.valueOf(m.from().rank() + 1) : String.valueOf((char) ('a' + m.from().file())));
                }
            }
            if (m.captured() != null) s.append('x');
            s.append(m.to());
            if (m.promotion() != null) s.append("=Q");
            return s.toString();
        }
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    private static boolean throwsAny(Runnable r) {
        try { r.run(); return false; } catch (RuntimeException e) { return true; }
    }

    static Game play(String... moves) {
        Game g = new Game();
        for (String m : moves) g.move(m.substring(0, 2), m.substring(2));
        return g;
    }

    static Board empty() { return new Board(); }

    public static void main(String[] args) {
        System.out.println("Stage 1: core model");
        {
            Game g = new Game();
            check("white moves first", throwsAny(() -> g.move("e7", "e5")));
            g.move("e2", "e4");
            check("pawn double step", g.board.pieceAt(Square.of("e4")) instanceof Pawn);
            check("wrong colour's turn fails", throwsAny(() -> g.move("d2", "d4")));
            g.move("d7", "d5");
            check("blocked rook fails", throwsAny(() -> g.move("a1", "a3")));
            check("knight jumps over pieces", g.move("g1", "f3") == Status.IN_PROGRESS);
            check("bishop cannot move like a rook", throwsAny(() -> g.move("c8", "c6")));
            g.move("b8", "c6");
            g.move("e4", "d5");
            check("capture removes the piece", g.board.pieceAt(Square.of("d5")) instanceof Pawn p && p.color == Color.WHITE
                    && g.board.squaresOf(Color.BLACK).size() == 15);
            check("unknown square fails", throwsAny(() -> g.move("z9", "a1")));
        }

        System.out.println("Stage 2: check and the end");
        {
            Game fool = play("f2f3", "e7e5", "g2g4");
            check("checkmate detected", fool.move("d8", "h4") == Status.CHECKMATE);
            check("no move after mate", throwsAny(() -> fool.move("a2", "a3")));

            Game check = play("e2e4", "f7f6", "d1h5");
            check("check reported", check.status() == Status.CHECK);
            check("move that ignores check is illegal", throwsAny(() -> check.move("a7", "a6")));
            check("blocking the check is legal", check.move("g7", "g6") == Status.IN_PROGRESS);

            Board pin = empty();
            pin.put(Square.of("e1"), new King(Color.WHITE));
            pin.put(Square.of("e2"), new Bishop(Color.WHITE));
            pin.put(Square.of("e8"), new Rook(Color.BLACK));
            pin.put(Square.of("a8"), new King(Color.BLACK));
            check("pinned piece cannot move", throwsAny(() -> new Game(pin).move("e2", "d3")));

            Board stale = empty();
            stale.put(Square.of("f7"), new King(Color.WHITE));
            stale.put(Square.of("g5"), new Queen(Color.WHITE));
            stale.put(Square.of("h8"), new King(Color.BLACK));
            check("stalemate detected", new Game(stale).move("g5", "g6") == Status.STALEMATE);

            Board castle = empty();
            castle.put(Square.of("e1"), new King(Color.WHITE));
            castle.put(Square.of("h1"), new Rook(Color.WHITE));
            castle.put(Square.of("a1"), new Rook(Color.WHITE));
            castle.put(Square.of("e8"), new King(Color.BLACK));
            castle.put(Square.of("d8"), new Rook(Color.BLACK)); // attacks d1: no queen-side castling
            Game cg = new Game(castle);
            check("cannot castle through an attacked square", throwsAny(() -> cg.move("e1", "c1")));
            cg.move("e1", "g1");
            check("king-side castling moves the rook", cg.board.pieceAt(Square.of("f1")) instanceof Rook
                    && cg.board.pieceAt(Square.of("h1")) == null);

            Board promo = empty();
            promo.put(Square.of("a7"), new Pawn(Color.WHITE));
            promo.put(Square.of("e1"), new King(Color.WHITE));
            promo.put(Square.of("h5"), new King(Color.BLACK));
            Game pg = new Game(promo);
            pg.move("a7", "a8");
            check("pawn promotes to a queen", pg.board.pieceAt(Square.of("a8")) instanceof Queen);
        }

        System.out.println("Stage 3: undo and history");
        {
            Game g = play("e2e4", "d7d5", "e4d5");
            g.undo();
            check("undo restores the captured piece", g.board.pieceAt(Square.of("d5")) instanceof Pawn p && p.color == Color.BLACK
                    && g.board.pieceAt(Square.of("e4")) instanceof Pawn);
            check("undo gives the turn back", g.toMove() == Color.WHITE);
            g.undo();
            g.undo();
            check("undo to the start: pawn may double-step again", g.move("e2", "e4") == Status.IN_PROGRESS);

            Board castle = empty();
            castle.put(Square.of("e1"), new King(Color.WHITE));
            castle.put(Square.of("a1"), new Rook(Color.WHITE));
            castle.put(Square.of("e8"), new King(Color.BLACK));
            Game cg = new Game(castle);
            cg.move("e1", "c1");
            cg.undo();
            check("undo castling restores king and rook", cg.board.pieceAt(Square.of("e1")) instanceof King
                    && cg.board.pieceAt(Square.of("a1")) instanceof Rook && cg.board.pieceAt(Square.of("d1")) == null);
            check("castling still allowed after undo", cg.move("e1", "c1") == Status.IN_PROGRESS);

            Board promo = empty();
            promo.put(Square.of("b7"), new Pawn(Color.WHITE));
            promo.put(Square.of("a8"), new Knight(Color.BLACK));
            promo.put(Square.of("e1"), new King(Color.WHITE));
            promo.put(Square.of("h5"), new King(Color.BLACK));
            Game pg = new Game(promo);
            pg.move("b7", "a8");
            pg.undo();
            check("undo promotion-capture restores pawn and knight", pg.board.pieceAt(Square.of("b7")) instanceof Pawn
                    && pg.board.pieceAt(Square.of("a8")) instanceof Knight);

            check("fool's mate notation", play("f2f3", "e7e5", "g2g4", "d8h4").moveList()
                    .equals(List.of("f3", "e5", "g4", "Qh4#")));
            check("capture with check", play("e2e4", "e7e5", "d1h5", "b8c6", "h5f7").moveList()
                    .equals(List.of("e4", "e5", "Qh5", "Nc6", "Qxf7+")));
            check("two knights reach e2: file disambiguates", play("e2e4", "e7e5", "b1c3", "d7d6", "c3e2").moveList()
                    .equals(List.of("e4", "e5", "Nc3", "d6", "Nce2")));
            check("pawn capture names its file", play("e2e4", "d7d5", "e4d5").moveList().getLast().equals("exd5"));
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
