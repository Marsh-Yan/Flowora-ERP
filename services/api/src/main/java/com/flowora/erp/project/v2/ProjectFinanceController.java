package com.flowora.erp.project.v2;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceView;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.project.v2.ProjectFinanceDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/projects")
public class ProjectFinanceController {
    private final ProjectFinanceService service;
    private final FloworaAuthorization authorization;

    public ProjectFinanceController(ProjectFinanceService service, FloworaAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping("/{id}/billing-configuration")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'project:billing')")
    public ApiResponse<BillingConfiguration> configure(@PathVariable String id,
                                                        @Valid @RequestBody BillingConfiguration body,
                                                        Authentication authentication, HttpServletRequest request) {
        return response(service.configure(principal(authentication), id, body), request);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'project:create')")
    public ApiResponse<MemberView> addMember(@PathVariable String id, @Valid @RequestBody MemberCreate body,
                                              Authentication authentication, HttpServletRequest request) {
        return response(service.addMember(principal(authentication), id, body), request);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'project:view')")
    public ApiResponse<List<MemberView>> members(@PathVariable String id, Authentication authentication,
                                                  HttpServletRequest request) {
        return response(service.members(principal(authentication).organizationId(), id), request);
    }

    @PostMapping("/timesheets/{id}/approval")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:approve')")
    public ApiResponse<BillingBasisView> approveTimesheet(@PathVariable String id,
                                                           @Valid @RequestBody ApprovalAction body,
                                                           Authentication authentication, HttpServletRequest request) {
        return response(service.approveTimesheet(principal(authentication), id, body), request);
    }

    @PostMapping("/expenses/{id}/approval")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:approve')")
    public ApiResponse<BillingBasisView> approveExpense(@PathVariable String id,
                                                         @Valid @RequestBody ApprovalAction body,
                                                         Authentication authentication, HttpServletRequest request) {
        return response(service.approveExpense(principal(authentication), id, body), request);
    }

    @PostMapping("/milestones/{id}/approval")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'workflow:approve')")
    public ApiResponse<BillingBasisView> approveMilestone(@PathVariable String id,
                                                           @Valid @RequestBody ApprovalAction body,
                                                           Authentication authentication, HttpServletRequest request) {
        return response(service.approveMilestone(principal(authentication), id, body), request);
    }

    @GetMapping("/{id}/billing-basis")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'project:view')")
    public ApiResponse<List<BillingBasisView>> billingBasis(@PathVariable String id,
                                                             Authentication authentication, HttpServletRequest request) {
        return response(service.billingBasis(principal(authentication).organizationId(), id), request);
    }

    @PostMapping("/{id}/invoices")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'project:billing')")
    public ApiResponse<InvoiceView> generateInvoice(@PathVariable String id,
                                                     @RequestHeader("Idempotency-Key") String key,
                                                     @Valid @RequestBody ProjectInvoiceCreate body,
                                                     Authentication authentication, HttpServletRequest request) {
        return response(service.generateInvoice(principal(authentication), id, key, body), request);
    }

    @GetMapping("/{id}/profitability")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'project:view')")
    public ApiResponse<ProjectProfitView> profitability(@PathVariable String id,
                                                         Authentication authentication, HttpServletRequest request) {
        return response(service.profit(principal(authentication).organizationId(), id), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
