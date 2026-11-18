package io.github.adit12t.bill;

import io.github.adit12t.bill.model.Enums.Role;
import io.github.adit12t.bill.model.Enums.TransactionType;
import io.github.adit12t.bill.model.Model.Bill;
import io.github.adit12t.bill.model.Model.StudentRecord;
import io.github.adit12t.bill.model.Model.Transaction;
import io.github.adit12t.bill.model.Model.User;
import io.github.adit12t.bill.model.Money;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The billing system, with the operations of the 2018 {@code BILLIntf} interface.
 * <p>
 * Students may see and pay only their own bill, and change only their contact details. Admins may see and
 * edit the records of students in their own college. Every call except {@link #logIn} needs a signed-in user.
 * Not thread-safe: one instance serves one session, as in the original design.
 */
public final class BillService {

    private final RecordStore store;
    private final BillCalculator calculator;
    private final Clock clock;
    private Map<String, User> users = Map.of();
    private final Map<String, StudentRecord> records = new LinkedHashMap<>();
    private User current;

    public BillService(RecordStore store, FeeSchedule fees, Clock clock) {
        this.store = store;
        this.calculator = new BillCalculator(fees);
        this.clock = clock;
    }

    // --- loading and sessions ---

    public void loadUsers() throws IOException {
        users = store.loadUsers().stream().collect(Collectors.toMap(User::id, Function.identity(), (a, b) -> {
            throw new IllegalStateException("Duplicate user id " + a.id());
        }, LinkedHashMap::new));
    }

    public void loadRecords() throws IOException {
        records.clear();
        for (StudentRecord r : store.loadRecords()) {
            List<String> problems = r.validate();
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Invalid record " + (r.student() == null ? "?" : r.student().id()) + ": " + problems);
            }
            if (records.put(r.student().id(), r) != null) {
                throw new IllegalStateException("Duplicate record for " + r.student().id());
            }
        }
    }

    public void logIn(String userId) {
        User user = users.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("Unknown user " + userId);
        }
        current = user;
    }

    public void logOut() {
        current = null;
    }

    public String getUser() {
        return requireUser().id();
    }

    // --- reading ---

    /** Students see only themselves; admins see the students of their college. */
    public List<String> getStudentIDs() {
        User user = requireUser();
        if (user.role() == Role.STUDENT) {
            return records.containsKey(user.id()) ? List.of(user.id()) : List.of();
        }
        return records.values().stream().filter(r -> r.college() == user.college()).map(r -> r.student().id()).toList();
    }

    public StudentRecord getRecord(String studentId) {
        return readable(studentId);
    }

    /** The bill as of today: recorded transactions plus this term's charges, which are not stored. */
    public Bill generateBill(String studentId) {
        StudentRecord r = readable(studentId);
        List<Transaction> all = new ArrayList<>(r.transactions());
        all.addAll(calculator.chargesFor(r, LocalDate.now(clock)));
        return new Bill(r.student(), r.college(), r.classStatus(), all, BillCalculator.balance(all));
    }

    /** Recorded transactions between two dates, inclusive, and what they add up to. */
    public Bill viewCharges(String studentId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Start date is after end date");
        }
        StudentRecord r = readable(studentId);
        List<Transaction> inRange = r.transactions().stream()
                .filter(t -> !t.date().isBefore(from) && !t.date().isAfter(to)).toList();
        return new Bill(r.student(), r.college(), r.classStatus(), inRange, BillCalculator.balance(inRange));
    }

    /** The original signature, with month, day and year passed separately. */
    public Bill viewCharges(String studentId, int startMonth, int startDay, int startYear, int endMonth, int endDay, int endYear) {
        try {
            return viewCharges(studentId, LocalDate.of(startYear, startMonth, startDay), LocalDate.of(endYear, endMonth, endDay));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid date: " + e.getMessage());
        }
    }

    // --- changing ---

    /**
     * Records a payment. Only the student may pay their own bill, and a payment can't exceed what is owed.
     * Payments are always saved.
     */
    public BigDecimal applyPayment(String studentId, String amount, String note) {
        User user = requireUser();
        if (user.role() != Role.STUDENT || !user.id().equals(studentId)) {
            throw new AccessDeniedException("Only the student can pay their own bill");
        }
        BigDecimal value = Money.parsePositive(amount);
        BigDecimal owed = generateBill(studentId).balance();
        if (value.compareTo(owed) > 0) {
            throw new IllegalArgumentException("Payment of " + value + " is more than the " + owed + " owed");
        }
        String cleanNote = Optional.ofNullable(note).map(String::strip).filter(s -> !s.isEmpty()).orElse("Payment");
        if (cleanNote.length() > 200) {
            throw new IllegalArgumentException("Note is longer than 200 characters");
        }
        StudentRecord updated = records.get(studentId).withTransaction(
                new Transaction(TransactionType.PAYMENT, LocalDate.now(clock), value, cleanNote));
        replace(studentId, updated, true);
        return owed.subtract(value);
    }

    /**
     * Replaces a record. Students may change only their contact details; admins may change any field of a
     * student in their college except the id and the transaction history. {@code permanent} saves to the store.
     */
    public void editRecord(String studentId, StudentRecord changed, boolean permanent) {
        User user = requireUser();
        StudentRecord existing = readable(studentId);
        if (changed == null || changed.student() == null || !studentId.equals(changed.student().id())) {
            throw new IllegalArgumentException("The record's student id must stay " + studentId);
        }
        if (!changed.transactions().equals(existing.transactions())) {
            throw new AccessDeniedException("Transactions can't be edited; record a payment instead");
        }
        StudentRecord result;
        if (user.role() == Role.STUDENT) {
            if (!changed.withStudent(existing.student()).equals(existing)) {
                throw new AccessDeniedException("Students may change only their contact details");
            }
            result = existing.withStudent(changed.student());
        } else {
            if (changed.college() != user.college()) {
                throw new AccessDeniedException("Admins can't move a student to another college");
            }
            result = changed;
        }
        List<String> problems = result.validate();
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Invalid record: " + String.join("; ", problems));
        }
        replace(studentId, result, permanent);
    }

    // --- helpers ---

    private User requireUser() {
        if (current == null) {
            throw new AccessDeniedException("Nobody is logged in");
        }
        return current;
    }

    private StudentRecord readable(String studentId) {
        User user = requireUser();
        StudentRecord r = records.get(studentId);
        boolean allowed = r != null && (user.role() == Role.STUDENT ? user.id().equals(studentId) : r.college() == user.college());
        if (!allowed) {
            // The same answer for "missing" and "not yours", so ids can't be probed.
            throw new AccessDeniedException("No accessible record for " + studentId);
        }
        return r;
    }

    private void replace(String studentId, StudentRecord record, boolean permanent) {
        StudentRecord previous = records.put(studentId, record);
        if (permanent) {
            try {
                store.saveRecords(List.copyOf(records.values()));
            } catch (IOException e) {
                records.put(studentId, previous);   // keep memory and file in step
                throw new UncheckedIOException("Could not save records", e);
            }
        }
    }
}
