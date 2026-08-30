package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.ActionRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.DecisionView;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.InstanceView;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.StartRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.TaskView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/workflows")
@Profile("local")
public class WorkflowEngineController {
    private final WorkflowEngineService service;

    public WorkflowEngineController(WorkflowEngineService service) {
        this.service = service;
    }

    @PostMapping("/instances")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:submit')")
    public ApiResponse<InstanceView> start(
            @Valid @RequestBody StartRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.start(principal(authentication), body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/instances/{instanceId}")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:view')")
    public ApiResponse<InstanceView> instance(
            @PathVariable String instanceId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.instance(principal(authentication).organizationId(), instanceId), request);
    }

    @GetMapping("/instances/{instanceId}/decisions")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:view')")
    public ApiResponse<List<DecisionView>> decisions(
            @PathVariable String instanceId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.decisions(principal(authentication).organizationId(), instanceId), request);
    }

    @PostMapping("/instances/{instanceId}/actions")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:submit') or " +
            "@floworaAuthorization.has(authentication, 'workflow:approve') or " +
            "@floworaAuthorization.has(authentication, 'workflow:admin')")
    public ApiResponse<InstanceView> actOnInstance(
            @PathVariable String instanceId,
            @Valid @RequestBody ActionRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.actOnInstance(
                principal(authentication), instanceId, body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/tasks")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:view')")
    public ApiResponse<List<TaskView>> inbox(
            @RequestParam(defaultValue = "MINE") String view,
            @RequestParam(defaultValue = "false") boolean overdue,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.inbox(principal(authentication), view, overdue), request);
    }

    @PostMapping("/tasks/{taskId}/actions")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:approve')")
    public ApiResponse<TaskView> actOnTask(
            @PathVariable String taskId,
            @RequestHeader("If-Match") long version,
            @Valid @RequestBody ActionRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.actOnTask(
                principal(authentication), taskId, version, body, RequestIdFilter.get(request)), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return (FloworaPrincipal) authentication.getPrincipal();
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
