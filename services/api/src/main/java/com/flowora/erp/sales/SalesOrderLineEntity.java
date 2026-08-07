package com.flowora.erp.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_sales_order_line")
public class SalesOrderLineEntity extends SalesEntity {
    @Column(name = "sales_order_id", length = 36, nullable = false)
    private String salesOrderId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(name = "ordered_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal orderedQuantity;

    @Column(name = "fulfilled_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal fulfilledQuantity;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "discount_rate", precision = 9, scale = 4, nullable = false)
    private BigDecimal discountRate;

    @Column(name = "tax_rate", precision = 9, scale = 4, nullable = false)
    private BigDecimal taxRate;

    protected SalesOrderLineEntity() {
    }

    public SalesOrderLineEntity(String organizationId, String salesOrderId, String itemId, BigDecimal orderedQuantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal taxRate) {
        super(organizationId);
        this.salesOrderId = salesOrderId;
        this.itemId = itemId;
        this.orderedQuantity = orderedQuantity;
        this.fulfilledQuantity = BigDecimal.ZERO;
        this.unitPrice = unitPrice;
        this.discountRate = discountRate;
        this.taxRate = taxRate;
    }

    public String salesOrderId() { return salesOrderId; }
    public String itemId() { return itemId; }
    public BigDecimal orderedQuantity() { return orderedQuantity; }
    public BigDecimal fulfilledQuantity() { return fulfilledQuantity; }
    public BigDecimal remainingQuantity() { return orderedQuantity.subtract(fulfilledQuantity); }
    public BigDecimal unitPrice() { return unitPrice; }
    public BigDecimal discountRate() { return discountRate; }
    public BigDecimal taxRate() { return taxRate; }
    public BigDecimal lineTotal() { return SalesQuoteLineEntity.total(orderedQuantity, unitPrice, discountRate, taxRate); }

    public void fulfill(BigDecimal quantity) {
        if (quantity.signum() <= 0 || quantity.compareTo(remainingQuantity()) > 0) {
            throw new IllegalArgumentException("Fulfillment quantity exceeds the sales order remaining quantity");
        }
        fulfilledQuantity = fulfilledQuantity.add(quantity);
    }
}
