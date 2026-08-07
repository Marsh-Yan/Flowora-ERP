package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_stock_ledger_entry")
public class StockLedgerEntryEntity extends InventoryEntity {
    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", length = 24, nullable = false)
    private InventoryMovementType movementType;

    @Column(name = "document_type", length = 64, nullable = false)
    private String documentType;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "quantity_delta", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantityDelta;

    @Column(name = "unit_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitCost;

    @Column(name = "value_delta", precision = 19, scale = 4, nullable = false)
    private BigDecimal valueDelta;

    @Column(name = "balance_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal balanceQuantity;

    @Column(name = "balance_value", precision = 19, scale = 4, nullable = false)
    private BigDecimal balanceValue;

    @Column(name = "actor_user_id", length = 64, nullable = false)
    private String actorUserId;

    protected StockLedgerEntryEntity() {
    }

    public StockLedgerEntryEntity(String organizationId, String warehouseId, String itemId, InventoryMovementType movementType, String documentType, String documentId, BigDecimal quantityDelta, BigDecimal unitCost, BigDecimal valueDelta, BigDecimal balanceQuantity, BigDecimal balanceValue, String actorUserId) {
        super(organizationId);
        this.warehouseId = warehouseId;
        this.itemId = itemId;
        this.movementType = movementType;
        this.documentType = documentType;
        this.documentId = documentId;
        this.quantityDelta = quantityDelta;
        this.unitCost = unitCost;
        this.valueDelta = valueDelta;
        this.balanceQuantity = balanceQuantity;
        this.balanceValue = balanceValue;
        this.actorUserId = actorUserId;
    }

    public String warehouseId() { return warehouseId; }
    public String itemId() { return itemId; }
    public InventoryMovementType movementType() { return movementType; }
    public String documentType() { return documentType; }
    public String documentId() { return documentId; }
    public BigDecimal quantityDelta() { return quantityDelta; }
    public BigDecimal unitCost() { return unitCost; }
    public BigDecimal valueDelta() { return valueDelta; }
    public BigDecimal balanceQuantity() { return balanceQuantity; }
    public BigDecimal balanceValue() { return balanceValue; }
    public String actorUserId() { return actorUserId; }
}
