package com.flowora.erp.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("floworaAuthorization")
public class FloworaAuthorization {
    private DatabaseIdentityAuthenticator authenticator;

    @Autowired(required = false)
    void setAuthenticator(DatabaseIdentityAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public boolean has(Authentication authentication, String permission) {
        return principal(authentication).permissions().contains(permission);
    }

    public boolean canAccess(
            Authentication authentication,
            String ownerUserId,
            String departmentId,
            boolean assigned
    ) {
        FloworaPrincipal principal = principal(authentication);
        return switch (principal.dataScope()) {
            case ALL -> true;
            case DEPARTMENT -> principal.departmentId() != null && principal.departmentId().equals(departmentId);
            case SELF -> principal.userId().equals(ownerUserId);
            case ASSIGNED -> assigned;
        };
    }

    public FloworaPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof FloworaPrincipal principal)) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated Flowora principal required");
        }
        if (authenticator == null) {
            return principal;
        }
        return authenticator.principalForOrganization(principal.userId(), principal.organizationId());
    }
}
