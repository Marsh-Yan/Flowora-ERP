package com.flowora.erp.workflow;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.*;
import com.flowora.erp.workflow.v2.WorkflowResourceAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkflowAssigneePolicyTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final DatabaseIdentityAuthenticator identities = mock(DatabaseIdentityAuthenticator.class);
    final WorkflowResourceAccessPolicy resources = mock(WorkflowResourceAccessPolicy.class);
    final WorkflowAssigneePolicy policy = new WorkflowAssigneePolicy(jdbc, identities, resources);

    FloworaPrincipal target(String org, String... permissions) {
        return new FloworaPrincipal("target", "target@audit.invalid", "Target", org, "Org", "membership", null,
                DataScope.ALL, List.of("CUSTOM"), List.of(permissions), false);
    }
    void active(FloworaPrincipal target) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("target"), eq("org"))).thenReturn(1);
        when(identities.principalForOrganization("target", "org")).thenReturn(target);
    }
    void pending() { policy.require("org", "target", WorkflowResourceType.PURCHASE_ORDER, "po", true); }
    void invalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PlatformApiException.class,
                ex -> { assertThat(ex.status().value()).isEqualTo(409); assertThat(ex.code()).isEqualTo("WORKFLOW_ASSIGNEE_INVALID"); assertThat(ex.args()).isEmpty(); });
    }
    @Test void inactiveOrForeignTargetDoesNotResolveIdentityOrSource() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("target"), eq("org"))).thenReturn(0);
        invalid(this::pending); verifyNoInteractions(identities, resources);
    }
    @Test void validCustomApproverAndDelegateBothRequireSourceAccess() {
        for (String action : List.of("workflow:approve", "workflow:delegate")) {
            var target = target("org", "workflow:view", action); active(target); pending();
            verify(resources).require(target, "PURCHASE_ORDER", "po", "read");
        }
    }
    @Test void inboxAndActionPermissionsAreBothRequiredWithoutRoleNameBypass() {
        for (var target : List.of(target("org", "workflow:view"), target("org", "workflow:approve"), target("org", "workflow:admin", "workflow:view"))) {
            active(target); invalid(this::pending);
        }
        verifyNoInteractions(resources);
    }
    @Test void targetScopeRejectionIsGenericAndDoesNotExposeSourceOrUserDetails() {
        var target = target("org", "workflow:view", "workflow:approve"); active(target);
        when(resources.require(target, "PURCHASE_ORDER", "po", "read")).thenThrow(new AccessDeniedException("Private source details"));
        invalid(this::pending);
    }
    @Test void autoApprovedAssigneeNeedsSubmitRatherThanApproval() {
        active(target("org", "workflow:view", "workflow:approve"));
        invalid(() -> policy.require("org", "target", WorkflowResourceType.GENERAL, "opaque", false));
        var completer = target("org", "workflow:view", "workflow:submit"); active(completer);
        policy.require("org", "target", WorkflowResourceType.GENERAL, "opaque", false);
        verify(resources).require(completer, "GENERAL", "opaque", "read");
    }
    @Test void identityResolutionCannotReturnAnotherOrganization() {
        active(target("other", "workflow:view", "workflow:approve")); invalid(this::pending);
        verifyNoInteractions(resources);
    }
}
