package com.flowora.erp.project;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record ProjectCreate(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            String customerId,
            String salesOrderId,
            String managerUserId,
            @NotNull LocalDate targetDate,
            @DecimalMin("0.00") BigDecimal budgetRevenue,
            @DecimalMin("0.00") BigDecimal budgetCost,
            @Size(min = 3, max = 3) String currencyCode
    ) {
    }

    public record StatusChange(@NotNull ProjectStatus status) {
    }

    public record MilestoneCreate(
            @NotBlank @Size(max = 160) String name,
            Integer sequenceNo,
            LocalDate targetDate
    ) {
    }

    public record TaskCreate(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            String milestoneId,
            String assigneeUserId,
            ProjectTaskPriority priority,
            LocalDate dueDate,
            @DecimalMin("0.00") BigDecimal estimatedHours
    ) {
    }

    public record TaskStatusChange(@NotNull ProjectTaskStatus status) {
    }

    public record TimesheetCreate(
            String taskId,
            @NotNull LocalDate workDate,
            @NotNull @DecimalMin("0.01") BigDecimal hours,
            @NotNull @DecimalMin("0.00") BigDecimal costRate,
            @NotNull @DecimalMin("0.00") BigDecimal billingRate,
            boolean billable,
            @Size(min = 3, max = 3) String currencyCode,
            @Size(max = 500) String note
    ) {
    }

    public record ExpenseCreate(
            String taskId,
            @NotNull LocalDate expenseDate,
            @NotBlank @Size(max = 64) String category,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            boolean billable,
            @Size(min = 3, max = 3) String currencyCode,
            @Size(max = 500) String description
    ) {
    }

    public record BudgetCreate(
            @NotBlank @Size(max = 64) String category,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @Size(min = 3, max = 3) String currencyCode,
            @Size(max = 500) String note
    ) {
    }

    public record ProjectResponse(
            String id, String number, String name, String description, String customerId, String salesOrderId,
            String managerUserId, LocalDate targetDate, BigDecimal budgetRevenue, BigDecimal budgetCost,
            String currencyCode, ProjectStatus status, BigDecimal progressPercent, BigDecimal actualCost,
            BigDecimal actualHours, BigDecimal billableAmount, int completedTaskCount, int totalTaskCount
    ) {
    }

    public record MilestoneResponse(String id, String projectId, String name, int sequenceNo, LocalDate targetDate, MilestoneStatus status) {
    }

    public record TaskResponse(String id, String projectId, String milestoneId, String title, String description,
                               String assigneeUserId, ProjectTaskPriority priority, LocalDate dueDate,
                               ProjectTaskStatus status, BigDecimal estimatedHours, BigDecimal actualHours) {
    }

    public record TimesheetResponse(String id, String projectId, String taskId, String userId, LocalDate workDate,
                                    BigDecimal hours, BigDecimal costAmount, BigDecimal billableAmount,
                                    boolean billable, String currencyCode, String note) {
    }

    public record ExpenseResponse(String id, String projectId, String taskId, String userId, LocalDate expenseDate,
                                  String category, BigDecimal amount, BigDecimal billableAmount, boolean billable,
                                  String currencyCode, String description) {
    }

    public record BudgetResponse(String id, String projectId, String category, BigDecimal amount, String currencyCode, String note) {
    }

    public record BillingBasisRow(String type, String id, LocalDate date, String taskId, String description,
                                  BigDecimal quantity, BigDecimal amount, String currencyCode, String sourceUserId) {
    }
}
