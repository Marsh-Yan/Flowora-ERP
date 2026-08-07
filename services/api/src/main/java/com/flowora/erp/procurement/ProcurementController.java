package com.flowora.erp.procurement;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.procurement.ProcurementDtos.PurchaseOrderCreate;
import com.flowora.erp.procurement.ProcurementDtos.PurchaseOrderResponse;
import com.flowora.erp.procurement.ProcurementDtos.PurchaseRequestCreate;
import com.flowora.erp.procurement.ProcurementDtos.PurchaseRequestResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procurement")
public class ProcurementController {
    private final ProcurementService service;

    public ProcurementController(ProcurementService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    public ApiResponse<PageResponse<PurchaseRequestResponse>> requests(
            @RequestParam(defaultValue = "") String query,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.requests(principal(authentication).organizationId(), query, pageable), request);
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<PurchaseRequestResponse> createRequest(
            @Valid @RequestBody PurchaseRequestCreate body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.createRequest(principal(authentication), body), request);
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<PurchaseOrderResponse>> orders(
            @RequestParam(defaultValue = "") String query,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.orders(principal(authentication).organizationId(), query, pageable), request);
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<PurchaseOrderResponse> createOrder(
            @Valid @RequestBody PurchaseOrderCreate body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.createOrder(principal(authentication), body), request);
    }

    @DeleteMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<Void> cancelOrder(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.cancelOrder(principal(authentication), id);
        return response(null, request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal current) return current;
        throw new IllegalStateException("Authenticated FloworaPrincipal is required");
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
