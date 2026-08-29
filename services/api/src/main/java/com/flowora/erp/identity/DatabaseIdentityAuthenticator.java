package com.flowora.erp.identity;

import com.flowora.erp.common.api.InvalidCredentialsException;
import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@Profile("local")
public class DatabaseIdentityAuthenticator implements IdentityAuthenticator {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final int maxFailures;
    private final int lockMinutes;

    public DatabaseIdentityAuthenticator(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${flowora.security.max-login-failures:5}") int maxFailures,
            @Value("${flowora.security.login-lock-minutes:15}") int lockMinutes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.maxFailures = Math.max(3, Math.min(maxFailures, 10));
        this.lockMinutes = Math.max(5, Math.min(lockMinutes, 60));
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public FloworaPrincipal authenticate(String username, String password) {
        String normalized = normalize(username);
        List<UserRow> users = jdbcTemplate.query("""
                SELECT id, username, display_name, password_hash, status,
                       failed_login_count, locked_until, must_change_password
                FROM flowora_user_account
                WHERE LOWER(username) = ?
                FOR UPDATE
                """, this::mapUser, normalized);
        if (users.isEmpty()) throw new InvalidCredentialsException();
        UserRow user = users.getFirst();
        Instant now = Instant.now();
        if (user.status() == UserStatus.DISABLED || user.status() == UserStatus.INVITED) {
            throw new InvalidCredentialsException();
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            throw new PlatformApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "errors.accountLocked",
                    Map.of("lockedUntil", user.lockedUntil().toString()));
        }
        if (password == null || !passwordEncoder.matches(password, user.passwordHash())) {
            registerFailure(user, now);
            throw new InvalidCredentialsException();
        }
        jdbcTemplate.update("""
                UPDATE flowora_user_account
                SET status = 'ACTIVE', failed_login_count = 0, locked_until = NULL, last_login_at = ?
                WHERE id = ?
                """, Timestamp.from(now), user.id());
        return principal(user.id(), user.username(), user.displayName(), user.mustChangePassword(), null);
    }

    @Transactional(readOnly = true)
    public FloworaPrincipal principalForOrganization(String userId, String organizationId) {
        UserRow user = jdbcTemplate.queryForObject("""
                SELECT id, username, display_name, password_hash, status,
                       failed_login_count, locked_until, must_change_password
                FROM flowora_user_account WHERE id = ?
                """, this::mapUser, userId);
        if (user == null || user.status() != UserStatus.ACTIVE) throw new InvalidCredentialsException();
        return principal(user.id(), user.username(), user.displayName(), user.mustChangePassword(), organizationId);
    }

    private FloworaPrincipal principal(
            String userId, String username, String displayName,
            boolean mustChangePassword, String requestedOrganizationId
    ) {
        List<MembershipRow> memberships = jdbcTemplate.query("""
                SELECT membership.id, membership.organization_id, organization.name,
                       membership.department_id, membership.default_organization
                FROM flowora_organization_membership membership
                JOIN flowora_organization organization ON organization.id = membership.organization_id
                WHERE membership.user_id = ? AND membership.status = 'ACTIVE'
                  AND organization.status = 'ACTIVE'
                  AND (? IS NULL OR membership.organization_id = ?)
                ORDER BY membership.default_organization DESC, organization.name
                """, this::mapMembership, userId, requestedOrganizationId, requestedOrganizationId);
        if (memberships.isEmpty()) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "errors.organizationAccessDenied");
        }
        MembershipRow membership = memberships.getFirst();
        List<String> roles = jdbcTemplate.queryForList("""
                SELECT role.code
                FROM flowora_membership_role membership_role
                JOIN flowora_role role ON role.id = membership_role.role_id
                WHERE membership_role.membership_id = ? AND role.active = TRUE
                ORDER BY role.code
                """, String.class, membership.id());
        List<String> permissions = jdbcTemplate.queryForList("""
                SELECT DISTINCT role_permission.permission_code
                FROM flowora_membership_role membership_role
                JOIN flowora_role role ON role.id = membership_role.role_id AND role.active = TRUE
                JOIN flowora_role_permission role_permission ON role_permission.role_id = role.id
                WHERE membership_role.membership_id = ?
                ORDER BY role_permission.permission_code
                """, String.class, membership.id());
        List<String> scopes = jdbcTemplate.queryForList("""
                SELECT DISTINCT role.data_scope
                FROM flowora_membership_role membership_role
                JOIN flowora_role role ON role.id = membership_role.role_id AND role.active = TRUE
                WHERE membership_role.membership_id = ?
                """, String.class, membership.id());
        DataScope scope = scopes.isEmpty() ? DataScope.SELF : DataScope.mostPermissive(scopes);
        return new FloworaPrincipal(userId, username, displayName, membership.organizationId(),
                membership.organizationName(), membership.id(), membership.departmentId(), scope,
                roles, permissions, mustChangePassword);
    }

    private void registerFailure(UserRow user, Instant now) {
        int failures = user.failedLoginCount() + 1;
        if (failures >= maxFailures) {
            jdbcTemplate.update("""
                    UPDATE flowora_user_account
                    SET status = 'LOCKED', failed_login_count = ?, locked_until = ?, last_failed_login_at = ?
                    WHERE id = ?
                    """, failures, Timestamp.from(now.plus(lockMinutes, ChronoUnit.MINUTES)), Timestamp.from(now), user.id());
        } else {
            jdbcTemplate.update("""
                    UPDATE flowora_user_account
                    SET failed_login_count = ?, last_failed_login_at = ? WHERE id = ?
                    """, failures, Timestamp.from(now), user.id());
        }
    }

    private UserRow mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp lockedUntil = resultSet.getTimestamp("locked_until");
        return new UserRow(
                resultSet.getString("id"), resultSet.getString("username"), resultSet.getString("display_name"),
                resultSet.getString("password_hash"), UserStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("failed_login_count"), lockedUntil == null ? null : lockedUntil.toInstant(),
                resultSet.getBoolean("must_change_password")
        );
    }

    private MembershipRow mapMembership(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MembershipRow(
                resultSet.getString("id"), resultSet.getString("organization_id"), resultSet.getString("name"),
                resultSet.getString("department_id"), resultSet.getBoolean("default_organization")
        );
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private record UserRow(
            String id, String username, String displayName, String passwordHash, UserStatus status,
            int failedLoginCount, Instant lockedUntil, boolean mustChangePassword
    ) {}

    private record MembershipRow(
            String id, String organizationId, String organizationName, String departmentId,
            boolean defaultOrganization
    ) {}
}
