# BILL — Student Billing Library

[![CI](https://github.com/Adit12T/BILL/actions/workflows/ci.yml/badge.svg)](https://github.com/Adit12T/BILL/actions/workflows/ci.yml)

A **Java 21** library that works out a university student's bill: tuition by residency, level and
credit load, per-term fees, waivers and scholarships, payments, and charges over a date range, with
role-based access for students and college administrators.

It implements the operations of the `BILLIntf` interface I designed for the Software Engineering course
(CSCE 740) in my MS at the University of South Carolina in 2018. Only that version's generated API
documentation survived, so version 2 is a fresh implementation against the same API; the 2018 docs are
kept in [`docs/api-2018`](docs/api-2018/index.html).

## Operations

| Method | What it does |
|---|---|
| `loadUsers()`, `loadRecords()` | Load users and student records from JSON; invalid or duplicate records are rejected at load |
| `logIn(id)`, `logOut()`, `getUser()` | Session for one user; everything else needs someone signed in |
| `getStudentIDs()` | A student sees only themselves; an admin sees the students of their own college |
| `getRecord(id)` | A student record, if the signed-in user may see it |
| `generateBill(id)` | Recorded transactions plus this term's charges, and the balance |
| `viewCharges(id, from, to)` | Transactions between two dates, inclusive (the original month/day/year signature is kept too) |
| `applyPayment(id, amount, note)` | A student pays their own bill; the payment is saved and can't exceed what is owed |
| `editRecord(id, record, permanent)` | Students change only their contact details; admins change anything except the id, college and transaction history; `permanent` saves to disk |

## How a bill is worked out

`BillCalculator` applies the rates in a `FeeSchedule`:

- **Tuition**: undergraduates below the full-time band pay per credit; inside it a flat rate; above it
  the flat rate plus per credit. Graduate students pay per credit; graduate assistants a flat rate.
  Residents, active-duty students and veterans pay in-state rates; free-tuition students pay none.
- **Fees**: technology fee per credit up to a cap; health fee for students on campus with 6 or more
  credits unless they have outside insurance; international, study-abroad and National Student
  Exchange fees where they apply.
- **Scholarships** appear as credits and never exceed the tuition charged.

All money is `BigDecimal` built from strings, stored with two decimals and rounded half-up, never
`double`, so totals are exact.

**The fee figures in `FeeSchedule.illustrative()` are invented for demos and tests**; they are not any
university's actual rates. Load a real schedule to use real numbers.

## Try it

```bash
mvn -q package
mvn -q exec:java -Dexec.args="data/users.json data/records.json admin-eng"   # bills for an admin's college
mvn -q exec:java -Dexec.args="data/users.json data/records.json s1001"       # a student's own bill
```

## Design notes

- **Access checks in one place.** Every read goes through one method that answers "no accessible
  record" for both missing and other people's records, so ids can't be probed.
- **Immutable model.** Records are Java records with defensive copies; edits replace a record whole.
- **Safe saves.** Records are written to a temporary file and moved into place; if a save fails, the
  change is rolled back in memory too.

## Tests

```bash
mvn verify
```

The tests cover each tuition rule, fee, waiver and scholarship cap, access control for both roles,
payment validation, date filtering, edit permissions, a failed save, exact money arithmetic, and a JSON
load-pay-save-reload round trip on the sample data.

## License

Copyright © 2018–2026 Aditya Tripathi. All rights reserved. The code is published for viewing only; see
[LICENSE](LICENSE).
