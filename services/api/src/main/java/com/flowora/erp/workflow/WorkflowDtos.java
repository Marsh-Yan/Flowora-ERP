package com.flowora.erp.workflow;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WorkflowDtos {
    private WorkflowDtos() {
    }

    public record TaskRequest(
            @NotNull WorkflowResourceType resourceType,
            @NotBlank @Size(max = 64) String resourceId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.0") BigDecimal amount,
            @Size(max = 64) String assigneeUserId,
            Instant dueAt
    ) {
    }

    public record ActionRequest(
            @NotNull WorkflowAction action,
            @Size(max = 64) String transferToUserId,
            @Size(max = 1000) String comment
    ) {
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String body) {
    }

    public record TaskResponse(
            String id,
            WorkflowResourceType resourceType,
            String resourceId,
            String title,
            String description,
            BigDecimal amount,
            String requesterUserId,
            String assigneeUserId,
            String assigneeRole,
            WorkflowTaskStatus status,
            Instant dueAt,
            Instant completedAt
    ) {
    }

    public record NotificationResponse(
            String id,
            String type,
            String title,
            String message,
            boolean read,
            Instant createdAt
    ) {
    }

    public record CommentResponse(
            String id,
            String resourceType,
            String resourceId,
            String authorUserId,
            String body,
            Instant createdAt
    ) {
    }

    public record ActivityResponse(
            String id,
            String actionCode,
            String summary,
            String actorUserId,
            Instant createdAt,
            Map<String, Object> details
    ) {
    }

    public record WorkflowActionResult(TaskResponse task, String action, boolean notificationCreated) {
    }

    public record UnreadCount(long count) {
    }
}
