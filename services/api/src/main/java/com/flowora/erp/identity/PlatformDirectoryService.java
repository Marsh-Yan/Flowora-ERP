package com.flowora.erp.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("local | production")
public class PlatformDirectoryService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicy;
    private final DatabaseAccountService accountService;
    private final ObjectMapper objectMapper;

    public PlatformDirectoryService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicy,
            DatabaseAccountService accountService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.accountService = accountService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<OrganizationView> organizations() {
        return jdbcTemplate.query("""
                SELECT id, parent_id, name, status, base_currency_code, timezone,
                       fiscal_year_start_month, amount_scale, price_scale, quantity_scale,
                       tax_rounding_mode, reservation_ttl_minutes, expiry_warning_days,
                       default_approval_policy
                FROM flowora_organization ORDER BY name
                """, this::mapOrganization);
    }

    @Transactional
    public OrganizationView createOrganization(CreateOrganization command) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO flowora_organization (
                    id, parent_id, name, status, base_currency_code, timezone,
                    fiscal_year_start_month, amount_scale, price_scale, quantity_scale,
                    tax_rounding_mode, reservation_ttl_minutes, expiry_warning_days,
                    default_approval_policy, approval_threshold, default_tax_rate, active
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, TRUE)
                """, id, blankToNull(command.parentId()), command.name().trim(), normalizeCode(command.baseCurrencyCode()),
                command.timezone().trim(), command.fiscalYearStartMonth(), command.amountScale(),
                command.priceScale(), command.quantityScale(), command.taxRoundingMode(),
                command.reservationTtlMinutes(), command.expiryWarningDays(), command.defaultApprovalPolicy());
        return organization(id);
    }

    @Transactional
    public void archiveOrganization(String organizationId) {
        int updated = jdbcTemplate.update("""
                UPDATE flowora_organization SET status = 'ARCHIVED', active = FALSE WHERE id = ?
                """, organizationId);
        if (updated != 1) notFound("organization", organizationId);
        jdbcTemplate.update("""
                UPDATE flowora_organization_membership SET status = 'DISABLED' WHERE organization_id = ?
                """, organizationId);
    }

    @Transactional(readOnly = true)
    public List<DepartmentView> departments(String organizationId) {
        return jdbcTemplate.query("""
                SELECT id, parent_id, code, name, manager_membership_id, active, version_no
                FROM flowora_department WHERE organization_id = ? ORDER BY code
                """, this::mapDepartment, organizationId);
    }

    @Transactional
    public DepartmentView createDepartment(String organizationId, CreateDepartment command) {
        validateParentDepartment(organizationId, command.parentId());
        String id = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO flowora_department (
                        id, organization_id, parent_id, code, name, manager_membership_id, active
                    ) VALUES (?, ?, ?, ?, ?, ?, TRUE)
                    """, id, organizationId, blankToNull(command.parentId()), normalizeCode(command.code()),
                    command.name().trim(), blankToNull(command.managerMembershipId()));
        } catch (DuplicateKeyException exception) {
            duplicate("department", command.code());
        }
        return department(organizationId, id);
    }

    @Transactional
    public void deactivateDepartment(String organizationId, String departmentId) {
        int updated = jdbcTemplate.update("""
                UPDATE flowora_department SET active = FALSE, version_no = version_no + 1
                WHERE id = ? AND organization_id = ?
                """, departmentId, organizationId);
        if (updated != 1) notFound("department", departmentId);
    }

    @Transactional(readOnly = true)
    public List<PermissionView> permissions() {
        return jdbcTemplate.query("""
                SELECT code, resource_code, action_code, description, `sensitive`
                FROM flowora_permission ORDER BY resource_code, action_code
                """, (rs, row) -> new PermissionView(
                rs.getString("code"), rs.getString("resource_code"), rs.getString("action_code"),
                rs.getString("description"), rs.getBoolean("sensitive")
        ));
    }

    @Transactional(readOnly = true)
    public List<RoleView> roles(String organizationId) {
        List<RoleView> roles = jdbcTemplate.query("""
                SELECT id, code, name, description, data_scope, system_role, active
                FROM flowora_role WHERE organization_id = ? ORDER BY code
                """, (rs, row) -> new RoleView(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), DataScope.valueOf(rs.getString("data_scope")),
                rs.getBoolean("system_role"), rs.getBoolean("active"), List.of()
        ), organizationId);
        return roles.stream().map(role -> new RoleView(
                role.id(), role.code(), role.name(), role.description(), role.dataScope(), role.systemRole(),
                role.active(), jdbcTemplate.queryForList("""
                        SELECT permission_code FROM flowora_role_permission
                        WHERE role_id = ? ORDER BY permission_code
                        """, String.class, role.id())
        )).toList();
    }

    @Transactional
    public RoleView createRole(String organizationId, CreateRole command) {
        String id = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO flowora_role (
                        id, organization_id, code, name, description, data_scope, system_role, active
                    ) VALUES (?, ?, ?, ?, ?, ?, FALSE, TRUE)
                    """, id, organizationId, normalizeCode(command.code()), command.name().trim(),
                    blankToNull(command.description()), command.dataScope().name());
        } catch (DuplicateKeyException exception) {
            duplicate("role", command.code());
        }
        replacePermissions(id, command.permissions());
        return role(organizationId, id);
    }

    @Transactional
    public RoleView updateRole(String organizationId, String roleId, UpdateRole command) {
        int updated = jdbcTemplate.update("""
                UPDATE flowora_role
                SET name = ?, description = ?, data_scope = ?, active = ?
                WHERE id = ? AND organization_id = ?
                """, command.name().trim(), blankToNull(command.description()), command.dataScope().name(),
                command.active(), roleId, organizationId);
        if (updated != 1) notFound("role", roleId);
        replacePermissions(roleId, command.permissions());
        return role(organizationId, roleId);
    }

    @Transactional(readOnly = true)
    public List<UserView> users(String organizationId) {
        return jdbcTemplate.query("""
                SELECT user.id, user.username, user.display_name, user.status,
                       membership.id AS membership_id, membership.department_id,
                       membership.status AS membership_status, membership.default_organization
                FROM flowora_organization_membership membership
                JOIN flowora_user_account user ON user.id = membership.user_id
                WHERE membership.organization_id = ?
                ORDER BY user.display_name, user.username
                """, (rs, row) -> mapUser(rs, organizationId), organizationId);
    }

    @Transactional
    public UserView createUser(String organizationId, CreateUser command) {
        String username = command.username().trim().toLowerCase();
        passwordPolicy.validate(username, command.temporaryPassword(), List.of());
        String userId = UUID.randomUUID().toString();
        String membershipId = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO flowora_user_account (
                        id, organization_id, username, display_name, password_hash, status,
                        failed_login_count, must_change_password, active
                    ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0, TRUE, TRUE)
                    """, userId, organizationId, username, command.displayName().trim(),
                    passwordEncoder.encode(command.temporaryPassword()));
            jdbcTemplate.update("""
                    INSERT INTO flowora_organization_membership (
                        id, organization_id, user_id, department_id, status, default_organization
                    ) VALUES (?, ?, ?, ?, 'ACTIVE', TRUE)
                    """, membershipId, organizationId, userId, blankToNull(command.departmentId()));
            assignRoles(organizationId, membershipId, command.roleIds());
        } catch (DuplicateKeyException exception) {
            duplicate("user", username);
        }
        return user(organizationId, userId);
    }

    @Transactional
    public UserView updateMembership(String organizationId, String userId, UpdateMembership command) {
        String membershipId = jdbcTemplate.query("""
                SELECT id FROM flowora_organization_membership
                WHERE organization_id = ? AND user_id = ? FOR UPDATE
                """, rs -> rs.next() ? rs.getString("id") : null, organizationId, userId);
        if (membershipId == null) notFound("membership", userId);
        if (!command.active()) protectLastAdministrator(userId, membershipId);
        jdbcTemplate.update("""
                UPDATE flowora_organization_membership
                SET department_id = ?, status = ?, version_no = version_no + 1
                WHERE id = ?
                """, blankToNull(command.departmentId()), command.active() ? "ACTIVE" : "DISABLED", membershipId);
        assignRoles(organizationId, membershipId, command.roleIds());
        return user(organizationId, userId);
    }

    @Transactional
    public void disableUser(String organizationId, String userId) {
        requireMembership(organizationId, userId);
        List<String> memberships = jdbcTemplate.queryForList("""
                SELECT id FROM flowora_organization_membership WHERE user_id = ?
                """, String.class, userId);
        memberships.forEach(membershipId -> protectLastAdministrator(userId, membershipId));
        int updated = jdbcTemplate.update("""
                UPDATE flowora_user_account SET status = 'DISABLED', active = FALSE WHERE id = ?
                """, userId);
        if (updated != 1) notFound("user", userId);
        jdbcTemplate.update("""
                UPDATE flowora_organization_membership SET status = 'DISABLED' WHERE user_id = ?
                """, userId);
        accountService.revokeUserSessions(userId);
    }

    public void resetPassword(String organizationId, String userId, String temporaryPassword) {
        requireMembership(organizationId, userId);
        accountService.resetPassword(userId, temporaryPassword);
    }

    @Transactional
    public void auditChange(
            FloworaPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String requestId,
            String reason,
            Object before,
            Object after
    ) {
        jdbcTemplate.update("""
                INSERT INTO flowora_audit_event (
                    id, organization_id, actor_user_id, action_code, resource_type,
                    resource_id, request_id, details_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), actor.organizationId(), actor.userId(), action,
                resourceType, resourceId, requestId, json(Map.of(
                        "reason", reason == null ? "" : reason,
                        "before", before == null ? Map.of() : before,
                        "after", after == null ? Map.of() : after
                )));
    }

    private OrganizationView organization(String id) {
        List<OrganizationView> found = jdbcTemplate.query("""
                SELECT id, parent_id, name, status, base_currency_code, timezone,
                       fiscal_year_start_month, amount_scale, price_scale, quantity_scale,
                       tax_rounding_mode, reservation_ttl_minutes, expiry_warning_days,
                       default_approval_policy
                FROM flowora_organization WHERE id = ?
                """, this::mapOrganization, id);
        if (found.isEmpty()) notFound("organization", id);
        return found.getFirst();
    }

    private OrganizationView mapOrganization(ResultSet rs, int row) throws SQLException {
        return new OrganizationView(
                rs.getString("id"), rs.getString("parent_id"), rs.getString("name"), rs.getString("status"),
                rs.getString("base_currency_code"), rs.getString("timezone"),
                rs.getInt("fiscal_year_start_month"), rs.getInt("amount_scale"), rs.getInt("price_scale"),
                rs.getInt("quantity_scale"), rs.getString("tax_rounding_mode"),
                rs.getInt("reservation_ttl_minutes"), rs.getInt("expiry_warning_days"),
                rs.getString("default_approval_policy")
        );
    }

    private DepartmentView department(String organizationId, String id) {
        List<DepartmentView> found = jdbcTemplate.query("""
                SELECT id, parent_id, code, name, manager_membership_id, active, version_no
                FROM flowora_department WHERE organization_id = ? AND id = ?
                """, this::mapDepartment, organizationId, id);
        if (found.isEmpty()) notFound("department", id);
        return found.getFirst();
    }

    private DepartmentView mapDepartment(ResultSet rs, int row) throws SQLException {
        return new DepartmentView(
                rs.getString("id"), rs.getString("parent_id"), rs.getString("code"), rs.getString("name"),
                rs.getString("manager_membership_id"), rs.getBoolean("active"), rs.getLong("version_no")
        );
    }

    private RoleView role(String organizationId, String id) {
        return roles(organizationId).stream().filter(role -> role.id().equals(id)).findFirst()
                .orElseThrow(() -> new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound"));
    }

    private UserView user(String organizationId, String userId) {
        return users(organizationId).stream().filter(user -> user.id().equals(userId)).findFirst()
                .orElseThrow(() -> new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound"));
    }

    private UserView mapUser(ResultSet rs, String organizationId) throws SQLException {
        String membershipId = rs.getString("membership_id");
        List<String> roleIds = jdbcTemplate.queryForList("""
                SELECT role_id FROM flowora_membership_role WHERE membership_id = ? ORDER BY role_id
                """, String.class, membershipId);
        return new UserView(
                rs.getString("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("status"), membershipId, organizationId, rs.getString("department_id"),
                rs.getString("membership_status"), rs.getBoolean("default_organization"), roleIds
        );
    }

    private void replacePermissions(String roleId, List<String> permissions) {
        List<String> unique = permissions == null ? List.of() : permissions.stream().distinct().toList();
        if (!unique.isEmpty()) {
            int known = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM flowora_permission WHERE code IN (%s)
                    """.formatted(placeholders(unique.size())), Integer.class, unique.toArray());
            if (known != unique.size()) {
                throw new PlatformApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PERMISSION", "errors.unknownPermission");
            }
        }
        jdbcTemplate.update("DELETE FROM flowora_role_permission WHERE role_id = ?", roleId);
        unique.forEach(permission -> jdbcTemplate.update("""
                INSERT INTO flowora_role_permission (role_id, permission_code) VALUES (?, ?)
                """, roleId, permission));
    }

    private void assignRoles(String organizationId, String membershipId, List<String> roleIds) {
        List<String> unique = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        if (!unique.isEmpty()) {
            List<String> valid = jdbcTemplate.queryForList("""
                    SELECT id FROM flowora_role
                    WHERE organization_id = ? AND active = TRUE AND id IN (%s)
                    """.formatted(placeholders(unique.size())), String.class,
                    merge(organizationId, unique));
            if (valid.size() != unique.size()) {
                throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE_ASSIGNMENT", "errors.invalidRoleAssignment");
            }
        }
        jdbcTemplate.update("DELETE FROM flowora_membership_role WHERE membership_id = ?", membershipId);
        unique.forEach(roleId -> jdbcTemplate.update("""
                INSERT INTO flowora_membership_role (membership_id, role_id) VALUES (?, ?)
                """, membershipId, roleId));
    }

    private void protectLastAdministrator(String userId, String membershipId) {
        Integer targetIsAdmin = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_membership_role membership_role
                JOIN flowora_role role ON role.id = membership_role.role_id
                WHERE membership_role.membership_id = ? AND role.code = 'ADMIN' AND role.active = TRUE
                """, Integer.class, membershipId);
        if (targetIsAdmin == null || targetIsAdmin == 0) return;
        Integer activeAdmins = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT membership.user_id)
                FROM flowora_organization_membership membership
                JOIN flowora_membership_role membership_role ON membership_role.membership_id = membership.id
                JOIN flowora_role role ON role.id = membership_role.role_id
                JOIN flowora_user_account user ON user.id = membership.user_id
                WHERE membership.status = 'ACTIVE' AND user.status = 'ACTIVE'
                  AND role.code = 'ADMIN' AND role.active = TRUE
                """, Integer.class);
        if (activeAdmins != null && activeAdmins <= 1) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "LAST_INSTANCE_ADMIN_REQUIRED", "errors.lastInstanceAdminRequired",
                    Map.of("userId", userId));
        }
    }

    private void validateParentDepartment(String organizationId, String parentId) {
        if (parentId == null || parentId.isBlank()) return;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_department WHERE id = ? AND organization_id = ?
                """, Integer.class, parentId, organizationId);
        if (count == null || count == 0) notFound("department", parentId);
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private Object[] merge(String first, List<String> rest) {
        List<Object> values = new ArrayList<>();
        values.add(first);
        values.addAll(rest);
        return values.toArray();
    }
    private void requireMembership(String organizationId, String userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_organization_membership
                WHERE organization_id = ? AND user_id = ?
                """, Integer.class, organizationId, userId);
        if (count == null || count == 0) {
            notFound("user", userId);
        }
    }


    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize audit details", exception);
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void notFound(String resource, String id) {
        throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound",
                Map.of("resource", resource, "id", id));
    }

    private void duplicate(String resource, String code) {
        throw new PlatformApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "errors.duplicateCode",
                Map.of("resource", resource, "code", code));
    }

    public record OrganizationView(
            String id, String parentId, String name, String status, String baseCurrencyCode, String timezone,
            int fiscalYearStartMonth, int amountScale, int priceScale, int quantityScale,
            String taxRoundingMode, int reservationTtlMinutes, int expiryWarningDays,
            String defaultApprovalPolicy
    ) {}

    public record CreateOrganization(
            String parentId, String name, String baseCurrencyCode, String timezone,
            int fiscalYearStartMonth, int amountScale, int priceScale, int quantityScale,
            String taxRoundingMode, int reservationTtlMinutes, int expiryWarningDays,
            String defaultApprovalPolicy
    ) {}

    public record DepartmentView(
            String id, String parentId, String code, String name, String managerMembershipId,
            boolean active, long version
    ) {}

    public record CreateDepartment(String parentId, String code, String name, String managerMembershipId) {}

    public record PermissionView(
            String code, String resource, String action, String description, boolean sensitive
    ) {}

    public record RoleView(
            String id, String code, String name, String description, DataScope dataScope,
            boolean systemRole, boolean active, List<String> permissions
    ) {}

    public record CreateRole(
            String code, String name, String description, DataScope dataScope, List<String> permissions
    ) {}

    public record UpdateRole(
            String name, String description, DataScope dataScope, boolean active, List<String> permissions
    ) {}

    public record UserView(
            String id, String username, String displayName, String status, String membershipId,
            String organizationId, String departmentId, String membershipStatus,
            boolean defaultOrganization, List<String> roleIds
    ) {}

    public record CreateUser(
            String username, String displayName, String temporaryPassword,
            String departmentId, List<String> roleIds
    ) {}

    public record UpdateMembership(String departmentId, boolean active, List<String> roleIds) {}
}
