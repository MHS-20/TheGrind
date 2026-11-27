import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class BookingService {

    // ---------- catalogue ----------

    enum SeatCategory {
        REGULAR(100), PREMIUM(150), RECLINER(250);

        final long basePrice;

        SeatCategory(long basePrice) { this.basePrice = basePrice; }
    }

    record Movie(String title) {}

    record City(String name) {}

    record Seat(char row, int number, SeatCategory category) {}

    record Screen(String id, List<Seat> seats) {}

    record Theatre(String name, City city, List<Screen> screens) {}

    record Hold(long id, Show show, Set<Seat> seats, String user, LocalDateTime expiry) {}

    record Booking(long id, Show show, Set<Seat> seats, String user, long amount) {}

    static final class BookingException extends RuntimeException {
        BookingException(String msg) { super(msg); }
    }

    // ---------- seat state of one show ----------

    sealed interface SeatState permits Held, Booked {}

    record Held(Hold hold) implements SeatState {}

    record Booked(Booking booking) implements SeatState {}

    /** A movie on a screen at a time. Its own monitor guards its seats, so shows never block each other. */
    static final class Show {
        final long id;
        final Movie movie;
        final Theatre theatre;
        final Screen screen;
        final LocalDateTime start;
        private final Map<Seat, SeatState> taken = new HashMap<>();

        Show(long id, Movie movie, Theatre theatre, Screen screen, LocalDateTime start) {
            this.id = id;
            this.movie = movie;
            this.theatre = theatre;
            this.screen = screen;
            this.start = start;
        }

        /** Expired holds count as free; no cleanup thread needed. */
        private boolean isFree(Seat s, LocalDateTime now) {
            SeatState st = taken.get(s);
            return st == null || st instanceof Held h && !h.hold().expiry().isAfter(now);
        }

        synchronized List<Seat> freeSeats(LocalDateTime now) {
            return screen.seats().stream().filter(s -> isFree(s, now)).toList();
        }

        /** All or nothing: check every seat, then mark them all. */
        synchronized void hold(Hold h, LocalDateTime now) {
            for (Seat s : h.seats()) {
                if (!screen.seats().contains(s)) throw new BookingException("no seat " + s);
                if (!isFree(s, now)) throw new BookingException("seat taken " + s.row() + s.number());
            }
            h.seats().forEach(s -> taken.put(s, new Held(h)));
        }

        synchronized boolean isActive(Hold h, LocalDateTime now) {
            return h.expiry().isAfter(now)
                    && h.seats().stream().allMatch(s -> taken.get(s) instanceof Held x && x.hold() == h);
        }

        /** Turns a still-active hold into a booking. */
        synchronized boolean book(Hold h, Booking b, LocalDateTime now) {
            if (!isActive(h, now)) return false;
            h.seats().forEach(s -> taken.put(s, new Booked(b)));
            return true;
        }

        synchronized void release(Booking b) {
            b.seats().forEach(s -> taken.remove(s, new Booked(b)));
        }
    }

    // ---------- pricing and payment ----------

    interface PricingStrategy {
        long price(Show show, Seat seat);
    }

    static final class CategoryPricing implements PricingStrategy {
        public long price(Show show, Seat seat) { return seat.category().basePrice; }
    }

    static final class EveningSurcharge implements PricingStrategy {
        static final int EVENING_HOUR = 18;
        private final PricingStrategy inner;

        EveningSurcharge(PricingStrategy inner) { this.inner = inner; }

        public long price(Show show, Seat seat) {
            long p = inner.price(show, seat);
            return show.start.getHour() >= EVENING_HOUR ? p * 6 / 5 : p;
        }
    }

    interface PaymentGateway {
        boolean charge(String user, long amount);
        void refund(String user, long amount);
    }

    // ---------- facade ----------

    static final Duration HOLD_TIME = Duration.ofMinutes(5);

    private final Map<Long, Show> shows = new ConcurrentHashMap<>();
    private final Map<Long, Booking> bookings = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(1);
    private final PricingStrategy pricing;
    private final Supplier<LocalDateTime> clock;

    BookingService(PricingStrategy pricing, Supplier<LocalDateTime> clock) {
        this.pricing = pricing;
        this.clock = clock;
    }

    Show addShow(Movie movie, Theatre theatre, Screen screen, LocalDateTime start) {
        Show s = new Show(ids.getAndIncrement(), movie, theatre, screen, start);
        shows.put(s.id, s);
        return s;
    }

    List<Show> search(Movie movie, City city) {
        return shows.values().stream()
                .filter(s -> s.movie.equals(movie) && s.theatre.city().equals(city))
                .toList();
    }

    List<Seat> freeSeats(Show show) { return show.freeSeats(clock.get()); }

    long price(Show show, Set<Seat> seats) {
        return seats.stream().mapToLong(s -> pricing.price(show, s)).sum();
    }

    Hold hold(String user, Show show, Set<Seat> seats) {
        LocalDateTime now = clock.get();
        Hold h = new Hold(ids.getAndIncrement(), show, Set.copyOf(seats), user, now.plus(HOLD_TIME));
        show.hold(h, now);
        return h;
    }

    /** Validates under the show lock, pays outside it, books under it again; refunds if the hold lapsed. */
    Booking confirm(Hold h, PaymentGateway payment) {
        if (!h.show().isActive(h, clock.get())) throw new BookingException("hold expired");
        long amount = price(h.show(), h.seats());
        if (!payment.charge(h.user(), amount)) throw new BookingException("payment failed");
        Booking b = new Booking(ids.getAndIncrement(), h.show(), h.seats(), h.user(), amount);
        if (!h.show().book(h, b, clock.get())) {
            payment.refund(h.user(), amount);
            throw new BookingException("hold expired during payment");
        }
        bookings.put(b.id(), b);
        return b;
    }

    /** One-step booking: hold and confirm at once. */
    Booking book(String user, Show show, Set<Seat> seats, PaymentGateway payment) {
        return confirm(hold(user, show, seats), payment);
    }

    void cancel(Booking b) {
        if (bookings.remove(b.id()) == null) throw new BookingException("unknown booking");
        b.show().release(b);
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    private static boolean fails(Runnable r) {
        try { r.run(); return false; } catch (BookingException e) { return true; }
    }

    static final class TestClock implements Supplier<LocalDateTime> {
        LocalDateTime now = LocalDateTime.of(2026, 11, 27, 10, 0);

        public LocalDateTime get() { return now; }
    }

    static final class FakePayment implements PaymentGateway {
        boolean succeed = true;
        long charged, refunded;
        Runnable duringCharge = () -> {};

        public boolean charge(String user, long amount) {
            duringCharge.run();
            if (succeed) charged += amount;
            return succeed;
        }

        public void refund(String user, long amount) { refunded += amount; }
    }

    static final City PUNE = new City("Pune"), GOA = new City("Goa");
    static final Movie DUNE = new Movie("Dune");
    static final Seat A1 = new Seat('A', 1, SeatCategory.REGULAR), A2 = new Seat('A', 2, SeatCategory.REGULAR),
            B1 = new Seat('B', 1, SeatCategory.PREMIUM), C1 = new Seat('C', 1, SeatCategory.RECLINER);
    static final Screen SCREEN = new Screen("S1", List.of(A1, A2, B1, C1));
    static final Theatre PVR = new Theatre("PVR", PUNE, List.of(SCREEN)), INOX = new Theatre("INOX", GOA, List.of(SCREEN));
    static final LocalDateTime MATINEE = LocalDateTime.of(2026, 11, 28, 14, 0),
            EVENING = LocalDateTime.of(2026, 11, 28, 19, 0);

    public static void main(String[] args) throws Exception {
        System.out.println("Stage 1: core model");
        {
            TestClock clock = new TestClock();
            BookingService svc = new BookingService(new CategoryPricing(), clock);
            FakePayment pay = new FakePayment();
            Show show = svc.addShow(DUNE, PVR, SCREEN, MATINEE);
            svc.addShow(DUNE, INOX, SCREEN, MATINEE);
            check("search by movie and city", svc.search(DUNE, PUNE).equals(List.of(show)));
            Booking b = svc.book("ann", show, Set.of(A1, A2), pay);
            check("booked seats are no longer free", svc.freeSeats(show).equals(List.of(B1, C1)));
            check("booking a taken seat fails", fails(() -> svc.book("bob", show, Set.of(B1, A1), pay)));
            check("all or nothing: B1 still free", svc.freeSeats(show).contains(B1));
            svc.cancel(b);
            check("cancel frees the seats", svc.freeSeats(show).size() == 4);
            check("cancel twice fails", fails(() -> svc.cancel(b)));
        }

        System.out.println("Stage 2: pricing and holds");
        {
            TestClock clock = new TestClock();
            BookingService svc = new BookingService(new EveningSurcharge(new CategoryPricing()), clock);
            FakePayment pay = new FakePayment();
            Show matinee = svc.addShow(DUNE, PVR, SCREEN, MATINEE);
            Show evening = svc.addShow(DUNE, PVR, SCREEN, EVENING);
            check("category prices", svc.price(matinee, Set.of(A1, B1, C1)) == 100 + 150 + 250);
            check("evening is 20% more", svc.price(evening, Set.of(A1, B1)) == 120 + 180);

            Hold h = svc.hold("ann", matinee, Set.of(C1));
            check("held seat is not free", !svc.freeSeats(matinee).contains(C1));
            clock.now = clock.now.plusMinutes(6);
            check("expired hold frees the seat", svc.freeSeats(matinee).contains(C1));
            check("confirming an expired hold fails", fails(() -> svc.confirm(h, pay)) && pay.charged == 0);

            Hold h2 = svc.hold("bob", matinee, Set.of(C1));
            pay.succeed = false;
            check("failed payment books nothing", fails(() -> svc.confirm(h2, pay)));
            pay.succeed = true;
            check("hold survives a failed payment", svc.confirm(h2, pay).amount() == 250);

            Hold h3 = svc.hold("cat", matinee, Set.of(A1));
            pay.duringCharge = () -> clock.now = clock.now.plusMinutes(10);
            check("hold lapsing during payment refunds", fails(() -> svc.confirm(h3, pay)) && pay.refunded == 100);
        }

        System.out.println("Stage 3: concurrency");
        {
            BookingService svc = new BookingService(new CategoryPricing(), new TestClock());
            Show show = svc.addShow(DUNE, PVR, SCREEN, MATINEE);
            AtomicInteger won = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(16);
            CountDownLatch go = new CountDownLatch(1);
            for (int i = 0; i < 200; i++) {
                String user = "u" + i;
                Set<Seat> want = i % 2 == 0 ? Set.of(A1, A2) : Set.of(A2, B1);
                pool.submit(() -> {
                    go.await();
                    if (!fails(() -> svc.hold(user, show, want))) won.incrementAndGet();
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            check("overlapping holds: exactly one wins", won.get() == 1);

            Show other = svc.addShow(DUNE, INOX, SCREEN, MATINEE);
            CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
            Thread blocker = new Thread(() -> {
                synchronized (show) {
                    locked.countDown();
                    try { release.await(); } catch (InterruptedException ignored) {}
                }
            });
            blocker.start();
            locked.await();
            Thread holder = new Thread(() -> svc.hold("zed", other, Set.of(C1)));
            holder.start();
            holder.join(2_000);
            check("a busy show does not block another show", !holder.isAlive());
            release.countDown();
            blocker.join();
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
