package io.github.adit12t.bill;

import io.github.adit12t.bill.model.Enums.Scholarship;
import io.github.adit12t.bill.model.Enums.TransactionType;
import io.github.adit12t.bill.model.Model.Course;
import io.github.adit12t.bill.model.Model.StudentRecord;
import io.github.adit12t.bill.model.Model.Transaction;
import io.github.adit12t.bill.model.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Works out the charges and credits for one term from a student record and a fee schedule. */
public final class BillCalculator {

    private final FeeSchedule fees;

    public BillCalculator(FeeSchedule fees) {
        this.fees = fees;
    }

    public List<Transaction> chargesFor(StudentRecord r, LocalDate date) {
        List<Transaction> out = new ArrayList<>();
        int credits = r.totalCredits();
        BigDecimal tuition = tuition(r, credits);
        add(out, date, tuition, r.freeTuition() ? "Tuition (waived)" : "Tuition, " + credits + " credits");

        add(out, date, fees.technologyFeePerCredit().multiply(BigDecimal.valueOf(credits)).min(fees.technologyFeeCap()),
                "Technology fee");
        boolean onCampus = r.courses().stream().anyMatch(c -> !c.online());
        if (!r.outsideInsurance() && onCampus && credits >= 6) {
            add(out, date, fees.healthFee(), "Student health fee");
        }
        if (r.international()) {
            switch (r.internationalStatus()) {
                case SHORT_TERM -> add(out, date, fees.internationalShortTermFee(), "International student fee (short term)");
                case SPONSORED -> add(out, date, fees.internationalSponsoredFee(), "International student fee (sponsored)");
                case NONE -> { }
            }
        }
        switch (r.studyAbroad()) {
            case REGULAR -> add(out, date, fees.studyAbroadFee(), "Study abroad fee");
            case COHORT -> add(out, date, fees.studyAbroadCohortFee(), "Study abroad cohort fee");
            case NONE -> { }
        }
        if (r.nationalStudentExchange()) {
            add(out, date, fees.nationalExchangeFee(), "National Student Exchange fee");
        }
        BigDecimal scholarship = scholarship(r.scholarship()).min(tuition);   // a scholarship never exceeds tuition
        if (scholarship.signum() > 0) {
            out.add(new Transaction(TransactionType.PAYMENT, date, scholarship, "Scholarship credit (" + r.scholarship() + ")"));
        }
        return List.copyOf(out);
    }

    BigDecimal tuition(StudentRecord r, int credits) {
        if (r.freeTuition() || credits == 0) {
            return Money.ZERO;
        }
        boolean inState = r.resident() || r.activeDuty() || r.veteran();
        BigDecimal n = BigDecimal.valueOf(credits);
        if (!r.classStatus().undergraduate()) {
            if (r.gradAssistant()) {
                return fees.gradAssistantFlat();
            }
            return (inState ? fees.gradResidentPerCredit() : fees.gradNonResidentPerCredit()).multiply(n);
        }
        BigDecimal perCredit = inState ? fees.undergradResidentPerCredit() : fees.undergradNonResidentPerCredit();
        if (credits < fees.fullTimeMinCredits()) {
            return perCredit.multiply(n);
        }
        BigDecimal flat = inState ? fees.undergradResidentFullTime() : fees.undergradNonResidentFullTime();
        int extra = Math.max(0, credits - fees.fullTimeMaxCredits());
        return flat.add(perCredit.multiply(BigDecimal.valueOf(extra)));
    }

    private BigDecimal scholarship(Scholarship s) {
        return switch (s) {
            case MERIT -> fees.meritScholarship();
            case ATHLETIC -> fees.athleticScholarship();
            case NEED_BASED -> fees.needBasedScholarship();
            case NONE -> Money.ZERO;
        };
    }

    private static void add(List<Transaction> out, LocalDate date, BigDecimal amount, String note) {
        if (amount.signum() > 0) {
            out.add(new Transaction(TransactionType.CHARGE, date, amount, note));
        }
    }

    /** Sum of charges minus payments. */
    public static BigDecimal balance(List<Transaction> transactions) {
        return Money.normalize(transactions.stream().map(Transaction::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static int credits(List<Course> courses) {
        return courses.stream().mapToInt(Course::credits).sum();
    }
}
