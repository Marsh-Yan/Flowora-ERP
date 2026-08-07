package com.flowora.erp.sales;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class SalesDtos {
    private SalesDtos() {
    }

    public record SalesQuoteCreate(
            @NotBlank String customerId,
            @NotBlank String itemId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal discountRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxRate,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotNull LocalDate validUntil,
            @Size(max = 500) String note
    ) {
    }

    public record SalesQuoteResponse(
            String id,
            String number,
            SalesQuoteStatus status,
            String workflowTaskId,
            String customerId,
            String itemId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal taxRate,
            String currencyCode,
            LocalDate validUntil,
            BigDecimal totalAmount,
            String note,
            Instant approvedAt
    ) {
    }

    public record SalesOrderCreate(
            String quoteId,
            @NotBlank String customerId,
            @NotBlank String warehouseId,
            @NotBlank String itemId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal discountRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxRate,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            LocalDate dueDate,
            @Size(max = 500) String note
    ) {
    }

    public record SalesOrderResponse(
            String id,
            String number,
            SalesOrderStatus status,
            String quoteId,
            String customerId,
            String warehouseId,
            String lineId,
            String itemId,
            BigDecimal orderedQuantity,
            BigDecimal fulfilledQuantity,
            BigDecimal remainingQuantity,
            BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal taxRate,
            String currencyCode,
            LocalDate orderDate,
            LocalDate dueDate,
            BigDecimal totalAmount,
            BigDecimal receivableAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            String note
    ) {
    }

    public record DeliveryCreate(
            @NotBlank String salesOrderId,
            @NotBlank String salesOrderLineId,
            @NotBlank String warehouseId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity
    ) {
    }

    public record DeliveryResponse(
            String id,
            String number,
            String salesOrderId,
            String warehouseId,
            String itemId,
            BigDecimal quantity,
            BigDecimal unitCost,
            DeliveryStatus status,
            Instant postedAt
    ) {
    }

    public record ReceivableResponse(
            String id,
            String number,
            String salesOrderId,
            String customerId,
            String sourceType,
            String currencyCode,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            ReceivableStatus status,
            LocalDate dueDate
    ) {
    }

    public record PaymentCreate(
            @NotBlank String receivableId,
            @NotNull @DecimalMin("0.0001") BigDecimal amount,
            @NotNull PaymentMethod method,
            @NotNull LocalDate paymentDate,
            @Size(max = 120) String reference
    ) {
    }

    public record PaymentResponse(
            String id,
            String number,
            String receivableId,
            String customerId,
            BigDecimal amount,
            String currencyCode,
            PaymentMethod method,
            LocalDate paymentDate,
            String reference
    ) {
    }
}
