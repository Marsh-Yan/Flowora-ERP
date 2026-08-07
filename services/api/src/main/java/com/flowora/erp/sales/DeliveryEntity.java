package com.flowora.erp.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "flowora_sales_delivery")
public class DeliveryEntity extends SalesEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "sales_order_id", length = 36, nullable = false)
    private String salesOrderId;

    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private DeliveryStatus status;

    @Column(name = "actor_user_id", length = 64, nullable = false)
    private String actorUserId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected DeliveryEntity() {
    }

    public DeliveryEntity(String organizationId, String number, String salesOrderId, String warehouseId, String actorUserId) {
        super(organizationId);
        this.number = number;
        this.salesOrderId = salesOrderId;
        this.warehouseId = warehouseId;
        this.status = DeliveryStatus.POSTED;
        this.actorUserId = actorUserId;
        this.postedAt = Instant.now();
    }

    public String number() { return number; }
    public String salesOrderId() { return salesOrderId; }
    public String warehouseId() { return warehouseId; }
    public DeliveryStatus status() { return status; }
    public String actorUserId() { return actorUserId; }
    public Instant postedAt() { return postedAt; }
}
