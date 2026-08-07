package com.flowora.erp.procurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "flowora_purchase_order")
public class PurchaseOrderEntity extends ProcurementEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "purchase_request_id", length = 36)
    private String purchaseRequestId;

    @Column(name = "supplier_id", length = 36, nullable = false)
    private String supplierId;

    @Column(name = "warehouse_id", length = 36, nullable = false)
    private String warehouseId;

    @Column(name = "buyer_user_id", length = 64, nullable = false)
    private String buyerUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private ProcurementDocumentStatus status;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(length = 500)
    private String note;

    protected PurchaseOrderEntity() {
    }

    public PurchaseOrderEntity(String organizationId, String number, String purchaseRequestId, String supplierId, String warehouseId, String buyerUserId, LocalDate expectedDate, String note) {
        super(organizationId);
        this.number = number;
        this.purchaseRequestId = purchaseRequestId;
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
        this.buyerUserId = buyerUserId;
        this.expectedDate = expectedDate;
        this.note = note;
        this.orderDate = LocalDate.now();
        this.status = ProcurementDocumentStatus.APPROVED;
    }

    public String number() { return number; }
    public String purchaseRequestId() { return purchaseRequestId; }
    public String supplierId() { return supplierId; }
    public String warehouseId() { return warehouseId; }
    public String buyerUserId() { return buyerUserId; }
    public ProcurementDocumentStatus status() { return status; }
    public LocalDate orderDate() { return orderDate; }
    public LocalDate expectedDate() { return expectedDate; }
    public String note() { return note; }

    public void markPartiallyReceived() { status = ProcurementDocumentStatus.PARTIALLY_RECEIVED; }
    public void markReceived() { status = ProcurementDocumentStatus.RECEIVED; }
    public void cancel() { status = ProcurementDocumentStatus.CANCELLED; }
}
