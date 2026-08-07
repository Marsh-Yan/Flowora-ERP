package com.flowora.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "flowora_stock_adjustment")
public class StockAdjustmentEntity extends InventoryEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(name = "quantity_delta", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantityDelta;

    @Column(name = "unit_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitCost;

    @Column(length = 500, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private StockAdjustmentStatus status;

    @Column(name = "workflow_task_id", length = 36)
    private String workflowTaskId;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "created_by", length = 64, nullable = false)
    private String createdBy;

    protected StockAdjustmentEntity() {
    }

    public StockAdjustmentEntity(String organizationId, String number, String warehouseId, String itemId, BigDecimal quantityDelta, BigDecimal unitCost, String reason, String createdBy) {
        super(organizationId);
        this.number = number;
        this.warehouseId = warehouseId;
        this.itemId = itemId;
        this.quantityDelta = quantityDelta;
        this.unitCost = unitCost;
        this.reason = reason;
        this.createdBy = createdBy;
        this.status = StockAdjustmentStatus.DRAFT;
    }

    public String number() { return number; }
    public String warehouseId() { return warehouseId; }
    public String itemId() { return itemId; }
    public BigDecimal quantityDelta() { return quantityDelta; }
    public BigDecimal unitCost() { return unitCost; }
    public String reason() { return reason; }
    public StockAdjustmentStatus status() { return status; }
    public String workflowTaskId() { return workflowTaskId; }
    public Instant postedAt() { return postedAt; }
    public String createdBy() { return createdBy; }

    public void pendingApproval(String workflowTaskId) {
        this.workflowTaskId = workflowTaskId;
        this.status = StockAdjustmentStatus.PENDING_APPROVAL;
    }

    public void post() {
        this.status = StockAdjustmentStatus.POSTED;
        this.postedAt = Instant.now();
    }

    public void reject() { this.status = StockAdjustmentStatus.REJECTED; }
}
