package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_purchase_receipt_line")
public class PurchaseReceiptLineEntity extends InventoryEntity {
    @Column(name = "purchase_receipt_id", length = 36, nullable = false, updatable = false)
    private String purchaseReceiptId;

    @Column(name = "purchase_order_line_id", length = 36, nullable = false)
    private String purchaseOrderLineId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitCost;

    protected PurchaseReceiptLineEntity() {
    }

    public PurchaseReceiptLineEntity(String organizationId, String purchaseReceiptId, String purchaseOrderLineId, String itemId, BigDecimal quantity, BigDecimal unitCost) {
        super(organizationId);
        this.purchaseReceiptId = purchaseReceiptId;
        this.purchaseOrderLineId = purchaseOrderLineId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    public String purchaseReceiptId() { return purchaseReceiptId; }
    public String purchaseOrderLineId() { return purchaseOrderLineId; }
    public String itemId() { return itemId; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal unitCost() { return unitCost; }
}
