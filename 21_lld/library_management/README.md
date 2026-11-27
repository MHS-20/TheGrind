# Library Management

A library that lends copies of books to members and tracks returns, reservations and fines.

## Stages

1. **Core model.** A book (ISBN, title, author) has several physical copies. Members borrow a copy (at most 5 at a time) for 14 days and return it. Search by title or author. Borrowing fails if no copy is available or the member is at the limit.
2. **Reservations and fines.** A member can reserve a book with no free copy; when a copy is returned, it is held for the first member in the queue for 3 days and nobody else can borrow it. Late returns cost 10 cents per day, and a member with unpaid fines above 500 cannot borrow. Members are notified when a reserved copy is ready.
3. **Member types.** Students borrow at most 3 books for 14 days; faculty 10 books for 30 days with no fines. Adding a new member type must not change the lending code.

## Entities

- `Library`: the facade; catalogue, members, loans.
- `Book` (ISBN, title, author), `Copy` (id, book, status: available, on loan, on hold).
- `Member`: id, current loans, unpaid fines, and a `MembershipPolicy`.
- `Loan`: copy, member, borrowed date, due date, returned date.
- `ReservationQueue`: per book, FIFO of members.
- `MembershipPolicy` (stage 3): max loans, loan days, fine per day.
- `Notifier`: interface for "your reservation is ready".

## Patterns used

- **Strategy** for membership rules (stage 3).
- **Observer** for reservation notifications (stage 2).
- **State** for a copy's status, kept as an enum: three states with simple rules.

## Design notes

- **Book vs copy** is the key modelling point: the catalogue is about books, loans are about copies. Mixing them breaks as soon as there are two copies.
- **Dates are parameters**, so fines are testable.
- **Fine computation lives in the policy**, not in `Loan`, so faculty's "no fines" is data, not an `if`.
