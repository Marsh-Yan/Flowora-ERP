package com.flowora.erp.project;

import com.flowora.erp.common.api.WorkflowStateConflictException;
import com.flowora.erp.workflow.WorkflowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_project")
public class ProjectEntity extends WorkflowEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;
    @Column(length = 160, nullable = false)
    private String name;
    @Column(length = 1000)
    private String description;
    @Column(name = "customer_id", length = 36)
    private String customerId;
    @Column(name = "sales_order_id", length = 36)
    private String salesOrderId;
    @Column(name = "manager_user_id", length = 64, nullable = false)
    private String managerUserId;
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;
    @Column(name = "budget_revenue", precision = 19, scale = 4, nullable = false)
    private BigDecimal budgetRevenue;
    @Column(name = "budget_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal budgetCost;
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;
    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private ProjectStatus status;

    protected ProjectEntity() {
    }

    public ProjectEntity(String organizationId, String number, String name, String description, String customerId,
                         String salesOrderId, String managerUserId, LocalDate targetDate, BigDecimal budgetRevenue,
                         BigDecimal budgetCost, String currencyCode) {
        super(organizationId);
        this.number = number;
        this.name = name;
        this.description = description;
        this.customerId = customerId;
        this.salesOrderId = salesOrderId;
        this.managerUserId = managerUserId;
        this.targetDate = targetDate;
        this.budgetRevenue = budgetRevenue;
        this.budgetCost = budgetCost;
        this.currencyCode = currencyCode;
        this.status = ProjectStatus.PLANNED;
    }

    public String number() { return number; }
    public String name() { return name; }
    public String description() { return description; }
    public String customerId() { return customerId; }
    public String salesOrderId() { return salesOrderId; }
    public String managerUserId() { return managerUserId; }
    public LocalDate targetDate() { return targetDate; }
    public BigDecimal budgetRevenue() { return budgetRevenue; }
    public BigDecimal budgetCost() { return budgetCost; }
    public String currencyCode() { return currencyCode; }
    public ProjectStatus status() { return status; }

    public void transitionTo(ProjectStatus next) {
        if (next == null || next == status) return;
        boolean allowed = switch (status) {
            case PLANNED -> next == ProjectStatus.ACTIVE || next == ProjectStatus.AT_RISK || next == ProjectStatus.ARCHIVED;
            case ACTIVE -> next == ProjectStatus.AT_RISK || next == ProjectStatus.COMPLETED || next == ProjectStatus.ARCHIVED;
            case AT_RISK -> next == ProjectStatus.ACTIVE || next == ProjectStatus.COMPLETED || next == ProjectStatus.ARCHIVED;
            case COMPLETED -> next == ProjectStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) throw new WorkflowStateConflictException("Project cannot move from " + status + " to " + next);
        this.status = next;
    }
}
