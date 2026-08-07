package com.flowora.erp.procurement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProcurementDtos {
    private ProcurementDtos() {
    }

    public record PurchaseRequestCreate(
            @NotBlank String supplierId,
            @NotBlank String warehouseId,
            @NotBlank String itemId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal estimatedUnitCost,
            @Size(max = 500) String note
    ) {
    }

    public record PurchaseRequestResponse(
            String id,
            String number,
            ProcurementDocumentStatus status,
            String supplierId,
            String warehouseId,
            String requesterUserId,
            String itemId,
            BigDecimal quantity,
            BigDecimal estimatedUnitCost,
            String note
    ) {
    }

    public record PurchaseOrderCreate(
            String purchaseRequestId,
            @NotBlank String supplierId,
            @NotBlank String warehouseId,
            @NotBlank String itemId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
            @NotNull @DecimalMin("0.0") BigDecimal taxRate,
            LocalDate expectedDate,
            @Size(max = 500) String note
    ) {
    }

    public record PurchaseOrderResponse(
            String id,
            String number,
            ProcurementDocumentStatus status,
            String purchaseRequestId,
            String supplierId,
            String warehouseId,
            String buyerUserId,
            String lineId,
            String itemId,
            BigDecimal orderedQuantity,
            BigDecimal receivedQuantity,
            BigDecimal remainingQuantity,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            LocalDate orderDate,
            LocalDate expectedDate,
            String note
    ) {
    }
}
