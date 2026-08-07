package com.flowora.erp.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flowora_audit_event")
public class AuditEventEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @Column(name = "organization_id", length = 36, nullable = false)
    private String organizationId;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "action_code", length = 64, nullable = false)
    private String actionCode;

    @Column(name = "resource_type", length = 96, nullable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Column(name = "request_id", length = 96, nullable = false)
    private String requestId;

    @Column(name = "details_json", columnDefinition = "json")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(String organizationId, String actorUserId, String actionCode, String resourceType, String resourceId, String requestId, String detailsJson) {
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.actionCode = actionCode;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.requestId = requestId;
        this.detailsJson = detailsJson;
        this.createdAt = Instant.now();
    }
}
