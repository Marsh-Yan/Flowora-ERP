package com.flowora.erp.trade.v2;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeAmountPolicyTest {
    @Test
    void freezesDiscountTaxAndRoundingPerLine() {
        TradeAmountPolicy.Amounts amounts = TradeAmountPolicy.calculate(
                new BigDecimal("3"), new BigDecimal("19.9999"), new BigDecimal("10"), new BigDecimal("13"));

        assertThat(amounts.net()).isEqualByComparingTo("53.9997");
        assertThat(amounts.tax()).isEqualByComparingTo("7.0200");
        assertThat(amounts.gross()).isEqualByComparingTo("61.0197");
    }

    @Test
    void rejectsInvalidRatesAndQuantities() {
        assertThatThrownBy(() -> TradeAmountPolicy.calculate(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeAmountPolicy.calculate(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
