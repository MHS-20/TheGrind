import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ParkingLot {

    // ---------- model ----------

    enum SpotSize {
        SMALL(10), MEDIUM(20), LARGE(50);

        final long hourlyRate;

        SpotSize(long hourlyRate) { this.hourlyRate = hourlyRate; }
    }

    enum VehicleType {
        MOTORCYCLE(SpotSize.SMALL), CAR(SpotSize.MEDIUM), TRUCK(SpotSize.LARGE);

        final SpotSize minSize;

        VehicleType(SpotSize minSize) { this.minSize = minSize; }
    }

    record Vehicle(String plate, VehicleType type, boolean electric) {
        Vehicle(String plate, VehicleType type) { this(plate, type, false); }
    }

    record Ticket(long id, Vehicle vehicle, Spot spot, LocalDateTime entry) {}

    static final class Spot {
        final int floor;
        final SpotSize size;
        final boolean charger;
        private final AtomicReference<Vehicle> occupant = new AtomicReference<>();

        Spot(int floor, SpotSize size, boolean charger) {
            this.floor = floor;
            this.size = size;
            this.charger = charger;
        }

        boolean canFit(Vehicle v) { return size.compareTo(v.type().minSize) >= 0; }

        boolean isFree() { return occupant.get() == null; }

        /** Atomic claim: only one caller wins a free spot. */
        boolean tryOccupy(Vehicle v) { return occupant.compareAndSet(null, v); }

        void release() { occupant.set(null); }
    }

    static final class Floor {
        final List<Spot> spots = new ArrayList<>();

        Floor(int number, int small, int medium, int large, int chargingMedium) {
            add(number, SpotSize.SMALL, small, false);
            add(number, SpotSize.MEDIUM, medium, false);
            add(number, SpotSize.LARGE, large, false);
            add(number, SpotSize.MEDIUM, chargingMedium, true);
        }

        private void add(int number, SpotSize size, int count, boolean charger) {
            for (int i = 0; i < count; i++) spots.add(new Spot(number, size, charger));
        }

        List<Spot> freeSpotsFor(Vehicle v) {
            return spots.stream().filter(s -> s.isFree() && s.canFit(v)).toList();
        }
    }

    // ---------- spot selection ----------

    interface SpotSelector {
        Comparator<Spot> preference(Vehicle v);
    }

    /** Lowest floor first, then the smallest spot that fits. */
    static final class NearestSmallestSelector implements SpotSelector {
        public Comparator<Spot> preference(Vehicle v) {
            return Comparator.<Spot>comparingInt(s -> s.floor).thenComparing(s -> s.size);
        }
    }

    /** Electric vehicles get a charging spot when one fits; others fall back to the base order. */
    static final class ChargerFirstSelector implements SpotSelector {
        private final SpotSelector base;

        ChargerFirstSelector(SpotSelector base) { this.base = base; }

        public Comparator<Spot> preference(Vehicle v) {
            Comparator<Spot> chargerFirst = Comparator.comparing(s -> !(v.electric() && s.charger));
            return chargerFirst.thenComparing(base.preference(v));
        }
    }

    // ---------- pricing ----------

    interface PricingStrategy {
        long fee(Ticket t, LocalDateTime exit);
    }

    static final class HourlyPricing implements PricingStrategy {
        public long fee(Ticket t, LocalDateTime exit) {
            long minutes = Duration.between(t.entry(), exit).toMinutes();
            long hours = Math.max(1, (minutes + 59) / 60);
            return hours * t.spot().size.hourlyRate;
        }
    }

    static final class WeekendPricing implements PricingStrategy {
        private final PricingStrategy inner;

        WeekendPricing(PricingStrategy inner) { this.inner = inner; }

        public long fee(Ticket t, LocalDateTime exit) {
            long fee = inner.fee(t, exit);
            DayOfWeek d = exit.getDayOfWeek();
            return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY ? fee / 2 : fee;
        }
    }

    static final class ChargingSurcharge implements PricingStrategy {
        static final long SURCHARGE = 30;
        private final PricingStrategy inner;

        ChargingSurcharge(PricingStrategy inner) { this.inner = inner; }

        public long fee(Ticket t, LocalDateTime exit) {
            return inner.fee(t, exit) + (t.spot().charger ? SURCHARGE : 0);
        }
    }

    // ---------- facade ----------

    private final List<Floor> floors;
    private final SpotSelector selector;
    private final PricingStrategy pricing;
    private final Map<Long, Ticket> active = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    ParkingLot(List<Floor> floors, SpotSelector selector, PricingStrategy pricing) {
        this.floors = List.copyOf(floors);
        this.selector = selector;
        this.pricing = pricing;
    }

    Optional<Ticket> park(Vehicle v, LocalDateTime time) {
        List<Spot> candidates = floors.stream()
                .flatMap(f -> f.freeSpotsFor(v).stream())
                .sorted(selector.preference(v))
                .toList();
        for (Spot s : candidates) {
            if (s.tryOccupy(v)) { // another gate may have taken it since the scan
                Ticket t = new Ticket(nextId.getAndIncrement(), v, s, time);
                active.put(t.id(), t);
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    long unpark(Ticket ticket, LocalDateTime time) {
        Ticket t = active.remove(ticket.id()); // only one caller gets it back
        if (t == null) throw new IllegalArgumentException("unknown or used ticket " + ticket.id());
        t.spot().release();
        return pricing.fee(t, time);
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

    // Monday and Saturday
    static final LocalDateTime MON = LocalDateTime.of(2026, 11, 23, 9, 0);
    static final LocalDateTime SAT = LocalDateTime.of(2026, 11, 28, 9, 0);

    static ParkingLot basic(List<Floor> floors) {
        return new ParkingLot(floors, new NearestSmallestSelector(), new HourlyPricing());
    }

    static ParkingLot full(List<Floor> floors) {
        return new ParkingLot(floors, new ChargerFirstSelector(new NearestSmallestSelector()),
                new ChargingSurcharge(new WeekendPricing(new HourlyPricing())));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Stage 1: core model");
        {
            ParkingLot lot = basic(List.of(new Floor(0, 1, 1, 0, 0), new Floor(1, 0, 0, 1, 0)));
            Ticket moto = lot.park(new Vehicle("M1", VehicleType.MOTORCYCLE), MON).orElseThrow();
            check("motorcycle gets smallest spot", moto.spot().size == SpotSize.SMALL);
            Ticket car = lot.park(new Vehicle("C1", VehicleType.CAR), MON).orElseThrow();
            check("car gets medium on floor 0", car.spot().size == SpotSize.MEDIUM && car.spot().floor == 0);
            Ticket truck = lot.park(new Vehicle("T1", VehicleType.TRUCK), MON).orElseThrow();
            check("truck goes to large on floor 1", truck.spot().floor == 1);
            check("lot full for another car", lot.park(new Vehicle("C2", VehicleType.CAR), MON).isEmpty());
            check("1h59m on medium costs 40", lot.unpark(car, MON.plusMinutes(119)) == 40);
            check("under an hour charges one hour", lot.unpark(moto, MON.plusMinutes(5)) == 10);
            check("used ticket fails", throwsIAE(() -> lot.unpark(car, MON.plusHours(3))));
            check("freed spot is reused", lot.park(new Vehicle("C2", VehicleType.CAR), MON).isPresent());
        }

        System.out.println("Stage 2: EV charging and weekend pricing");
        {
            ParkingLot lot = full(List.of(new Floor(0, 0, 1, 0, 0), new Floor(1, 0, 0, 0, 1)));
            Ticket ev = lot.park(new Vehicle("E1", VehicleType.CAR, true), MON).orElseThrow();
            check("EV gets charging spot even on a higher floor", ev.spot().charger);
            Ticket ev2 = lot.park(new Vehicle("E2", VehicleType.CAR, true), SAT).orElseThrow();
            check("EV falls back to a normal spot", !ev2.spot().charger);
            check("charging spot adds 30", lot.unpark(ev, MON.plusHours(2)) == 2 * 20 + 30);
            Ticket car = lot.park(new Vehicle("C1", VehicleType.CAR), SAT).orElseThrow();
            check("non-EV may use a charging spot when nothing else is free", car.spot().charger);
            check("weekend exit is half price", lot.unpark(ev2, SAT.plusHours(2)) == 20);
            check("weekend halves the rate but not the surcharge", lot.unpark(car, SAT.plusHours(2)) == 20 + 30);
        }

        System.out.println("Stage 3: concurrency");
        {
            int spots = 50, gates = 200;
            ParkingLot lot = basic(List.of(new Floor(0, 0, spots, 0, 0)));
            ExecutorService pool = Executors.newFixedThreadPool(16);
            CountDownLatch start = new CountDownLatch(1);
            Map<Spot, Ticket> claimed = new ConcurrentHashMap<>();
            AtomicInteger doubleBooked = new AtomicInteger();
            List<Ticket> tickets = new java.util.concurrent.CopyOnWriteArrayList<>();
            for (int i = 0; i < gates; i++) {
                String plate = "C" + i;
                pool.submit(() -> {
                    start.await();
                    lot.park(new Vehicle(plate, VehicleType.CAR), MON).ifPresent(t -> {
                        if (claimed.putIfAbsent(t.spot(), t) != null) doubleBooked.incrementAndGet();
                        tickets.add(t);
                    });
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
            check("exactly " + spots + " cars parked", tickets.size() == spots);
            check("no spot given twice", doubleBooked.get() == 0);

            Ticket t = tickets.get(0);
            AtomicInteger paid = new AtomicInteger();
            ExecutorService payers = Executors.newFixedThreadPool(8);
            CountDownLatch go = new CountDownLatch(1);
            for (int i = 0; i < 8; i++) {
                payers.submit(() -> {
                    go.await();
                    try { lot.unpark(t, MON.plusHours(1)); paid.incrementAndGet(); }
                    catch (IllegalArgumentException ignored) {}
                    return null;
                });
            }
            go.countDown();
            payers.shutdown();
            payers.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
            check("ticket paid exactly once", paid.get() == 1);
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
