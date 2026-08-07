package com.flowora.erp.inventory;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.inventory.InventoryDtos.PurchaseReceiptRequest;
import com.flowora.erp.inventory.InventoryDtos.PurchaseReceiptResponse;
import com.flowora.erp.inventory.InventoryDtos.StockAdjustmentRequest;
import com.flowora.erp.inventory.InventoryDtos.StockAdjustmentResponse;
import com.flowora.erp.inventory.InventoryDtos.StockBalanceResponse;
import com.flowora.erp.inventory.InventoryDtos.StockCountRequest;
import com.flowora.erp.inventory.InventoryDtos.StockCountResponse;
import com.flowora.erp.inventory.InventoryDtos.StockLedgerResponse;
import com.flowora.erp.inventory.InventoryDtos.StockTransferRequest;
import com.flowora.erp.inventory.InventoryDtos.StockTransferResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/balances")
    public ApiResponse<PageResponse<StockBalanceResponse>> balances(
            @RequestParam(defaultValue = "") String warehouseId,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.balances(principal(authentication).organizationId(), warehouseId, pageable), request);
    }

    @GetMapping("/ledger")
    public ApiResponse<PageResponse<StockLedgerResponse>> ledger(
            @RequestParam(defaultValue = "") String warehouseId,
            @RequestParam(defaultValue = "") String itemId,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.ledger(principal(authentication).organizationId(), warehouseId, itemId, pageable), request);
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ApiResponse<PurchaseReceiptResponse> receive(@Valid @RequestBody PurchaseReceiptRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.receive(principal(authentication), body), request);
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ApiResponse<StockTransferResponse> transfer(@Valid @RequestBody StockTransferRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.transfer(principal(authentication), body), request);
    }

    @PostMapping("/counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ApiResponse<StockCountResponse> count(@Valid @RequestBody StockCountRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.count(principal(authentication), body), request);
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ApiResponse<StockAdjustmentResponse> createAdjustment(@Valid @RequestBody StockAdjustmentRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createAdjustment(principal(authentication), body, RequestIdFilter.get(request)), request);
    }

    @PostMapping("/adjustments/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGEMENT')")
    public ApiResponse<StockAdjustmentResponse> approveAdjustment(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.approveAdjustment(principal(authentication), id, RequestIdFilter.get(request)), request);
    }

    @PostMapping("/adjustments/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGEMENT')")
    public ApiResponse<StockAdjustmentResponse> rejectAdjustment(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.rejectAdjustment(principal(authentication), id, RequestIdFilter.get(request)), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal current) return current;
        throw new IllegalStateException("Authenticated FloworaPrincipal is required");
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
