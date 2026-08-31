package com.flowora.erp.analytics;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.identity.FloworaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import static com.flowora.erp.analytics.AnalyticsDtos.*;

@RestController
@RequestMapping("/api/v2/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    private final FloworaAuthorization authorization;

    public AnalyticsController(AnalyticsService service, FloworaAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/workspace")
    public ApiResponse<WorkspaceSnapshot> workspace(Authentication authentication, HttpServletRequest request) {
        return response(service.workspace(principal(authentication)), request);
    }

    @GetMapping("/snapshot")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:view')")
    public ApiResponse<AnalyticsSnapshot> analytics(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                                     Authentication authentication, HttpServletRequest request) {
        return response(service.analytics(principal(authentication), from, to), request);
    }

    @GetMapping("/organizations")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:cross-org')")
    public ApiResponse<List<OrganizationSummary>> organizations(
            @RequestParam(required = false) List<String> organizationId,
            @RequestParam(defaultValue = "USD") String reportCurrency,
            Authentication authentication, HttpServletRequest request) {
        return response(service.crossOrganization(principal(authentication), organizationId, reportCurrency), request);
    }

    @PostMapping("/views")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:view')")
    public ApiResponse<SavedView> saveView(@Valid @RequestBody SavedViewCreate body,
                                           Authentication authentication, HttpServletRequest request) {
        return response(service.saveView(principal(authentication), body), request);
    }

    @GetMapping("/views")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:view')")
    public ApiResponse<List<SavedView>> views(@RequestParam String resourceType,
                                              Authentication authentication, HttpServletRequest request) {
        return response(service.views(principal(authentication), resourceType), request);
    }

    @DeleteMapping("/views/{id}")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:view')")
    public ApiResponse<Void> deleteView(@PathVariable String id, Authentication authentication,
                                        HttpServletRequest request) {
        service.deleteView(principal(authentication), id);
        return response(null, request);
    }

    private FloworaPrincipal principal(Authentication authentication) { return authorization.principal(authentication); }
    private <T> ApiResponse<T> response(T data, HttpServletRequest request) { return ApiResponse.of(data, RequestIdFilter.get(request)); }
}
