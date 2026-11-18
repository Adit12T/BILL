package io.github.adit12t.bill;

import io.github.adit12t.bill.model.Enums.ClassStatus;
import io.github.adit12t.bill.model.Enums.College;
import io.github.adit12t.bill.model.Enums.InternationalStatus;
import io.github.adit12t.bill.model.Enums.Role;
import io.github.adit12t.bill.model.Enums.Scholarship;
import io.github.adit12t.bill.model.Enums.Season;
import io.github.adit12t.bill.model.Enums.StudyAbroad;
import io.github.adit12t.bill.model.Enums.TransactionType;
import io.github.adit12t.bill.model.Model.Bill;
import io.github.adit12t.bill.model.Model.Course;
import io.github.adit12t.bill.model.Model.Student;
import io.github.adit12t.bill.model.Model.StudentRecord;
import io.github.adit12t.bill.model.Model.Term;
import io.github.adit12t.bill.model.Model.Transaction;
import io.github.adit12t.bill.model.Model.User;
import io.github.adit12t.bill.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    /** Keeps everything in memory and counts saves. */
    static final class MemoryStore implements RecordStore {
        List<User> users;
        List<StudentRecord> records;
        int saves;
        boolean failSaves;

        MemoryStore(List<User> users, List<StudentRecord> records) {
            this.users = users;
            this.records = records;
        }

        public List<User> loadUsers() { return users; }

        public List<StudentRecord> loadRecords() { return records; }

        public void saveRecords(List<StudentRecord> r) throws java.io.IOException {
            if (failSaves) throw new java.io.IOException("disk full");
            records = r;
            saves++;
        }
    }

    static List<Course> courses(int count, int credits, boolean online) {
        return IntStream.range(0, count).mapToObj(i -> new Course("C" + i, "Course " + i, credits, online)).toList();
    }

    static StudentRecord record(String id, College college, ClassStatus status, boolean resident, List<Course> courses) {
        return new StudentRecord(new Student(id, "First", "Last", id + "@example.edu", "555", "Addr"), college,
                new Term(Season.FALL, 2025), status, false, false, InternationalStatus.NONE, resident, false, false, false,
                Scholarship.NONE, StudyAbroad.NONE, false, false, courses, List.of());
    }

    static StudentRecord copy(StudentRecord r, java.util.function.UnaryOperator<Object[]> change) {
        Object[] f = {r.student(), r.college(), r.termBegin(), r.classStatus(), r.gradAssistant(), r.international(),
                r.internationalStatus(), r.resident(), r.activeDuty(), r.veteran(), r.freeTuition(), r.scholarship(),
                r.studyAbroad(), r.nationalStudentExchange(), r.outsideInsurance(), r.courses(), r.transactions()};
        f = change.apply(f);
        @SuppressWarnings("unchecked")
        StudentRecord out = new StudentRecord((Student) f[0], (College) f[1], (Term) f[2], (ClassStatus) f[3], (boolean) f[4],
                (boolean) f[5], (InternationalStatus) f[6], (boolean) f[7], (boolean) f[8], (boolean) f[9], (boolean) f[10],
                (Scholarship) f[11], (StudyAbroad) f[12], (boolean) f[13], (boolean) f[14], (List<Course>) f[15],
                (List<Transaction>) f[16]);
        return out;
    }

    MemoryStore store;
    BillService service;

    @BeforeEach
    void setUp() throws Exception {
        store = new MemoryStore(
                List.of(new User("admin", "A", "Dmin", Role.ADMIN, College.ENGINEERING_AND_COMPUTING),
                        new User("other-admin", "O", "Ther", Role.ADMIN, College.BUSINESS),
                        new User("s1", "S", "One", Role.STUDENT, College.ENGINEERING_AND_COMPUTING),
                        new User("s2", "S", "Two", Role.STUDENT, College.ENGINEERING_AND_COMPUTING)),
                new ArrayList<>(List.of(
                        record("s1", College.ENGINEERING_AND_COMPUTING, ClassStatus.JUNIOR, true, courses(5, 3, false)),
                        record("s2", College.ENGINEERING_AND_COMPUTING, ClassStatus.SENIOR, false, courses(2, 3, false)))));
        service = new BillService(store, FeeSchedule.illustrative(), CLOCK);
        service.loadUsers();
        service.loadRecords();
    }

    static BigDecimal charge(Bill bill, String noteStart) {
        return bill.transactions().stream().filter(t -> t.note().startsWith(noteStart)).map(Transaction::amount)
                .findFirst().orElse(Money.ZERO);
    }

    @Test
    void fullTimeResidentUndergraduatePaysTheFlatRatePlusFees() {
        service.logIn("s1");
        Bill bill = service.generateBill("s1");
        assertEquals(Money.of("6000.00"), charge(bill, "Tuition"));
        assertEquals(Money.of("200.00"), charge(bill, "Technology fee"), "15 x 17 = 255, capped at 200");
        assertEquals(Money.of("250.00"), charge(bill, "Student health fee"));
        assertEquals(Money.of("6450.00"), bill.balance());
    }

    @Test
    void partTimeNonResidentPaysPerCredit() {
        service.logIn("s2");
        Bill bill = service.generateBill("s2");
        assertEquals(Money.of("7800.00"), charge(bill, "Tuition"), "6 credits x 1300");
        assertEquals(Money.of("102.00"), charge(bill, "Technology fee"));
        assertEquals(Money.of("8152.00"), bill.balance());
    }

    @Test
    void creditsAboveTheFullTimeBandAreChargedPerCredit() {
        var calc = new BillCalculator(FeeSchedule.illustrative());
        var r = record("x", College.BUSINESS, ClassStatus.FRESHMAN, true, courses(6, 3, false));
        assertEquals(Money.of("7000.00"), calc.tuition(r, 18), "flat 6000 + 2 extra credits x 500");
    }

    @Test
    void waiversAssistantshipsScholarshipsAndSpecialFees() {
        var calc = new BillCalculator(FeeSchedule.illustrative());
        var base = record("x", College.GRADUATE_SCHOOL, ClassStatus.MASTERS, false, courses(3, 3, false));

        var assistant = copy(base, f -> { f[4] = true; return f; });
        assertEquals(Money.of("250.00"), calc.tuition(assistant, 9));

        var veteran = copy(base, f -> { f[9] = true; return f; });
        assertEquals(Money.of("5400.00"), calc.tuition(veteran, 9), "veterans pay the in-state rate");

        var free = copy(base, f -> { f[10] = true; return f; });
        assertEquals(Money.ZERO, calc.tuition(free, 9));

        var intl = copy(base, f -> { f[5] = true; f[6] = InternationalStatus.SPONSORED; f[12] = StudyAbroad.COHORT;
            f[13] = true; f[14] = true; return f; });
        var notes = calc.chargesFor(intl, TODAY).stream().map(Transaction::note).toList();
        assertTrue(notes.contains("International student fee (sponsored)"), notes.toString());
        assertTrue(notes.contains("Study abroad cohort fee"));
        assertTrue(notes.contains("National Student Exchange fee"));
        assertTrue(notes.stream().noneMatch(n -> n.startsWith("Student health fee")), "outside insurance waives it");

        var onlineOnly = record("y", College.BUSINESS, ClassStatus.SENIOR, true, courses(4, 3, true));
        assertTrue(calc.chargesFor(onlineOnly, TODAY).stream().noneMatch(t -> t.note().startsWith("Student health")));

        var scholar = copy(record("z", College.BUSINESS, ClassStatus.SENIOR, true, courses(1, 3, false)),
                f -> { f[11] = Scholarship.ATHLETIC; return f; });
        Transaction credit = calc.chargesFor(scholar, TODAY).stream().filter(t -> t.type() == TransactionType.PAYMENT).findFirst().orElseThrow();
        assertEquals(Money.of("1500.00"), credit.amount(), "capped at the 3 x 500 tuition");
    }

    @Test
    void studentsSeeOnlyThemselvesAndAdminsOnlyTheirCollege() {
        service.logIn("s1");
        assertEquals(List.of("s1"), service.getStudentIDs());
        assertThrows(AccessDeniedException.class, () -> service.getRecord("s2"));
        assertThrows(AccessDeniedException.class, () -> service.getRecord("nobody"));

        service.logIn("admin");
        assertEquals(List.of("s1", "s2"), service.getStudentIDs());
        service.logIn("other-admin");
        assertEquals(List.of(), service.getStudentIDs());
        assertThrows(AccessDeniedException.class, () -> service.generateBill("s1"));

        service.logOut();
        assertThrows(AccessDeniedException.class, service::getUser);
        assertThrows(IllegalArgumentException.class, () -> service.logIn("ghost"));
    }

    @Test
    void paymentsReduceTheBalanceAreSavedAndAreValidated() {
        service.logIn("s1");
        assertEquals(Money.of("6000.00"), service.applyPayment("s1", "450.00", "  "));
        assertEquals(1, store.saves);
        Transaction paid = store.records.get(0).transactions().get(0);
        assertEquals("Payment", paid.note());
        assertEquals(TODAY, paid.date());
        assertEquals(Money.of("6000.00"), service.generateBill("s1").balance());

        assertThrows(IllegalArgumentException.class, () -> service.applyPayment("s1", "6000.01", null), "more than owed");
        assertThrows(IllegalArgumentException.class, () -> service.applyPayment("s1", "0", null));
        assertThrows(IllegalArgumentException.class, () -> service.applyPayment("s1", "1.005", null));
        assertThrows(IllegalArgumentException.class, () -> service.applyPayment("s1", "abc", null));
        assertThrows(AccessDeniedException.class, () -> service.applyPayment("s2", "1", null));
        service.logIn("admin");
        assertThrows(AccessDeniedException.class, () -> service.applyPayment("s1", "1", null), "admins don't pay for students");
    }

    @Test
    void viewChargesFiltersByInclusiveDates() {
        service.logIn("s1");
        service.applyPayment("s1", "100", "first");
        assertEquals(1, service.viewCharges("s1", 9, 1, 2026, 9, 1, 2026).transactions().size());
        assertEquals(Money.of("-100.00"), service.viewCharges("s1", TODAY, TODAY).balance());
        assertEquals(0, service.viewCharges("s1", 9, 2, 2026, 12, 31, 2026).transactions().size());
        assertThrows(IllegalArgumentException.class, () -> service.viewCharges("s1", 2, 30, 2026, 3, 1, 2026));
        assertThrows(IllegalArgumentException.class, () -> service.viewCharges("s1", TODAY, TODAY.minusDays(1)));
    }

    @Test
    void studentsMayEditOnlyTheirContactDetails() {
        service.logIn("s1");
        StudentRecord mine = service.getRecord("s1");
        Student newContact = new Student("s1", "First", "Last", "new@example.edu", "555-9999", "New address");
        service.editRecord("s1", mine.withStudent(newContact), true);
        assertEquals("new@example.edu", service.getRecord("s1").student().email());

        StudentRecord cheaper = copy(service.getRecord("s1"), f -> { f[10] = true; return f; });
        assertThrows(AccessDeniedException.class, () -> service.editRecord("s1", cheaper, false));

        StudentRecord badEmail = mine.withStudent(new Student("s1", "F", "L", "not-an-email", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.editRecord("s1", badEmail, false));
    }

    @Test
    void adminsEditWithinTheirCollegeAndTemporaryEditsAreNotSaved() {
        service.logIn("admin");
        StudentRecord s2 = service.getRecord("s2");
        service.editRecord("s2", copy(s2, f -> { f[7] = true; return f; }), false);
        assertTrue(service.getRecord("s2").resident());
        assertEquals(0, store.saves);

        assertThrows(AccessDeniedException.class,
                () -> service.editRecord("s2", copy(s2, f -> { f[1] = College.BUSINESS; return f; }), true));
        assertThrows(AccessDeniedException.class,
                () -> service.editRecord("s2", s2.withTransaction(new Transaction(TransactionType.PAYMENT, TODAY, Money.of("1"), "x")), true));
        assertThrows(IllegalArgumentException.class,
                () -> service.editRecord("s2", copy(s2, f -> { f[4] = true; return f; }), true), "undergraduates can't be assistants");
    }

    @Test
    void aFailedSaveLeavesTheRecordUnchanged() {
        service.logIn("s1");
        store.failSaves = true;
        assertThrows(java.io.UncheckedIOException.class, () -> service.applyPayment("s1", "10", null));
        assertEquals(0, service.getRecord("s1").transactions().size());
    }

    @Test
    void moneyIsExactAndRoundsHalfUp() {
        assertEquals(new BigDecimal("0.30"), Money.of("0.1").add(Money.of("0.2")));
        assertEquals(new BigDecimal("2.68"), Money.normalize(new BigDecimal("2.675")));
    }
}
