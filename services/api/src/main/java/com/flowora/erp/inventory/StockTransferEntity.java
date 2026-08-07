package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "flowora_stock_transfer")
public class StockTransferEntity extends InventoryEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "source_warehouse_id", length = 36, nullable = false)
    private String sourceWarehouseId;

    @Column(name = "target_warehouse_id", length = 36, nullable = false)
    private String targetWarehouseId;

    @Column(name = "actor_user_id", length = 64, nullable = false)
    private String actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private StockTransferStatus status;

    protected StockTransferEntity() {
    }

    public StockTransferEntity(String organizationId, String number, String sourceWarehouseId, String targetWarehouseId, String actorUserId) {
        super(organizationId);
        this.number = number;
        this.sourceWarehouseId = sourceWarehouseId;
        this.targetWarehouseId = targetWarehouseId;
        this.actorUserId = actorUserId;
        this.status = StockTransferStatus.POSTED;
    }

    public String number() { return number; }
    public String sourceWarehouseId() { return sourceWarehouseId; }
    public String targetWarehouseId() { return targetWarehouseId; }
    public String actorUserId() { return actorUserId; }
    public StockTransferStatus status() { return status; }
}
