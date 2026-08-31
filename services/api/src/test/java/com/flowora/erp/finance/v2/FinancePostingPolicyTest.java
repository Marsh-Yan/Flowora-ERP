package com.flowora.erp.finance.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.finance.v2.FinancePostingPolicy.PostingLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancePostingPolicyTest {
    @Test
    void acceptsBalancedTwoSidedJournal() {
        List<PostingLine> lines = List.of(
                PostingLine.debit("1100", "Receivable", new BigDecimal("113.0000"), null, "CUSTOMER", "c1", null),
                PostingLine.credit("4000", "Revenue", new BigDecimal("100.0000"), null, "CUSTOMER", "c1", null),
                PostingLine.credit("2200", "Tax", new BigDecimal("13.0000"), null, "CUSTOMER", "c1", null)
        );

        FinancePostingPolicy.requireBalanced(lines);
    }

    @Test
    void rejectsUnbalancedAndMixedSideLines() {
        assertThatThrownBy(() -> FinancePostingPolicy.requireBalanced(List.of(
                new PostingLine("1100", "invalid", BigDecimal.ONE, BigDecimal.ONE, null, null, null, null),
                PostingLine.credit("4000", "Revenue", BigDecimal.ONE, null, null, null, null)
        ))).isInstanceOf(PlatformApiException.class);

        assertThatThrownBy(() -> FinancePostingPolicy.requireBalanced(List.of(
                PostingLine.debit("1100", "Receivable", new BigDecimal("10"), null, null, null, null),
                PostingLine.credit("4000", "Revenue", new BigDecimal("9"), null, null, null, null)
        ))).isInstanceOf(PlatformApiException.class);
    }

    @Test
    void convertsTransactionAmountToFourDecimalBaseAmount() {
        assertThat(FinancePostingPolicy.base(new BigDecimal("10.25"), new BigDecimal("7.12345678")))
                .isEqualByComparingTo("73.0154");
    }
}
