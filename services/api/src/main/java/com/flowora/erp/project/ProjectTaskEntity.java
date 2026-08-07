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
@Table(name = "flowora_project_task")
public class ProjectTaskEntity extends WorkflowEntity {
    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;
    @Column(name = "milestone_id", length = 36)
    private String milestoneId;
    @Column(length = 200, nullable = false)
    private String title;
    @Column(length = 1000)
    private String description;
    @Column(name = "assignee_user_id", length = 64, nullable = false)
    private String assigneeUserId;
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private ProjectTaskPriority priority;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private ProjectTaskStatus status;
    @Column(name = "estimated_hours", precision = 19, scale = 4, nullable = false)
    private BigDecimal estimatedHours;

    protected ProjectTaskEntity() {
    }

    public ProjectTaskEntity(String organizationId, String projectId, String milestoneId, String title, String description,
                             String assigneeUserId, ProjectTaskPriority priority, LocalDate dueDate, BigDecimal estimatedHours) {
        super(organizationId);
        this.projectId = projectId;
        this.milestoneId = milestoneId;
        this.title = title;
        this.description = description;
        this.assigneeUserId = assigneeUserId;
        this.priority = priority;
        this.dueDate = dueDate;
        this.estimatedHours = estimatedHours;
        this.status = ProjectTaskStatus.TODO;
    }

    public String projectId() { return projectId; }
    public String milestoneId() { return milestoneId; }
    public String title() { return title; }
    public String description() { return description; }
    public String assigneeUserId() { return assigneeUserId; }
    public ProjectTaskPriority priority() { return priority; }
    public LocalDate dueDate() { return dueDate; }
    public ProjectTaskStatus status() { return status; }
    public BigDecimal estimatedHours() { return estimatedHours; }

    public void transitionTo(ProjectTaskStatus next) {
        if (next == null || next == status) return;
        boolean allowed = switch (status) {
            case TODO -> next == ProjectTaskStatus.IN_PROGRESS || next == ProjectTaskStatus.BLOCKED;
            case IN_PROGRESS -> next == ProjectTaskStatus.BLOCKED || next == ProjectTaskStatus.DONE;
            case BLOCKED -> next == ProjectTaskStatus.IN_PROGRESS || next == ProjectTaskStatus.DONE;
            case DONE -> false;
        };
        if (!allowed) throw new WorkflowStateConflictException("Task cannot move from " + status + " to " + next);
        this.status = next;
    }
}
