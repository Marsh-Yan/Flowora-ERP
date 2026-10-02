package com.flowora.erp.workflow;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.workflow.v2.WorkflowResourceAccessPolicy;
import org.springframework.beans.factory.ObjectProvider;
import com.flowora.erp.workflow.WorkflowDtos.ActionRequest;
import com.flowora.erp.workflow.WorkflowDtos.ActivityResponse;
import com.flowora.erp.workflow.WorkflowDtos.CommentRequest;
import com.flowora.erp.workflow.WorkflowDtos.CommentResponse;
import com.flowora.erp.workflow.WorkflowDtos.NotificationResponse;
import com.flowora.erp.workflow.WorkflowDtos.TaskRequest;
import com.flowora.erp.workflow.WorkflowDtos.TaskResponse;
import com.flowora.erp.workflow.WorkflowDtos.UnreadCount;
import com.flowora.erp.workflow.WorkflowDtos.WorkflowActionResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/workflow", "/api/v2/compat/workflow"})
public class WorkflowController {
    private final WorkflowService service;
    private final FloworaAuthorization authorization;
    private final WorkflowResourceAccessPolicy resourceAccess;

    public WorkflowController(WorkflowService service, FloworaAuthorization authorization,
                              ObjectProvider<WorkflowResourceAccessPolicy> resourceAccess) {
        this.service = service;
        this.authorization = authorization;
        this.resourceAccess = resourceAccess.getIfAvailable();
    }

    @GetMapping("/tasks")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:view')")
    public ApiResponse<PageResponse<TaskResponse>> inbox(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.inbox(principal(authentication), pageable), request);
    }

    @PostMapping("/tasks")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:submit')")
    public ApiResponse<TaskResponse> createTask(
            @Valid @RequestBody TaskRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        requireResource(authentication, body.resourceType().name(), body.resourceId(), "read");
        return response(service.createTask(principal(authentication), body, RequestIdFilter.get(request)), request);
    }

    @PostMapping("/tasks/{id}/actions")
    public ApiResponse<WorkflowActionResult> act(
            @PathVariable String id,
            @Valid @RequestBody ActionRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.act(principal(authentication), id, body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/notifications")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:view')")
    public ApiResponse<PageResponse<NotificationResponse>> notifications(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.notifications(principal(authentication), pageable), request);
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:view')")
    public ApiResponse<UnreadCount> unreadCount(Authentication authentication, HttpServletRequest request) {
        return response(service.unreadCount(principal(authentication)), request);
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:view')")
    public ApiResponse<NotificationResponse> markNotificationRead(
            @PathVariable String id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.markNotificationRead(principal(authentication), id), request);
    }

    @GetMapping("/resources/{resourceType}/{resourceId}/comments")
    public ApiResponse<PageResponse<CommentResponse>> comments(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        requireResource(authentication, resourceType, resourceId, "read");
        return response(service.comments(principal(authentication), resourceType, resourceId, pageable), request);
    }

    @PostMapping("/resources/{resourceType}/{resourceId}/comments")
    public ApiResponse<CommentResponse> addComment(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @Valid @RequestBody CommentRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        requireResource(authentication, resourceType, resourceId, "comment");
        return response(service.addComment(principal(authentication), resourceType, resourceId, body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/resources/{resourceType}/{resourceId}/activities")
    public ApiResponse<PageResponse<ActivityResponse>> activities(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        requireResource(authentication, resourceType, resourceId, "read");
        return response(service.activities(principal(authentication), resourceType, resourceId, pageable), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }

    private void requireResource(Authentication authentication, String resourceType, String resourceId, String capability) {
        if (resourceAccess != null) resourceAccess.require(principal(authentication), resourceType, resourceId, capability);
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
