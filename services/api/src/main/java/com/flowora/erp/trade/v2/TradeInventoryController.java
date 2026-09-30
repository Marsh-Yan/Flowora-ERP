package com.flowora.erp.trade.v2;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.trade.v2.TradeInventoryDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
@Profile("local | production")
public class TradeInventoryController {
    private final TradeInventoryService service;
    private final FloworaAuthorization authorization;

    public TradeInventoryController(TradeInventoryService service, FloworaAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/inventory/availability")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:view')")
    public ApiResponse<List<AvailabilityView>> availability(
            @RequestParam(defaultValue = "") String warehouseId,
            @RequestParam(defaultValue = "") String itemId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.availability(principal(authentication).organizationId(), warehouseId, itemId), request);
    }

    @PostMapping("/inventory/receipts")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:post')")
    public ApiResponse<MovementView> receive(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReceiptRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.receive(principal(authentication), body, idempotencyKey), request);
    }

    @PostMapping("/inventory/reservations")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:reserve')")
    public ApiResponse<List<ReservationView>> reserve(
            @Valid @RequestBody ReservationRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.reserve(principal(authentication), body), request);
    }

    @PostMapping("/inventory/shipments")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:post')")
    public ApiResponse<MovementView> ship(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ShipmentRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.ship(principal(authentication), body, idempotencyKey), request);
    }

    @PostMapping("/inventory/transfers")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:post')")
    public ApiResponse<MovementView> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.transfer(principal(authentication), body, idempotencyKey), request);
    }

    @PostMapping("/inventory/reservations/{reservationId}/release")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:reserve')")
    public ApiResponse<ReservationView> releaseReservation(
            @PathVariable String reservationId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.releaseReservation(principal(authentication), reservationId), request);
    }

    @PostMapping("/inventory/reservations/expire")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:reserve')")
    public ApiResponse<Map<String, Integer>> expireReservations(
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.expireReservations(principal(authentication)), request);
    }

    @PostMapping("/inventory/counts")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:post')")
    public ApiResponse<MovementView> count(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CountRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.count(principal(authentication), body, idempotencyKey), request);
    }

    @PostMapping("/inventory/freezes")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:freeze')")
    public ApiResponse<Map<String, String>> freeze(
            @Valid @RequestBody FreezeRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.freeze(principal(authentication), body), request);
    }

    @PostMapping("/inventory/freezes/{freezeId}/release")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:freeze')")
    public ApiResponse<Map<String, String>> releaseFreeze(
            @PathVariable String freezeId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.releaseFreeze(principal(authentication), freezeId), request);
    }

    @GetMapping("/inventory/trace")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:trace')")
    public ApiResponse<TraceView> trace(
            @RequestParam String itemId,
            @RequestParam(defaultValue = "") String lotId,
            @RequestParam(defaultValue = "") String serialId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.trace(principal(authentication).organizationId(), itemId, lotId, serialId), request);
    }

    @PostMapping("/sales/returns")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:return')")
    public ApiResponse<MovementView> salesReturn(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReturnRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.salesReturn(principal(authentication), body, idempotencyKey), request);
    }

    @PostMapping("/procurement/returns")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'inventory:return')")
    public ApiResponse<MovementView> purchaseReturn(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReturnRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.purchaseReturn(principal(authentication), body, idempotencyKey), request);
    }

    @GetMapping("/trade/financial-source-events")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'trade:financial-source')")
    public ApiResponse<List<FinancialSourceEventView>> financialEvents(
            @RequestParam(defaultValue = "PENDING") String status,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.financialEvents(principal(authentication).organizationId(), status), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
