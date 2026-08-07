package com.flowora.erp.procurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "flowora_purchase_request")
public class PurchaseRequestEntity extends ProcurementEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "supplier_id", length = 36, nullable = false)
    private String supplierId;

    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Column(name = "requester_user_id", length = 64, nullable = false)
    private String requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private ProcurementDocumentStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected PurchaseRequestEntity() {
    }

    public PurchaseRequestEntity(String organizationId, String number, String supplierId, String warehouseId, String requesterUserId, String note) {
        super(organizationId);
        this.number = number;
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
        this.requesterUserId = requesterUserId;
        this.note = note;
        this.status = ProcurementDocumentStatus.DRAFT;
    }

    public String number() { return number; }
    public String supplierId() { return supplierId; }
    public String warehouseId() { return warehouseId; }
    public String requesterUserId() { return requesterUserId; }
    public ProcurementDocumentStatus status() { return status; }
    public String note() { return note; }
    public Instant submittedAt() { return submittedAt; }

    public void submit() {
        status = ProcurementDocumentStatus.SUBMITTED;
        submittedAt = Instant.now();
    }

    public void approve() { status = ProcurementDocumentStatus.APPROVED; }
    public void reject() { status = ProcurementDocumentStatus.REJECTED; }
    public void cancel() { status = ProcurementDocumentStatus.CANCELLED; }
}
