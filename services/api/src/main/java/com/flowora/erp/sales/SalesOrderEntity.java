package com.flowora.erp.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_sales_order")
public class SalesOrderEntity extends SalesEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "quote_id", length = 36)
    private String quoteId;

    @Column(name = "customer_id", length = 36, nullable = false)
    private String customerId;

    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private SalesOrderStatus status;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalAmount;

    @Column(length = 500)
    private String note;

    @Column(name = "sales_user_id", length = 64, nullable = false)
    private String salesUserId;

    protected SalesOrderEntity() {
    }

    public SalesOrderEntity(String organizationId, String number, String quoteId, String customerId, String warehouseId, SalesOrderStatus status, String currencyCode, LocalDate orderDate, LocalDate dueDate, BigDecimal totalAmount, String note, String salesUserId) {
        super(organizationId);
        this.number = number;
        this.quoteId = quoteId;
        this.customerId = customerId;
        this.warehouseId = warehouseId;
        this.status = status;
        this.currencyCode = currencyCode;
        this.orderDate = orderDate;
        this.dueDate = dueDate;
        this.totalAmount = totalAmount;
        this.note = note;
        this.salesUserId = salesUserId;
    }

    public String number() { return number; }
    public String quoteId() { return quoteId; }
    public String customerId() { return customerId; }
    public String warehouseId() { return warehouseId; }
    public SalesOrderStatus status() { return status; }
    public String currencyCode() { return currencyCode; }
    public LocalDate orderDate() { return orderDate; }
    public LocalDate dueDate() { return dueDate; }
    public BigDecimal totalAmount() { return totalAmount; }
    public String note() { return note; }
    public String salesUserId() { return salesUserId; }

    public void updateFulfillment(boolean complete, boolean hasFulfillment) {
        this.status = complete ? SalesOrderStatus.FULFILLED : (hasFulfillment ? SalesOrderStatus.PARTIALLY_FULFILLED : SalesOrderStatus.CONFIRMED);
    }
}
