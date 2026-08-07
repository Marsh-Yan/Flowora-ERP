package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "flowora_stock_balance")
public class StockBalanceEntity extends InventoryEntity {
    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "average_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal averageCost;

    protected StockBalanceEntity() {
    }

    public StockBalanceEntity(String organizationId, String warehouseId, String itemId, BigDecimal quantity, BigDecimal averageCost) {
        super(organizationId);
        this.warehouseId = warehouseId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    public String warehouseId() { return warehouseId; }
    public String itemId() { return itemId; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal averageCost() { return averageCost; }
    public BigDecimal inventoryValue() { return quantity.multiply(averageCost).setScale(4, RoundingMode.HALF_UP); }

    public void receive(BigDecimal incomingQuantity, BigDecimal incomingUnitCost) {
        BigDecimal oldValue = quantity.multiply(averageCost);
        BigDecimal incomingValue = incomingQuantity.multiply(incomingUnitCost);
        BigDecimal newQuantity = quantity.add(incomingQuantity);
        quantity = newQuantity;
        averageCost = newQuantity.signum() == 0 ? BigDecimal.ZERO : oldValue.add(incomingValue).divide(newQuantity, 4, RoundingMode.HALF_UP);
    }

    public void increase(BigDecimal delta) { quantity = quantity.add(delta); }

    public void decrease(BigDecimal delta) {
        if (delta.signum() <= 0 || delta.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("Insufficient stock for the requested movement");
        }
        quantity = quantity.subtract(delta);
    }

    public void setQuantity(BigDecimal newQuantity) {
        if (newQuantity.signum() < 0) throw new IllegalArgumentException("Stock quantity cannot be negative");
        quantity = newQuantity;
    }
}
