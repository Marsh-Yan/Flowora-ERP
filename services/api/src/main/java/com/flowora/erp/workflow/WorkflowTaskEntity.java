package com.flowora.erp.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "flowora_workflow_task")
public class WorkflowTaskEntity extends WorkflowEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 32, nullable = false)
    private WorkflowResourceType resourceType;

    @Column(name = "resource_id", length = 64, nullable = false)
    private String resourceId;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "requester_user_id", length = 64, nullable = false)
    private String requesterUserId;

    @Column(name = "assignee_user_id", length = 64)
    private String assigneeUserId;

    @Column(name = "assignee_role", length = 64)
    private String assigneeRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private WorkflowTaskStatus status;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WorkflowTaskEntity() {
    }

    public WorkflowTaskEntity(
            String organizationId,
            WorkflowResourceType resourceType,
            String resourceId,
            String title,
            String description,
            BigDecimal amount,
            String requesterUserId,
            String assigneeUserId,
            String assigneeRole,
            WorkflowTaskStatus status,
            Instant dueAt
    ) {
        super(organizationId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.requesterUserId = requesterUserId;
        this.assigneeUserId = assigneeUserId;
        this.assigneeRole = assigneeRole;
        this.status = status;
        this.dueAt = dueAt;
    }

    public WorkflowResourceType resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public String title() { return title; }
    public String description() { return description; }
    public BigDecimal amount() { return amount; }
    public String requesterUserId() { return requesterUserId; }
    public String assigneeUserId() { return assigneeUserId; }
    public String assigneeRole() { return assigneeRole; }
    public WorkflowTaskStatus status() { return status; }
    public Instant dueAt() { return dueAt; }
    public Instant completedAt() { return completedAt; }

    public void approve() {
        status = WorkflowTaskStatus.APPROVED;
    }

    public void reject() {
        status = WorkflowTaskStatus.REJECTED;
        completedAt = Instant.now();
    }

    public void transfer(String assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
        this.assigneeRole = null;
        this.status = WorkflowTaskStatus.OPEN;
    }

    public void complete() {
        status = WorkflowTaskStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void cancel() {
        status = WorkflowTaskStatus.CANCELLED;
        completedAt = Instant.now();
    }
}
