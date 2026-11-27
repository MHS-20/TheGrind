# Behavioral Patterns

Behavioral patterns decide which object makes a decision and how objects notify each other. They cover most of the extension requirements in LLD rounds: a new pricing rule is a strategy, a new notification channel is an observer, and a machine with modes is a state.

| Pattern | The requirement that calls for it |
|---------|-----------------------------------|
| Strategy | "the rule for X may change / has variants" |
| Observer | "notify A, B and C when X happens" |
| State | "the machine behaves differently in each mode" |
| Command | "undo", "queue requests", "log every action" |
| Chain of responsibility | "a request passes through several checks or handlers" |
| Template method | "same steps, one or two differ per variant" |
| Iterator | "walk a custom collection without exposing it" |
| Mediator | "many objects talk to each other" |
| Memento | "save and restore state" |

## Strategy

A strategy is an interface for one varying decision, with one class per variant. The context holds a strategy and calls it, without knowing which one it has. It is the most common pattern in LLD rounds, because "the rule may change" is the most common extension.

Splitwise splits an expense in different ways:

```java
interface SplitStrategy {
    Map<String, Long> split(long amountCents, List<String> users, List<Long> params);
}

class EqualSplit implements SplitStrategy {
    public Map<String, Long> split(long amount, List<String> users, List<Long> params) {
        Map<String, Long> shares = new LinkedHashMap<>();
        long base = amount / users.size();
        long remainder = amount % users.size();       // cents nobody can split evenly
        for (int i = 0; i < users.size(); i++) {
            shares.put(users.get(i), base + (i < remainder ? 1 : 0));
        }
        return shares;
    }
}

class PercentSplit implements SplitStrategy {
    public Map<String, Long> split(long amount, List<String> users, List<Long> percents) {
        if (percents.stream().mapToLong(Long::longValue).sum() != 100) {
            throw new IllegalArgumentException("percents must sum to 100");
        }
        Map<String, Long> shares = new LinkedHashMap<>();
        long assigned = 0;
        for (int i = 0; i < users.size(); i++) {
            long share = (i == users.size() - 1)
                    ? amount - assigned                 // last user absorbs rounding
                    : amount * percents.get(i) / 100;
            shares.put(users.get(i), share);
            assigned += share;
        }
        return shares;
    }
}

class ExpenseService {
    Map<String, Long> addExpense(long amount, List<String> users, SplitStrategy how, List<Long> params) {
        return how.split(amount, users, params);
    }
}
```

Other common uses: parking fees, elevator scheduling (nearest car vs least busy), spot allocation (first free vs nearest to the gate), rate limiting (token bucket vs sliding window), ride matching.

In Java, a strategy with one method is a functional interface, so a lambda works for small variants: `SplitStrategy exact = (amount, users, params) -> ...`. Use a named class when the strategy has state, validation, or a name worth showing in the diagram.

Strategy vs a `switch`: see [Open/closed](16b_oop_and_solid.md#openclosed). The pattern pays off when variants are added over time or chosen at runtime.

## Observer

An observer registers with a subject and is called when the subject's state changes. The subject knows only the observer interface, so new reactions are added without editing it.

```java
interface OrderListener {
    void onStatusChange(Order order, OrderStatus from, OrderStatus to);
}

class Order {
    private final String id;
    private OrderStatus status = OrderStatus.PLACED;
    private final List<OrderListener> listeners = new CopyOnWriteArrayList<>();

    Order(String id) { this.id = id; }

    void subscribe(OrderListener l)   { listeners.add(l); }
    void unsubscribe(OrderListener l) { listeners.remove(l); }

    void advance(OrderStatus next) {
        OrderStatus prev = status;
        status = next;
        for (OrderListener l : listeners) l.onStatusChange(this, prev, next);
    }
}

class SmsNotifier implements OrderListener {
    public void onStatusChange(Order o, OrderStatus from, OrderStatus to) {
        if (to == OrderStatus.OUT_FOR_DELIVERY) send("Your order is on its way");
    }
}

class AnalyticsRecorder implements OrderListener { ... }

order.subscribe(new SmsNotifier());
order.subscribe(new AnalyticsRecorder());
```

Points to raise in an interview:

- **Which thread runs the listeners?** Here, the caller's. One slow listener (an SMS call) blocks the order update. Fix: hand events to an executor or a queue.
- **One failing listener** should not stop the others: catch per listener.
- **Iterating while listeners subscribe**: `CopyOnWriteArrayList` gives a snapshot per iteration, which suits lists that are read far more than written.
- **Memory leaks**: a subject holds strong references; listeners that are never unsubscribed live forever.

When the subject and observers should not know each other at all, put a broker in between: topics, publishers, subscribers. That is **pub/sub**, a full LLD problem on its own.

## State

A state object represents one mode of a machine. The machine delegates each action to its current state, and the state decides what happens and which state comes next. It replaces `switch (state)` blocks repeated in every method.

A vending machine:

| State \ action | insertCoin | selectProduct | dispense |
|----------------|------------|---------------|----------|
| Idle | → HasMoney | error: insert coin | error |
| HasMoney | add credit | → Dispensing (if enough credit) | error |
| Dispensing | error: busy | error: busy | → Idle |

```java
interface VendingState {
    default void insertCoin(VendingMachine m, int cents) { throw new IllegalStateException(name() + ": cannot insert coin"); }
    default void select(VendingMachine m, String code)   { throw new IllegalStateException(name() + ": cannot select"); }
    default void dispense(VendingMachine m)              { throw new IllegalStateException(name() + ": cannot dispense"); }
    String name();
}

class Idle implements VendingState {
    public String name() { return "Idle"; }
    public void insertCoin(VendingMachine m, int cents) {
        m.addCredit(cents);
        m.setState(new HasMoney());
    }
}

class HasMoney implements VendingState {
    public String name() { return "HasMoney"; }
    public void insertCoin(VendingMachine m, int cents) { m.addCredit(cents); }
    public void select(VendingMachine m, String code) {
        int price = m.priceOf(code);
        if (m.credit() < price) throw new IllegalStateException("insufficient credit");
        m.reserve(code, price);
        m.setState(new Dispensing());
    }
}

class Dispensing implements VendingState {
    public String name() { return "Dispensing"; }
    public void dispense(VendingMachine m) {
        m.releaseReservedAndChange();
        m.setState(new Idle());
    }
}

class VendingMachine {
    private VendingState state = new Idle();
    void insertCoin(int cents)  { state.insertCoin(this, cents); }
    void select(String code)    { state.select(this, code); }
    void dispense()             { state.dispense(this); }
    void setState(VendingState s) { state = s; }
    // credit, inventory, reserve(...) ...
}
```

The default methods give "not allowed in this state" for free; each state overrides only the actions it accepts. Adding a `Maintenance` state is one new class plus the transitions into it.

Compared with an enum and a `switch` in each method:

- **Enum + switch** is shorter when there are three or four states and few actions. All transitions are visible in one place.
- **State classes** win when each state has real logic, when states are added later, or when a state carries its own data (the amount entered so far, the selected product).

A middle ground that is often best in Java: an enum where each constant overrides methods, which keeps the transitions in one file with no `switch`.

Typical problems: vending machine, ATM (idle, card inserted, PIN verified), elevator (moving up, moving down, idle, doors open), order lifecycle, traffic light.

## Command

A command turns a request into an object. Once it is an object it can be stored, queued, logged, and undone.

A text editor with undo:

```java
interface Command {
    void execute();
    void undo();
}

class Document {
    private final StringBuilder text = new StringBuilder();
    void insert(int pos, String s) { text.insert(pos, s); }
    void delete(int pos, int len)  { text.delete(pos, pos + len); }
    String text() { return text.toString(); }
}

class InsertCommand implements Command {
    private final Document doc;
    private final int pos;
    private final String s;

    InsertCommand(Document doc, int pos, String s) { this.doc = doc; this.pos = pos; this.s = s; }

    public void execute() { doc.insert(pos, s); }
    public void undo()    { doc.delete(pos, s.length()); }
}

class Editor {
    private final Deque<Command> undo = new ArrayDeque<>();
    private final Deque<Command> redo = new ArrayDeque<>();

    void run(Command c) {
        c.execute();
        undo.push(c);
        redo.clear();                 // a new action invalidates the redo history
    }

    void undo() {
        if (undo.isEmpty()) return;
        Command c = undo.pop();
        c.undo();
        redo.push(c);
    }

    void redo() {
        if (redo.isEmpty()) return;
        Command c = redo.pop();
        c.execute();
        undo.push(c);
    }
}
```

A delete command must remember the text it removed, or `undo` cannot put it back: a command stores whatever its inverse needs.

Other uses:

- **Chess moves**: a `Move` stores the piece, from, to, and any captured piece, so it can be undone and replayed.
- **Elevator requests**: requests are objects in a queue that the scheduler orders.
- **Job queues**: the producer creates commands, worker threads execute them.

## Chain of responsibility

A request passes along a chain of handlers. Each handler either handles it, rejects it, or passes it to the next one. The sender does not know which handler will act.

An ATM dispensing cash with the largest notes first:

```java
abstract class NoteDispenser {
    private final int note;
    private NoteDispenser next;

    NoteDispenser(int note) { this.note = note; }

    NoteDispenser then(NoteDispenser next) { this.next = next; return next; }

    void dispense(int amount, Map<Integer, Integer> out) {
        int count = amount / note;
        if (count > 0) out.put(note, count);
        int rest = amount % note;
        if (rest == 0) return;
        if (next == null) throw new IllegalArgumentException("cannot dispense " + rest);
        next.dispense(rest, out);
    }
}

class Note100 extends NoteDispenser { Note100() { super(100); } }
class Note50  extends NoteDispenser { Note50()  { super(50); } }
class Note20  extends NoteDispenser { Note20()  { super(20); } }

NoteDispenser chain = new Note100();
chain.then(new Note50()).then(new Note20());

var out = new LinkedHashMap<Integer, Integer>();
chain.dispense(270, out);      // {100=2, 50=1, 20=1}
```

Greedy is fine for 100/50/20 with these amounts but not for every set of notes (with 50 and 20, 60 is three 20s, while greedy takes 50 and gets stuck). Saying so is a good signal; a real ATM checks its inventory too.

The other common shape is a list of validators, where any one can reject:

```java
interface OrderCheck { Optional<String> check(Order o); }   // empty = passed

class Checkout {
    private final List<OrderCheck> checks;
    Checkout(List<OrderCheck> checks) { this.checks = checks; }

    void place(Order o) {
        for (OrderCheck c : checks) {
            c.check(o).ifPresent(err -> { throw new IllegalArgumentException(err); });
        }
        // ...
    }
}

new Checkout(List.of(new StockCheck(), new FraudCheck(), new AddressCheck()));
```

A list is easier to read and reorder than linked handlers, and is what most real code does (servlet filters, middleware). Use the linked form when a handler must decide whether the next one runs at all, or must change the request before passing it on.

Also used for: logging levels, support-ticket escalation, approval chains (manager, director, VP by amount).

## Template method

A template method is a method in an abstract class that fixes the order of steps, and leaves some steps to subclasses. The skeleton cannot be broken by a subclass, because it is `final`.

```java
abstract class Game {
    protected final List<String> players;
    Game(List<String> players) { this.players = players; }

    final String play() {                        // the template method
        setup();
        int turn = 0;
        while (!isOver()) {
            makeMove(players.get(turn % players.size()));
            turn++;
        }
        return winner();
    }

    protected abstract void setup();
    protected abstract void makeMove(String player);
    protected abstract boolean isOver();
    protected abstract String winner();
}

class SnakeAndLadder extends Game { ... }
class TicTacToe extends Game { ... }
```

Steps can also be optional **hooks**: a non-abstract method with an empty or default body that a subclass may override (`protected void onTurnEnd() {}`).

Template method vs Strategy: both vary one part of an algorithm. Template method uses inheritance and is fixed at compile time; Strategy uses composition and can be swapped at runtime. When in doubt in an interview, Strategy is the more flexible answer; Template method is fine when the variants really are different kinds of the same thing (games, report formats, data importers that differ only in parsing).

## Iterator

An iterator walks a collection without exposing how it is stored. In Java, implementing `Iterable<T>` makes a class usable in a for-each loop.

A playlist that can be played in order or shuffled, without giving out its internal list:

```java
class Playlist implements Iterable<Song> {
    private final List<Song> songs = new ArrayList<>();

    void add(Song s) { songs.add(s); }

    public Iterator<Song> iterator() {
        return Collections.unmodifiableList(songs).iterator();
    }

    Iterator<Song> shuffled(Random rnd) {
        List<Song> copy = new ArrayList<>(songs);
        Collections.shuffle(copy, rnd);
        return copy.iterator();
    }
}

for (Song s : playlist) play(s);
```

Writing one by hand, for a structure the JDK does not have, means keeping the position as state between calls:

```java
class RangeIterator implements Iterator<Integer> {
    private int next;
    private final int end, step;

    RangeIterator(int start, int end, int step) { this.next = start; this.end = end; this.step = step; }

    public boolean hasNext() { return next < end; }

    public Integer next() {
        if (!hasNext()) throw new NoSuchElementException();
        int v = next;
        next += step;
        return v;
    }
}
```

LeetCode has several iterator design problems that are asked in phone screens: Peeking Iterator (284), Flatten Nested List Iterator (341), Binary Search Tree Iterator (173), Zigzag Iterator (281).

## Mediator

A mediator is a central object that the others talk through, instead of referencing each other directly. With n objects that all talk to each other there are n² links; with a mediator there are n.

```java
class ChatRoom {                                    // the mediator
    private final Map<String, User> users = new HashMap<>();

    void join(User u) { users.put(u.name(), u); }

    void send(String from, String to, String text) {
        User target = users.get(to);
        if (target != null) target.receive(from, text);
    }

    void broadcast(String from, String text) {
        users.values().stream().filter(u -> !u.name().equals(from)).forEach(u -> u.receive(from, text));
    }
}
```

Users know the room, not each other. Typical examples: a chat room, an air-traffic controller that decides which plane lands, an auction house between bidders. An elevator system's dispatcher is a mediator between the floor buttons and the cars. The risk is the same as with a facade: the mediator becomes a god class.

## Memento

A memento captures an object's state so it can be restored later, without exposing the state's internals to whoever stores it.

```java
class Editor {
    private String text = "";
    private int cursor = 0;

    record Snapshot(String text, int cursor) {}      // the memento: immutable

    Snapshot save()             { return new Snapshot(text, cursor); }
    void restore(Snapshot s)    { text = s.text(); cursor = s.cursor(); }
    void type(String s)         { text = text.substring(0, cursor) + s + text.substring(cursor); cursor += s.length(); }
}

class History {                                      // the caretaker: stores, never reads
    private final Deque<Editor.Snapshot> stack = new ArrayDeque<>();
    void push(Editor.Snapshot s) { stack.push(s); }
    Editor.Snapshot pop()        { return stack.pop(); }
}
```

Memento vs Command for undo: Command stores the change and its inverse, which is cheap but requires an inverse for every operation. Memento stores the whole state, which is simple but costs memory per snapshot. Games (save points) and editors with small documents use mementos; large documents use commands.

## Rarer ones

These come up rarely in LLD rounds. Knowing the one-line idea is enough.

- **Visitor**: add an operation over a fixed set of classes without editing them (a tax calculator visiting each item type). With sealed interfaces and pattern-matching `switch`, Java code now usually does this with a `switch` instead.
- **Flyweight**: share the immutable part of many similar objects to save memory (glyphs in an editor, tree models in a game). `Integer.valueOf` caches small values the same way.
- **Prototype**: create objects by copying an existing one instead of calling a constructor. In Java, a copy constructor is preferred over `clone()`.

## Used in

Filled as problems are solved in [`21_lld`](../../21_lld/).

| Pattern | Problems |
|---------|----------|
| Strategy | parking_lot (pricing), elevator (scheduling), splitwise (splits), rate_limiter |
| Observer | pub_sub, food_delivery (order status), stock alerts |
| State | vending_machine, atm, elevator |
| Command | text_editor (undo), chess (moves), elevator (requests) |
| Chain of responsibility | atm (notes), logger (levels) |
| Template method | snake_and_ladder, tic_tac_toe |
| Iterator | playlist, LeetCode 284/341/173 |
| Mediator | chat room, elevator dispatcher |
| Memento | text_editor, game save |
