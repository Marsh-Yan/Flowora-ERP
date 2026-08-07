package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_stock_transfer_line")
public class StockTransferLineEntity extends InventoryEntity {
    @Column(name = "stock_transfer_id", length = 36, nullable = false, updatable = false)
    private String stockTransferId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitCost;

    protected StockTransferLineEntity() {
    }

    public StockTransferLineEntity(String organizationId, String stockTransferId, String itemId, BigDecimal quantity, BigDecimal unitCost) {
        super(organizationId);
        this.stockTransferId = stockTransferId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    public String stockTransferId() { return stockTransferId; }
    public String itemId() { return itemId; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal unitCost() { return unitCost; }
}
