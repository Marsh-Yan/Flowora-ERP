package com.flowora.erp.finance.v2;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class FinancePostingPolicy {
    private FinancePostingPolicy() {
    }

    public static void requireBalanced(List<PostingLine> lines) {
        if (lines == null || lines.size() < 2) throw invalid("JOURNAL_REQUIRES_TWO_LINES");
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (PostingLine line : lines) {
            BigDecimal dr = amount(line.debit());
            BigDecimal cr = amount(line.credit());
            if ((dr.signum() > 0) == (cr.signum() > 0)) throw invalid("JOURNAL_LINE_REQUIRES_ONE_SIDE");
            debit = debit.add(dr);
            credit = credit.add(cr);
        }
        if (debit.signum() <= 0 || debit.compareTo(credit) != 0) throw invalid("JOURNAL_NOT_BALANCED");
    }

    public static BigDecimal base(BigDecimal transactionAmount, BigDecimal exchangeRate) {
        if (transactionAmount == null || exchangeRate == null || exchangeRate.signum() <= 0) {
            throw invalid("INVALID_EXCHANGE_RATE");
        }
        return transactionAmount.multiply(exchangeRate).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private static PlatformApiException invalid(String code) {
        return new PlatformApiException(HttpStatus.CONFLICT, code, "errors.financePostingInvalid");
    }

    public record PostingLine(String accountCode, String description, BigDecimal debit,
                              BigDecimal credit, String projectId, String partyType,
                              String partyId, String sourceLineId) {
        public static PostingLine debit(String account, String description, BigDecimal amount,
                                        String projectId, String partyType, String partyId, String sourceLineId) {
            return new PostingLine(account, description, amount, BigDecimal.ZERO, projectId, partyType, partyId, sourceLineId);
        }

        public static PostingLine credit(String account, String description, BigDecimal amount,
                                         String projectId, String partyType, String partyId, String sourceLineId) {
            return new PostingLine(account, description, BigDecimal.ZERO, amount, projectId, partyType, partyId, sourceLineId);
        }
    }
}
