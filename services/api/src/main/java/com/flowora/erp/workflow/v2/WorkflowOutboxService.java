package com.flowora.erp.workflow.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("local | production")
public class WorkflowOutboxService {
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate dispatchTransaction;

    public WorkflowOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dispatchTransaction = new TransactionTemplate(transactionManager);
    }

    public String enqueue(
            String organizationId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String recipientUserId,
            Map<String, Object> payload
    ) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO flowora_outbox_event (
                    id, organization_id, event_type, aggregate_type, aggregate_id,
                    recipient_user_id, payload_json, status, available_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                """, id, organizationId, eventType, aggregateType, aggregateId,
                recipientUserId, json(payload));
        return id;
    }

    @Scheduled(fixedDelayString = "${flowora.workflow.outbox-delay-ms:5000}")
    public void scheduledDispatch() {
        processBatch(50);
    }

    public int processBatch(int requestedLimit) {
        return dispatchTransaction.execute(status -> dispatchLockedBatch(requestedLimit));
    }

    private int dispatchLockedBatch(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        List<OutboxRow> rows = jdbcTemplate.query("""
                SELECT id, organization_id, event_type, aggregate_type, aggregate_id,
                       recipient_user_id, payload_json, attempts
                FROM flowora_outbox_event
                WHERE status IN ('PENDING', 'RETRY') AND available_at <= CURRENT_TIMESTAMP
                ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new OutboxRow(
                rs.getString("id"), rs.getString("organization_id"), rs.getString("event_type"),
                rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                rs.getString("recipient_user_id"), rs.getString("payload_json"), rs.getInt("attempts")
        ), limit);
        rows.forEach(this::deliver);
        return rows.size();
    }

    @Transactional
    public void replay(String organizationId, String eventId) {
        int updated = jdbcTemplate.update("""
                UPDATE flowora_outbox_event
                SET status = 'PENDING', attempts = 0, available_at = CURRENT_TIMESTAMP, last_error = NULL
                WHERE id = ? AND organization_id = ? AND status = 'DEAD'
                """, eventId, organizationId);
        if (updated != 1) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "OUTBOX_EVENT_NOT_REPLAYABLE",
                    "errors.outboxEventNotReplayable");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(String organizationId, String status) {
        String normalized = status == null || status.isBlank() ? "DEAD" : status.trim().toUpperCase();
        return jdbcTemplate.queryForList("""
                SELECT id, event_type, aggregate_type, aggregate_id, recipient_user_id,
                       status, attempts, available_at, delivered_at, last_error, created_at
                FROM flowora_outbox_event
                WHERE organization_id = ? AND status = ? ORDER BY created_at DESC LIMIT 200
                """, organizationId, normalized);
    }

    private void deliver(OutboxRow row) {
        int attempt = row.attempts() + 1;
        try {
            if (row.recipientUserId() != null) {
                Map<String, Object> payload = payload(row.payloadJson());
                jdbcTemplate.update("""
                        INSERT INTO flowora_notification (
                            id, organization_id, recipient_user_id, type, event_type,
                            title, message, resource_type, resource_id, outbox_event_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE outbox_event_id=VALUES(outbox_event_id)
                        """, UUID.randomUUID().toString(), row.organizationId(), row.recipientUserId(),
                        "WORKFLOW", row.eventType(), text(payload, "title", row.eventType()),
                        text(payload, "message", "Workflow update"), row.aggregateType(),
                        row.aggregateId(), row.id());
            }
            recordAttempt(row.id(), "IN_APP", "DELIVERED", null);
            jdbcTemplate.update("""
                    UPDATE flowora_outbox_event
                    SET status = 'DELIVERED', attempts = ?, delivered_at = CURRENT_TIMESTAMP, last_error = NULL
                    WHERE id = ?
                    """, attempt, row.id());
        } catch (RuntimeException exception) {
            recordAttempt(row.id(), "IN_APP", "FAILED", trim(exception.getMessage()));
            boolean dead = attempt >= MAX_ATTEMPTS;
            long delaySeconds = Math.min(3600, 1L << Math.min(attempt, 10));
            jdbcTemplate.update("""
                    UPDATE flowora_outbox_event
                    SET status = ?, attempts = ?, available_at = TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP), last_error = ? WHERE id = ?
                    """, dead ? "DEAD" : "RETRY", attempt,
                    delaySeconds,
                    trim(exception.getMessage()), row.id());
        }
    }

    private void recordAttempt(String eventId, String channel, String outcome, String error) {
        // The event row stays locked. Replay resets the retry budget, not its audit history.
        Integer attempt = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(attempt_number),0)+1 FROM flowora_delivery_attempt WHERE outbox_event_id=? AND channel=?", Integer.class, eventId, channel);
        jdbcTemplate.update("""
                INSERT INTO flowora_delivery_attempt (
                    id, outbox_event_id, attempt_number, channel, outcome, error_message
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), eventId, attempt, channel, outcome, error);
    }

    private Map<String, Object> payload(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid outbox payload", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize outbox payload", exception);
        }
    }

    private String text(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String trim(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record OutboxRow(
            String id,
            String organizationId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String recipientUserId,
            String payloadJson,
            int attempts
    ) {
    }
}
