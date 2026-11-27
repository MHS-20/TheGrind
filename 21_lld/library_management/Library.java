import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Library {

    // ---------- model ----------

    record Book(String isbn, String title, String author) {}

    enum CopyStatus { AVAILABLE, ON_LOAN, ON_HOLD }

    static final class Copy {
        final String id;
        final Book book;
        CopyStatus status = CopyStatus.AVAILABLE;
        Member heldFor;
        LocalDate holdUntil;

        Copy(String id, Book book) { this.id = id; this.book = book; }

        boolean canBeBorrowedBy(Member m, LocalDate today) {
            return status == CopyStatus.AVAILABLE
                    || status == CopyStatus.ON_HOLD && heldFor == m && !today.isAfter(holdUntil);
        }
    }

    /** Lending rules per member type. A new type is a new implementation. */
    interface MembershipPolicy {
        int maxLoans();
        int loanDays();
        long finePerDay();

        default long fine(long daysLate) { return Math.max(0, daysLate) * finePerDay(); }
    }

    record Policy(int maxLoans, int loanDays, long finePerDay) implements MembershipPolicy {}

    static final MembershipPolicy STANDARD = new Policy(5, 14, 10);
    static final MembershipPolicy STUDENT = new Policy(3, 14, 10);
    static final MembershipPolicy FACULTY = new Policy(10, 30, 0);

    static final class Member {
        final String id;
        final MembershipPolicy policy;
        final List<Loan> loans = new ArrayList<>();
        long unpaidFines;

        Member(String id, MembershipPolicy policy) { this.id = id; this.policy = policy; }
    }

    static final class Loan {
        final Copy copy;
        final Member member;
        final LocalDate borrowed, due;
        LocalDate returned;

        Loan(Copy copy, Member member, LocalDate borrowed, LocalDate due) {
            this.copy = copy;
            this.member = member;
            this.borrowed = borrowed;
            this.due = due;
        }
    }

    interface Notifier {
        void reservationReady(Member m, Book b, LocalDate until);
    }

    static final class LibraryException extends RuntimeException {
        LibraryException(String msg) { super(msg); }
    }

    // ---------- facade ----------

    static final int HOLD_DAYS = 3;
    static final long MAX_UNPAID_FINES = 500;

    private final Map<String, Book> catalogue = new HashMap<>();
    private final Map<String, List<Copy>> copies = new HashMap<>();
    private final Map<String, Deque<Member>> reservations = new HashMap<>();
    private final Notifier notifier;

    Library(Notifier notifier) { this.notifier = notifier; }

    synchronized void addBook(Book b, int count) {
        catalogue.put(b.isbn(), b);
        List<Copy> list = copies.computeIfAbsent(b.isbn(), k -> new ArrayList<>());
        for (int i = 0; i < count; i++) list.add(new Copy(b.isbn() + "-" + (list.size() + 1), b));
    }

    synchronized List<Book> searchByTitle(String q) {
        return catalogue.values().stream().filter(b -> contains(b.title(), q)).toList();
    }

    synchronized List<Book> searchByAuthor(String q) {
        return catalogue.values().stream().filter(b -> contains(b.author(), q)).toList();
    }

    private static boolean contains(String s, String q) { return s.toLowerCase().contains(q.toLowerCase()); }

    synchronized Loan borrow(Member m, String isbn, LocalDate today) {
        if (m.unpaidFines > MAX_UNPAID_FINES) throw new LibraryException("unpaid fines");
        if (m.loans.size() >= m.policy.maxLoans()) throw new LibraryException("loan limit reached");
        expireHolds(isbn, today);
        // a copy held for this member first, then any free one
        Copy copy = copiesOf(isbn).stream()
                .filter(c -> c.canBeBorrowedBy(m, today))
                .min((a, b) -> Boolean.compare(a.heldFor != m, b.heldFor != m))
                .orElseThrow(() -> new LibraryException("no copy available"));
        copy.status = CopyStatus.ON_LOAN;
        copy.heldFor = null;
        copy.holdUntil = null;
        Loan loan = new Loan(copy, m, today, today.plusDays(m.policy.loanDays()));
        m.loans.add(loan);
        return loan;
    }

    /** Returns the fine charged for this loan. */
    synchronized long returnCopy(Loan loan, LocalDate today) {
        if (loan.returned != null) throw new LibraryException("already returned");
        loan.returned = today;
        loan.member.loans.remove(loan);
        long fine = loan.member.policy.fine(ChronoUnit.DAYS.between(loan.due, today));
        loan.member.unpaidFines += fine;
        passOn(loan.copy, today);
        return fine;
    }

    synchronized void reserve(Member m, String isbn, LocalDate today) {
        expireHolds(isbn, today);
        if (copiesOf(isbn).stream().anyMatch(c -> c.status == CopyStatus.AVAILABLE)) {
            throw new LibraryException("a copy is available; borrow it instead");
        }
        reservations.computeIfAbsent(isbn, k -> new ArrayDeque<>()).add(m);
    }

    synchronized void payFine(Member m, long amount) { m.unpaidFines = Math.max(0, m.unpaidFines - amount); }

    /** A freed copy goes to the next member in the queue, or back on the shelf. */
    private void passOn(Copy copy, LocalDate today) {
        Member next = reservations.getOrDefault(copy.book.isbn(), new ArrayDeque<>()).poll();
        if (next == null) {
            copy.status = CopyStatus.AVAILABLE;
            copy.heldFor = null;
            copy.holdUntil = null;
            return;
        }
        copy.status = CopyStatus.ON_HOLD;
        copy.heldFor = next;
        copy.holdUntil = today.plusDays(HOLD_DAYS);
        notifier.reservationReady(next, copy.book, copy.holdUntil);
    }

    /** Lazy expiry: an uncollected hold passes on when the book is next touched. */
    private void expireHolds(String isbn, LocalDate today) {
        for (Copy c : copiesOf(isbn)) {
            if (c.status == CopyStatus.ON_HOLD && today.isAfter(c.holdUntil)) passOn(c, today);
        }
    }

    private List<Copy> copiesOf(String isbn) {
        List<Copy> list = copies.get(isbn);
        if (list == null) throw new LibraryException("unknown ISBN " + isbn);
        return list;
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    private static boolean fails(Runnable r) {
        try { r.run(); return false; } catch (LibraryException e) { return true; }
    }

    static final Book DDIA = new Book("111", "Designing Data-Intensive Applications", "Kleppmann");
    static final Book SICP = new Book("222", "Structure and Interpretation", "Abelson");
    static final LocalDate DAY0 = LocalDate.of(2026, 11, 1);

    public static void main(String[] args) {
        System.out.println("Stage 1: core model");
        {
            Library lib = new Library((m, b, d) -> {});
            lib.addBook(DDIA, 2);
            lib.addBook(SICP, 6);
            Member ann = new Member("ann", STANDARD), bob = new Member("bob", STANDARD);
            check("search by title", lib.searchByTitle("data-intensive").equals(List.of(DDIA)));
            check("search by author", lib.searchByAuthor("abel").equals(List.of(SICP)));
            Loan l = lib.borrow(ann, "111", DAY0);
            check("loan is due in 14 days", l.due.equals(DAY0.plusDays(14)));
            lib.borrow(bob, "111", DAY0);
            check("no copy left fails", fails(() -> lib.borrow(ann, "111", DAY0)));
            for (int i = 0; i < 4; i++) lib.borrow(ann, "222", DAY0);
            check("sixth loan over the limit of 5 fails", fails(() -> lib.borrow(ann, "222", DAY0)));
            lib.returnCopy(l, DAY0.plusDays(3));
            check("returned copy can be borrowed again", lib.borrow(bob, "111", DAY0.plusDays(3)) != null);
        }

        System.out.println("Stage 2: reservations and fines");
        {
            List<String> notified = new ArrayList<>();
            Library lib = new Library((m, b, until) -> notified.add(m.id + ":" + b.isbn()));
            lib.addBook(DDIA, 1);
            Member ann = new Member("ann", STANDARD), bob = new Member("bob", STANDARD),
                    cat = new Member("cat", STANDARD);
            Loan l = lib.borrow(ann, "111", DAY0);
            lib.reserve(bob, "111", DAY0);
            lib.reserve(cat, "111", DAY0);
            long fine = lib.returnCopy(l, DAY0.plusDays(20)); // 6 days late
            check("late return fined 10 per day", fine == 60);
            check("first in queue notified", notified.equals(List.of("bob:111")));
            check("held copy cannot be borrowed by others", fails(() -> lib.borrow(cat, "111", DAY0.plusDays(20))));
            check("hold expires after 3 days and passes to the next",
                    lib.borrow(cat, "111", DAY0.plusDays(24)) != null && notified.equals(List.of("bob:111", "cat:111")));
            check("reserving when a copy is free fails", fails(() -> {
                Library l2 = new Library((m, b, d) -> {});
                l2.addBook(SICP, 1);
                l2.reserve(bob, "222", DAY0);
            }));

            Library l3 = new Library((m, b, d) -> {});
            l3.addBook(SICP, 2);
            Member dan = new Member("dan", STANDARD);
            Loan late = l3.borrow(dan, "222", DAY0);
            l3.returnCopy(late, DAY0.plusDays(14 + 51)); // 510
            check("fines above 500 block borrowing", fails(() -> l3.borrow(dan, "222", DAY0.plusDays(70))));
            l3.payFine(dan, 100);
            check("paying fines unblocks", l3.borrow(dan, "222", DAY0.plusDays(70)) != null);
        }

        System.out.println("Stage 3: member types");
        {
            Library lib = new Library((m, b, d) -> {});
            lib.addBook(SICP, 20);
            Member stu = new Member("stu", STUDENT), fac = new Member("fac", FACULTY);
            for (int i = 0; i < 3; i++) lib.borrow(stu, "222", DAY0);
            check("student limited to 3", fails(() -> lib.borrow(stu, "222", DAY0)));
            Loan fl = null;
            for (int i = 0; i < 10; i++) fl = lib.borrow(fac, "222", DAY0);
            check("faculty may borrow 10", fac.loans.size() == 10);
            check("faculty loan is 30 days", fl.due.equals(DAY0.plusDays(30)));
            check("faculty pays no fines", lib.returnCopy(fl, DAY0.plusDays(90)) == 0);
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
