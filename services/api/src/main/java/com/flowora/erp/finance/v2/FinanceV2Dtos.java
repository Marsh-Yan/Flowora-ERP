package com.flowora.erp.finance.v2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FinanceV2Dtos {
    private FinanceV2Dtos() {
    }

    public record InvoiceSourceCreate(
            @NotBlank String sourceType,
            @NotBlank String sourceId,
            String sourceLineId,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal amount
    ) {
    }

    public record InvoiceLineCreate(
            String itemId,
            @NotBlank @Size(max = 240) String description,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice,
            @NotNull @DecimalMin("0") BigDecimal discountRate,
            @NotNull @DecimalMin("0") BigDecimal taxRate,
            String accountCode,
            String projectId,
            @Valid List<InvoiceSourceCreate> sources
    ) {
    }

    public record InvoiceCreate(
            @NotBlank String documentType,
            @NotBlank String partyId,
            String projectId,
            String originalInvoiceId,
            @NotNull LocalDate businessDate,
            @NotNull LocalDate accountingDate,
            @NotNull LocalDate dueDate,
            @NotNull LocalDate exchangeRateDate,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotNull @Positive BigDecimal exchangeRate,
            @NotEmpty @Valid List<InvoiceLineCreate> lines
    ) {
    }

    public record MatchExceptionApproval(@NotBlank @Size(max = 500) String reason) {
    }

    public record StockInvoiceSourceView(String documentType, String sourceType, String sourceId,
                                         String sourceLineId, String sourceNumber, String orderNumber,
                                         String partyId, String partyName, String itemId, String description,
                                         String currencyCode, BigDecimal quantity, BigDecimal remainingQuantity,
                                         BigDecimal unitPrice, BigDecimal discountRate, BigDecimal taxRate) {}

    public record InvoiceSourceView(String sourceType, String sourceId, String sourceLineId,
                                    BigDecimal quantity, BigDecimal amount) {
    }

    public record InvoiceLineView(String id, int lineNo, String itemId, String description,
                                  BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate,
                                  BigDecimal taxRate, BigDecimal netAmount, BigDecimal taxAmount,
                                  BigDecimal totalAmount, BigDecimal baseTotalAmount, String accountCode,
                                  String projectId, BigDecimal creditedQuantity, String reversalAccountCode,
                                  BigDecimal matchQuantityVariance, BigDecimal matchPriceVarianceRate,
                                  BigDecimal matchTaxVariance, List<InvoiceSourceView> sources) {
    }

    public record InvoiceView(String id, String number, String documentType, String partyType,
                              String partyId, String originalInvoiceId, String projectId, String status,
                              String settlementStatus, String creditStatus, LocalDate businessDate,
                              LocalDate accountingDate, LocalDate dueDate, LocalDate exchangeRateDate,
                              String currencyCode, String baseCurrencyCode, BigDecimal exchangeRate,
                              BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                              BigDecimal baseTotalAmount, BigDecimal allocatedAmount,
                              BigDecimal creditedAmount, String matchStatus, String matchExceptionApprovedBy, String matchExceptionReason, LocalDateTime postedAt,
                              long version, List<InvoiceLineView> lines) {
    }

    public record PaymentCreate(
            @NotBlank String paymentType,
            @NotBlank String partyId,
            String bankAccountId,
            @NotNull LocalDate businessDate,
            @NotNull LocalDate accountingDate,
            @NotNull LocalDate exchangeRateDate,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotNull @Positive BigDecimal exchangeRate,
            @NotNull @Positive BigDecimal amount,
            @Size(max = 160) String reference,
            String originalPaymentId
    ) {
        public PaymentCreate(String paymentType, String partyId, String bankAccountId,
                             LocalDate businessDate, LocalDate accountingDate, LocalDate exchangeRateDate,
                             String currencyCode, BigDecimal exchangeRate, BigDecimal amount, String reference) {
            this(paymentType, partyId, bankAccountId, businessDate, accountingDate, exchangeRateDate,
                    currencyCode, exchangeRate, amount, reference, null);
        }
    }

    public record AllocationCreate(@NotBlank String invoiceId, @NotNull @Positive BigDecimal amount) {
    }

    public record AllocationReverse(@NotBlank @Size(max = 500) String reason) {
    }

    public record AllocationView(String id, String paymentId, String invoiceId, BigDecimal amount,
                                 BigDecimal baseAmount, BigDecimal realizedExchangeDifference,
                                 String status, LocalDateTime allocatedAt, LocalDateTime reversedAt) {
    }

    public record PaymentView(String id, String number, String paymentType, String partyType,
                              String partyId, String bankAccountId, String status,
                              String allocationStatus, LocalDate businessDate, LocalDate accountingDate,
                              LocalDate exchangeRateDate, String currencyCode, String baseCurrencyCode,
                              BigDecimal exchangeRate, BigDecimal amount, BigDecimal baseAmount,
                              BigDecimal allocatedAmount, String reference, String reversalOfId,
                              LocalDateTime postedAt, long version, String originalPaymentId, List<AllocationView> allocations) {
    }

    public record JournalLineView(int lineNo, String accountCode, String description,
                                  BigDecimal debit, BigDecimal credit, BigDecimal baseDebit,
                                  BigDecimal baseCredit, String currencyCode, String projectId,
                                  String partyType, String partyId, String sourceLineId) {
    }

    public record JournalView(String id, String number, String sourceType, String sourceId,
                              String reversalOfId, LocalDate businessDate, LocalDate accountingDate,
                              LocalDate exchangeRateDate, String currencyCode, String baseCurrencyCode,
                              BigDecimal exchangeRate, BigDecimal totalDebit, BigDecimal totalCredit,
                              String status, LocalDateTime postedAt, List<JournalLineView> lines) {
    }

    public record PeriodAction(@NotBlank @Size(max = 500) String reason) {
    }

    public record PeriodCloseView(String periodId, String status, int failedChecks,
                                  List<CloseCheckView> checks) {
    }

    public record CloseCheckView(String code, String status, int count, String details) {
    }

    public record BankAccountView(String id, String code, String name, String currencyCode) {}

    public record StatementLineCreate(@NotNull LocalDate transactionDate, LocalDate valueDate,
                                      @NotNull BigDecimal amount, @NotBlank String currencyCode,
                                      @NotBlank @Size(max = 160) String externalReference,
                                      String counterparty, String description) {
    }

    public record StatementImport(@NotBlank String bankAccountId,
                                  @NotEmpty @Valid List<StatementLineCreate> lines) {
    }

    public record StatementLineView(String id, String bankAccountId, LocalDate transactionDate,
                                    LocalDate valueDate, BigDecimal amount, String currencyCode,
                                    String externalReference, String counterparty, String description,
                                    String reconciliationStatus, String importBatchId) {
    }

    public record ReconciliationLinkCreate(@NotBlank String statementLineId,
                                           @NotBlank String paymentId,
                                           @NotNull @Positive BigDecimal matchedAmount) {
    }

    public record ReconciliationCreate(@NotBlank String bankAccountId,
                                       @NotEmpty @Valid List<ReconciliationLinkCreate> links) {
    }

    public record ReconciliationLinkView(String statementLineId, String paymentId, BigDecimal matchedAmount) {}

    public record ReconciliationView(String id, String number, String bankAccountId, String status,
                                     BigDecimal totalStatementAmount, BigDecimal totalPaymentAmount,
                                     BigDecimal differenceAmount, LocalDateTime confirmedAt,
                                     LocalDateTime reversedAt, List<ReconciliationLinkView> links) {
    }

    public record BudgetLineCreate(int month, @NotBlank String accountCode, String departmentId,
                                   String projectId, @NotNull @DecimalMin("0") BigDecimal amount) {
    }

    public record BudgetCreate(@NotBlank String name, int fiscalYear, @NotBlank String controlPolicy,
                               @NotEmpty @Valid List<BudgetLineCreate> lines) {
    }

    public record BudgetExecutionView(String accountCode, String projectId, BigDecimal budget,
                                      BigDecimal actual, BigDecimal committed, BigDecimal available) {
    }

    public record RevaluationCreate(@NotNull LocalDate accountingDate,
                                    @NotBlank String currencyCode,
                                    @NotNull @Positive BigDecimal rate,
                                    LocalDate reversalDate) {
    }

    public record RevaluationView(String id, String number, LocalDate accountingDate,
                                  String currencyCode, BigDecimal rate, BigDecimal totalGain,
                                  BigDecimal totalLoss, String journalEntryId, LocalDate reversalDate) {
    }

    public record RevaluationHistoryView(String id, String number, LocalDate accountingDate,
                                         String currencyCode, BigDecimal rate, BigDecimal totalGain,
                                         BigDecimal totalLoss, String journalEntryId, String status) {}

    public record TrialBalanceRow(String accountCode, BigDecimal debit, BigDecimal credit,
                                  BigDecimal balance) {
    }

    public record FinanceDashboard(BigDecimal receivables, BigDecimal payables, BigDecimal cash,
                                   BigDecimal revenue, BigDecimal expense, BigDecimal netIncome,
                                   BigDecimal trialDebit, BigDecimal trialCredit,
                                   int unmatchedBankLines, int matchExceptions) {
    }
}
