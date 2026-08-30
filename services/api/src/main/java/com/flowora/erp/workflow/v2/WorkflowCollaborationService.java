package com.flowora.erp.workflow.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.ActivityView;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.CommentRequest;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.CommentView;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.NotificationView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowCollaborationService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowResourceAccessPolicy accessPolicy;
    private final WorkflowOutboxService outboxService;

    public WorkflowCollaborationService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            WorkflowResourceAccessPolicy accessPolicy,
            WorkflowOutboxService outboxService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.accessPolicy = accessPolicy;
        this.outboxService = outboxService;
    }

    @Transactional
    public CommentView addComment(
            FloworaPrincipal principal,
            String resourceType,
            String resourceId,
            CommentRequest request
    ) {
        String normalized = accessPolicy.require(principal, resourceType, resourceId, "comment");
        String commentId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO flowora_comment (
                    id, organization_id, resource_type, resource_id, author_user_id, body
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, commentId, principal.organizationId(), normalized, resourceId,
                principal.userId(), request.body().trim());

        LinkedHashSet<String> mentions = new LinkedHashSet<>(request.mentionedUserIds());
        mentions.remove(principal.userId());
        if (mentions.size() > 20) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "MENTION_LIMIT_EXCEEDED", "errors.validation");
        }
        for (String mentionedUserId : mentions) {
            ensureActiveMember(principal.organizationId(), mentionedUserId);
            jdbcTemplate.update("""
                    INSERT INTO flowora_mention (id, organization_id, comment_id, mentioned_user_id)
                    VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), principal.organizationId(), commentId, mentionedUserId);
            outboxService.enqueue(
                    principal.organizationId(), "COLLABORATION_MENTION", normalized, resourceId,
                    mentionedUserId, Map.of(
                            "title", "You were mentioned",
                            "message", principal.displayName() + " mentioned you in a comment",
                            "resourceType", normalized,
                            "resourceId", resourceId
                    ));
        }
        jdbcTemplate.update("""
                INSERT INTO flowora_activity_event (
                    id, organization_id, resource_type, resource_id, actor_user_id,
                    action_code, summary, details_json
                ) VALUES (?, ?, ?, ?, ?, 'COMMENT_ADDED', ?, CAST(? AS JSON))
                """, UUID.randomUUID().toString(), principal.organizationId(), normalized, resourceId,
                principal.userId(), "Comment added", json(Map.of("commentId", commentId, "mentions", mentions)));
        return comment(principal, commentId, normalized, resourceId);
    }

    public List<CommentView> comments(FloworaPrincipal principal, String resourceType, String resourceId) {
        String normalized = accessPolicy.require(principal, resourceType, resourceId, "view");
        return jdbcTemplate.query("""
                SELECT comment.id, comment.resource_type, comment.resource_id, comment.author_user_id,
                       comment.body, comment.created_at
                FROM flowora_comment comment
                WHERE comment.organization_id = ? AND comment.resource_type = ? AND comment.resource_id = ?
                ORDER BY comment.created_at, comment.id
                LIMIT 200
                """, (rs, rowNum) -> new CommentView(
                rs.getString("id"), rs.getString("resource_type"), rs.getString("resource_id"),
                rs.getString("author_user_id"), rs.getString("body"),
                mentions(rs.getString("id")), instant(rs.getTimestamp("created_at"))
        ), principal.organizationId(), normalized, resourceId);
    }

    public List<ActivityView> activities(FloworaPrincipal principal, String resourceType, String resourceId) {
        String normalized = accessPolicy.require(principal, resourceType, resourceId, "view");
        return jdbcTemplate.query("""
                SELECT id, action_code, summary, actor_user_id, created_at, details_json
                FROM flowora_activity_event
                WHERE organization_id = ? AND resource_type = ? AND resource_id = ?
                ORDER BY created_at, id
                LIMIT 500
                """, (rs, rowNum) -> new ActivityView(
                rs.getString("id"), rs.getString("action_code"), rs.getString("summary"),
                rs.getString("actor_user_id"), instant(rs.getTimestamp("created_at")),
                parseMap(rs.getString("details_json"))
        ), principal.organizationId(), normalized, resourceId);
    }

    public List<NotificationView> notifications(FloworaPrincipal principal, boolean unreadOnly) {
        return jdbcTemplate.query("""
                SELECT id, type, event_type, title, message, resource_type, resource_id, read_at, created_at
                FROM flowora_notification
                WHERE organization_id = ? AND recipient_user_id = ?
                  AND (? = FALSE OR read_at IS NULL)
                ORDER BY created_at DESC, id DESC
                LIMIT 200
                """, (rs, rowNum) -> new NotificationView(
                rs.getString("id"), rs.getString("type"), rs.getString("event_type"),
                rs.getString("title"), rs.getString("message"), rs.getString("resource_type"),
                rs.getString("resource_id"), rs.getTimestamp("read_at") != null,
                instant(rs.getTimestamp("created_at"))
        ), principal.organizationId(), principal.userId(), unreadOnly);
    }

    public long unreadCount(FloworaPrincipal principal) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_notification
                WHERE organization_id = ? AND recipient_user_id = ? AND read_at IS NULL
                """, Long.class, principal.organizationId(), principal.userId());
        return count == null ? 0 : count;
    }

    @Transactional
    public int markRead(FloworaPrincipal principal, List<String> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) return 0;
        if (notificationIds.size() > 200) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "NOTIFICATION_LIMIT_EXCEEDED", "errors.validation");
        }
        int updated = 0;
        for (String id : new LinkedHashSet<>(notificationIds)) {
            updated += jdbcTemplate.update("""
                    UPDATE flowora_notification SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                    WHERE id = ? AND organization_id = ? AND recipient_user_id = ?
                    """, id, principal.organizationId(), principal.userId());
        }
        return updated;
    }

    private CommentView comment(FloworaPrincipal principal, String id, String type, String resourceId) {
        return jdbcTemplate.query("""
                SELECT id, resource_type, resource_id, author_user_id, body, created_at
                FROM flowora_comment
                WHERE id = ? AND organization_id = ?
                """, rs -> {
            if (!rs.next()) throw new IllegalStateException("Created comment not found");
            return new CommentView(
                    rs.getString("id"), type, resourceId, rs.getString("author_user_id"),
                    rs.getString("body"), mentions(id), instant(rs.getTimestamp("created_at")));
        }, id, principal.organizationId());
    }

    private List<String> mentions(String commentId) {
        return jdbcTemplate.queryForList("""
                SELECT mentioned_user_id FROM flowora_mention
                WHERE comment_id = ? ORDER BY mentioned_user_id
                """, String.class, commentId);
    }

    private void ensureActiveMember(String organizationId, String userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_organization_membership membership
                JOIN flowora_user_account user ON user.id = membership.user_id
                WHERE membership.organization_id = ? AND membership.user_id = ?
                  AND membership.status = 'ACTIVE' AND user.status = 'ACTIVE'
                """, Integer.class, organizationId, userId);
        if (count == null || count == 0) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "MENTION_USER_INVALID", "errors.validation");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize collaboration payload", exception);
        }
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
