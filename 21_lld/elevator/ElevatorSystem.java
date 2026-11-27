import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ElevatorSystem {

    // ---------- model ----------

    enum Direction { UP, DOWN, IDLE }

    enum Status { MOVING, DOORS_OPEN, IDLE, MAINTENANCE }

    /** A floor button press. Car buttons are plain destination floors. */
    record Request(int floor, Direction direction) {}

    /** Immutable view of a car, taken under the car's lock. */
    record CarState(int id, int floor, Direction direction, Status status, SortedSet<Integer> stops) {}

    static final class Car {
        final int id;
        private int floor;
        private Direction direction = Direction.IDLE;
        private Status status = Status.IDLE;
        private final TreeSet<Integer> stops = new TreeSet<>();

        Car(int id) { this.id = id; }

        synchronized void addStop(int f) { stops.add(f); }

        synchronized void setMaintenance(boolean on) {
            status = on ? Status.MAINTENANCE : Status.IDLE;
            if (on) { stops.clear(); direction = Direction.IDLE; }
        }

        /** One tick of SCAN: open at a stop, else move one floor in the current direction. */
        synchronized void step() {
            if (status == Status.MAINTENANCE) return;
            if (stops.remove(floor)) {
                status = Status.DOORS_OPEN;
                return;
            }
            direction = nextDirection();
            if (direction == Direction.IDLE) {
                status = Status.IDLE;
                return;
            }
            floor += direction == Direction.UP ? 1 : -1;
            status = Status.MOVING;
        }

        private Direction nextDirection() {
            boolean above = stops.higher(floor) != null, below = stops.lower(floor) != null;
            return switch (direction) {
                case UP, IDLE -> above ? Direction.UP : below ? Direction.DOWN : Direction.IDLE;
                case DOWN -> below ? Direction.DOWN : above ? Direction.UP : Direction.IDLE;
            };
        }

        synchronized CarState snapshot() {
            return new CarState(id, floor, direction, status, new TreeSet<>(stops));
        }
    }

    // ---------- dispatching ----------

    interface Dispatcher {
        Optional<CarState> choose(List<CarState> available, Request r);
    }

    /** An idle car closest to the floor; if none is idle, the closest car. */
    static final class NearestIdleDispatcher implements Dispatcher {
        public Optional<CarState> choose(List<CarState> cars, Request r) {
            Comparator<CarState> byDistance = Comparator.comparingInt(c -> Math.abs(c.floor() - r.floor()));
            return cars.stream()
                    .min(Comparator.<CarState, Boolean>comparing(c -> c.direction() != Direction.IDLE)
                            .thenComparing(byDistance));
        }
    }

    /** The car that reaches the floor in the fewest ticks under SCAN. */
    static final class SoonestArrivalDispatcher implements Dispatcher {
        public Optional<CarState> choose(List<CarState> cars, Request r) {
            return cars.stream().min(Comparator.comparingInt(c -> eta(c, r.floor())));
        }

        static int eta(CarState c, int target) {
            int d = target - c.floor();
            boolean ahead = c.direction() == Direction.UP && d >= 0 || c.direction() == Direction.DOWN && d <= 0;
            if (c.direction() == Direction.IDLE || ahead) return Math.abs(d);
            // finish the current sweep, then come back
            int turn = c.direction() == Direction.UP ? c.stops().last() : c.stops().first();
            return Math.abs(turn - c.floor()) + Math.abs(turn - target);
        }
    }

    // ---------- facade ----------

    private final int topFloor;
    private final List<Car> cars = new ArrayList<>();
    private final Dispatcher dispatcher;
    /** Floor calls waiting for a car; the key is the call, the value the assigned car. */
    private final Map<Request, Integer> pending = new ConcurrentHashMap<>();
    private final Object assignLock = new Object();

    ElevatorSystem(int topFloor, int carCount, Dispatcher dispatcher) {
        this.topFloor = topFloor;
        for (int i = 0; i < carCount; i++) cars.add(new Car(i));
        this.dispatcher = dispatcher;
    }

    /** Assigns a floor call to a car and returns its id. Pressing an already pending call is a no-op. */
    int requestFloor(int floor, Direction dir) {
        checkFloor(floor);
        Request r = new Request(floor, dir);
        synchronized (assignLock) {
            Integer assigned = pending.get(r);
            if (assigned != null) return assigned;
            List<CarState> available = state().stream().filter(c -> c.status() != Status.MAINTENANCE).toList();
            CarState chosen = dispatcher.choose(available, r)
                    .orElseThrow(() -> new IllegalStateException("no car in service"));
            pending.put(r, chosen.id()); // before the stop, so a tick serving it also clears it
            cars.get(chosen.id()).addStop(floor);
            return chosen.id();
        }
    }

    void requestInCar(int carId, int floor) {
        checkFloor(floor);
        cars.get(carId).addStop(floor);
    }

    void setMaintenance(int carId, boolean on) {
        synchronized (assignLock) {
            boolean hasCalls = pending.containsValue(carId);
            boolean othersInService = cars.stream()
                    .anyMatch(c -> c.id != carId && c.snapshot().status() != Status.MAINTENANCE);
            if (on && hasCalls && !othersInService) throw new IllegalStateException("no car can take its calls");
            cars.get(carId).setMaintenance(on);
            if (on) { // hand its floor calls to other cars
                List<Request> orphaned = pending.entrySet().stream()
                        .filter(e -> e.getValue() == carId).map(Map.Entry::getKey).toList();
                orphaned.forEach(pending::remove);
                orphaned.forEach(r -> requestFloor(r.floor(), r.direction()));
            }
        }
    }

    void step() {
        for (Car car : cars) {
            car.step();
            CarState s = car.snapshot();
            if (s.status() == Status.DOORS_OPEN) {
                pending.entrySet().removeIf(e -> e.getValue() == s.id() && e.getKey().floor() == s.floor());
            }
        }
    }

    List<CarState> state() { return cars.stream().map(Car::snapshot).toList(); }

    boolean allIdle() {
        return pending.isEmpty() && state().stream().allMatch(c -> c.stops().isEmpty());
    }

    private void checkFloor(int f) {
        if (f < 0 || f > topFloor) throw new IllegalArgumentException("no floor " + f);
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    /** Steps until the car opens its doors; returns the floors where it opened, in order. */
    static List<Integer> runUntilIdle(ElevatorSystem s, int carId, int maxTicks) {
        List<Integer> opened = new ArrayList<>();
        for (int i = 0; i < maxTicks && !s.allIdle(); i++) {
            s.step();
            CarState c = s.state().get(carId);
            if (c.status() == Status.DOORS_OPEN) opened.add(c.floor());
        }
        return opened;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Stage 1: core model");
        {
            ElevatorSystem s = new ElevatorSystem(10, 1, new NearestIdleDispatcher());
            s.requestInCar(0, 3);
            s.step();
            check("car moves one floor per tick", s.state().get(0).floor() == 1);
            check("car reports direction up", s.state().get(0).direction() == Direction.UP);
            s.step();
            s.step();
            s.step();
            check("doors open at requested floor", s.state().get(0).status() == Status.DOORS_OPEN
                    && s.state().get(0).floor() == 3);

            s.requestInCar(0, 1);   // below
            s.requestInCar(0, 6);   // above
            s.requestFloor(5, Direction.DOWN);
            check("SCAN serves the current direction first", runUntilIdle(s, 0, 50).equals(List.of(5, 6, 1)));
            check("idle after serving everything", s.state().get(0).direction() == Direction.IDLE
                    || s.state().get(0).stops().isEmpty());
            check("unknown floor rejected", throwsIAE(() -> s.requestInCar(0, 11)));
        }

        System.out.println("Stage 2: several cars and scheduling");
        {
            ElevatorSystem s = new ElevatorSystem(20, 2, new NearestIdleDispatcher());
            s.requestInCar(0, 10);
            s.step();
            check("nearest idle car gets the call", s.requestFloor(2, Direction.UP) == 1);

            ElevatorSystem soon = new ElevatorSystem(20, 2, new SoonestArrivalDispatcher());
            soon.requestInCar(0, 20);
            soon.requestInCar(1, 0);
            for (int i = 0; i < 5; i++) soon.step(); // car 0 at 5 going up, car 1 at 0 opening doors
            check("car passing the floor wins", soon.requestFloor(8, Direction.UP) == 0);

            ElevatorSystem m = new ElevatorSystem(10, 2, new NearestIdleDispatcher());
            m.setMaintenance(0, true);
            check("car in maintenance gets no calls", m.requestFloor(0, Direction.UP) == 1);
            check("same call is not assigned twice", m.requestFloor(0, Direction.UP) == 1 && m.pending.size() == 1);
            check("last car with calls cannot go to maintenance", throwsISE(() -> m.setMaintenance(1, true)));
            m.setMaintenance(0, false);
            m.setMaintenance(1, true);
            check("its calls move to another car", m.pending.get(new Request(0, Direction.UP)) == 0);
        }

        System.out.println("Stage 3: concurrency");
        {
            ElevatorSystem s = new ElevatorSystem(30, 4, new SoonestArrivalDispatcher());
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicBoolean torn = new AtomicBoolean(false);
            Set<Integer> requested = ConcurrentHashMap.newKeySet();
            Set<Integer> served = ConcurrentHashMap.newKeySet();
            Thread clock = new Thread(() -> {
                while (running.get() || !s.allIdle()) {
                    s.step();
                    for (CarState c : s.state()) {
                        if (c.status() == Status.DOORS_OPEN) served.add(c.floor());
                        if (c.floor() < 0 || c.floor() > 30) torn.set(true);
                    }
                }
            });
            clock.start();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch go = new CountDownLatch(1);
            for (int i = 0; i < 400; i++) {
                int floor = (i * 7) % 31;
                pool.submit(() -> {
                    go.await();
                    requested.add(floor);
                    s.requestFloor(floor, floor % 2 == 0 ? Direction.UP : Direction.DOWN);
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            running.set(false);
            clock.join(10_000);
            check("no request lost: every called floor was served", served.containsAll(requested));
            check("no pending calls left", s.pending.isEmpty());
            check("snapshots always consistent", !torn.get());
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }

    private static boolean throwsIAE(Runnable r) {
        try { r.run(); return false; } catch (IllegalArgumentException e) { return true; }
    }

    private static boolean throwsISE(Runnable r) {
        try { r.run(); return false; } catch (IllegalStateException e) { return true; }
    }
}
