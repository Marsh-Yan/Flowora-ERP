package com.flowora.erp.inventory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockBalanceEntityTest {
    @Test
    void calculatesMovingWeightedAverageWhenReceivingAtANewCost() {
        StockBalanceEntity balance = new StockBalanceEntity(
                "org-a", "warehouse-a", "item-a", new BigDecimal("10"), new BigDecimal("10")
        );

        balance.receive(new BigDecimal("5"), new BigDecimal("20"));

        assertThat(balance.quantity()).isEqualByComparingTo("15");
        assertThat(balance.averageCost()).isEqualByComparingTo("13.3333");
        assertThat(balance.inventoryValue()).isEqualByComparingTo("199.9995");
    }

    @Test
    void rejectsADecreaseLargerThanAvailableStock() {
        StockBalanceEntity balance = new StockBalanceEntity(
                "org-a", "warehouse-a", "item-a", new BigDecimal("3"), new BigDecimal("10")
        );

        assertThatThrownBy(() -> balance.decrease(new BigDecimal("3.0001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }
}
