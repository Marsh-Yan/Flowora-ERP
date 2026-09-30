package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.DelegationRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/workflows")
@Profile("local | production")
public class WorkflowOperationsController {
    private final WorkflowDelegationService delegationService;
    private final WorkflowOutboxService outboxService;

    public WorkflowOperationsController(
            WorkflowDelegationService delegationService,
            WorkflowOutboxService outboxService
    ) {
        this.delegationService = delegationService;
        this.outboxService = outboxService;
    }

    @GetMapping("/delegations")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:delegate')")
    public ApiResponse<List<Map<String, Object>>> delegations(
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(delegationService.delegations(principal(authentication)), request);
    }

    @PostMapping("/delegations")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:delegate')")
    public ApiResponse<Map<String, Object>> createDelegation(
            @Valid @RequestBody DelegationRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(delegationService.create(principal(authentication), body), request);
    }

    @PostMapping("/delegations/{delegationId}/cancel")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:delegate')")
    public ApiResponse<Void> cancelDelegation(
            @PathVariable String delegationId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        delegationService.cancel(principal(authentication), delegationId);
        return response(null, request);
    }

    @GetMapping("/outbox")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:admin')")
    public ApiResponse<List<Map<String, Object>>> outbox(
            @RequestParam(defaultValue = "DEAD") String status,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(outboxService.events(principal(authentication).organizationId(), status), request);
    }

    @PostMapping("/outbox/process")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:admin')")
    public ApiResponse<Map<String, Integer>> process(HttpServletRequest request) {
        return response(Map.of("processed", outboxService.processBatch(100)), request);
    }

    @PostMapping("/outbox/{eventId}/replay")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:admin')")
    public ApiResponse<Void> replay(
            @PathVariable String eventId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        outboxService.replay(principal(authentication).organizationId(), eventId);
        return response(null, request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return (FloworaPrincipal) authentication.getPrincipal();
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
