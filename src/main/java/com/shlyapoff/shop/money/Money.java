package com.shlyapoff.shop.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Monetary value with one rounding policy for the whole application. */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(SCALE, ROUNDING_MODE);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative");
        }
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount == null ? BigDecimal.ZERO : amount);
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("Money result cannot be negative");
        }
        return new Money(result);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public Money percentage(BigDecimal percent) {
        Objects.requireNonNull(percent, "percent");
        if (percent.signum() < 0) {
            throw new IllegalArgumentException("Percent cannot be negative");
        }
        return new Money(amount.multiply(percent).movePointLeft(2));
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }
}
