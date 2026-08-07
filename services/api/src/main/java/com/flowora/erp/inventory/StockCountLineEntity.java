package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_stock_count_line")
public class StockCountLineEntity extends InventoryEntity {
    @Column(name = "stock_count_id", length = 36, nullable = false, updatable = false)
    private String stockCountId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(name = "expected_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal expectedQuantity;

    @Column(name = "counted_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal countedQuantity;

    @Column(name = "unit_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitCost;

    protected StockCountLineEntity() {
    }

    public StockCountLineEntity(String organizationId, String stockCountId, String itemId, BigDecimal expectedQuantity, BigDecimal countedQuantity, BigDecimal unitCost) {
        super(organizationId);
        this.stockCountId = stockCountId;
        this.itemId = itemId;
        this.expectedQuantity = expectedQuantity;
        this.countedQuantity = countedQuantity;
        this.unitCost = unitCost;
    }

    public String stockCountId() { return stockCountId; }
    public String itemId() { return itemId; }
    public BigDecimal expectedQuantity() { return expectedQuantity; }
    public BigDecimal countedQuantity() { return countedQuantity; }
    public BigDecimal unitCost() { return unitCost; }
    public BigDecimal variance() { return countedQuantity.subtract(expectedQuantity); }
}
