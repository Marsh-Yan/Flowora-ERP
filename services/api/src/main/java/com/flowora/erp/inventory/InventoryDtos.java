package com.flowora.erp.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record PurchaseReceiptRequest(
            @NotBlank String purchaseOrderId,
            @NotBlank String purchaseOrderLineId,
            @NotBlank String warehouseId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitCost
    ) {
    }

    public record PurchaseReceiptResponse(
            String id,
            String number,
            String purchaseOrderId,
            String warehouseId,
            String itemId,
            BigDecimal quantity,
            BigDecimal unitCost,
            Instant receivedAt
    ) {
    }

    public record StockBalanceResponse(
            String id,
            String warehouseId,
            String itemId,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal inventoryValue
    ) {
    }

    public record StockLedgerResponse(
            String id,
            String warehouseId,
            String itemId,
            InventoryMovementType movementType,
            String documentType,
            String documentId,
            BigDecimal quantityDelta,
            BigDecimal unitCost,
            BigDecimal valueDelta,
            BigDecimal balanceQuantity,
            BigDecimal balanceValue,
            String actorUserId,
            Instant createdAt
    ) {
    }

    public record StockTransferRequest(
            @NotBlank String sourceWarehouseId,
            @NotBlank String targetWarehouseId,
            @NotBlank String itemId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitCost
    ) {
    }

    public record StockTransferResponse(
            String id,
            String number,
            String sourceWarehouseId,
            String targetWarehouseId,
            String itemId,
            BigDecimal quantity,
            StockTransferStatus status
    ) {
    }

    public record StockCountRequest(
            @NotBlank String warehouseId,
            @NotBlank String itemId,
            @NotNull @DecimalMin("0.0") BigDecimal countedQuantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitCost
    ) {
    }

    public record StockCountResponse(
            String id,
            String number,
            String warehouseId,
            String itemId,
            BigDecimal expectedQuantity,
            BigDecimal countedQuantity,
            BigDecimal variance,
            StockCountStatus status
    ) {
    }

    public record StockAdjustmentRequest(
            @NotBlank String warehouseId,
            @NotBlank String itemId,
            @NotNull BigDecimal quantityDelta,
            @NotNull @DecimalMin("0.0") BigDecimal unitCost,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record StockAdjustmentResponse(
            String id,
            String number,
            String warehouseId,
            String itemId,
            BigDecimal quantityDelta,
            BigDecimal unitCost,
            String reason,
            StockAdjustmentStatus status,
            String workflowTaskId,
            Instant postedAt
    ) {
    }
}
