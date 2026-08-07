package com.flowora.erp.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "flowora_activity_event")
public class ActivityEventEntity extends WorkflowEntity {
    @Column(name = "resource_type", length = 64, nullable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 64, nullable = false)
    private String resourceId;

    @Column(name = "actor_user_id", length = 64, nullable = false)
    private String actorUserId;

    @Column(name = "action_code", length = 64, nullable = false)
    private String actionCode;

    @Column(length = 500, nullable = false)
    private String summary;

    @Column(name = "details_json", columnDefinition = "json")
    private String detailsJson;

    protected ActivityEventEntity() {
    }

    public ActivityEventEntity(
            String organizationId,
            String resourceType,
            String resourceId,
            String actorUserId,
            String actionCode,
            String summary,
            String detailsJson
    ) {
        super(organizationId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.actorUserId = actorUserId;
        this.actionCode = actionCode;
        this.summary = summary;
        this.detailsJson = detailsJson;
    }

    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public String actorUserId() { return actorUserId; }
    public String actionCode() { return actionCode; }
    public String summary() { return summary; }
    public String detailsJson() { return detailsJson; }
}
