package com.flowora.erp.trade.v2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class TradeInventoryDtos {
    private TradeInventoryDtos() {
    }

    public record ReceiptRequest(
            @NotBlank String purchaseOrderId,
            @NotBlank String warehouseId,
            @NotEmpty @Size(max = 200) List<@Valid ReceiptLineRequest> lines
    ) {
    }

    public record ReceiptLineRequest(
            @NotBlank String purchaseOrderLineId,
            @NotBlank String itemId,
            String locationId,
            String lotCode,
            String supplierLotCode,
            LocalDate manufacturedOn,
            LocalDate expiresOn,
            List<@NotBlank String> serialCodes,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.0000") BigDecimal unitCost,
            @NotNull @DecimalMin(value = "0.0000") BigDecimal rejectedQuantity
    ) {
    }

    public record ReservationRequest(
            @NotBlank String salesOrderId,
            @Future Instant expiresAt,
            @NotEmpty @Size(max = 200) List<@Valid ReservationLineRequest> lines
    ) {
    }

    public record ReservationLineRequest(
            @NotBlank String salesOrderLineId,
            @NotBlank String warehouseId,
            String locationId,
            @NotBlank String itemId,
            String lotId,
            String serialId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity
    ) {
    }

    public record ShipmentRequest(
            @NotBlank String salesOrderId,
            @NotEmpty @Size(max = 200) List<@Valid ShipmentLineRequest> lines
    ) {
    }

    public record ShipmentLineRequest(
            @NotBlank String reservationId,
            @NotBlank String salesOrderLineId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity
    ) {
    }

    public record TransferRequest(
            @NotBlank String sourceWarehouseId,
            @NotBlank String targetWarehouseId,
            @NotEmpty @Size(max = 200) List<@Valid TransferLineRequest> lines
    ) {
    }

    public record TransferLineRequest(
            @NotBlank String itemId,
            String sourceLocationId,
            String targetLocationId,
            String lotId,
            String serialId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity
    ) {
    }

    public record ReturnRequest(
            @NotBlank String sourceDocumentId,
            @NotBlank String sourceMovementId,
            @NotBlank String disposition,
            @NotEmpty @Size(max = 200) List<@Valid ReturnLineRequest> lines
    ) {
    }

    public record ReturnLineRequest(
            @NotBlank String movementLineId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity
    ) {
    }

    public record FreezeRequest(
            @NotBlank String warehouseId,
            String locationId,
            String itemId,
            String lotId,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record CountRequest(
            @NotBlank String warehouseId,
            String locationId,
            @NotEmpty @Size(max = 500) List<@Valid CountLineRequest> lines
    ) {
    }

    public record CountLineRequest(
            @NotBlank String itemId,
            String lotId,
            String serialId,
            @NotNull @DecimalMin(value = "0.0000") BigDecimal countedQuantity
    ) {
    }

    public record AvailabilityView(
            String warehouseId,
            String locationId,
            String itemId,
            String lotId,
            String serialId,
            BigDecimal onHand,
            BigDecimal reserved,
            BigDecimal available,
            BigDecimal averageCost,
            boolean frozen
    ) {
    }

    public record ReservationView(
            String id,
            String salesOrderId,
            String salesOrderLineId,
            String warehouseId,
            String locationId,
            String itemId,
            String lotId,
            String serialId,
            BigDecimal reservedQuantity,
            BigDecimal consumedQuantity,
            String status,
            Instant expiresAt,
            long version
    ) {
    }

    public record MovementLineView(
            String id,
            int sequence,
            String itemId,
            String fromWarehouseId,
            String fromLocationId,
            String toWarehouseId,
            String toLocationId,
            String lotId,
            String serialId,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal valueAmount,
            String sourceLineType,
            String sourceLineId
    ) {
    }

    public record MovementView(
            String id,
            String number,
            String movementType,
            String sourceType,
            String sourceId,
            String reversalOfId,
            String requestId,
            Instant postedAt,
            List<MovementLineView> lines
    ) {
    }

    public record TraceView(
            String itemId,
            String lotId,
            String serialId,
            List<MovementView> movements
    ) {
    }

    public record FinancialSourceEventView(
            String id,
            String eventType,
            String sourceType,
            String sourceId,
            String currency,
            BigDecimal quantity,
            BigDecimal amount,
            String status,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
    }
}
