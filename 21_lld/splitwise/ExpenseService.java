import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

public class ExpenseService {

    // ---------- model ----------

    record User(String name) {}

    record Expense(long id, User payer, long amount, Map<User, Long> shares) {}

    record Payment(User from, User to, long amount) {}

    // ---------- splits ----------

    /** Turns a total into each participant's share, or rejects invalid input. New split types are new classes. */
    interface Split {
        Map<User, Long> shares(long amount);
    }

    /** Equal parts; leftover cents go to the first participants. */
    record EqualSplit(List<User> participants) implements Split {
        public Map<User, Long> shares(long amount) {
            if (participants.isEmpty()) throw new IllegalArgumentException("no participants");
            long base = amount / participants.size(), extra = amount % participants.size();
            Map<User, Long> out = new LinkedHashMap<>();
            for (int i = 0; i < participants.size(); i++) out.put(participants.get(i), base + (i < extra ? 1 : 0));
            return out;
        }
    }

    record ExactSplit(Map<User, Long> amounts) implements Split {
        public Map<User, Long> shares(long amount) {
            long sum = amounts.values().stream().mapToLong(Long::longValue).sum();
            if (sum != amount) throw new IllegalArgumentException("exact amounts sum to " + sum + ", not " + amount);
            return new LinkedHashMap<>(amounts);
        }
    }

    /** Percentages summing to 100; the last participant absorbs rounding. Pass a LinkedHashMap for a stable "last". */
    record PercentSplit(Map<User, Integer> percents) implements Split {
        public Map<User, Long> shares(long amount) {
            int total = percents.values().stream().mapToInt(Integer::intValue).sum();
            if (total != 100) throw new IllegalArgumentException("percentages sum to " + total);
            Map<User, Long> out = new LinkedHashMap<>();
            long assigned = 0;
            User last = null;
            for (var e : percents.entrySet()) {
                long share = amount * e.getValue() / 100;
                out.put(e.getKey(), share);
                assigned += share;
                last = e.getKey();
            }
            out.merge(last, amount - assigned, Long::sum);
            return out;
        }
    }

    // ---------- balances ----------

    /** Pairwise net debts: owes(a, b) > 0 means a owes b. Kept antisymmetric. */
    static final class BalanceSheet {
        private final Map<User, Map<User, Long>> owes = new HashMap<>();

        void add(User debtor, User creditor, long amount) {
            if (debtor.equals(creditor) || amount == 0) return;
            owes.computeIfAbsent(debtor, k -> new HashMap<>()).merge(creditor, amount, Long::sum);
            owes.computeIfAbsent(creditor, k -> new HashMap<>()).merge(debtor, -amount, Long::sum);
        }

        /** For each other user: positive means they owe {@code user}. */
        Map<User, Long> balancesOf(User user) {
            Map<User, Long> out = new HashMap<>();
            owes.getOrDefault(user, Map.of()).forEach((other, v) -> { if (v != 0) out.put(other, -v); });
            return out;
        }

        /** Net per user: positive is owed money, negative owes money. Sums to zero. */
        Map<User, Long> net() {
            Map<User, Long> out = new HashMap<>();
            owes.forEach((u, row) -> out.put(u, -row.values().stream().mapToLong(Long::longValue).sum()));
            return out;
        }
    }

    static final class Group {
        final String name;
        final List<User> members;
        final List<Expense> expenses = new ArrayList<>();
        final BalanceSheet sheet = new BalanceSheet();

        Group(String name, List<User> members) { this.name = name; this.members = List.copyOf(members); }
    }

    // ---------- facade ----------

    private final Map<String, Group> groups = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(1);

    Group createGroup(String name, List<User> members) {
        Group g = new Group(name, members);
        groups.put(name, g);
        return g;
    }

    /** Computes all shares first, so an invalid split changes nothing. */
    synchronized Expense addExpense(Group g, User payer, long amount, Split split) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Map<User, Long> shares = split.shares(amount);
        if (!g.members.containsAll(shares.keySet()) || !g.members.contains(payer)) {
            throw new IllegalArgumentException("not a group member");
        }
        Expense e = new Expense(ids.getAndIncrement(), payer, amount, Map.copyOf(shares));
        g.expenses.add(e);
        shares.forEach((u, share) -> g.sheet.add(u, payer, share));
        return e;
    }

    synchronized void recordPayment(Group g, Payment p) { g.sheet.add(p.to(), p.from(), p.amount()); }

    /** Net amounts across all groups; positive means the other user owes {@code user}. */
    synchronized Map<User, Long> balances(User user) {
        Map<User, Long> out = new HashMap<>();
        for (Group g : groups.values()) g.sheet.balancesOf(user).forEach((o, v) -> out.merge(o, v, Long::sum));
        out.values().removeIf(v -> v == 0);
        return out;
    }

    /** Greedy: the largest debtor pays the largest creditor. At most n-1 payments, not always the minimum. */
    synchronized List<Payment> settleUp(Group g) {
        record Entry(User user, long amount) {}
        Comparator<Entry> largestFirst = Comparator.comparingLong(Entry::amount).reversed();
        PriorityQueue<Entry> creditors = new PriorityQueue<>(largestFirst), debtors = new PriorityQueue<>(largestFirst);
        g.sheet.net().forEach((u, v) -> {
            if (v > 0) creditors.add(new Entry(u, v));
            if (v < 0) debtors.add(new Entry(u, -v));
        });
        List<Payment> payments = new ArrayList<>();
        while (!debtors.isEmpty()) {
            Entry d = debtors.poll(), c = creditors.poll();
            long x = Math.min(d.amount(), c.amount());
            payments.add(new Payment(d.user(), c.user(), x));
            if (d.amount() > x) debtors.add(new Entry(d.user(), d.amount() - x));
            if (c.amount() > x) creditors.add(new Entry(c.user(), c.amount() - x));
        }
        return payments;
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    private static boolean throwsIAE(Runnable r) {
        try { r.run(); return false; } catch (IllegalArgumentException e) { return true; }
    }

    static final User A = new User("ann"), B = new User("bob"), C = new User("cat"), D = new User("dan");

    public static void main(String[] args) {
        System.out.println("Stage 1: core model");
        {
            ExpenseService s = new ExpenseService();
            Group trip = s.createGroup("trip", List.of(A, B, C));
            Expense e = s.addExpense(trip, A, 1000, new EqualSplit(List.of(A, B, C)));
            check("1000 / 3 = 334, 333, 333", e.shares().equals(Map.of(A, 334L, B, 333L, C, 333L)));
            check("ann is owed by bob and cat", s.balances(A).equals(Map.of(B, 333L, C, 333L)));
            check("bob owes ann", s.balances(B).equals(Map.of(A, -333L)));
            s.addExpense(trip, B, 600, new EqualSplit(List.of(A, B)));
            check("debts in both directions net out", s.balances(A).get(B) == 33L && s.balances(B).get(A) == -33L);
            check("group balances sum to zero",
                    trip.sheet.net().values().stream().mapToLong(Long::longValue).sum() == 0);
        }

        System.out.println("Stage 2: split types");
        {
            ExpenseService s = new ExpenseService();
            Group g = s.createGroup("flat", List.of(A, B, C));
            Expense exact = s.addExpense(g, A, 1000, new ExactSplit(Map.of(A, 200L, B, 300L, C, 500L)));
            check("exact split", exact.shares().get(C) == 500L);
            check("exact split not summing is rejected",
                    throwsIAE(() -> s.addExpense(g, A, 1000, new ExactSplit(Map.of(B, 300L, C, 600L)))));
            Map<User, Integer> pct = new LinkedHashMap<>();
            pct.put(A, 33); pct.put(B, 33); pct.put(C, 34);
            Expense p = s.addExpense(g, B, 1001, new PercentSplit(pct));
            check("last participant absorbs rounding", p.shares().equals(Map.of(A, 330L, B, 330L, C, 341L)));
            Map<User, Integer> bad = new LinkedHashMap<>();
            bad.put(A, 50); bad.put(B, 40);
            int before = g.expenses.size();
            check("percentages not summing to 100 rejected",
                    throwsIAE(() -> s.addExpense(g, A, 100, new PercentSplit(bad))));
            check("rejected split changes nothing", g.expenses.size() == before);
        }

        System.out.println("Stage 3: simplify debts");
        {
            ExpenseService s = new ExpenseService();
            Group g = s.createGroup("house", List.of(A, B, C, D));
            s.addExpense(g, A, 400, new EqualSplit(List.of(A, B, C, D)));  // A +300, B C D -100
            s.addExpense(g, B, 200, new EqualSplit(List.of(B, C)));        // B +100, C -100
            List<Payment> plan = s.settleUp(g);
            check("at most n-1 payments", plan.size() <= 3);
            plan.forEach(p -> s.recordPayment(g, p));
            check("settling clears every balance", g.sheet.net().values().stream().allMatch(v -> v == 0));
            check("largest debtor pays largest creditor first", plan.get(0).equals(new Payment(C, A, 200)));

            ExpenseService s2 = new ExpenseService();
            Group g2 = s2.createGroup("pair", List.of(A, B));
            s2.addExpense(g2, A, 100, new EqualSplit(List.of(A, B)));
            s2.recordPayment(g2, new Payment(B, A, 20));
            check("recorded payment updates balance", s2.balances(A).equals(Map.of(B, 30L)));
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
