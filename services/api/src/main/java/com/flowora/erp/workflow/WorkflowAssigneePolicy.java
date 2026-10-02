package com.flowora.erp.workflow;

import com.flowora.erp.common.api.InvalidCredentialsException;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.DatabaseIdentityAuthenticator;
import com.flowora.erp.workflow.v2.WorkflowResourceAccessPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Resolve the target's current membership and permissions; never grant access by assigning a task. */
@Component
@Profile("local | production")
public class WorkflowAssigneePolicy {
    private final JdbcTemplate jdbc;
    private final DatabaseIdentityAuthenticator identities;
    private final WorkflowResourceAccessPolicy resources;

    public WorkflowAssigneePolicy(JdbcTemplate jdbc, DatabaseIdentityAuthenticator identities,
                                        WorkflowResourceAccessPolicy resources) {
        this.jdbc = jdbc;
        this.identities = identities;
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public void require(String organizationId, String targetUserId, WorkflowResourceType type, String resourceId, boolean pendingApproval) {
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flowora_user_account u
                JOIN flowora_organization_membership m ON m.user_id=u.id
                JOIN flowora_organization o ON o.id=m.organization_id
                WHERE u.id=? AND m.organization_id=? AND u.status='ACTIVE' AND u.active=TRUE
                  AND m.status='ACTIVE' AND o.status='ACTIVE' AND o.active=TRUE
                  AND (u.locked_until IS NULL OR u.locked_until<=CURRENT_TIMESTAMP)
                """, Integer.class, targetUserId, organizationId);
        if (active == null || active == 0) throw invalid();
        try {
            var target = identities.principalForOrganization(targetUserId, organizationId);
            if (!target.organizationId().equals(organizationId)
                    || !target.permissions().contains("workflow:view")
                    || (pendingApproval
                        ? !target.permissions().contains("workflow:approve") && !target.permissions().contains("workflow:delegate")
                        : !target.permissions().contains("workflow:submit"))) {
                throw invalid();
            }
            resources.require(target, type.name(), resourceId, "read");
        } catch (InvalidCredentialsException | PlatformApiException | AccessDeniedException exception) {
            // Do not reveal whether another organization's user exists or which permission is missing.
            throw invalid();
        }
    }

    static PlatformApiException invalid() {
        return new PlatformApiException(HttpStatus.CONFLICT, "WORKFLOW_ASSIGNEE_INVALID", "errors.workflowAssigneeInvalid");
    }
}
