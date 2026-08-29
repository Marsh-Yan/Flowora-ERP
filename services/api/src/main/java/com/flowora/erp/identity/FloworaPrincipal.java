package com.flowora.erp.identity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public record FloworaPrincipal(
        String userId,
        String username,
        String displayName,
        String organizationId,
        String organizationName,
        String membershipId,
        String departmentId,
        DataScope dataScope,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword
) implements UserDetails {
    public FloworaPrincipal {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }

    public FloworaPrincipal(
            String userId,
            String username,
            String displayName,
            String organizationId,
            String organizationName,
            List<String> roles
    ) {
        this(userId, username, displayName, organizationId, organizationName,
                "membership-" + userId, null, DataScope.ALL, roles,
                DemoPermissionCatalog.forRoles(roles), false);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return List.copyOf(authorities);
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
