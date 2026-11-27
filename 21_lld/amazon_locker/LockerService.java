import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class LockerService {

    // ---------- model ----------

    enum Size { SMALL, MEDIUM, LARGE }

    record Package(String orderId, Size size) {}

    record Position(int x, int y) {
        double distanceTo(Position o) { return Math.hypot(x - o.x, y - o.y); }
    }

    enum SlotStatus {
        FREE, RESERVED, OCCUPIED, TO_RETURN;

        /** The only legal moves of a slot's lifecycle. */
        boolean canMoveTo(SlotStatus next) {
            return switch (this) {
                case FREE -> next == RESERVED;
                case RESERVED -> next == OCCUPIED || next == FREE;
                case OCCUPIED -> next == FREE || next == TO_RETURN;
                case TO_RETURN -> next == FREE;
            };
        }
    }

    static final class LockerException extends RuntimeException {
        LockerException(String msg) { super(msg); }
    }

    static final class Slot {
        final String id;
        final Size size;
        private final AtomicReference<SlotStatus> status = new AtomicReference<>(SlotStatus.FREE);
        volatile Package pkg;

        Slot(String id, Size size) { this.id = id; this.size = size; }

        boolean fits(Package p) { return size.compareTo(p.size()) >= 0; }

        SlotStatus status() { return status.get(); }

        /** Atomic, checked transition: false if another thread moved the slot first. */
        boolean move(SlotStatus from, SlotStatus to) {
            if (!from.canMoveTo(to)) throw new IllegalStateException(from + " -> " + to);
            return status.compareAndSet(from, to);
        }
    }

    static final class LockerLocation {
        final String id;
        final Position position;
        final List<Slot> slots = new ArrayList<>();

        LockerLocation(String id, Position position, int small, int medium, int large) {
            this.id = id;
            this.position = position;
            add(Size.SMALL, small);
            add(Size.MEDIUM, medium);
            add(Size.LARGE, large);
        }

        private void add(Size size, int n) {
            for (int i = 0; i < n; i++) slots.add(new Slot(id + "-" + size.name().charAt(0) + i, size));
        }

        /** Reserves the smallest free slot that fits, or nothing. */
        Optional<Slot> reserve(Package p) {
            return slots.stream()
                    .filter(s -> s.fits(p) && s.status() == SlotStatus.FREE)
                    .sorted(Comparator.comparing(s -> s.size))
                    .filter(s -> s.move(SlotStatus.FREE, SlotStatus.RESERVED))
                    .findFirst();
        }
    }

    record PickupCode(String code, Slot slot, LocalDateTime expiry) {}

    interface LocationSelector {
        List<LockerLocation> order(List<LockerLocation> locations, Position address);
    }

    static final class NearestFirst implements LocationSelector {
        public List<LockerLocation> order(List<LockerLocation> locations, Position address) {
            return locations.stream().sorted(Comparator.comparingDouble(l -> l.position.distanceTo(address))).toList();
        }
    }

    // ---------- facade ----------

    static final Duration PICKUP_WINDOW = Duration.ofDays(3);

    private final List<LockerLocation> locations;
    private final LocationSelector selector;
    private final Supplier<String> codes;
    private final Supplier<LocalDateTime> clock;
    private final Map<String, PickupCode> live = new ConcurrentHashMap<>();

    LockerService(List<LockerLocation> locations, LocationSelector selector,
                  Supplier<String> codes, Supplier<LocalDateTime> clock) {
        this.locations = List.copyOf(locations);
        this.selector = selector;
        this.codes = codes;
        this.clock = clock;
    }

    Slot assign(Package p, Position address) {
        for (LockerLocation loc : selector.order(locations, address)) {
            Optional<Slot> s = loc.reserve(p);
            if (s.isPresent()) return s.get();
        }
        throw new LockerException("no slot fits " + p.orderId());
    }

    /** Puts the package in its reserved slot and returns the customer's code. */
    String deposit(Slot slot, Package p) {
        if (!slot.move(SlotStatus.RESERVED, SlotStatus.OCCUPIED)) throw new LockerException("slot not reserved");
        slot.pkg = p;
        while (true) { // unique among live codes
            String code = codes.get();
            PickupCode pc = new PickupCode(code, slot, clock.get().plus(PICKUP_WINDOW));
            if (live.putIfAbsent(code, pc) == null) return code;
        }
    }

    /** Opens the slot once. A wrong, used or expired code fails. */
    Package pickup(String code) {
        PickupCode pc = live.remove(code); // only one caller gets it
        if (pc == null) throw new LockerException("invalid code");
        if (!clock.get().isBefore(pc.expiry())) {
            pc.slot().move(SlotStatus.OCCUPIED, SlotStatus.TO_RETURN);
            throw new LockerException("code expired");
        }
        Package p = pc.slot().pkg;
        pc.slot().pkg = null;
        pc.slot().move(SlotStatus.OCCUPIED, SlotStatus.FREE);
        return p;
    }

    /** Sweep: marks slots with uncollected packages for return. */
    List<Slot> expire() {
        LocalDateTime now = clock.get();
        List<Slot> marked = new ArrayList<>();
        for (PickupCode pc : live.values()) {
            if (!now.isBefore(pc.expiry()) && live.remove(pc.code(), pc)
                    && pc.slot().move(SlotStatus.OCCUPIED, SlotStatus.TO_RETURN)) {
                marked.add(pc.slot());
            }
        }
        return marked;
    }

    /** The courier takes the package back and the slot is free again. */
    Package collectReturn(Slot slot) {
        Package p = slot.pkg;
        if (!slot.move(SlotStatus.TO_RETURN, SlotStatus.FREE)) throw new LockerException("nothing to return");
        slot.pkg = null;
        return p;
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    private static boolean fails(Runnable r) {
        try { r.run(); return false; } catch (LockerException e) { return true; }
    }

    static final class TestClock implements Supplier<LocalDateTime> {
        LocalDateTime now = LocalDateTime.of(2026, 11, 27, 9, 0);

        public LocalDateTime get() { return now; }
    }

    static Supplier<String> sequence(String... values) {
        AtomicInteger i = new AtomicInteger();
        return () -> values[i.getAndIncrement() % values.length];
    }

    static Supplier<String> random() {
        Random r = new Random(42);
        return () -> String.format("%06d", r.nextInt(1_000_000));
    }

    static final Position HOME = new Position(0, 0);

    public static void main(String[] args) throws Exception {
        System.out.println("Stage 1: core model");
        {
            LockerLocation loc = new LockerLocation("L1", new Position(1, 1), 1, 1, 0);
            LockerService svc = new LockerService(List.of(loc), new NearestFirst(), sequence("111111", "222222"), new TestClock());
            Package small = new Package("o1", Size.SMALL), medium = new Package("o2", Size.MEDIUM);
            Slot s1 = svc.assign(small, HOME);
            check("smallest fitting slot reserved", s1.size == Size.SMALL && s1.status() == SlotStatus.RESERVED);
            check("large package with no large slot fails", fails(() -> svc.assign(new Package("o3", Size.LARGE), HOME)));
            String code = svc.deposit(s1, small);
            check("code is 6 digits", code.matches("\\d{6}"));
            check("wrong code fails", fails(() -> svc.pickup("000000")));
            check("pickup returns the package", svc.pickup(code).equals(small));
            check("slot freed after pickup", s1.status() == SlotStatus.FREE);
            Slot s2 = svc.assign(medium, HOME);
            check("medium goes to medium slot", s2.size == Size.MEDIUM);
        }

        System.out.println("Stage 2: expiry and returns");
        {
            TestClock clock = new TestClock();
            LockerLocation far = new LockerLocation("FAR", new Position(50, 50), 2, 0, 0);
            LockerLocation near = new LockerLocation("NEAR", new Position(2, 0), 1, 0, 0);
            LockerService svc = new LockerService(List.of(far, near), new NearestFirst(), random(), clock);
            Package a = new Package("a", Size.SMALL), b = new Package("b", Size.SMALL);
            Slot sa = svc.assign(a, HOME);
            check("nearest location chosen", sa.id.startsWith("NEAR"));
            Slot sb = svc.assign(b, HOME);
            check("falls back to the next nearest when full", sb.id.startsWith("FAR"));
            String ca = svc.deposit(sa, a), cb = svc.deposit(sb, b);
            svc.pickup(ca);
            check("code works only once", fails(() -> svc.pickup(ca)));
            clock.now = clock.now.plusDays(3);
            check("expired code fails", fails(() -> svc.pickup(cb)));
            check("expired slot marked for return", sb.status() == SlotStatus.TO_RETURN);
            check("courier collects the package", svc.collectReturn(sb).equals(b) && sb.status() == SlotStatus.FREE);

            Slot sc = svc.assign(new Package("c", Size.SMALL), HOME);
            svc.deposit(sc, new Package("c", Size.SMALL));
            clock.now = clock.now.plusDays(4);
            check("sweep marks uncollected packages", svc.expire().equals(List.of(sc)));

            LockerService dup = new LockerService(List.of(new LockerLocation("D", HOME, 2, 0, 0)),
                    new NearestFirst(), sequence("123456", "123456", "654321"), new TestClock());
            Package d1 = new Package("d1", Size.SMALL), d2 = new Package("d2", Size.SMALL);
            String c1 = dup.deposit(dup.assign(d1, HOME), d1), c2 = dup.deposit(dup.assign(d2, HOME), d2);
            check("colliding code is regenerated", !c1.equals(c2));
        }

        System.out.println("Stage 3: concurrency");
        {
            LockerLocation loc = new LockerLocation("L", HOME, 30, 0, 0);
            LockerService svc = new LockerService(List.of(loc), new NearestFirst(), random(), new TestClock());
            Set<Slot> taken = ConcurrentHashMap.newKeySet();
            AtomicInteger doubled = new AtomicInteger(), assigned = new AtomicInteger();
            List<String> issued = new java.util.concurrent.CopyOnWriteArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(16);
            CountDownLatch go = new CountDownLatch(1);
            for (int i = 0; i < 100; i++) {
                Package p = new Package("p" + i, Size.SMALL);
                pool.submit(() -> {
                    go.await();
                    try {
                        Slot s = svc.assign(p, HOME);
                        if (!taken.add(s)) doubled.incrementAndGet();
                        assigned.incrementAndGet();
                        issued.add(svc.deposit(s, p));
                    } catch (LockerException ignored) {}
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            check("exactly 30 packages placed", assigned.get() == 30);
            check("no slot assigned twice", doubled.get() == 0);

            String code = issued.get(0);
            AtomicInteger opened = new AtomicInteger();
            ExecutorService pickers = Executors.newFixedThreadPool(8);
            CountDownLatch go2 = new CountDownLatch(1);
            for (int i = 0; i < 8; i++) {
                pickers.submit(() -> {
                    go2.await();
                    if (!fails(() -> svc.pickup(code))) opened.incrementAndGet();
                    return null;
                });
            }
            go2.countDown();
            pickers.shutdown();
            pickers.awaitTermination(10, TimeUnit.SECONDS);
            check("a code opens its slot once", opened.get() == 1);
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
