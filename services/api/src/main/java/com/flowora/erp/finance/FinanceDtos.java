package com.flowora.erp.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinanceDtos {
    private FinanceDtos() {
    }

    public record JournalLineInput(
            @NotBlank String accountCode,
            @Size(max = 200) String description,
            @NotNull @DecimalMin("0.0") BigDecimal debit,
            @NotNull @DecimalMin("0.0") BigDecimal credit
    ) {
    }

    public record ManualJournalCreate(
            @NotNull LocalDate entryDate,
            @NotBlank @Size(max = 500) String memo,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotEmpty @Size(min = 2, max = 50) List<@Valid JournalLineInput> lines
    ) {
    }

    public record JournalLineResponse(
            int lineNo,
            String accountCode,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            String currencyCode
    ) {
    }

    public record JournalEntryResponse(
            String id,
            String number,
            String periodId,
            LocalDate entryDate,
            String sourceType,
            String sourceId,
            String memo,
            String currencyCode,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            JournalEntryStatus status,
            List<JournalLineResponse> lines
    ) {
    }

    public record AccountingPeriodResponse(
            String id,
            int year,
            int month,
            LocalDate startDate,
            LocalDate endDate,
            AccountingPeriodStatus status
    ) {
    }

    public record TrialBalanceRow(
            String accountCode,
            String accountName,
            String accountType,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance
    ) {
    }

    public record TrialBalanceResponse(
            LocalDate from,
            LocalDate to,
            List<TrialBalanceRow> rows,
            BigDecimal totalDebit,
            BigDecimal totalCredit
    ) {
    }

    public record StatementRow(
            String accountCode,
            String accountName,
            String accountType,
            BigDecimal amount
    ) {
    }

    public record FinancialStatementResponse(
            LocalDate from,
            LocalDate to,
            List<StatementRow> rows,
            BigDecimal total
    ) {
    }

    public record AgingRow(
            String documentType,
            String number,
            String partyId,
            LocalDate dueDate,
            BigDecimal outstandingAmount,
            long daysOverdue,
            String bucket
    ) {
    }

    public record PayableResponse(
            String id,
            String number,
            String purchaseReceiptId,
            String supplierId,
            String sourceType,
            String currencyCode,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            PayableStatus status,
            LocalDate dueDate
    ) {
    }

    public record SupplierPaymentCreate(
            @NotBlank String payableId,
            @NotNull @DecimalMin("0.0001") BigDecimal amount,
            @NotNull FinancePaymentMethod method,
            @NotNull LocalDate paymentDate,
            @Size(max = 120) String reference
    ) {
    }

    public record SupplierPaymentResponse(
            String id,
            String number,
            String payableId,
            String supplierId,
            BigDecimal amount,
            String currencyCode,
            FinancePaymentMethod method,
            LocalDate paymentDate,
            String reference
    ) {
    }
}
