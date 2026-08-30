package com.flowora.erp.trade.v2;

import com.flowora.erp.common.api.PlatformApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryQuantityPolicyTest {
    @Test
    void calculatesAvailableStockWithoutReturningNegativeValues() {
        assertThat(InventoryQuantityPolicy.available(new BigDecimal("10"), new BigDecimal("3.5"), false))
                .isEqualByComparingTo("6.5");
        assertThat(InventoryQuantityPolicy.available(new BigDecimal("2"), new BigDecimal("3"), false))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(InventoryQuantityPolicy.available(new BigDecimal("10"), BigDecimal.ZERO, true))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void preventsReservationAndShipmentFromExceedingProtectedQuantities() {
        assertThatThrownBy(() -> InventoryQuantityPolicy.requireReservable(
                new BigDecimal("5"), new BigDecimal("4"), new BigDecimal("2"), false))
                .isInstanceOf(PlatformApiException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_AVAILABLE_STOCK");

        assertThatThrownBy(() -> InventoryQuantityPolicy.requireShippable(
                new BigDecimal("5"), new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("2"), false))
                .isInstanceOf(PlatformApiException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_AVAILABLE_STOCK");
    }

    @Test
    void calculatesWeightedAverageCostAtFixedPrecision() {
        assertThat(InventoryQuantityPolicy.inboundAverageCost(
                new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("3"), new BigDecimal("20")))
                .isEqualByComparingTo("16.00000000");
    }
}
