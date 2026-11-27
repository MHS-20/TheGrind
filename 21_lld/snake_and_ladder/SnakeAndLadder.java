import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SnakeAndLadder {

    // ---------- model ----------

    record Player(String id) {}

    record TurnResult(Player player, List<Integer> rolls, int position, boolean won) {}

    // ---------- cells ----------

    /** What a cell does to a player who lands on it. A new kind of cell is a new class. */
    interface CellEffect {
        int apply(Game game, Player p, int position);
    }

    /** Snakes and ladders: a fixed jump. */
    interface Jump extends CellEffect {
        int to();

        default int apply(Game game, Player p, int position) { return to(); }
    }

    record Snake(int head, int tail) implements Jump {
        Snake {
            if (tail >= head) throw new IllegalArgumentException("snake must go down");
        }

        public int to() { return tail; }
    }

    record Ladder(int bottom, int top) implements Jump {
        Ladder {
            if (top <= bottom) throw new IllegalArgumentException("ladder must go up");
        }

        public int to() { return top; }
    }

    /** The player who lands here skips their next turn. */
    record Trap() implements CellEffect {
        public int apply(Game game, Player p, int position) {
            game.skipNext(p);
            return position;
        }
    }

    static final class Board {
        final int size;
        private final Map<Integer, CellEffect> effects;

        /** Validated once, so the turn loop never meets an invalid jump. */
        Board(int size, Map<Integer, CellEffect> effects) {
            for (var e : effects.entrySet()) {
                int from = e.getKey();
                if (from <= 1 || from >= size) throw new IllegalArgumentException("no effect allowed on cell " + from);
                if (e.getValue() instanceof Jump j && (j.to() < 1 || j.to() > size)) {
                    throw new IllegalArgumentException("jump off the board from " + from);
                }
            }
            for (int start : effects.keySet()) { // follow chains of jumps; a repeat is a cycle
                Set<Integer> seen = new HashSet<>();
                for (Integer at = start; effects.get(at) instanceof Jump j; at = j.to()) {
                    if (!seen.add(at)) throw new IllegalArgumentException("jump cycle through " + at);
                }
            }
            this.size = size;
            this.effects = Map.copyOf(effects);
        }

        static Builder builder(int size) { return new Builder(size); }

        static final class Builder {
            private final int size;
            private final Map<Integer, CellEffect> effects = new HashMap<>();

            Builder(int size) { this.size = size; }

            Builder snake(int head, int tail) { return put(head, new Snake(head, tail)); }

            Builder ladder(int bottom, int top) { return put(bottom, new Ladder(bottom, top)); }

            Builder trap(int cell) { return put(cell, new Trap()); }

            private Builder put(int cell, CellEffect e) {
                if (effects.putIfAbsent(cell, e) != null) throw new IllegalArgumentException("two effects on " + cell);
                return this;
            }

            Board build() { return new Board(size, effects); }
        }

        /** Applies effects until the player lands on a plain cell (chains are acyclic). */
        int land(Game game, Player p, int position) {
            CellEffect e;
            while ((e = effects.get(position)) != null) {
                int next = e.apply(game, p, position);
                if (next == position) break;
                position = next;
            }
            return position;
        }
    }

    // ---------- dice and turn rules ----------

    interface Dice {
        int roll();
    }

    static final class RandomDice implements Dice {
        private final Random random;

        RandomDice(Random random) { this.random = random; }

        public int roll() { return 1 + random.nextInt(6); }
    }

    static final class FixedDice implements Dice {
        private final int[] values;
        private int i;

        FixedDice(int... values) { this.values = values; }

        public int roll() { return values[i++ % values.length]; }
    }

    record TwoDice(Dice a, Dice b) implements Dice {
        public int roll() { return a.roll() + b.roll(); }
    }

    /** Which rolls a turn consists of. */
    interface TurnRule {
        List<Integer> rolls(Dice dice);
    }

    static final class SingleRoll implements TurnRule {
        public List<Integer> rolls(Dice dice) { return List.of(dice.roll()); }
    }

    /** A six rolls again; three sixes in a row forfeit the whole turn. */
    static final class ExtraRollOnSix implements TurnRule {
        public List<Integer> rolls(Dice dice) {
            List<Integer> out = new ArrayList<>();
            int r;
            do {
                r = dice.roll();
                out.add(r);
                if (out.size() == 3 && r == 6) return List.of();
            } while (r == 6);
            return out;
        }
    }

    // ---------- game ----------

    static final class Game {
        final String id;
        final Board board;
        final List<Player> players;
        private final Map<Player, Integer> positions = new LinkedHashMap<>();
        private final Set<Player> skipping = new HashSet<>();
        private final Dice dice;
        private final TurnRule rule;
        private int turn;
        private Player winner;

        Game(String id, Board board, List<Player> players, Dice dice, TurnRule rule) {
            this.id = id;
            this.board = board;
            this.players = List.copyOf(players);
            this.dice = dice;
            this.rule = rule;
            players.forEach(p -> positions.put(p, 1));
        }

        void skipNext(Player p) { skipping.add(p); }

        synchronized boolean isOver() { return winner != null; }

        synchronized int position(Player p) { return positions.get(p); }

        /** roll(s) -> move -> apply cell -> check win, then pass the turn. */
        synchronized TurnResult playTurn() {
            if (winner != null) throw new IllegalStateException("game over");
            Player p = players.get(turn);
            turn = (turn + 1) % players.size();
            if (skipping.remove(p)) return new TurnResult(p, List.of(), positions.get(p), false);

            List<Integer> rolls = rule.rolls(dice);
            int pos = positions.get(p);
            for (int r : rolls) {
                if (pos + r > board.size) continue; // overshoot: stay
                pos = board.land(this, p, pos + r);
                if (pos == board.size) break;
            }
            positions.put(p, pos);
            if (pos == board.size) winner = p;
            return new TurnResult(p, rolls, pos, winner == p);
        }

        /** Winner first, then the others by how far they got. */
        synchronized List<Player> ranking() {
            if (winner == null) throw new IllegalStateException("game not finished");
            return players.stream()
                    .sorted(Comparator.comparing((Player p) -> p != winner)
                            .thenComparing(p -> -positions.get(p)))
                    .toList();
        }
    }

    // ---------- many games ----------

    static final class GameService {
        private final Map<String, Game> games = new ConcurrentHashMap<>();
        private final Map<Player, String> playing = new ConcurrentHashMap<>();

        /** All players join or none does. */
        synchronized Game create(String id, Board board, List<Player> players, Dice dice, TurnRule rule) {
            for (Player p : players) {
                if (playing.containsKey(p)) throw new IllegalStateException(p.id() + " is already in a game");
            }
            Game g = new Game(id, board, players, dice, rule);
            if (games.putIfAbsent(id, g) != null) throw new IllegalStateException("game id taken");
            players.forEach(p -> playing.put(p, id));
            return g;
        }

        TurnResult playTurn(String gameId) {
            Game g = games.get(gameId);
            TurnResult r = g.playTurn();
            if (r.won()) g.players.forEach(p -> playing.remove(p, gameId));
            return r;
        }

        List<Player> ranking(String gameId) { return games.get(gameId).ranking(); }
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

    static final Player ANN = new Player("ann"), BOB = new Player("bob"), CAT = new Player("cat");

    static Board classic() {
        return Board.builder(100).ladder(2, 38).ladder(28, 84).snake(99, 7).snake(50, 30).build();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Stage 1: core model");
        {
            check("snake on cell 100 rejected", throwsAny(() -> Board.builder(100).snake(100, 5).build()));
            check("two jumps on one cell rejected", throwsAny(() -> Board.builder(100).snake(40, 5).ladder(40, 60)));
            check("jump cycle rejected", throwsAny(() -> Board.builder(100).ladder(10, 40).snake(40, 10).build()));

            Game g = new Game("g", classic(), List.of(ANN, BOB), new FixedDice(1, 5, 4), new SingleRoll());
            TurnResult t = g.playTurn();
            check("ladder takes ann from 2 to 38", t.player() == ANN && t.position() == 38);
            check("turns alternate", g.playTurn().player() == BOB && g.position(BOB) == 6);

            Board small = Board.builder(10).snake(9, 3).build();
            Game end = new Game("e", small, List.of(ANN, BOB), new FixedDice(6, 1, 4, 1, 3), new SingleRoll());
            end.playTurn();                 // ann 7
            end.playTurn();                 // bob 2
            check("overshoot leaves player in place", end.playTurn().position() == 7); // ann rolls 4: 11 > 10
            end.playTurn();                 // bob 3
            TurnResult win = end.playTurn(); // ann rolls 3: exactly 10
            check("exactly the last cell wins", win.won() && end.isOver());
            check("no turns after the end", throwsAny(end::playTurn));
        }

        System.out.println("Stage 2: rules and dice");
        {
            Game two = new Game("t", Board.builder(100).build(), List.of(ANN, BOB),
                    new TwoDice(new FixedDice(3), new FixedDice(4)), new SingleRoll());
            check("two dice add up", two.playTurn().position() == 8);

            Game six = new Game("s", Board.builder(100).build(), List.of(ANN, BOB),
                    new FixedDice(6, 2, 6, 6, 6, 1), new ExtraRollOnSix());
            TurnResult extra = six.playTurn();
            check("six gives another roll", extra.rolls().equals(List.of(6, 2)) && extra.position() == 9);
            TurnResult lost = six.playTurn();
            check("three sixes lose the turn", lost.rolls().isEmpty() && lost.position() == 1);

            Game trap = new Game("x", Board.builder(100).trap(4).build(), List.of(ANN, BOB),
                    new FixedDice(3, 1, 2, 1), new SingleRoll());
            trap.playTurn();                   // ann lands on trap at 4
            trap.playTurn();                   // bob 2
            TurnResult skipped = trap.playTurn();
            check("trap skips the next turn", skipped.player() == ANN && skipped.rolls().isEmpty());
            check("turn passes after the skip", trap.playTurn().player() == BOB);
        }

        System.out.println("Stage 3: several games");
        {
            GameService svc = new GameService();
            Board small = Board.builder(10).build();
            svc.create("g1", small, List.of(ANN, BOB), new FixedDice(3), new SingleRoll());
            check("player cannot join two games", throwsAny(() -> svc.create("g2", small, List.of(CAT, ANN),
                    new FixedDice(1), new SingleRoll())));
            check("failed create joins nobody", !svc.playing.containsKey(CAT));
            svc.playTurn("g1"); svc.playTurn("g1"); svc.playTurn("g1"); svc.playTurn("g1");
            svc.playTurn("g1");                // ann: 1 -> 4 -> 7 -> 10
            check("ranking lists all players, winner first", svc.ranking("g1").equals(List.of(ANN, BOB)));
            check("finished players can join a new game",
                    svc.create("g3", small, List.of(ANN, CAT), new FixedDice(1), new SingleRoll()) != null);

            // many games played in parallel do not interfere
            GameService many = new GameService();
            int n = 50;
            for (int i = 0; i < n; i++) {
                many.create("m" + i, classic(), List.of(new Player("a" + i), new Player("b" + i)),
                        new RandomDice(new Random(i)), new SingleRoll());
            }
            ExecutorService pool = Executors.newFixedThreadPool(8);
            AtomicInteger finished = new AtomicInteger();
            CountDownLatch go = new CountDownLatch(1);
            for (int i = 0; i < n; i++) {
                String id = "m" + i;
                pool.submit(() -> {
                    go.await();
                    while (!many.playTurn(id).won()) { }
                    finished.incrementAndGet();
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(20, TimeUnit.SECONDS);
            check("50 parallel games all finish", finished.get() == n && many.playing.isEmpty());
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
