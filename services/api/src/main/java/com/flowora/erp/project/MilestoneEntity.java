package com.flowora.erp.project;

import com.flowora.erp.workflow.WorkflowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "flowora_project_milestone")
public class MilestoneEntity extends WorkflowEntity {
    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;
    @Column(length = 160, nullable = false)
    private String name;
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;
    @Column(name = "target_date")
    private LocalDate targetDate;
    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private MilestoneStatus status;

    protected MilestoneEntity() {
    }

    public MilestoneEntity(String organizationId, String projectId, String name, int sequenceNo, LocalDate targetDate) {
        super(organizationId);
        this.projectId = projectId;
        this.name = name;
        this.sequenceNo = sequenceNo;
        this.targetDate = targetDate;
        this.status = MilestoneStatus.PLANNED;
    }

    public String projectId() { return projectId; }
    public String name() { return name; }
    public int sequenceNo() { return sequenceNo; }
    public LocalDate targetDate() { return targetDate; }
    public MilestoneStatus status() { return status; }
}
