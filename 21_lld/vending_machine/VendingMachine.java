import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VendingMachine {

    // ---------- model ----------

    enum Coin {
        NICKEL(5), DIME(10), QUARTER(25), DOLLAR(100);

        final int cents;

        Coin(int cents) { this.cents = cents; }
    }

    record Sale(String product, Map<Coin, Integer> change) {
        int changeTotal() {
            return change.entrySet().stream().mapToInt(e -> e.getKey().cents * e.getValue()).sum();
        }

        int coinCount() { return change.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    static final class VendingException extends RuntimeException {
        VendingException(String msg) { super(msg); }
    }

    static final class Slot {
        final String code;
        final String product;
        final int price;
        int quantity;

        Slot(String code, String product, int price, int quantity) {
            this.code = code;
            this.product = product;
            this.price = price;
            this.quantity = quantity;
        }
    }

    static final class Inventory {
        private final Map<String, Slot> slots = new HashMap<>();

        void add(Slot s) { slots.put(s.code, s); }

        Slot get(String code) {
            Slot s = slots.get(code);
            if (s == null) throw new VendingException("unknown code " + code);
            return s;
        }

        void refill(String code, int quantity) { get(code).quantity += quantity; }
    }

    /** Finite stock of each coin. Change uses as few coins as possible, or fails. */
    static final class CoinBox {
        private final EnumMap<Coin, Integer> counts = new EnumMap<>(Coin.class);

        CoinBox() { for (Coin c : Coin.values()) counts.put(c, 0); }

        void add(Coin c, int n) { counts.merge(c, n, Integer::sum); }

        void addAll(List<Coin> coins) { coins.forEach(c -> add(c, 1)); }

        void removeAll(List<Coin> coins) { coins.forEach(c -> add(c, -1)); }

        int count(Coin c) { return counts.get(c); }

        /** Plans change without taking it. Exhaustive search over counts: greedy can get stuck. */
        Optional<Map<Coin, Integer>> planChange(int amount) {
            Coin[] coins = Coin.values();
            int[] use = new int[coins.length];
            int[] best = search(coins, coins.length - 1, amount, use, null);
            if (best == null) return Optional.empty();
            Map<Coin, Integer> plan = new EnumMap<>(Coin.class);
            for (int i = 0; i < coins.length; i++) if (best[i] > 0) plan.put(coins[i], best[i]);
            return Optional.of(plan);
        }

        private int[] search(Coin[] coins, int i, int left, int[] use, int[] best) {
            if (left == 0) return best == null || sum(use) < sum(best) ? use.clone() : best;
            if (i < 0) return best;
            int max = Math.min(counts.get(coins[i]), left / coins[i].cents);
            for (int n = max; n >= 0; n--) {
                use[i] = n;
                best = search(coins, i - 1, left - n * coins[i].cents, use, best);
            }
            use[i] = 0;
            return best;
        }

        private static int sum(int[] a) { int s = 0; for (int x : a) s += x; return s; }

        void take(Map<Coin, Integer> plan) { plan.forEach((c, n) -> add(c, -n)); }
    }

    // ---------- states ----------

    /** Every action is rejected unless a state allows it. */
    interface VendingState {
        default void insert(VendingMachine m, Coin c) { reject("insert"); }
        default Sale select(VendingMachine m, String code) { return reject("select"); }
        default List<Coin> cancel(VendingMachine m) { return reject("cancel"); }
        default void enterMaintenance(VendingMachine m) { reject("enter maintenance"); }
        default void exitMaintenance(VendingMachine m) { reject("exit maintenance"); }
        default void refill(VendingMachine m, Runnable action) { reject("refill"); }

        String name();

        private <T> T reject(String action) {
            throw new VendingException("cannot " + action + " while " + name());
        }
    }

    static final class Idle implements VendingState {
        public String name() { return "idle"; }

        public void insert(VendingMachine m, Coin c) {
            m.inserted.add(c);
            m.state = HAS_MONEY;
        }

        public List<Coin> cancel(VendingMachine m) { return List.of(); }

        public void enterMaintenance(VendingMachine m) { m.state = OUT_OF_SERVICE; }
    }

    static final class HasMoney implements VendingState {
        public String name() { return "has money"; }

        public void insert(VendingMachine m, Coin c) { m.inserted.add(c); }

        public Sale select(VendingMachine m, String code) {
            Slot slot = m.inventory.get(code);
            if (slot.quantity == 0) throw new VendingException("slot " + code + " is empty");
            int credit = m.credit();
            if (credit < slot.price) throw new VendingException("insufficient credit");

            // inserted coins can be part of the change
            m.coinBox.addAll(m.inserted);
            Optional<Map<Coin, Integer>> change = m.coinBox.planChange(credit - slot.price);
            if (change.isEmpty()) {
                m.coinBox.removeAll(m.inserted);
                throw new VendingException("cannot make exact change");
            }
            m.state = DISPENSING;
            m.coinBox.take(change.get());
            slot.quantity--;
            m.inserted.clear();
            m.state = IDLE;
            return new Sale(slot.product, change.get());
        }

        public List<Coin> cancel(VendingMachine m) {
            List<Coin> back = List.copyOf(m.inserted);
            m.inserted.clear();
            m.state = IDLE;
            return back;
        }
    }

    /** Transient while a product drops; every action is rejected. */
    static final class Dispensing implements VendingState {
        public String name() { return "dispensing"; }
    }

    static final class OutOfService implements VendingState {
        public String name() { return "out of service"; }

        public void refill(VendingMachine m, Runnable action) { action.run(); }

        public void exitMaintenance(VendingMachine m) { m.state = IDLE; }
    }

    static final VendingState IDLE = new Idle(), HAS_MONEY = new HasMoney(),
            DISPENSING = new Dispensing(), OUT_OF_SERVICE = new OutOfService();

    // ---------- facade ----------

    private final Inventory inventory = new Inventory();
    private final CoinBox coinBox = new CoinBox();
    private final List<Coin> inserted = new ArrayList<>();
    private VendingState state = IDLE;

    synchronized void insert(Coin c) { state.insert(this, c); }

    synchronized Sale select(String code) { return state.select(this, code); }

    synchronized List<Coin> cancel() { return state.cancel(this); }

    synchronized void enterMaintenance() { state.enterMaintenance(this); }

    synchronized void exitMaintenance() { state.exitMaintenance(this); }

    synchronized void addSlot(String code, String product, int price, int quantity) {
        state.refill(this, () -> inventory.add(new Slot(code, product, price, quantity)));
    }

    synchronized void refillProduct(String code, int quantity) {
        state.refill(this, () -> inventory.refill(code, quantity));
    }

    synchronized void refillCoins(Coin c, int n) { state.refill(this, () -> coinBox.add(c, n)); }

    synchronized int credit() { return inserted.stream().mapToInt(c -> c.cents).sum(); }

    synchronized String stateName() { return state.name(); }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    private static boolean fails(Runnable r) {
        try { r.run(); return false; } catch (VendingException e) { return true; }
    }

    static VendingMachine stocked() {
        VendingMachine m = new VendingMachine();
        m.enterMaintenance();
        m.addSlot("A1", "Chips", 65, 2);
        m.addSlot("A2", "Water", 50, 0);
        for (Coin c : Coin.values()) m.refillCoins(c, 10);
        m.exitMaintenance();
        return m;
    }

    public static void main(String[] args) {
        System.out.println("Stage 1: core model");
        {
            VendingMachine m = stocked();
            m.insert(Coin.DOLLAR);
            Sale s = m.select("A1");
            check("sells the product", s.product().equals("Chips"));
            check("returns 35 in change", s.changeTotal() == 35);
            m.insert(Coin.QUARTER);
            check("too little credit fails", fails(() -> m.select("A1")));
            check("credit kept after failure", m.credit() == 25);
            check("unknown code fails", fails(() -> m.select("Z9")));
            m.insert(Coin.QUARTER);
            check("empty slot fails", fails(() -> m.select("A2")));
            check("cancel returns the credit", m.cancel().equals(List.of(Coin.QUARTER, Coin.QUARTER)) && m.credit() == 0);
        }

        System.out.println("Stage 2: states and change");
        {
            VendingMachine m = stocked();
            check("starts idle", m.stateName().equals("idle"));
            check("select while idle fails", fails(() -> m.select("A1")));
            m.insert(Coin.DOLLAR);
            check("coin moves to has money", m.stateName().equals("has money"));
            Sale s = m.select("A1");
            check("change uses fewest coins (25 + 10)", s.coinCount() == 2);
            check("back to idle after sale", m.stateName().equals("idle"));

            // owe 30 with one 25 and three 10s: greedy fails, search succeeds
            VendingMachine g = new VendingMachine();
            g.enterMaintenance();
            g.addSlot("B1", "Gum", 70, 5);
            g.refillCoins(Coin.QUARTER, 1);
            g.refillCoins(Coin.DIME, 3);
            g.exitMaintenance();
            g.insert(Coin.DOLLAR);
            Sale gum = g.select("B1");
            check("makes 30 as three dimes when greedy gets stuck",
                    gum.change().equals(Map.of(Coin.DIME, 3)));

            VendingMachine empty = new VendingMachine();
            empty.enterMaintenance();
            empty.addSlot("C1", "Mint", 60, 5);
            empty.exitMaintenance();
            empty.insert(Coin.DOLLAR);
            check("no exact change refuses sale", fails(() -> empty.select("C1")));
            check("credit stays after refused sale", empty.credit() == 100);
            check("product not consumed", empty.inventory.get("C1").quantity == 5);
            check("dispensing state rejects coins",
                    fails(() -> DISPENSING.insert(empty, Coin.DIME)));
        }

        System.out.println("Stage 3: restocking");
        {
            VendingMachine m = stocked();
            check("refill fails while in service", fails(() -> m.refillProduct("A2", 3)));
            m.insert(Coin.DIME);
            check("cannot enter maintenance with credit", fails(m::enterMaintenance));
            m.cancel();
            m.enterMaintenance();
            check("user actions fail while out of service", fails(() -> m.insert(Coin.DIME)));
            m.refillProduct("A2", 3);
            m.exitMaintenance();
            m.insert(Coin.QUARTER);
            m.insert(Coin.QUARTER);
            check("refilled slot sells", m.select("A2").product().equals("Water"));
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
