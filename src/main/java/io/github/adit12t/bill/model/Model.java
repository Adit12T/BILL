package io.github.adit12t.bill.model;

import io.github.adit12t.bill.model.Enums.ClassStatus;
import io.github.adit12t.bill.model.Enums.College;
import io.github.adit12t.bill.model.Enums.InternationalStatus;
import io.github.adit12t.bill.model.Enums.Role;
import io.github.adit12t.bill.model.Enums.Scholarship;
import io.github.adit12t.bill.model.Enums.Season;
import io.github.adit12t.bill.model.Enums.StudyAbroad;
import io.github.adit12t.bill.model.Enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** The immutable records that make up users, student records and bills. */
public final class Model {
    private Model() {
    }

    /** A system user. Admins manage the students of their own college. */
    public record User(String id, String firstName, String lastName, Role role, College college) {
        public User {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(role, "role");
        }
    }

    public record Term(Season season, int year) {
        public Term {
            Objects.requireNonNull(season, "season");
        }

        @Override
        public String toString() {
            return season + " " + year;
        }
    }

    /** Contact details: the only part of a record a student may change. */
    public record Student(String id, String firstName, String lastName, String email, String phone, String address) {
    }

    public record Course(String id, String name, int credits, boolean online) {
    }

    public record Transaction(TransactionType type, LocalDate date, BigDecimal amount, String note) {
        public Transaction {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(date, "date");
            amount = Money.normalize(amount);
        }

        /** Positive for charges, negative for payments. */
        public BigDecimal signedAmount() {
            return type == TransactionType.CHARGE ? amount : amount.negate();
        }
    }

    public record StudentRecord(Student student, College college, Term termBegin, ClassStatus classStatus,
                                boolean gradAssistant, boolean international, InternationalStatus internationalStatus,
                                boolean resident, boolean activeDuty, boolean veteran, boolean freeTuition,
                                Scholarship scholarship, StudyAbroad studyAbroad, boolean nationalStudentExchange,
                                boolean outsideInsurance, List<Course> courses, List<Transaction> transactions) {

        private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

        public StudentRecord {
            courses = courses == null ? List.of() : List.copyOf(courses);
            transactions = transactions == null ? List.of() : List.copyOf(transactions);
            scholarship = scholarship == null ? Scholarship.NONE : scholarship;
            studyAbroad = studyAbroad == null ? StudyAbroad.NONE : studyAbroad;
            internationalStatus = internationalStatus == null ? InternationalStatus.NONE : internationalStatus;
        }

        public int totalCredits() {
            return courses.stream().mapToInt(Course::credits).sum();
        }

        public StudentRecord withTransaction(Transaction t) {
            List<Transaction> all = new ArrayList<>(transactions);
            all.add(t);
            return new StudentRecord(student, college, termBegin, classStatus, gradAssistant, international, internationalStatus,
                    resident, activeDuty, veteran, freeTuition, scholarship, studyAbroad, nationalStudentExchange,
                    outsideInsurance, courses, all);
        }

        public StudentRecord withStudent(Student s) {
            return new StudentRecord(s, college, termBegin, classStatus, gradAssistant, international, internationalStatus,
                    resident, activeDuty, veteran, freeTuition, scholarship, studyAbroad, nationalStudentExchange,
                    outsideInsurance, courses, transactions);
        }

        /** Returns every problem with the record; an empty list means it is valid. */
        public List<String> validate() {
            List<String> problems = new ArrayList<>();
            if (student == null || student.id() == null || student.id().isBlank()) {
                problems.add("student id is required");
                return problems;
            }
            if (student.email() != null && !EMAIL.matcher(student.email()).matches()) {
                problems.add("email is not valid");
            }
            if (college == null) problems.add("college is required");
            if (classStatus == null) problems.add("class status is required");
            if (termBegin == null) problems.add("starting term is required");
            if (international && internationalStatus == InternationalStatus.NONE) {
                problems.add("international students need an international status");
            }
            if (!international && internationalStatus != InternationalStatus.NONE) {
                problems.add("international status is set but the student is not international");
            }
            if (gradAssistant && classStatus != null && classStatus.undergraduate()) {
                problems.add("only graduate students can be graduate assistants");
            }
            for (Course c : courses) {
                if (c.credits() < 0 || c.credits() > 6) {
                    problems.add("course " + c.id() + " must have 0 to 6 credits");
                }
            }
            if (totalCredits() > 24) problems.add("more than 24 credits in a term");
            return problems;
        }
    }

    /** A bill: the student, the transactions it covers and the balance they add up to. */
    public record Bill(Student student, College college, ClassStatus classStatus, List<Transaction> transactions,
                       BigDecimal balance) {
        public Bill {
            transactions = List.copyOf(transactions);
            balance = Money.normalize(balance);
        }
    }
}
