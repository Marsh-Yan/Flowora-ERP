package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_item")
public class ItemEntity extends MasterDataEntity {
    @Column(length = 48, nullable = false)
    private String code;

    @Column(length = 160, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 16, nullable = false)
    private ItemType type;

    @Column(length = 24, nullable = false)
    private String unit;

    @Column(name = "sales_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal salesPrice;

    @Column(name = "purchase_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal purchasePrice;

    @Column(name = "average_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal averageCost;

    @Column(name = "tax_rate", precision = 9, scale = 4, nullable = false)
    private BigDecimal taxRate;

    @Column(name = "inventory_managed", nullable = false)
    private boolean inventoryManaged;

    @Column(nullable = false)
    private boolean active;

    protected ItemEntity() {
    }

    public ItemEntity(
            String organizationId,
            String code,
            String name,
            ItemType type,
            String unit,
            BigDecimal salesPrice,
            BigDecimal purchasePrice,
            BigDecimal averageCost,
            BigDecimal taxRate,
            boolean inventoryManaged,
            boolean active
    ) {
        super(organizationId);
        this.update(code, name, type, unit, salesPrice, purchasePrice, averageCost, taxRate, inventoryManaged, active);
    }

    public String code() { return code; }
    public String name() { return name; }
    public ItemType type() { return type; }
    public String unit() { return unit; }
    public BigDecimal salesPrice() { return salesPrice; }
    public BigDecimal purchasePrice() { return purchasePrice; }
    public BigDecimal averageCost() { return averageCost; }
    public BigDecimal taxRate() { return taxRate; }
    public boolean inventoryManaged() { return inventoryManaged; }
    public boolean active() { return active; }

    public void update(
            String code,
            String name,
            ItemType type,
            String unit,
            BigDecimal salesPrice,
            BigDecimal purchasePrice,
            BigDecimal averageCost,
            BigDecimal taxRate,
            boolean inventoryManaged,
            boolean active
    ) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.unit = unit;
        this.salesPrice = salesPrice;
        this.purchasePrice = purchasePrice;
        this.averageCost = averageCost;
        this.taxRate = taxRate;
        this.inventoryManaged = inventoryManaged;
        this.active = active;
    }
}
