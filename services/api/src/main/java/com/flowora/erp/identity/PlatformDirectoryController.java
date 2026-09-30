package com.flowora.erp.identity;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2")
@Profile("local | production")
public class PlatformDirectoryController {
    private final PlatformDirectoryService service;
    private final FloworaAuthorization authorization;

    public PlatformDirectoryController(PlatformDirectoryService service, FloworaAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/organizations")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'organization:view')")
    public ApiResponse<List<PlatformDirectoryService.OrganizationView>> organizations(Authentication authentication, HttpServletRequest request) {
        return response(service.organizations(principal(authentication).userId()), request);
    }

    @PostMapping("/organizations")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'organization:configure')")
    public ApiResponse<PlatformDirectoryService.OrganizationView> createOrganization(
            @Valid @RequestBody OrganizationRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        var created = service.createOrganization(principal(authentication), body.toCommand());
        audit(authentication, "ORGANIZATION_CREATED", "ORGANIZATION", created.id(), body.reason(), null, created, request);
        return response(created, request);
    }

    @PostMapping("/organizations/{organizationId}/archive")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'organization:configure')")
    public ApiResponse<Void> archiveOrganization(
            @PathVariable String organizationId,
            @Valid @RequestBody ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal actor = principal(authentication);
        service.archiveOrganization(actor, organizationId);
        service.auditChange(actor, "ORGANIZATION_ARCHIVED", "ORGANIZATION", organizationId,
                RequestIdFilter.get(request), body.reason(), null, null);
        return response(null, request);
    }

    @GetMapping("/departments")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'organization:view')")
    public ApiResponse<List<PlatformDirectoryService.DepartmentView>> departments(
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.departments(principal(authentication).organizationId()), request);
    }

    @PostMapping("/departments")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'organization:configure')")
    public ApiResponse<PlatformDirectoryService.DepartmentView> createDepartment(
            @Valid @RequestBody DepartmentRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = principal(authentication);
        var created = service.createDepartment(principal.organizationId(), body.toCommand());
        audit(authentication, "DEPARTMENT_CREATED", "DEPARTMENT", created.id(), body.reason(), null, created, request);
        return response(created, request);
    }

    @PostMapping("/departments/{departmentId}/deactivate")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'organization:configure')")
    public ApiResponse<Void> deactivateDepartment(
            @PathVariable String departmentId,
            @Valid @RequestBody ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = principal(authentication);
        service.deactivateDepartment(principal.organizationId(), departmentId);
        audit(authentication, "DEPARTMENT_DEACTIVATED", "DEPARTMENT", departmentId, body.reason(), null, null, request);
        return response(null, request);
    }

    @GetMapping("/permissions")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'role:view')")
    public ApiResponse<List<PlatformDirectoryService.PermissionView>> permissions(HttpServletRequest request) {
        return response(service.permissions(), request);
    }

    @GetMapping("/roles")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'role:view')")
    public ApiResponse<List<PlatformDirectoryService.RoleView>> roles(
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.roles(principal(authentication).organizationId()), request);
    }

    @PostMapping("/roles")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'role:configure')")
    public ApiResponse<PlatformDirectoryService.RoleView> createRole(
            @Valid @RequestBody RoleCreateRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = principal(authentication);
        var created = service.createRole(principal.organizationId(), body.toCommand());
        audit(authentication, "ROLE_CREATED", "ROLE", created.id(), body.reason(), null, created, request);
        return response(created, request);
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'role:configure')")
    public ApiResponse<PlatformDirectoryService.RoleView> updateRole(
            @PathVariable String roleId,
            @Valid @RequestBody RoleUpdateRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = principal(authentication);
        var updated = service.updateRole(principal.organizationId(), roleId, body.toCommand());
        audit(authentication, "ROLE_UPDATED", "ROLE", roleId, body.reason(), null, updated, request);
        return response(updated, request);
    }

    @GetMapping("/users")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'user:view')")
    public ApiResponse<List<PlatformDirectoryService.UserView>> users(
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.users(principal(authentication).organizationId()), request);
    }

    @PostMapping("/users")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'user:configure')")
    public ApiResponse<PlatformDirectoryService.UserView> createUser(
            @Valid @RequestBody UserCreateRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = principal(authentication);
        var created = service.createUser(principal.organizationId(), body.toCommand());
        audit(authentication, "USER_CREATED", "USER", created.id(), body.reason(), null, created, request);
        return response(created, request);
    }

    @PutMapping("/users/{userId}/membership")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'user:configure')")
    public ApiResponse<PlatformDirectoryService.UserView> updateMembership(
            @PathVariable String userId,
            @Valid @RequestBody MembershipUpdateRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = principal(authentication);
        var updated = service.updateMembership(principal.organizationId(), userId, body.toCommand());
        audit(authentication, "MEMBERSHIP_UPDATED", "USER", userId, body.reason(), null, updated, request);
        return response(updated, request);
    }

    @PostMapping("/users/{userId}/disable")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'user:configure')")
    public ApiResponse<Void> disableUser(
            @PathVariable String userId,
            @Valid @RequestBody ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = principal(authentication);
        service.disableUser(principal.organizationId(), userId);
        audit(authentication, "USER_DISABLED", "USER", userId, body.reason(), null, null, request);
        return response(null, request);
    }

    @PostMapping("/users/{userId}/reset-password")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'user:configure')")
    public ApiResponse<Void> resetPassword(
            @PathVariable String userId,
            @Valid @RequestBody PasswordResetRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = principal(authentication);
        service.resetPassword(principal.organizationId(), userId, body.temporaryPassword());
        audit(authentication, "PASSWORD_RESET", "USER", userId, body.reason(), null, null, request);
        return response(null, request);
    }

    private void audit(
            Authentication authentication,
            String action,
            String resourceType,
            String resourceId,
            String reason,
            Object before,
            Object after,
            HttpServletRequest request
    ) {
        service.auditChange(principal(authentication), action, resourceType, resourceId,
                RequestIdFilter.get(request), reason, before, after);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record OrganizationRequest(
            String parentId,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(min = 3, max = 3) String baseCurrencyCode,
            @NotBlank @Size(max = 64) String timezone,
            @Min(1) @Max(12) int fiscalYearStartMonth,
            @Min(0) @Max(6) int amountScale,
            @Min(0) @Max(8) int priceScale,
            @Min(0) @Max(8) int quantityScale,
            @NotBlank String taxRoundingMode,
            @Min(15) @Max(525600) int reservationTtlMinutes,
            @Min(0) @Max(3650) int expiryWarningDays,
            @NotBlank String defaultApprovalPolicy,
            @NotBlank @Size(max = 500) String reason
    ) {
        PlatformDirectoryService.CreateOrganization toCommand() {
            return new PlatformDirectoryService.CreateOrganization(
                    parentId, name, baseCurrencyCode, timezone, fiscalYearStartMonth, amountScale,
                    priceScale, quantityScale, taxRoundingMode, reservationTtlMinutes,
                    expiryWarningDays, defaultApprovalPolicy
            );
        }
    }

    public record DepartmentRequest(
            String parentId,
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            String managerMembershipId,
            @NotBlank @Size(max = 500) String reason
    ) {
        PlatformDirectoryService.CreateDepartment toCommand() {
            return new PlatformDirectoryService.CreateDepartment(parentId, code, name, managerMembershipId);
        }
    }

    public record RoleCreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            @NotNull DataScope dataScope,
            @NotNull List<String> permissions,
            @NotBlank @Size(max = 500) String reason
    ) {
        PlatformDirectoryService.CreateRole toCommand() {
            return new PlatformDirectoryService.CreateRole(code, name, description, dataScope, permissions);
        }
    }

    public record RoleUpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            @NotNull DataScope dataScope,
            boolean active,
            @NotNull List<String> permissions,
            @NotBlank @Size(max = 500) String reason
    ) {
        PlatformDirectoryService.UpdateRole toCommand() {
            return new PlatformDirectoryService.UpdateRole(name, description, dataScope, active, permissions);
        }
    }

    public record UserCreateRequest(
            @NotBlank @Size(max = 190) String username,
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Size(min = 12, max = 128) String temporaryPassword,
            String departmentId,
            @NotEmpty List<String> roleIds,
            @NotBlank @Size(max = 500) String reason
    ) {
        PlatformDirectoryService.CreateUser toCommand() {
            return new PlatformDirectoryService.CreateUser(
                    username, displayName, temporaryPassword, departmentId, roleIds
            );
        }
    }

    public record MembershipUpdateRequest(
            String departmentId,
            boolean active,
            @NotEmpty List<String> roleIds,
            @NotBlank @Size(max = 500) String reason
    ) {
        PlatformDirectoryService.UpdateMembership toCommand() {
            return new PlatformDirectoryService.UpdateMembership(departmentId, active, roleIds);
        }
    }

    public record PasswordResetRequest(
            @NotBlank @Size(min = 12, max = 128) String temporaryPassword,
            @NotBlank @Size(max = 500) String reason
    ) {}
}
