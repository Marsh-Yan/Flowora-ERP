package com.flowora.erp.identity;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/session/mfa")
@Profile("local | production")
public class MfaController {
    private final DatabaseMfaService mfaService;
    private final FloworaAuthorization authorization;
    private final SecurityAuditService auditService;

    public MfaController(
            DatabaseMfaService mfaService,
            FloworaAuthorization authorization,
            SecurityAuditService auditService
    ) {
        this.mfaService = mfaService;
        this.authorization = authorization;
        this.auditService = auditService;
    }

    @PostMapping("/enroll")
    public ApiResponse<DatabaseMfaService.Enrollment> enroll(
            @RequestBody(required = false) EnrollmentRequest body,
            Authentication authentication, HttpServletRequest request) {
        FloworaPrincipal principal = authorization.principal(authentication);
        var enrollment = mfaService.startEnrollment(principal, body == null ? null : body.currentCode());
        auditService.record(principal.userId(), principal.organizationId(), "MFA_ENROLLMENT_STARTED", "SUCCESS", request, null);
        return ApiResponse.of(enrollment, RequestIdFilter.get(request));
    }

    @PostMapping("/cancel")
    public ApiResponse<Map<String, Boolean>> cancel(Authentication authentication, HttpServletRequest request) {
        FloworaPrincipal principal = authorization.principal(authentication);
        mfaService.cancelEnrollment(principal.userId());
        auditService.record(principal.userId(), principal.organizationId(), "MFA_ENROLLMENT_CANCELLED", "SUCCESS", request, null);
        return ApiResponse.of(Map.of("cancelled", true), RequestIdFilter.get(request));
    }

    @PostMapping("/confirm")
    public ApiResponse<RecoveryCodes> confirm(
            @Valid @RequestBody CodeRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = authorization.principal(authentication);
        List<String> codes = mfaService.confirmEnrollment(principal.userId(), body.code());
        auditService.record(principal.userId(), principal.organizationId(), "MFA_ENABLED", "SUCCESS", request, null);
        return ApiResponse.of(new RecoveryCodes(codes), RequestIdFilter.get(request));
    }

    @PostMapping("/disable")
    public ApiResponse<Map<String, Boolean>> disable(
            @Valid @RequestBody CodeRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = authorization.principal(authentication);
        mfaService.disable(principal.userId(), body.code());
        auditService.record(principal.userId(), principal.organizationId(), "MFA_DISABLED", "SUCCESS", request, null);
        return ApiResponse.of(Map.of("enabled", false), RequestIdFilter.get(request));
    }

    public record CodeRequest(@NotBlank String code) {}
    public record EnrollmentRequest(String currentCode) {}
    public record RecoveryCodes(List<String> codes) {}
}
