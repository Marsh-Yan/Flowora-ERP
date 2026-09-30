package com.flowora.erp.identity;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/session")
public class SessionV2Controller {
    private final IdentityAuthenticator authenticator;
    private final DatabaseAccountService accountService;
    private final SecurityAuditService auditService;
    private final DatabaseMfaService mfaService;
    private final SessionGovernanceService sessionGovernance;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public SessionV2Controller(
            IdentityAuthenticator authenticator,
            ObjectProvider<DatabaseAccountService> accountService,
            ObjectProvider<DatabaseMfaService> mfaService,
            SecurityAuditService auditService,
            SessionGovernanceService sessionGovernance
    ) {
        this.authenticator = authenticator;
        this.accountService = accountService.getIfAvailable();
        this.mfaService = mfaService.getIfAvailable();
        this.auditService = auditService;
        this.sessionGovernance = sessionGovernance;
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token, HttpServletRequest request) {
        return ApiResponse.of(Map.of("token", token.getToken()), RequestIdFilter.get(request));
    }

    @PostMapping("/login")
    public ApiResponse<SessionUser> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            FloworaPrincipal principal = authenticator.authenticate(body.username(), body.password());
            if (mfaService != null && mfaService.required(principal.userId())) {
                if (body.mfaCode() == null || body.mfaCode().isBlank()) {
                    throw new PlatformApiException(HttpStatus.UNAUTHORIZED, "MFA_REQUIRED", "errors.mfaRequired");
                }
                mfaService.verifyLogin(principal.userId(), body.mfaCode());
            }
            if (request.getSession(false) != null) request.changeSessionId();
            sessionGovernance.reserveLogin(principal.username(), request.getSession(true).getId());
            save(principal, request, response);
            auditService.record(principal.userId(), principal.organizationId(), "LOGIN", "SUCCESS", request, null);
            return ApiResponse.of(SessionUser.from(principal), RequestIdFilter.get(request));
        } catch (RuntimeException exception) {
            auditService.record(null, null, "LOGIN", "FAILURE", request, null);
            throw exception;
        }
    }

    @GetMapping("/me")
    public ApiResponse<SessionUser> me(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(SessionUser.from(principal(authentication)), RequestIdFilter.get(request));
    }

    @GetMapping("/organizations")
    public ApiResponse<List<OrganizationOption>> organizations(Authentication authentication, HttpServletRequest request) {
        FloworaPrincipal principal = principal(authentication);
        List<OrganizationOption> options = accountService == null
                ? List.of(new OrganizationOption(principal.organizationId(), principal.organizationName(), true, principal.departmentId()))
                : accountService.organizations(principal.userId()).stream().map(OrganizationOption::from).toList();
        return ApiResponse.of(options, RequestIdFilter.get(request));
    }

    @PostMapping("/switch-organization")
    public ApiResponse<SessionUser> switchOrganization(
            @Valid @RequestBody SwitchOrganizationRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        FloworaPrincipal current = principal(authentication);
        FloworaPrincipal switched;
        if (accountService == null) {
            if (!current.organizationId().equals(body.organizationId())) {
                throw new PlatformApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "errors.organizationAccessDenied");
            }
            switched = current;
        } else {
            switched = accountService.switchOrganization(current.userId(), body.organizationId());
        }
        request.changeSessionId();
        save(switched, request, response);
        auditService.record(switched.userId(), switched.organizationId(), "ORGANIZATION_SWITCH", "SUCCESS", request, null);
        return ApiResponse.of(SessionUser.from(switched), RequestIdFilter.get(request));
    }

    @PostMapping("/change-password")
    public ApiResponse<Map<String, Boolean>> changePassword(
            @Valid @RequestBody ChangePasswordRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        FloworaPrincipal principal = principal(authentication);
        if (accountService == null) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "PERSISTENT_IDENTITY_REQUIRED", "errors.persistentIdentityRequired");
        }
        accountService.changePassword(principal, body.currentPassword(), body.newPassword());
        auditService.record(principal.userId(), principal.organizationId(), "PASSWORD_CHANGED", "SUCCESS", request, null);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ApiResponse.of(Map.of("reauthenticationRequired", true), RequestIdFilter.get(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Boolean>> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        FloworaPrincipal principal = principal(authentication);
        auditService.record(principal.userId(), principal.organizationId(), "LOGOUT", "SUCCESS", request, null);
        if (request.getSession(false) != null) sessionGovernance.clearReservation(principal.username(), request.getSession(false).getId());
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ApiResponse.of(Map.of("authenticated", false), RequestIdFilter.get(request));
    }

    private void save(FloworaPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof FloworaPrincipal principal)) {
            throw new PlatformApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "errors.authenticationRequired");
        }
        return principal;
    }

    public record LoginRequest(
            @NotBlank String username, @NotBlank String password, String mfaCode
    ) {}
    public record SwitchOrganizationRequest(@NotBlank String organizationId) {}
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {}

    public record OrganizationOption(
            String id, String name, boolean defaultOrganization, String departmentId
    ) {
        static OrganizationOption from(DatabaseAccountService.OrganizationOption option) {
            return new OrganizationOption(option.id(), option.name(), option.defaultOrganization(), option.departmentId());
        }
    }

    public record SessionUser(
            String id,
            String username,
            String displayName,
            String organizationId,
            String organizationName,
            String departmentId,
            DataScope dataScope,
            List<String> roles,
            List<String> permissions,
            boolean mustChangePassword
    ) {
        static SessionUser from(FloworaPrincipal principal) {
            return new SessionUser(
                    principal.userId(), principal.username(), principal.displayName(),
                    principal.organizationId(), principal.organizationName(), principal.departmentId(),
                    principal.dataScope(), principal.roles(), principal.permissions(), principal.mustChangePassword()
            );
        }
    }
}
