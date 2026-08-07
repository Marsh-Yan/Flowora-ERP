package com.flowora.erp.project;

import com.flowora.erp.workflow.WorkflowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_project_expense")
public class ProjectExpenseEntity extends WorkflowEntity {
    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;
    @Column(name = "task_id", length = 36)
    private String taskId;
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;
    @Column(length = 64, nullable = false)
    private String category;
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    @Column(name = "billable_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal billableAmount;
    @Column(nullable = false)
    private boolean billable;
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;
    @Column(length = 500)
    private String description;

    protected ProjectExpenseEntity() {
    }

    public ProjectExpenseEntity(String organizationId, String projectId, String taskId, String userId, LocalDate expenseDate,
                                String category, BigDecimal amount, boolean billable, String currencyCode, String description) {
        super(organizationId);
        this.projectId = projectId;
        this.taskId = taskId;
        this.userId = userId;
        this.expenseDate = expenseDate;
        this.category = category;
        this.amount = amount;
        this.billableAmount = billable ? amount : BigDecimal.ZERO;
        this.billable = billable;
        this.currencyCode = currencyCode;
        this.description = description;
    }

    public String projectId() { return projectId; }
    public String taskId() { return taskId; }
    public String userId() { return userId; }
    public LocalDate expenseDate() { return expenseDate; }
    public String category() { return category; }
    public BigDecimal amount() { return amount; }
    public BigDecimal billableAmount() { return billableAmount; }
    public boolean billable() { return billable; }
    public String currencyCode() { return currencyCode; }
    public String description() { return description; }
}
