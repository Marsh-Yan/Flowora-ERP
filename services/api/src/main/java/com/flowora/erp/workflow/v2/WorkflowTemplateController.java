package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.TemplateRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.TemplateView;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.VersionRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.VersionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/workflows/templates")
@Profile("local | production")
public class WorkflowTemplateController {
    private final WorkflowTemplateService service;

    public WorkflowTemplateController(WorkflowTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:view')")
    public ApiResponse<List<TemplateView>> templates(Authentication authentication, HttpServletRequest request) {
        return response(service.templates(principal(authentication).organizationId()), request);
    }

    @PostMapping
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:configure')")
    public ApiResponse<TemplateView> create(
            @Valid @RequestBody TemplateRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.create(principal(authentication), body, RequestIdFilter.get(request)), request);
    }

    @PostMapping("/{templateId}/versions")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:configure')")
    public ApiResponse<VersionView> createVersion(
            @PathVariable String templateId,
            @Valid @RequestBody VersionRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.createVersion(
                principal(authentication), templateId, body, RequestIdFilter.get(request)), request);
    }

    @PostMapping("/{templateId}/versions/{versionId}/publish")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:configure')")
    public ApiResponse<VersionView> publish(
            @PathVariable String templateId,
            @PathVariable String versionId,
            @Valid @RequestBody ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.publish(principal(authentication), templateId, versionId,
                body.reason(), RequestIdFilter.get(request)), request);
    }

    @PostMapping("/{templateId}/retire")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:configure')")
    public ApiResponse<Void> retire(
            @PathVariable String templateId,
            @Valid @RequestBody ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        service.retire(principal(authentication), templateId, body.reason(), RequestIdFilter.get(request));
        return response(null, request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return (FloworaPrincipal) authentication.getPrincipal();
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
