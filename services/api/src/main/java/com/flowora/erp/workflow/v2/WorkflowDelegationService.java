package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.DelegationRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Profile("local")
public class WorkflowDelegationService {
    private final JdbcTemplate jdbcTemplate;
    private final WorkflowApproverResolver approverResolver;

    public WorkflowDelegationService(JdbcTemplate jdbcTemplate, WorkflowApproverResolver approverResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.approverResolver = approverResolver;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> delegations(FloworaPrincipal actor) {
        return jdbcTemplate.queryForList("""
                SELECT id, delegate_user_id, resource_type, starts_at, ends_at, active, reason, created_at
                FROM flowora_workflow_delegation
                WHERE organization_id = ? AND delegator_user_id = ? ORDER BY starts_at DESC
                """, actor.organizationId(), actor.userId());
    }

    @Transactional
    public Map<String, Object> create(FloworaPrincipal actor, DelegationRequest request) {
        if (!request.startsAt().isBefore(request.endsAt())) conflict("WORKFLOW_DELEGATION_WINDOW_INVALID");
        if (actor.userId().equals(request.delegateUserId())) conflict("WORKFLOW_DELEGATION_SELF");
        approverResolver.requireEligibleUser(actor.organizationId(), request.delegateUserId());
        ensureNoCycle(actor.organizationId(), actor.userId(), request.delegateUserId(), request.resourceType(),
                request.startsAt(), request.endsAt());
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO flowora_workflow_delegation (
                    id, organization_id, delegator_user_id, delegate_user_id, resource_type,
                    starts_at, ends_at, active, reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                """, id, actor.organizationId(), actor.userId(), request.delegateUserId(),
                normalizeNullable(request.resourceType()), Timestamp.from(request.startsAt()),
                Timestamp.from(request.endsAt()), request.reason().trim());
        return jdbcTemplate.queryForMap("""
                SELECT id, delegate_user_id, resource_type, starts_at, ends_at, active, reason, created_at
                FROM flowora_workflow_delegation WHERE id = ?
                """, id);
    }

    @Transactional
    public void cancel(FloworaPrincipal actor, String delegationId) {
        int updated = jdbcTemplate.update("""
                UPDATE flowora_workflow_delegation SET active = FALSE, version_no = version_no + 1
                WHERE id = ? AND organization_id = ? AND delegator_user_id = ?
                """, delegationId, actor.organizationId(), actor.userId());
        if (updated != 1) {
            throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound");
        }
    }

    private void ensureNoCycle(
            String organizationId,
            String delegator,
            String delegate,
            String resourceType,
            Instant startsAt,
            Instant endsAt
    ) {
        Set<String> visited = new HashSet<>();
        visited.add(delegator);
        String cursor = delegate;
        while (cursor != null) {
            if (!visited.add(cursor)) conflict("WORKFLOW_DELEGATION_CYCLE");
            List<String> next = jdbcTemplate.queryForList("""
                    SELECT delegate_user_id FROM flowora_workflow_delegation
                    WHERE organization_id = ? AND delegator_user_id = ? AND active = TRUE
                      AND starts_at <= ? AND ends_at >= ?
                      AND (resource_type IS NULL OR resource_type = ?)
                    ORDER BY created_at DESC LIMIT 1
                    """, String.class, organizationId, cursor, Timestamp.from(endsAt),
                    Timestamp.from(startsAt), normalizeNullable(resourceType));
            cursor = next.isEmpty() ? null : next.getFirst();
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private void conflict(String code) {
        throw new PlatformApiException(HttpStatus.CONFLICT, code, "errors." + code.toLowerCase());
    }
}
