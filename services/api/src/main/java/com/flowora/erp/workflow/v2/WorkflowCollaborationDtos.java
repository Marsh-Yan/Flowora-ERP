package com.flowora.erp.workflow.v2;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WorkflowCollaborationDtos {
    private WorkflowCollaborationDtos() {
    }

    public record CommentRequest(
            @NotBlank @Size(max = 2000) String body,
            @Size(max = 20) List<@Size(max = 36) String> mentionedUserIds
    ) {
        public CommentRequest {
            mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
        }
    }

    public record CommentView(
            String id, String resourceType, String resourceId, String authorUserId,
            String body, List<String> mentionedUserIds, Instant createdAt
    ) {
    }

    public record ActivityView(
            String id, String actionCode, String summary, String actorUserId,
            Instant createdAt, Map<String, Object> details
    ) {
    }

    public record AttachmentView(
            String id, String resourceType, String resourceId, String originalFilename,
            String mediaType, long sizeBytes, String sha256Hex, String uploaderUserId,
            String status, Instant createdAt, Instant linkedAt
    ) {
    }

    public record NotificationView(
            String id, String type, String eventType, String title, String message,
            String resourceType, String resourceId, boolean read, Instant createdAt
    ) {
    }

    public record UnreadCount(long count) {
    }

    public record MarkReadRequest(@Size(max = 200) List<@Size(max = 36) String> notificationIds) {
        public MarkReadRequest {
            notificationIds = notificationIds == null ? List.of() : List.copyOf(notificationIds);
        }
    }
}
