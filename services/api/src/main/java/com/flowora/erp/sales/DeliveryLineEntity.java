package com.flowora.erp.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_sales_delivery_line")
public class DeliveryLineEntity extends SalesEntity {
    @Column(name = "delivery_id", length = 36, nullable = false)
    private String deliveryId;

    @Column(name = "sales_order_line_id", length = 36, nullable = false)
    private String salesOrderLineId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitCost;

    protected DeliveryLineEntity() {
    }

    public DeliveryLineEntity(String organizationId, String deliveryId, String salesOrderLineId, String itemId, BigDecimal quantity, BigDecimal unitCost) {
        super(organizationId);
        this.deliveryId = deliveryId;
        this.salesOrderLineId = salesOrderLineId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    public String deliveryId() { return deliveryId; }
    public String salesOrderLineId() { return salesOrderLineId; }
    public String itemId() { return itemId; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal unitCost() { return unitCost; }
}
