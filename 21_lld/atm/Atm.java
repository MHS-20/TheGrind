import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Atm {

    // ---------- model ----------

    record Card(String number) {}

    static final class AtmException extends RuntimeException {
        AtmException(String msg) { super(msg); }
    }

    // ---------- bank ----------

    interface Bank {
        /** False on a wrong PIN; throws if the card is blocked. */
        boolean verifyPin(Card card, String pin);
        long balance(Card card);
        /** Atomic check-and-subtract; false on insufficient funds. */
        boolean debit(Card card, long amount, String txId);
        void credit(Card card, long amount);
        /** Undoes a debit. Idempotent per transaction id. */
        void reverse(String txId);
    }

    static final class InMemoryBank implements Bank {
        static final int MAX_ATTEMPTS = 3;

        private static final class Account {
            final String pin;
            long balance;
            int failedAttempts;

            Account(String pin, long balance) { this.pin = pin; this.balance = balance; }
        }

        private record Debit(Account account, long amount) {}

        private final Map<String, Account> accounts = new ConcurrentHashMap<>();
        private final Map<String, Debit> debits = new ConcurrentHashMap<>();

        void open(Card card, String pin, long balance) { accounts.put(card.number(), new Account(pin, balance)); }

        private Account account(Card card) {
            Account a = accounts.get(card.number());
            if (a == null) throw new AtmException("unknown card");
            return a;
        }

        public boolean verifyPin(Card card, String pin) {
            Account a = account(card);
            synchronized (a) {
                if (a.failedAttempts >= MAX_ATTEMPTS) throw new AtmException("card blocked");
                if (a.pin.equals(pin)) { a.failedAttempts = 0; return true; }
                a.failedAttempts++;
                return false;
            }
        }

        public long balance(Card card) {
            Account a = account(card);
            synchronized (a) { return a.balance; }
        }

        public boolean debit(Card card, long amount, String txId) {
            Account a = account(card);
            synchronized (a) {
                if (a.balance < amount) return false;
                a.balance -= amount;
            }
            debits.put(txId, new Debit(a, amount));
            return true;
        }

        public void credit(Card card, long amount) {
            Account a = account(card);
            synchronized (a) { a.balance += amount; }
        }

        public void reverse(String txId) {
            Debit d = debits.remove(txId); // only the first reversal gets it
            if (d == null) return;
            synchronized (d.account()) { d.account().balance += d.amount(); }
        }
    }

    // ---------- cash ----------

    /** Hardware that physically pushes notes out; may fail. */
    interface NoteSlot {
        void push(Map<Integer, Integer> notes);
    }

    /** One link per note value; tries the most notes it can, then backs off if the rest cannot be paid. */
    static final class NoteHandler {
        final int value;
        final NoteHandler next;

        NoteHandler(int value, NoteHandler next) { this.value = value; this.next = next; }

        Optional<Map<Integer, Integer>> plan(int amount, Map<Integer, Integer> stock) {
            if (amount == 0) return Optional.of(new TreeMap<>());
            int max = Math.min(stock.getOrDefault(value, 0), amount / value);
            for (int n = max; n >= 0; n--) {
                int rest = amount - n * value;
                Optional<Map<Integer, Integer>> tail =
                        rest == 0 ? Optional.of(new TreeMap<>()) : next == null ? Optional.empty() : next.plan(rest, stock);
                if (tail.isPresent()) {
                    if (n > 0) tail.get().put(value, n);
                    return tail;
                }
            }
            return Optional.empty();
        }
    }

    static final class CashDispenser {
        private final Map<Integer, Integer> stock = new HashMap<>();
        private final NoteHandler chain = new NoteHandler(100, new NoteHandler(50, new NoteHandler(20, null)));
        private final NoteSlot slot;

        CashDispenser(NoteSlot slot) { this.slot = slot; }

        synchronized void load(int note, int count) { stock.merge(note, count, Integer::sum); }

        synchronized Optional<Map<Integer, Integer>> plan(int amount) { return chain.plan(amount, stock); }

        /** Takes the notes and pushes them out; on a hardware failure the notes go back. */
        synchronized void dispense(Map<Integer, Integer> plan) {
            for (var e : plan.entrySet()) {
                if (stock.getOrDefault(e.getKey(), 0) < e.getValue()) throw new AtmException("notes ran out");
            }
            plan.forEach((note, n) -> stock.merge(note, -n, Integer::sum));
            try {
                slot.push(plan);
            } catch (RuntimeException e) {
                plan.forEach((note, n) -> stock.merge(note, n, Integer::sum));
                throw new AtmException("dispense failed");
            }
        }

        synchronized int total() {
            return stock.entrySet().stream().mapToInt(e -> e.getKey() * e.getValue()).sum();
        }
    }

    /** Daily withdrawal limit per card; can be shared by several ATMs. */
    static final class DailyLimit {
        private final long limit;
        private final Supplier<LocalDate> today;
        private final Map<String, Long> used = new HashMap<>();

        DailyLimit(long limit, Supplier<LocalDate> today) { this.limit = limit; this.today = today; }

        private String key(Card c) { return c.number() + "@" + today.get(); }

        synchronized boolean tryReserve(Card c, long amount) {
            long u = used.getOrDefault(key(c), 0L);
            if (u + amount > limit) return false;
            used.put(key(c), u + amount);
            return true;
        }

        synchronized void release(Card c, long amount) { used.merge(key(c), -amount, Long::sum); }
    }

    // ---------- session states ----------

    interface AtmState {
        default void insertCard(Atm atm, Card card) { reject("insert a card"); }
        default void enterPin(Atm atm, String pin) { reject("enter a PIN"); }
        default long balance(Atm atm) { return reject("check balance"); }
        default Map<Integer, Integer> withdraw(Atm atm, int amount) { return reject("withdraw"); }
        default void deposit(Atm atm, long amount) { reject("deposit"); }
        default void eject(Atm atm) { reject("eject"); }

        String name();

        private <T> T reject(String action) {
            throw new AtmException("cannot " + action + " while " + name());
        }
    }

    static final class Idle implements AtmState {
        public String name() { return "idle"; }

        public void insertCard(Atm atm, Card card) {
            atm.card = card;
            atm.state = CARD_INSERTED;
        }
    }

    static final class CardInserted implements AtmState {
        public String name() { return "card inserted"; }

        public void enterPin(Atm atm, String pin) {
            try {
                if (!atm.bank.verifyPin(atm.card, pin)) throw new AtmException("wrong PIN");
            } catch (AtmException e) {
                if (e.getMessage().equals("card blocked")) eject(atm);
                throw e;
            }
            atm.state = AUTHENTICATED;
        }

        public void eject(Atm atm) {
            atm.card = null;
            atm.state = IDLE;
        }
    }

    static final class Authenticated implements AtmState {
        public String name() { return "authenticated"; }

        public long balance(Atm atm) { return atm.bank.balance(atm.card); }

        /** Plan the notes, reserve the limit, debit, then dispense; undo in reverse order on failure. */
        public Map<Integer, Integer> withdraw(Atm atm, int amount) {
            Map<Integer, Integer> notes = atm.dispenser.plan(amount)
                    .orElseThrow(() -> new AtmException("cannot pay " + amount + " with available notes"));
            if (!atm.limit.tryReserve(atm.card, amount)) throw new AtmException("daily limit exceeded");
            String txId = UUID.randomUUID().toString();
            if (!atm.bank.debit(atm.card, amount, txId)) {
                atm.limit.release(atm.card, amount);
                throw new AtmException("insufficient funds");
            }
            try {
                atm.dispenser.dispense(notes);
            } catch (AtmException e) {
                atm.bank.reverse(txId);
                atm.limit.release(atm.card, amount);
                throw e;
            }
            return notes;
        }

        public void deposit(Atm atm, long amount) { atm.bank.credit(atm.card, amount); }

        public void eject(Atm atm) {
            atm.card = null;
            atm.state = IDLE;
        }
    }

    static final AtmState IDLE = new Idle(), CARD_INSERTED = new CardInserted(), AUTHENTICATED = new Authenticated();

    // ---------- facade ----------

    private final Bank bank;
    private final CashDispenser dispenser;
    private final DailyLimit limit;
    private AtmState state = IDLE;
    private Card card;

    Atm(Bank bank, CashDispenser dispenser, DailyLimit limit) {
        this.bank = bank;
        this.dispenser = dispenser;
        this.limit = limit;
    }

    synchronized void insertCard(Card c) { state.insertCard(this, c); }

    synchronized void enterPin(String pin) { state.enterPin(this, pin); }

    synchronized long balance() { return state.balance(this); }

    synchronized Map<Integer, Integer> withdraw(int amount) { return state.withdraw(this, amount); }

    synchronized void deposit(long amount) { state.deposit(this, amount); }

    synchronized void eject() { state.eject(this); }

    synchronized String stateName() { return state.name(); }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    private static boolean fails(Runnable r) {
        try { r.run(); return false; } catch (AtmException e) { return true; }
    }

    static final Card CARD = new Card("4111");
    static final LocalDate DAY = LocalDate.of(2026, 11, 27);

    static CashDispenser dispenser(int hundreds, int fifties, int twenties) {
        CashDispenser d = new CashDispenser(notes -> {});
        d.load(100, hundreds);
        d.load(50, fifties);
        d.load(20, twenties);
        return d;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Stage 1: core model");
        {
            InMemoryBank bank = new InMemoryBank();
            bank.open(CARD, "1234", 500);
            Atm atm = new Atm(bank, dispenser(10, 10, 10), new DailyLimit(10_000, () -> DAY));
            check("withdraw before PIN fails", fails(() -> atm.withdraw(100)));
            atm.insertCard(CARD);
            check("wrong PIN fails", fails(() -> atm.enterPin("0000")));
            atm.enterPin("1234");
            check("balance shown", atm.balance() == 500);
            atm.withdraw(200);
            atm.deposit(50);
            check("withdraw and deposit update balance", atm.balance() == 350);
            check("insufficient funds fails", fails(() -> atm.withdraw(400)));
            atm.eject();
            check("eject returns to idle", atm.stateName().equals("idle"));

            atm.insertCard(CARD);
            for (int i = 0; i < 3; i++) fails(() -> atm.enterPin("9999"));
            check("fourth try on a blocked card fails, even with the right PIN", fails(() -> atm.enterPin("1234")));
            check("blocked card is ejected", atm.stateName().equals("idle"));
        }

        System.out.println("Stage 2: cash dispensing");
        {
            InMemoryBank bank = new InMemoryBank();
            bank.open(CARD, "1234", 5_000);
            Atm atm = new Atm(bank, dispenser(1, 1, 5), new DailyLimit(500, () -> DAY));
            atm.insertCard(CARD);
            atm.enterPin("1234");
            // 110: greedy takes 100 and is stuck; 50 + 3x20 works
            check("falls back when greedy fails", atm.withdraw(110).equals(Map.of(50, 1, 20, 3)));
            check("amount the notes cannot make is refused", fails(() -> atm.withdraw(30)));
            check("refused withdrawal not debited", atm.balance() == 5_000 - 110);
            atm.withdraw(100);
            check("daily limit enforced", fails(() -> atm.withdraw(300)) && atm.balance() == 5_000 - 210);
        }

        System.out.println("Stage 3: consistency");
        {
            InMemoryBank bank = new InMemoryBank();
            bank.open(CARD, "1234", 1_000);
            CashDispenser jammed = new CashDispenser(notes -> { throw new RuntimeException("jam"); });
            jammed.load(100, 5);
            Atm atm = new Atm(bank, jammed, new DailyLimit(10_000, () -> DAY));
            atm.insertCard(CARD);
            atm.enterPin("1234");
            check("dispense failure is reported", fails(() -> atm.withdraw(200)));
            check("debit reversed after failed dispense", atm.balance() == 1_000);
            check("notes stay in the machine", jammed.total() == 500);

            InMemoryBank shared = new InMemoryBank();
            shared.open(CARD, "1234", 1_000);
            DailyLimit limit = new DailyLimit(1_000_000, () -> DAY);
            Atm a1 = new Atm(shared, dispenser(0, 0, 1_000), limit);
            Atm a2 = new Atm(shared, dispenser(0, 0, 1_000), limit);
            for (Atm a : new Atm[] {a1, a2}) { a.insertCard(CARD); a.enterPin("1234"); }
            AtomicInteger ok = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch go = new CountDownLatch(1);
            for (int i = 0; i < 200; i++) {
                Atm a = i % 2 == 0 ? a1 : a2;
                pool.submit(() -> {
                    go.await();
                    if (!fails(() -> a.withdraw(20))) ok.incrementAndGet();
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            check("two ATMs never overdraw", shared.balance(CARD) == 0 && ok.get() == 50);
            check("reversal is idempotent", idempotentReverse());
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }

    static boolean idempotentReverse() {
        InMemoryBank b = new InMemoryBank();
        b.open(CARD, "1234", 100);
        b.debit(CARD, 60, "tx1");
        b.reverse("tx1");
        b.reverse("tx1");
        return b.balance(CARD) == 100;
    }
}
