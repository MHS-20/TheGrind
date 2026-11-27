import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class TicTacToe {

    // ---------- model ----------

    record Cell(int row, int col) {}

    record Move(Player player, Cell cell) {}

    sealed interface GameStatus permits InProgress, Won, Draw {}

    record InProgress() implements GameStatus {}

    record Won(Player winner) implements GameStatus {}

    record Draw() implements GameStatus {}

    static final GameStatus IN_PROGRESS = new InProgress(), DRAW = new Draw();

    /** A human has no strategy: their moves come from the caller. */
    record Player(String name, char symbol, MoveStrategy strategy) {
        static Player human(String name, char symbol) { return new Player(name, symbol, null); }

        boolean isBot() { return strategy != null; }
    }

    static final class Board {
        static final char EMPTY = '.';
        private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        final int n, k;
        private final char[][] cells;
        private int filled;

        Board(int n, int k) {
            if (k < 1 || k > n) throw new IllegalArgumentException("need 1 <= k <= n");
            this.n = n;
            this.k = k;
            cells = new char[n][n];
            for (char[] row : cells) java.util.Arrays.fill(row, EMPTY);
        }

        boolean inside(Cell c) { return c.row() >= 0 && c.row() < n && c.col() >= 0 && c.col() < n; }

        boolean isFree(Cell c) { return cells[c.row()][c.col()] == EMPTY; }

        boolean isFull() { return filled == n * n; }

        void place(Cell c, char symbol) { cells[c.row()][c.col()] = symbol; filled++; }

        void clear(Cell c) { cells[c.row()][c.col()] = EMPTY; filled--; }

        /** Only lines through the last move can have changed: O(k) instead of O(n^2). */
        boolean winsAt(Cell c) {
            char s = cells[c.row()][c.col()];
            for (int[] d : DIRECTIONS) {
                if (1 + run(c, s, d[0], d[1]) + run(c, s, -d[0], -d[1]) >= k) return true;
            }
            return false;
        }

        private int run(Cell c, char s, int dr, int dc) {
            int count = 0, r = c.row() + dr, col = c.col() + dc;
            while (r >= 0 && r < n && col >= 0 && col < n && cells[r][col] == s && count < k) {
                count++;
                r += dr;
                col += dc;
            }
            return count;
        }

        boolean wouldWin(Cell c, char symbol) {
            place(c, symbol);
            boolean win = winsAt(c);
            clear(c);
            return win;
        }

        List<Cell> freeCells() {
            List<Cell> out = new ArrayList<>();
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (cells[r][c] == EMPTY) out.add(new Cell(r, c));
            return out;
        }
    }

    // ---------- bots ----------

    interface MoveStrategy {
        Cell choose(Board board, Player me, List<Player> players);
    }

    static final class RandomStrategy implements MoveStrategy {
        private final Random random;

        RandomStrategy(Random random) { this.random = random; }

        public Cell choose(Board board, Player me, List<Player> players) {
            List<Cell> free = board.freeCells();
            return free.get(random.nextInt(free.size()));
        }
    }

    /** Win if one move wins; else block any opponent's winning cell; else defer to a fallback. */
    static final class WinOrBlockStrategy implements MoveStrategy {
        private final MoveStrategy fallback;

        WinOrBlockStrategy(MoveStrategy fallback) { this.fallback = fallback; }

        public Cell choose(Board board, Player me, List<Player> players) {
            List<Cell> free = board.freeCells();
            Optional<Cell> win = free.stream().filter(c -> board.wouldWin(c, me.symbol())).findFirst();
            if (win.isPresent()) return win.get();
            for (Player p : players) {
                if (p == me) continue;
                Optional<Cell> block = free.stream().filter(c -> board.wouldWin(c, p.symbol())).findFirst();
                if (block.isPresent()) return block.get();
            }
            return fallback.choose(board, me, players);
        }
    }

    // ---------- game ----------

    static final class Game {
        final Board board;
        final List<Player> players;
        private final Deque<Move> history = new ArrayDeque<>();
        private int turn;
        private GameStatus status = IN_PROGRESS;

        Game(int n, int k, List<Player> players) {
            if (players.size() < 2) throw new IllegalArgumentException("need at least two players");
            if (players.stream().map(Player::symbol).distinct().count() != players.size()) {
                throw new IllegalArgumentException("symbols must differ");
            }
            this.board = new Board(n, k);
            this.players = List.copyOf(players);
        }

        Player current() { return players.get(turn); }

        GameStatus status() { return status; }

        GameStatus move(Player p, int row, int col) {
            Cell c = new Cell(row, col);
            if (status != IN_PROGRESS) throw new IllegalStateException("game is over");
            if (p != current()) throw new IllegalStateException("not " + p.name() + "'s turn");
            if (!board.inside(c)) throw new IllegalArgumentException("out of range");
            if (!board.isFree(c)) throw new IllegalArgumentException("cell taken");
            board.place(c, p.symbol());
            history.push(new Move(p, c));
            status = board.winsAt(c) ? new Won(p) : board.isFull() ? DRAW : IN_PROGRESS;
            turn = (turn + 1) % players.size();
            return status;
        }

        /** Lets the current bot pick its move. */
        GameStatus playBot() {
            Player p = current();
            if (!p.isBot()) throw new IllegalStateException(p.name() + " is human");
            Cell c = p.strategy().choose(board, p, players);
            return move(p, c.row(), c.col());
        }

        void undo() {
            Move m = history.poll();
            if (m == null) throw new IllegalStateException("nothing to undo");
            board.clear(m.cell());
            turn = players.indexOf(m.player());
            status = IN_PROGRESS;
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

    static final Player X = Player.human("x", 'X'), O = Player.human("o", 'O'), Z = Player.human("z", 'Z');

    public static void main(String[] args) {
        System.out.println("Stage 1: core model");
        {
            Game g = new Game(3, 3, List.of(X, O));
            check("wrong turn fails", throwsAny(() -> g.move(O, 0, 0)));
            g.move(X, 0, 0);
            check("taken cell fails", throwsAny(() -> g.move(O, 0, 0)));
            check("out of range fails", throwsAny(() -> g.move(O, 3, 0)));
            g.move(O, 1, 0);
            g.move(X, 1, 1);
            g.move(O, 2, 0);
            check("diagonal win", g.move(X, 2, 2).equals(new Won(X)));
            check("move after the end fails", throwsAny(() -> g.move(O, 0, 1)));

            Game d = new Game(3, 3, List.of(X, O));
            int[][] seq = {{0, 0}, {0, 1}, {0, 2}, {1, 1}, {1, 0}, {1, 2}, {2, 1}, {2, 0}, {2, 2}};
            GameStatus last = null;
            for (int i = 0; i < seq.length; i++) last = d.move(i % 2 == 0 ? X : O, seq[i][0], seq[i][1]);
            check("full board with no line is a draw", last == DRAW);
        }

        System.out.println("Stage 2: generalise");
        {
            Game g = new Game(5, 4, List.of(X, O, Z));
            for (int c = 0; c < 3; c++) {
                g.move(X, 0, c);
                g.move(O, 1, c);
                g.move(Z, 2, c);
            }
            check("three in a row is not enough when k = 4", g.status() == IN_PROGRESS);
            check("k in a row wins with three players", g.move(X, 0, 3).equals(new Won(X)));

            Game anti = new Game(4, 3, List.of(X, O));
            anti.move(X, 0, 3);
            anti.move(O, 0, 0);
            anti.move(X, 1, 2);
            anti.move(O, 0, 1);
            check("anti-diagonal win", anti.move(X, 2, 1).equals(new Won(X)));
            check("duplicate symbols rejected", throwsAny(() -> new Game(3, 3, List.of(X, Player.human("y", 'X')))));
        }

        System.out.println("Stage 3: undo and bots");
        {
            Game g = new Game(3, 3, List.of(X, O));
            g.move(X, 0, 0);
            g.move(O, 1, 1);
            g.undo();
            check("undo gives the turn back", g.current() == O && g.board.isFree(new Cell(1, 1)));
            g.undo();
            check("undo back to the empty board", g.board.freeCells().size() == 9 && g.current() == X);
            check("undo on empty board fails", throwsAny(g::undo));

            Player bot = new Player("bot", 'O', new WinOrBlockStrategy(new RandomStrategy(new Random(1))));
            Game b = new Game(3, 3, List.of(X, bot));
            b.move(X, 0, 0);
            b.playBot();
            b.move(X, 0, 1);
            b.playBot();
            check("bot blocks an open line", !b.board.isFree(new Cell(0, 2)));

            Game w = new Game(3, 3, List.of(X, bot));
            w.move(X, 2, 2);
            w.board.place(new Cell(1, 0), 'O'); // set up a winning chance for the bot
            w.board.place(new Cell(1, 1), 'O');
            w.board.place(new Cell(2, 1), 'X'); // and a threat to block
            check("bot takes a win over a block", w.playBot().equals(new Won(bot)));
            Game won = new Game(3, 3, List.of(X, O));
            won.move(X, 0, 0); won.move(O, 1, 0); won.move(X, 0, 1); won.move(O, 1, 1); won.move(X, 0, 2);
            won.undo();
            check("undo after a win resumes the game", won.status() == IN_PROGRESS && won.current() == X);
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
