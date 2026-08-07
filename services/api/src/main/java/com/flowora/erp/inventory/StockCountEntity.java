package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "flowora_stock_count")
public class StockCountEntity extends InventoryEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Column(name = "counted_by", length = 64, nullable = false)
    private String countedBy;

    @Column(name = "counted_at", nullable = false)
    private Instant countedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private StockCountStatus status;

    protected StockCountEntity() {
    }

    public StockCountEntity(String organizationId, String number, String warehouseId, String countedBy) {
        super(organizationId);
        this.number = number;
        this.warehouseId = warehouseId;
        this.countedBy = countedBy;
        this.countedAt = Instant.now();
        this.status = StockCountStatus.POSTED;
    }

    public String number() { return number; }
    public String warehouseId() { return warehouseId; }
    public String countedBy() { return countedBy; }
    public Instant countedAt() { return countedAt; }
    public StockCountStatus status() { return status; }
}
