package com.flowora.erp.identity;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/session/active")
public class SessionGovernanceController {
    private final SessionGovernanceService service;
    private final FloworaAuthorization authorization;
    private final SecurityAuditService auditService;

    public SessionGovernanceController(
            SessionGovernanceService service,
            FloworaAuthorization authorization,
            SecurityAuditService auditService
    ) {
        this.service = service;
        this.authorization = authorization;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<SessionGovernanceService.SessionView>> sessions(
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = authorization.principal(authentication);
        return ApiResponse.of(service.sessions(principal.username(), request.getSession().getId()), RequestIdFilter.get(request));
    }

    @PostMapping("/{sessionId}/revoke")
    public ApiResponse<Map<String, Boolean>> revoke(
            @PathVariable String sessionId,
            @Valid @RequestBody RevokeRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FloworaPrincipal principal = authorization.principal(authentication);
        service.revoke(principal.username(), sessionId);
        auditService.record(principal.userId(), principal.organizationId(), "SESSION_REVOKED", "SUCCESS", request,
                "{\"reason\":\"" + body.reason().replace("\"", "") + "\"}");
        return ApiResponse.of(Map.of("revoked", true), RequestIdFilter.get(request));
    }

    public record RevokeRequest(@NotBlank String reason) {}
}
