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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("local")
public class DatabaseAccountService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicy;
    private final DatabaseIdentityAuthenticator authenticator;
    private final SessionGovernanceService sessionGovernance;
    private final int historySize;

    public DatabaseAccountService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicy,
            DatabaseIdentityAuthenticator authenticator,
            SessionGovernanceService sessionGovernance,
            @Value("${flowora.security.password-history:5}") int historySize
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.authenticator = authenticator;
        this.sessionGovernance = sessionGovernance;
        this.historySize = Math.max(1, Math.min(historySize, 12));
    }

    @Transactional(readOnly = true)
    public List<OrganizationOption> organizations(String userId) {
        return jdbcTemplate.query("""
                SELECT membership.organization_id, organization.name, membership.default_organization,
                       membership.department_id
                FROM flowora_organization_membership membership
                JOIN flowora_organization organization ON organization.id = membership.organization_id
                WHERE membership.user_id = ? AND membership.status = 'ACTIVE'
                  AND organization.status = 'ACTIVE'
                ORDER BY membership.default_organization DESC, organization.name
                """, this::mapOrganization, userId);
    }

    public FloworaPrincipal switchOrganization(String userId, String organizationId) {
        return authenticator.principalForOrganization(userId, organizationId);
    }

    @Transactional
    public void changePassword(FloworaPrincipal principal, String currentPassword, String newPassword) {
        Map<String, Object> account = jdbcTemplate.queryForMap("""
                SELECT username, password_hash FROM flowora_user_account WHERE id = ? FOR UPDATE
                """, principal.userId());
        String currentHash = (String) account.get("password_hash");
        String username = (String) account.get("username");
        if (!passwordEncoder.matches(currentPassword, currentHash)) {
            throw new InvalidCredentialsException();
        }
        List<String> recent = jdbcTemplate.queryForList("""
                SELECT password_hash FROM flowora_password_history
                WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
                """, String.class, principal.userId(), historySize - 1);
        recent = new java.util.ArrayList<>(recent);
        recent.addFirst(currentHash);
        passwordPolicy.validate(username, newPassword, recent);
        jdbcTemplate.update("""
                INSERT INTO flowora_password_history (id, user_id, password_hash)
                VALUES (?, ?, ?)
                """, UUID.randomUUID().toString(), principal.userId(), currentHash);
        jdbcTemplate.update("""
                UPDATE flowora_user_account
                SET password_hash = ?, must_change_password = FALSE, password_changed_at = ?,
                    failed_login_count = 0, locked_until = NULL, status = 'ACTIVE'
                WHERE id = ?
                """, passwordEncoder.encode(newPassword), Timestamp.from(Instant.now()), principal.userId());
        jdbcTemplate.update("""
                DELETE FROM flowora_password_history
                WHERE user_id = ? AND id NOT IN (
                    SELECT id FROM (
                        SELECT id FROM flowora_password_history
                        WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
                    ) retained
                )
                """, principal.userId(), principal.userId(), historySize);
        revokeAfterCommit(username);
    }

    @Transactional
    public void resetPassword(String userId, String temporaryPassword) {
        Map<String, Object> account = jdbcTemplate.queryForMap("""
                SELECT username, password_hash FROM flowora_user_account WHERE id = ? FOR UPDATE
                """, userId);
        List<String> recent = jdbcTemplate.queryForList("""
                SELECT password_hash FROM flowora_password_history
                WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
                """, String.class, userId, historySize - 1);
        recent = new java.util.ArrayList<>(recent);
        recent.addFirst((String) account.get("password_hash"));
        passwordPolicy.validate((String) account.get("username"), temporaryPassword, recent);
        jdbcTemplate.update("""
                INSERT INTO flowora_password_history (id, user_id, password_hash)
                VALUES (?, ?, ?)
                """, UUID.randomUUID().toString(), userId, account.get("password_hash"));
        int updated = jdbcTemplate.update("""
                UPDATE flowora_user_account
                SET password_hash = ?, must_change_password = TRUE, failed_login_count = 0,
                    locked_until = NULL, status = 'ACTIVE', password_changed_at = ?
                WHERE id = ?
                """, passwordEncoder.encode(temporaryPassword), Timestamp.from(Instant.now()), userId);
        if (updated != 1) {
            throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound");
        }
        revokeAfterCommit((String) account.get("username"));
    }

    public void revokeUserSessions(String userId) {
        String username = jdbcTemplate.queryForObject(
                "SELECT username FROM flowora_user_account WHERE id = ?", String.class, userId);
        revokeAfterCommit(username);
    }

    private void revokeAfterCommit(String username) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sessionGovernance.revokeAll(username);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionGovernance.revokeAll(username);
            }
        });
    }

    private OrganizationOption mapOrganization(ResultSet rs, int rowNumber) throws SQLException {
        return new OrganizationOption(
                rs.getString("organization_id"), rs.getString("name"),
                rs.getBoolean("default_organization"), rs.getString("department_id")
        );
    }

    public record OrganizationOption(
            String id, String name, boolean defaultOrganization, String departmentId
    ) {}
}
