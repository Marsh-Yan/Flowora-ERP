package com.flowora.erp.project;

import com.flowora.erp.workflow.WorkflowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_timesheet")
public class TimesheetEntity extends WorkflowEntity {
    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;
    @Column(name = "task_id", length = 36)
    private String taskId;
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal hours;
    @Column(name = "cost_rate", precision = 19, scale = 4, nullable = false)
    private BigDecimal costRate;
    @Column(name = "billing_rate", precision = 19, scale = 4, nullable = false)
    private BigDecimal billingRate;
    @Column(name = "cost_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal costAmount;
    @Column(name = "billable_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal billableAmount;
    @Column(nullable = false)
    private boolean billable;
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;
    @Column(length = 500)
    private String note;

    protected TimesheetEntity() {
    }

    public TimesheetEntity(String organizationId, String projectId, String taskId, String userId, LocalDate workDate,
                           BigDecimal hours, BigDecimal costRate, BigDecimal billingRate, boolean billable,
                           String currencyCode, String note) {
        super(organizationId);
        this.projectId = projectId;
        this.taskId = taskId;
        this.userId = userId;
        this.workDate = workDate;
        this.hours = hours;
        this.costRate = costRate;
        this.billingRate = billingRate;
        this.costAmount = hours.multiply(costRate);
        this.billableAmount = billable ? hours.multiply(billingRate) : BigDecimal.ZERO;
        this.billable = billable;
        this.currencyCode = currencyCode;
        this.note = note;
    }

    public String projectId() { return projectId; }
    public String taskId() { return taskId; }
    public String userId() { return userId; }
    public LocalDate workDate() { return workDate; }
    public BigDecimal hours() { return hours; }
    public BigDecimal costAmount() { return costAmount; }
    public BigDecimal billableAmount() { return billableAmount; }
    public boolean billable() { return billable; }
    public String currencyCode() { return currencyCode; }
    public String note() { return note; }
}
