package com.flowora.erp.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "flowora_notification")
public class NotificationEntity extends WorkflowEntity {
    @Column(name = "recipient_user_id", length = 80, nullable = false)
    private String recipientUserId;

    @Column(length = 64, nullable = false)
    private String type;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 1000, nullable = false)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(String organizationId, String recipientUserId, String type, String title, String message) {
        super(organizationId);
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
    }

    public String recipientUserId() { return recipientUserId; }
    public String type() { return type; }
    public String title() { return title; }
    public String message() { return message; }
    public Instant readAt() { return readAt; }
    public boolean read() { return readAt != null; }

    public void markRead() {
        readAt = Instant.now();
    }
}
