package com.flowora.erp.trade.v2;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TradeAmountPolicy {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private TradeAmountPolicy() {
    }

    public static Amounts calculate(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal taxRate) {
        requireNonNegative(unitPrice, "unitPrice");
        requireRate(discountRate, "discountRate");
        requireRate(taxRate, "taxRate");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        BigDecimal net = quantity.multiply(unitPrice)
                .multiply(BigDecimal.ONE.subtract(discountRate.divide(HUNDRED, 8, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal tax = net.multiply(taxRate).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return new Amounts(net, tax, net.add(tax).setScale(4, RoundingMode.HALF_UP));
    }

    private static void requireRate(BigDecimal value, String field) {
        requireNonNegative(value, field);
        if (value.compareTo(HUNDRED) > 0) throw new IllegalArgumentException(field + " must not exceed 100");
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(field + " must be non-negative");
    }

    public record Amounts(BigDecimal net, BigDecimal tax, BigDecimal gross) {
    }
}
