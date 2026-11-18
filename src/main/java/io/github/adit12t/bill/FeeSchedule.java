package io.github.adit12t.bill;

import io.github.adit12t.bill.model.Money;

import java.math.BigDecimal;

/**
 * Per-term rates used to build a bill. Load the real figures from JSON; {@link #illustrative()} is a made-up
 * schedule for demos and tests, not any university's actual fees.
 */
public record FeeSchedule(
        BigDecimal undergradResidentPerCredit, BigDecimal undergradNonResidentPerCredit,
        BigDecimal undergradResidentFullTime, BigDecimal undergradNonResidentFullTime,
        BigDecimal gradResidentPerCredit, BigDecimal gradNonResidentPerCredit, BigDecimal gradAssistantFlat,
        BigDecimal technologyFeePerCredit, BigDecimal technologyFeeCap,
        BigDecimal healthFee, BigDecimal internationalShortTermFee, BigDecimal internationalSponsoredFee,
        BigDecimal studyAbroadFee, BigDecimal studyAbroadCohortFee, BigDecimal nationalExchangeFee,
        BigDecimal meritScholarship, BigDecimal athleticScholarship, BigDecimal needBasedScholarship,
        int fullTimeMinCredits, int fullTimeMaxCredits) {

    public FeeSchedule {
        if (fullTimeMinCredits < 1 || fullTimeMaxCredits < fullTimeMinCredits) {
            throw new IllegalArgumentException("Full-time credit band is invalid");
        }
        BigDecimal[] all = {undergradResidentPerCredit, undergradNonResidentPerCredit, undergradResidentFullTime,
                undergradNonResidentFullTime, gradResidentPerCredit, gradNonResidentPerCredit, gradAssistantFlat,
                technologyFeePerCredit, technologyFeeCap, healthFee, internationalShortTermFee, internationalSponsoredFee,
                studyAbroadFee, studyAbroadCohortFee, nationalExchangeFee, meritScholarship, athleticScholarship,
                needBasedScholarship};
        for (BigDecimal amount : all) {
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Every fee must be present and not negative");
            }
        }
    }

    /** Invented figures for demos and tests. */
    public static FeeSchedule illustrative() {
        return new FeeSchedule(
                Money.of("500.00"), Money.of("1300.00"), Money.of("6000.00"), Money.of("16000.00"),
                Money.of("600.00"), Money.of("1300.00"), Money.of("250.00"),
                Money.of("17.00"), Money.of("200.00"),
                Money.of("250.00"), Money.of("100.00"), Money.of("300.00"),
                Money.of("150.00"), Money.of("300.00"), Money.of("250.00"),
                Money.of("2000.00"), Money.of("3000.00"), Money.of("1500.00"),
                12, 16);
    }
}
