package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.workflow.v2.WorkflowTemplateService.MatchedVersion;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.ApproverType;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.StepRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Profile("local")
public class WorkflowApproverResolver {
    private final JdbcTemplate jdbcTemplate;

    public WorkflowApproverResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ResolvedApprover> resolve(
            String organizationId,
            String requesterUserId,
            String ownerUserId,
            String departmentId,
            String resourceType,
            StepRequest step,
            MatchedVersion version
    ) {
        List<String> originals = switch (step.approverType()) {
            case USER -> eligibleUsers(organizationId, "membership.user_id = ?", step.approverRef());
            case ROLE -> eligibleUsers(organizationId, "role.code = ?", normalize(step.approverRef()));
            case DEPARTMENT_MANAGER -> departmentManager(organizationId, departmentId);
            case DOCUMENT_OWNER -> eligibleUsers(organizationId, "membership.user_id = ?", ownerUserId);
            case REQUESTER_MANAGER -> requesterManager(organizationId, requesterUserId);
        };
        Set<String> unique = new LinkedHashSet<>(originals);
        if (!version.allowSelfApproval()) unique.remove(requesterUserId);
        if (step.completionMode() == WorkflowV2Dtos.CompletionMode.SERIAL && unique.size() > 1) {
            throw conflict("WORKFLOW_SERIAL_APPROVER_AMBIGUOUS", step.stepKey());
        }
        if (unique.isEmpty()) throw conflict("WORKFLOW_APPROVER_NOT_FOUND", step.stepKey());
        return unique.stream().map(original -> new ResolvedApprover(
                original, activeDelegate(organizationId, original, resourceType)
        )).toList();
    }

    public void requireEligibleUser(String organizationId, String userId) {
        if (eligibleUsers(organizationId, "membership.user_id = ?", userId).isEmpty()) {
            throw conflict("WORKFLOW_ASSIGNEE_NOT_ELIGIBLE", userId);
        }
    }

    private List<String> eligibleUsers(String organizationId, String predicate, String value) {
        if (value == null || value.isBlank()) return List.of();
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT membership.user_id
                FROM flowora_organization_membership membership
                JOIN flowora_user_account user ON user.id = membership.user_id
                JOIN flowora_membership_role membership_role ON membership_role.membership_id = membership.id
                JOIN flowora_role role ON role.id = membership_role.role_id AND role.active = TRUE
                JOIN flowora_role_permission role_permission ON role_permission.role_id = role.id
                WHERE membership.organization_id = ? AND membership.status = 'ACTIVE'
                  AND user.status = 'ACTIVE' AND role_permission.permission_code = 'workflow:approve'
                  AND %s
                ORDER BY membership.user_id
                """.formatted(predicate), String.class, organizationId, value);
    }

    private List<String> departmentManager(String organizationId, String departmentId) {
        if (departmentId == null || departmentId.isBlank()) return List.of();
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT manager.user_id
                FROM flowora_department department
                JOIN flowora_organization_membership manager ON manager.id = department.manager_membership_id
                JOIN flowora_membership_role manager_role ON manager_role.membership_id = manager.id
                JOIN flowora_role role ON role.id = manager_role.role_id AND role.active = TRUE
                JOIN flowora_role_permission permission ON permission.role_id = role.id
                WHERE department.organization_id = ? AND department.id = ?
                  AND department.active = TRUE AND manager.status = 'ACTIVE'
                  AND permission.permission_code = 'workflow:approve'
                """, String.class, organizationId, departmentId);
    }

    private List<String> requesterManager(String organizationId, String requesterUserId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT manager.user_id
                FROM flowora_organization_membership requester
                JOIN flowora_department department ON department.id = requester.department_id
                JOIN flowora_organization_membership manager ON manager.id = department.manager_membership_id
                JOIN flowora_membership_role manager_role ON manager_role.membership_id = manager.id
                JOIN flowora_role role ON role.id = manager_role.role_id AND role.active = TRUE
                JOIN flowora_role_permission permission ON permission.role_id = role.id
                WHERE requester.organization_id = ? AND requester.user_id = ?
                  AND requester.status = 'ACTIVE' AND manager.status = 'ACTIVE'
                  AND permission.permission_code = 'workflow:approve'
                """, String.class, organizationId, requesterUserId);
    }

    private String activeDelegate(String organizationId, String original, String resourceType) {
        List<String> delegates = jdbcTemplate.queryForList("""
                SELECT delegation.delegate_user_id
                FROM flowora_workflow_delegation delegation
                JOIN flowora_organization_membership membership
                  ON membership.organization_id = delegation.organization_id
                 AND membership.user_id = delegation.delegate_user_id
                 AND membership.status = 'ACTIVE'
                JOIN flowora_user_account user ON user.id = delegation.delegate_user_id AND user.status = 'ACTIVE'
                WHERE delegation.organization_id = ? AND delegation.delegator_user_id = ?
                  AND delegation.active = TRUE AND delegation.starts_at <= ? AND delegation.ends_at >= ?
                  AND (delegation.resource_type IS NULL OR delegation.resource_type = ?)
                ORDER BY delegation.resource_type DESC, delegation.created_at DESC LIMIT 1
                """, String.class, organizationId, original, Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()), normalize(resourceType));
        if (delegates.isEmpty()) return original;
        requireEligibleUser(organizationId, delegates.getFirst());
        return delegates.getFirst();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private PlatformApiException conflict(String code, String value) {
        return new PlatformApiException(HttpStatus.CONFLICT, code, "errors." + code.toLowerCase(),
                Map.of("value", value == null ? "" : value));
    }

    public record ResolvedApprover(String originalUserId, String assigneeUserId) {
    }
}
