package com.flowora.erp.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_payable_document")
public class PayableEntity extends AccountingEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "purchase_receipt_id", length = 36, nullable = false)
    private String purchaseReceiptId;

    @Column(name = "supplier_id", length = 36, nullable = false)
    private String supplierId;

    @Column(name = "source_type", length = 32, nullable = false)
    private String sourceType;

    @Column(name = "source_id", length = 36, nullable = false)
    private String sourceId;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private PayableStatus status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    protected PayableEntity() {
    }

    public PayableEntity(String organizationId, String number, String purchaseReceiptId, String supplierId, String sourceType, String sourceId, String currencyCode, BigDecimal totalAmount, LocalDate dueDate) {
        super(organizationId);
        this.number = number;
        this.purchaseReceiptId = purchaseReceiptId;
        this.supplierId = supplierId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.currencyCode = currencyCode;
        this.totalAmount = totalAmount;
        this.paidAmount = BigDecimal.ZERO;
        this.status = PayableStatus.OPEN;
        this.dueDate = dueDate;
    }

    public String number() { return number; }
    public String purchaseReceiptId() { return purchaseReceiptId; }
    public String supplierId() { return supplierId; }
    public String sourceType() { return sourceType; }
    public String sourceId() { return sourceId; }
    public String currencyCode() { return currencyCode; }
    public BigDecimal totalAmount() { return totalAmount; }
    public BigDecimal paidAmount() { return paidAmount; }
    public BigDecimal remainingAmount() { return totalAmount.subtract(paidAmount); }
    public PayableStatus status() { return status; }
    public LocalDate dueDate() { return dueDate; }

    public void recordPayment(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(remainingAmount()) > 0) throw new IllegalArgumentException("Payment exceeds the payable outstanding amount");
        paidAmount = paidAmount.add(amount);
        status = paidAmount.compareTo(totalAmount) == 0 ? PayableStatus.SETTLED : PayableStatus.PARTIALLY_SETTLED;
    }
}
