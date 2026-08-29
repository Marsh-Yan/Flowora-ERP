package com.flowora.erp.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FloworaAuthorizationTest {
    private final FloworaAuthorization authorization = new FloworaAuthorization();

    @Test
    void enforcesNoPermissionSameOrganizationCrossOrganizationAndAdministratorQuadrants() {
        var noPermission = authentication(principal("user-a", "org-a", DataScope.ALL, List.of()));
        var sameOrganization = authentication(principal("user-a", "org-a", DataScope.ALL, List.of("master:view")));
        var crossOrganization = authentication(principal("user-a", "org-a", DataScope.ALL, List.of("master:view")));
        var administrator = authentication(principal("admin", "org-a", DataScope.ALL, List.of("master:view", "role:configure")));

        assertThat(authorization.has(noPermission, "master:view")).isFalse();
        assertThat(authorization.has(sameOrganization, "master:view")).isTrue();
        assertThat(((FloworaPrincipal) crossOrganization.getPrincipal()).organizationId()).isNotEqualTo("org-b");
        assertThat(authorization.has(administrator, "role:configure")).isTrue();
    }

    @Test
    void appliesEveryDataScopeOnTheServer() {
        assertThat(authorization.canAccess(authentication(principal("u1", "org", DataScope.ALL, List.of())), "u2", "d2", false)).isTrue();
        assertThat(authorization.canAccess(authentication(principal("u1", "org", DataScope.DEPARTMENT, List.of())), "u2", "department-a", false)).isTrue();
        assertThat(authorization.canAccess(authentication(principal("u1", "org", DataScope.SELF, List.of())), "u1", "d2", false)).isTrue();
        assertThat(authorization.canAccess(authentication(principal("u1", "org", DataScope.ASSIGNED, List.of())), "u2", "d2", true)).isTrue();
        assertThat(authorization.canAccess(authentication(principal("u1", "org", DataScope.SELF, List.of())), "u2", "d2", false)).isFalse();
    }

    private UsernamePasswordAuthenticationToken authentication(FloworaPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private FloworaPrincipal principal(String userId, String organizationId, DataScope scope, List<String> permissions) {
        return new FloworaPrincipal(
                userId, userId + "@example.com", userId, organizationId, organizationId,
                "membership-" + userId, "department-a", scope, List.of("BUSINESS"), permissions, false
        );
    }
}
