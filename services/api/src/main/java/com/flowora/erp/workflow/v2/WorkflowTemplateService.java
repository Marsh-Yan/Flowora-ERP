package com.flowora.erp.workflow.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.ConditionGroup;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.StepRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.TemplateRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.TemplateStatus;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.TemplateView;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.VersionRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.VersionStatus;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.VersionView;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("local | production")
public class WorkflowTemplateService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowConditionEvaluator evaluator;

    public WorkflowTemplateService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            WorkflowConditionEvaluator evaluator
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.evaluator = evaluator;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates(String organizationId) {
        return jdbcTemplate.query("""
                SELECT id, code, name, resource_type, priority, status, current_version_id, version_no
                FROM flowora_workflow_template
                WHERE organization_id = ? ORDER BY resource_type, priority, code
                """, this::mapTemplate, organizationId);
    }

    @Transactional
    public TemplateView create(FloworaPrincipal actor, TemplateRequest request, String requestId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO flowora_workflow_template (
                    id, organization_id, code, name, resource_type, priority, status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?)
                """, id, actor.organizationId(), normalize(request.code()), request.name().trim(),
                normalize(request.resourceType()), request.priority(), actor.userId());
        audit(actor, "WORKFLOW_TEMPLATE_CREATED", "WORKFLOW_TEMPLATE", id, request.reason(), requestId);
        return template(actor.organizationId(), id);
    }

    @Transactional
    public VersionView createVersion(
            FloworaPrincipal actor,
            String templateId,
            VersionRequest request,
            String requestId
    ) {
        TemplateView template = templateForUpdate(actor.organizationId(), templateId);
        if (template.status() == TemplateStatus.RETIRED) conflict("WORKFLOW_TEMPLATE_RETIRED");
        evaluator.validate(request.condition());
        validateSteps(request.steps());
        Integer nextVersion = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_number), 0) + 1
                FROM flowora_workflow_version WHERE template_id = ?
                """, Integer.class, templateId);
        String versionId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO flowora_workflow_version (
                    id, template_id, version_number, status, condition_json,
                    definition_json, allow_self_approval
                ) VALUES (?, ?, ?, 'DRAFT', ?, ?, ?)
                """, versionId, templateId, nextVersion, json(request.condition()), json(request.steps()),
                request.allowSelfApproval());
        request.steps().stream().sorted(Comparator.comparingInt(StepRequest::sequence)).forEach(step ->
                jdbcTemplate.update("""
                        INSERT INTO flowora_workflow_step_definition (
                            id, workflow_version_id, step_key, name, sequence_no, completion_mode,
                            approver_type, approver_ref, due_hours, allowed_actions_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID().toString(), versionId, normalize(step.stepKey()), step.name().trim(),
                        step.sequence(), step.completionMode().name(), step.approverType().name(),
                        blankToNull(step.approverRef()), step.dueHours(),
                        json(List.of("APPROVE", "REJECT", "RETURN", "TRANSFER"))));
        audit(actor, "WORKFLOW_VERSION_CREATED", "WORKFLOW_VERSION", versionId,
                "Draft version created", requestId);
        return version(actor.organizationId(), templateId, versionId);
    }

    @Transactional
    public VersionView publish(
            FloworaPrincipal actor,
            String templateId,
            String versionId,
            String reason,
            String requestId
    ) {
        TemplateView target = templateForUpdate(actor.organizationId(), templateId);
        VersionView version = version(actor.organizationId(), templateId, versionId);
        if (version.status() != VersionStatus.DRAFT) conflict("WORKFLOW_VERSION_IMMUTABLE");
        evaluator.validate(version.condition());
        validateSteps(version.steps());

        List<PublishedCandidate> conflicts = jdbcTemplate.query("""
                SELECT template.id, version.condition_json
                FROM flowora_workflow_template template
                JOIN flowora_workflow_version version ON version.id = template.current_version_id
                WHERE template.organization_id = ? AND template.resource_type = ?
                  AND template.priority = ? AND template.status = 'PUBLISHED' AND template.id <> ?
                """, (rs, row) -> new PublishedCandidate(rs.getString("id"), condition(rs.getString("condition_json"))),
                actor.organizationId(), target.resourceType(), target.priority(), templateId);
        if (conflicts.stream().anyMatch(candidate -> candidate.condition().equals(version.condition()))) {
            conflict("WORKFLOW_TEMPLATE_SCOPE_CONFLICT");
        }

        jdbcTemplate.update("""
                UPDATE flowora_workflow_version
                SET status = 'RETIRED', retired_at = ?
                WHERE template_id = ? AND status = 'PUBLISHED'
                """, Timestamp.from(Instant.now()), templateId);
        jdbcTemplate.update("""
                UPDATE flowora_workflow_version
                SET status = 'PUBLISHED', published_by = ?, published_at = ? WHERE id = ?
                """, actor.userId(), Timestamp.from(Instant.now()), versionId);
        jdbcTemplate.update("""
                UPDATE flowora_workflow_template
                SET status = 'PUBLISHED', current_version_id = ?, version_no = version_no + 1
                WHERE id = ? AND organization_id = ?
                """, versionId, templateId, actor.organizationId());
        audit(actor, "WORKFLOW_VERSION_PUBLISHED", "WORKFLOW_VERSION", versionId, reason, requestId);
        return version(actor.organizationId(), templateId, versionId);
    }

    @Transactional
    public void retire(FloworaPrincipal actor, String templateId, String reason, String requestId) {
        templateForUpdate(actor.organizationId(), templateId);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE flowora_workflow_template
                SET status = 'RETIRED', version_no = version_no + 1
                WHERE id = ? AND organization_id = ?
                """, templateId, actor.organizationId());
        jdbcTemplate.update("""
                UPDATE flowora_workflow_version SET status = 'RETIRED', retired_at = ?
                WHERE template_id = ? AND status = 'PUBLISHED'
                """, Timestamp.from(now), templateId);
        audit(actor, "WORKFLOW_TEMPLATE_RETIRED", "WORKFLOW_TEMPLATE", templateId, reason, requestId);
    }

    @Transactional(readOnly = true)
    public MatchedVersion match(String organizationId, String resourceType, Map<String, Object> context) {
        List<MatchedVersion> candidates = jdbcTemplate.query("""
                SELECT template.id AS template_id, template.priority, version.id AS version_id,
                       version.condition_json, version.definition_json, version.allow_self_approval
                FROM flowora_workflow_template template
                JOIN flowora_workflow_version version ON version.id = template.current_version_id
                WHERE template.organization_id = ? AND template.resource_type = ?
                  AND template.status = 'PUBLISHED' AND version.status = 'PUBLISHED'
                ORDER BY template.priority, template.code
                """, (rs, row) -> new MatchedVersion(
                rs.getString("template_id"), rs.getString("version_id"), rs.getInt("priority"),
                condition(rs.getString("condition_json")), steps(rs.getString("definition_json")),
                rs.getBoolean("allow_self_approval")
        ), organizationId, normalize(resourceType));
        List<MatchedVersion> matched = candidates.stream()
                .filter(candidate -> evaluator.matches(candidate.condition(), context)).toList();
        if (matched.isEmpty()) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "WORKFLOW_TEMPLATE_NOT_FOUND",
                    "errors.workflowTemplateNotFound", Map.of("resourceType", resourceType));
        }
        int priority = matched.getFirst().priority();
        List<MatchedVersion> top = matched.stream().filter(candidate -> candidate.priority() == priority).toList();
        if (top.size() != 1) conflict("WORKFLOW_TEMPLATE_AMBIGUOUS");
        return top.getFirst();
    }

    private TemplateView template(String organizationId, String templateId) {
        List<TemplateView> found = jdbcTemplate.query("""
                SELECT id, code, name, resource_type, priority, status, current_version_id, version_no
                FROM flowora_workflow_template WHERE organization_id = ? AND id = ?
                """, this::mapTemplate, organizationId, templateId);
        if (found.isEmpty()) notFound("workflowTemplate", templateId);
        return found.getFirst();
    }

    private TemplateView templateForUpdate(String organizationId, String templateId) {
        List<TemplateView> found = jdbcTemplate.query("""
                SELECT id, code, name, resource_type, priority, status, current_version_id, version_no
                FROM flowora_workflow_template WHERE organization_id = ? AND id = ? FOR UPDATE
                """, this::mapTemplate, organizationId, templateId);
        if (found.isEmpty()) notFound("workflowTemplate", templateId);
        return found.getFirst();
    }

    private VersionView version(String organizationId, String templateId, String versionId) {
        List<VersionView> found = jdbcTemplate.query("""
                SELECT version.id, version.template_id, version.version_number, version.status,
                       version.condition_json, version.definition_json, version.allow_self_approval,
                       version.published_at
                FROM flowora_workflow_version version
                JOIN flowora_workflow_template template ON template.id = version.template_id
                WHERE template.organization_id = ? AND template.id = ? AND version.id = ?
                """, this::mapVersion, organizationId, templateId, versionId);
        if (found.isEmpty()) notFound("workflowVersion", versionId);
        return found.getFirst();
    }

    private TemplateView mapTemplate(ResultSet rs, int row) throws SQLException {
        return new TemplateView(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("resource_type"), rs.getInt("priority"),
                TemplateStatus.valueOf(rs.getString("status")), rs.getString("current_version_id"),
                rs.getLong("version_no")
        );
    }

    private VersionView mapVersion(ResultSet rs, int row) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new VersionView(
                rs.getString("id"), rs.getString("template_id"), rs.getInt("version_number"),
                VersionStatus.valueOf(rs.getString("status")), condition(rs.getString("condition_json")),
                steps(rs.getString("definition_json")), rs.getBoolean("allow_self_approval"),
                publishedAt == null ? null : publishedAt.toInstant()
        );
    }

    private void validateSteps(List<StepRequest> steps) {
        if (steps == null || steps.isEmpty()) conflict("WORKFLOW_STEPS_REQUIRED");
        HashSet<String> keys = new HashSet<>();
        HashSet<Integer> sequences = new HashSet<>();
        for (StepRequest step : steps) {
            if (!keys.add(normalize(step.stepKey())) || !sequences.add(step.sequence())) {
                conflict("WORKFLOW_STEP_DUPLICATE");
            }
            boolean requiresReference = step.approverType() == WorkflowV2Dtos.ApproverType.USER
                    || step.approverType() == WorkflowV2Dtos.ApproverType.ROLE;
            if (requiresReference && (step.approverRef() == null || step.approverRef().isBlank())) {
                conflict("WORKFLOW_APPROVER_REFERENCE_REQUIRED");
            }
        }
        List<Integer> ordered = sequences.stream().sorted().toList();
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index) != index + 1) conflict("WORKFLOW_STEP_SEQUENCE_INVALID");
        }
    }

    private void audit(
            FloworaPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String reason,
            String requestId
    ) {
        jdbcTemplate.update("""
                INSERT INTO flowora_audit_event (
                    id, organization_id, actor_user_id, action_code, resource_type,
                    resource_id, request_id, details_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), actor.organizationId(), actor.userId(), action,
                resourceType, resourceId, requestId, json(Map.of("reason", reason)));
    }

    private ConditionGroup condition(String json) {
        try {
            return objectMapper.readValue(json, ConditionGroup.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid workflow condition JSON", exception);
        }
    }

    private List<StepRequest> steps(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid workflow definition JSON", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize workflow JSON", exception);
        }
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void notFound(String resource, String id) {
        throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "errors.resourceNotFound", Map.of("resource", resource, "id", id));
    }

    private void conflict(String code) {
        throw new PlatformApiException(HttpStatus.CONFLICT, code, "errors." + code.toLowerCase());
    }

    public record MatchedVersion(
            String templateId,
            String versionId,
            int priority,
            ConditionGroup condition,
            List<StepRequest> steps,
            boolean allowSelfApproval
    ) {
    }

    private record PublishedCandidate(String templateId, ConditionGroup condition) {
    }
}
