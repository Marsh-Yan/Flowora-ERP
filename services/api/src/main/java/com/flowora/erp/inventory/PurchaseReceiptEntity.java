package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "flowora_purchase_receipt")
public class PurchaseReceiptEntity extends InventoryEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "purchase_order_id", length = 36, nullable = false)
    private String purchaseOrderId;

    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Column(name = "received_by", length = 64, nullable = false)
    private String receivedBy;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private ReceiptStatus status;

    protected PurchaseReceiptEntity() {
    }

    public PurchaseReceiptEntity(String organizationId, String number, String purchaseOrderId, String warehouseId, String receivedBy) {
        super(organizationId);
        this.number = number;
        this.purchaseOrderId = purchaseOrderId;
        this.warehouseId = warehouseId;
        this.receivedBy = receivedBy;
        this.receivedAt = Instant.now();
        this.status = ReceiptStatus.POSTED;
    }

    public String number() { return number; }
    public String purchaseOrderId() { return purchaseOrderId; }
    public String warehouseId() { return warehouseId; }
    public String receivedBy() { return receivedBy; }
    public Instant receivedAt() { return receivedAt; }
    public ReceiptStatus status() { return status; }
}
