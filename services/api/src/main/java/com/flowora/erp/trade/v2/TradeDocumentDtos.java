package com.flowora.erp.trade.v2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TradeDocumentDtos {
    private TradeDocumentDtos() {
    }

    public record LineRequest(
            @NotBlank String itemId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discountRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
            String sourceDocumentType,
            String sourceDocumentId,
            String sourceLineId
    ) {
    }

    public record SalesOrderRequest(
            @NotBlank String customerId,
            @NotBlank String warehouseId,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            LocalDate dueDate,
            @Size(max = 500) String note,
            @NotEmpty @Size(max = 200) List<@Valid LineRequest> lines
    ) {
    }

    public record PurchaseOrderRequest(
            @NotBlank String supplierId,
            @NotBlank String warehouseId,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            LocalDate expectedDate,
            @Size(max = 500) String note,
            @NotEmpty @Size(max = 200) List<@Valid LineRequest> lines
    ) {
    }

    public record LineView(
            String id,
            String itemId,
            BigDecimal orderedQuantity,
            BigDecimal fulfilledQuantity,
            BigDecimal reservedQuantity,
            BigDecimal returnedQuantity,
            BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal taxRate,
            BigDecimal netAmount,
            BigDecimal taxAmount,
            BigDecimal grossAmount
    ) {
    }

    public record DocumentView(
            String id,
            String number,
            String documentType,
            String partnerId,
            String warehouseId,
            String status,
            String currencyCode,
            LocalDate documentDate,
            LocalDate dueDate,
            BigDecimal totalAmount,
            String note,
            long version,
            Instant createdAt,
            List<LineView> lines
    ) {
    }
}
