package io.github.adit12t.bill.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers: amounts are BigDecimal with 2 decimals, rounded half-up as accounting expects. */
public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() {
    }

    public static BigDecimal of(String amount) {
        return normalize(new BigDecimal(amount));
    }

    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** Parses a payment or charge amount: positive, at most 2 decimals, at most 1,000,000. */
    public static BigDecimal parsePositive(String amount) {
        BigDecimal value;
        try {
            value = new BigDecimal(amount.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not an amount: " + amount);
        }
        if (value.signum() <= 0 || value.scale() > 2 || value.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("Amount must be above 0, at most 1,000,000, with at most 2 decimals: " + amount);
        }
        return normalize(value);
    }
}
