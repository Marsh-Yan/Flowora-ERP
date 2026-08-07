package com.flowora.erp.procurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_purchase_order_line")
public class PurchaseOrderLineEntity extends ProcurementEntity {
    @Column(name = "purchase_order_id", length = 36, nullable = false, updatable = false)
    private String purchaseOrderId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(name = "ordered_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal orderedQuantity;

    @Column(name = "received_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal receivedQuantity;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "tax_rate", precision = 9, scale = 4, nullable = false)
    private BigDecimal taxRate;

    protected PurchaseOrderLineEntity() {
    }

    public PurchaseOrderLineEntity(String organizationId, String purchaseOrderId, String itemId, BigDecimal orderedQuantity, BigDecimal unitPrice, BigDecimal taxRate) {
        super(organizationId);
        this.purchaseOrderId = purchaseOrderId;
        this.itemId = itemId;
        this.orderedQuantity = orderedQuantity;
        this.receivedQuantity = BigDecimal.ZERO;
        this.unitPrice = unitPrice;
        this.taxRate = taxRate;
    }

    public String purchaseOrderId() { return purchaseOrderId; }
    public String itemId() { return itemId; }
    public BigDecimal orderedQuantity() { return orderedQuantity; }
    public BigDecimal receivedQuantity() { return receivedQuantity; }
    public BigDecimal unitPrice() { return unitPrice; }
    public BigDecimal taxRate() { return taxRate; }
    public BigDecimal remainingQuantity() { return orderedQuantity.subtract(receivedQuantity); }

    public void receive(BigDecimal quantity) {
        if (quantity.signum() <= 0 || quantity.compareTo(remainingQuantity()) > 0) {
            throw new IllegalArgumentException("Receipt quantity exceeds the remaining purchase order quantity");
        }
        receivedQuantity = receivedQuantity.add(quantity);
    }
}
