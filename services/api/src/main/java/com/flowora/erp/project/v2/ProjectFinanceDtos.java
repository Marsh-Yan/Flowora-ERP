package com.flowora.erp.project.v2;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectFinanceDtos {
    private ProjectFinanceDtos() {
    }

    public record BillingConfiguration(@NotBlank String billingMode,
                                       @NotNull BigDecimal contractAmount,
                                       String departmentId) {
    }

    public record MemberCreate(@NotBlank String userId, @NotBlank String projectRole) {
    }

    public record MemberView(String id, String userId, String projectRole, boolean active) {
    }

    public record ApprovalAction(boolean approved, String reason) {
    }

    public record BillingBasisView(String id, String projectId, String basisType, String sourceId,
                                   String description, BigDecimal availableAmount, BigDecimal billedAmount,
                                   BigDecimal remainingAmount, String currencyCode, String status,
                                   LocalDateTime approvedAt) {
    }

    public record ProjectInvoiceLine(@NotBlank String billingBasisId,
                                     @NotNull @Positive BigDecimal amount,
                                     BigDecimal taxRate,
                                     String accountCode) {
    }

    public record ProjectInvoiceCreate(@NotNull LocalDate businessDate,
                                       @NotNull LocalDate accountingDate,
                                       @NotNull LocalDate dueDate,
                                       @NotNull LocalDate exchangeRateDate,
                                       @NotBlank String currencyCode,
                                       @NotNull @Positive BigDecimal exchangeRate,
                                       @NotEmpty List<ProjectInvoiceLine> lines) {
    }

    public record ProjectProfitView(String projectId, String currencyCode, BigDecimal contractAmount,
                                    BigDecimal availableBilling, BigDecimal billedAmount,
                                    BigDecimal postedRevenue, BigDecimal postedCost,
                                    BigDecimal managementLaborCost, BigDecimal grossProfit,
                                    BigDecimal budgetRevenueVariance, BigDecimal budgetCostVariance,
                                    int openTasks, int pendingApprovals, int unsettledInvoices,
                                    boolean closeEligible) {
    }
}
