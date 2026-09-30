package com.flowora.erp.trade.v2;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.trade.v2.TradeDocumentDtos.DocumentView;
import com.flowora.erp.trade.v2.TradeDocumentDtos.PurchaseOrderRequest;
import com.flowora.erp.trade.v2.TradeDocumentDtos.SalesOrderRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
@Profile("local | production")
public class TradeDocumentController {
    private final TradeDocumentService service;
    private final FloworaAuthorization authorization;
    private final OrderReadScope readScope;

    public TradeDocumentController(TradeDocumentService service, FloworaAuthorization authorization, OrderReadScope readScope) {
        this.service = service;
        this.authorization = authorization;
        this.readScope = readScope;
    }

    @PostMapping("/sales/orders")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'sales:create')")
    public ApiResponse<DocumentView> createSalesOrder(@RequestHeader("Idempotency-Key") String key,
                                                       @Valid @RequestBody SalesOrderRequest body,
                                                       Authentication authentication, HttpServletRequest request) {
        return response(service.createSalesOrder(principal(authentication), body, key), request);
    }

    @GetMapping("/sales/orders/{id}")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'sales:view')")
    public ApiResponse<DocumentView> salesOrder(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        FloworaPrincipal actor = principal(authentication);
        readScope.requireSales(actor, id);
        return response(service.salesOrder(actor.organizationId(), id), request);
    }

    @PostMapping("/sales/orders/{id}/confirm")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'sales:submit')")
    public ApiResponse<DocumentView> confirmSalesOrder(@PathVariable String id, @RequestParam long version,
                                                        Authentication authentication, HttpServletRequest request) {
        return response(service.confirmSalesOrder(principal(authentication).organizationId(), id, version), request);
    }

    @PostMapping("/sales/orders/{id}/cancel")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'sales:submit')")
    public ApiResponse<DocumentView> cancelSalesOrder(@PathVariable String id, @RequestParam long version,
                                                       Authentication authentication, HttpServletRequest request) {
        return response(service.cancelSalesOrder(principal(authentication).organizationId(), id, version), request);
    }

    @PostMapping("/procurement/orders")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'procurement:create')")
    public ApiResponse<DocumentView> createPurchaseOrder(@RequestHeader("Idempotency-Key") String key,
                                                          @Valid @RequestBody PurchaseOrderRequest body,
                                                          Authentication authentication, HttpServletRequest request) {
        return response(service.createPurchaseOrder(principal(authentication), body, key), request);
    }

    @GetMapping("/procurement/orders/{id}")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'procurement:view')")
    public ApiResponse<DocumentView> purchaseOrder(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        FloworaPrincipal actor = principal(authentication);
        readScope.requirePurchase(actor, id);
        return response(service.purchaseOrder(actor.organizationId(), id), request);
    }

    @PostMapping("/procurement/orders/{id}/confirm")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'procurement:submit')")
    public ApiResponse<DocumentView> confirmPurchaseOrder(@PathVariable String id, @RequestParam long version,
                                                           Authentication authentication, HttpServletRequest request) {
        return response(service.confirmPurchaseOrder(principal(authentication).organizationId(), id, version), request);
    }

    @PostMapping("/procurement/orders/{id}/cancel")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'procurement:submit')")
    public ApiResponse<DocumentView> cancelPurchaseOrder(@PathVariable String id, @RequestParam long version,
                                                          Authentication authentication, HttpServletRequest request) {
        return response(service.cancelPurchaseOrder(principal(authentication).organizationId(), id, version), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
