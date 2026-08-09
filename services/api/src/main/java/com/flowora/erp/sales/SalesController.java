package com.flowora.erp.sales;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.sales.SalesDtos.DeliveryCreate;
import com.flowora.erp.sales.SalesDtos.DeliveryResponse;
import com.flowora.erp.sales.SalesDtos.PaymentCreate;
import com.flowora.erp.sales.SalesDtos.PaymentResponse;
import com.flowora.erp.sales.SalesDtos.ReceivableResponse;
import com.flowora.erp.sales.SalesDtos.SalesOrderCreate;
import com.flowora.erp.sales.SalesDtos.SalesOrderResponse;
import com.flowora.erp.sales.SalesDtos.SalesQuoteCreate;
import com.flowora.erp.sales.SalesDtos.SalesQuoteResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {
    private final SalesService service;

    public SalesController(SalesService service) {
        this.service = service;
    }

    @GetMapping("/quotes")
    public ApiResponse<PageResponse<SalesQuoteResponse>> quotes(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.quotes(principal(authentication).organizationId(), query, pageable), request);
    }

    @PostMapping("/quotes")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<SalesQuoteResponse> createQuote(@Valid @RequestBody SalesQuoteCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.createQuote(principal(authentication), body, RequestIdFilter.get(request)), request);
    }

    @PostMapping("/quotes/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGEMENT')")
    public ApiResponse<SalesQuoteResponse> approveQuote(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.approveQuote(principal(authentication), id, RequestIdFilter.get(request)), request);
    }

    @PostMapping("/quotes/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGEMENT')")
    public ApiResponse<SalesQuoteResponse> rejectQuote(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.rejectQuote(principal(authentication), id, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<SalesOrderResponse>> orders(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.orders(principal(authentication).organizationId(), query, pageable), request);
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<SalesOrderResponse> createOrder(@Valid @RequestBody SalesOrderCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.createOrder(principal(authentication), body), request);
    }

    @GetMapping("/deliveries")
    public ApiResponse<PageResponse<DeliveryResponse>> deliveries(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.deliveries(principal(authentication).organizationId(), query, pageable), request);
    }

    @PostMapping("/deliveries")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ApiResponse<DeliveryResponse> deliver(@Valid @RequestBody DeliveryCreate body, Authentication authentication, HttpServletRequest request,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return response(service.deliver(principal(authentication), body, idempotencyKey), request);
    }

    @GetMapping("/receivables")
    public ApiResponse<PageResponse<ReceivableResponse>> receivables(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.receivables(principal(authentication).organizationId(), query, pageable), request);
    }

    @GetMapping("/receivables/{id}/payments")
    public ApiResponse<PageResponse<PaymentResponse>> payments(@PathVariable String id, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.payments(principal(authentication).organizationId(), id, pageable), request);
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<PaymentResponse> pay(@Valid @RequestBody PaymentCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.pay(principal(authentication), body), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal current) return current;
        throw new IllegalStateException("Authenticated FloworaPrincipal is required");
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
