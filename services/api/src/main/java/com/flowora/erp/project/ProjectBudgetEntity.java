package com.flowora.erp.project;

import com.flowora.erp.workflow.WorkflowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_project_budget")
public class ProjectBudgetEntity extends WorkflowEntity {
    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;
    @Column(length = 64, nullable = false)
    private String category;
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;
    @Column(length = 500)
    private String note;

    protected ProjectBudgetEntity() {
    }

    public ProjectBudgetEntity(String organizationId, String projectId, String category, BigDecimal amount, String currencyCode, String note) {
        super(organizationId);
        this.projectId = projectId;
        this.category = category;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.note = note;
    }

    public String projectId() { return projectId; }
    public String category() { return category; }
    public BigDecimal amount() { return amount; }
    public String currencyCode() { return currencyCode; }
    public String note() { return note; }
}
