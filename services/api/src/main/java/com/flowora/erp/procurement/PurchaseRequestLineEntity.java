package com.flowora.erp.procurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_purchase_request_line")
public class PurchaseRequestLineEntity extends ProcurementEntity {
    @Column(name = "purchase_request_id", length = 36, nullable = false, updatable = false)
    private String purchaseRequestId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "estimated_unit_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal estimatedUnitCost;

    protected PurchaseRequestLineEntity() {
    }

    public PurchaseRequestLineEntity(String organizationId, String purchaseRequestId, String itemId, BigDecimal quantity, BigDecimal estimatedUnitCost) {
        super(organizationId);
        this.purchaseRequestId = purchaseRequestId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.estimatedUnitCost = estimatedUnitCost;
    }

    public String purchaseRequestId() { return purchaseRequestId; }
    public String itemId() { return itemId; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal estimatedUnitCost() { return estimatedUnitCost; }
}
