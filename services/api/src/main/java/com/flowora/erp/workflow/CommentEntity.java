package com.flowora.erp.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "flowora_comment")
public class CommentEntity extends WorkflowEntity {
    @Column(name = "resource_type", length = 64, nullable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 64, nullable = false)
    private String resourceId;

    @Column(name = "author_user_id", length = 64, nullable = false)
    private String authorUserId;

    @Column(length = 2000, nullable = false)
    private String body;

    protected CommentEntity() {
    }

    public CommentEntity(String organizationId, String resourceType, String resourceId, String authorUserId, String body) {
        super(organizationId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.authorUserId = authorUserId;
        this.body = body;
    }

    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public String authorUserId() { return authorUserId; }
    public String body() { return body; }
}
