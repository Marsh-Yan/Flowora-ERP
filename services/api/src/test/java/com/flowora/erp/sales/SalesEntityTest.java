package com.flowora.erp.sales;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesEntityTest {
    @Test
    void calculatesDiscountAndTaxInTheExpectedOrder() {
        SalesQuoteLineEntity line = new SalesQuoteLineEntity(
                "org-a", "quote-a", "item-a", new BigDecimal("2"), new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("13")
        );

        assertThat(line.lineTotal()).isEqualByComparingTo("203.4000");
    }

    @Test
    void partialFulfillmentTracksRemainingQuantityAndRejectsOverDelivery() {
        SalesOrderLineEntity line = new SalesOrderLineEntity(
                "org-a", "order-a", "item-a", new BigDecimal("10"), new BigDecimal("20"), BigDecimal.ZERO, BigDecimal.ZERO
        );

        line.fulfill(new BigDecimal("4"));

        assertThat(line.fulfilledQuantity()).isEqualByComparingTo("4");
        assertThat(line.remainingQuantity()).isEqualByComparingTo("6");
        assertThatThrownBy(() -> line.fulfill(new BigDecimal("7")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receivableSupportsSplitPaymentsAndSettlesExactlyAtTheTotal() {
        ReceivableEntity receivable = new ReceivableEntity(
                "org-a", "AR-1", "order-a", "customer-a", "SALES_ORDER", "order-a", "USD", new BigDecimal("100"), LocalDate.now()
        );

        receivable.recordPayment(new BigDecimal("40"));
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.PARTIALLY_SETTLED);
        assertThat(receivable.remainingAmount()).isEqualByComparingTo("60");

        receivable.recordPayment(new BigDecimal("60"));
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.SETTLED);
        assertThat(receivable.remainingAmount()).isEqualByComparingTo("0");
    }
}
