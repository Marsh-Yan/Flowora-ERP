package com.flowora.erp.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_sales_quote")
public class SalesQuoteEntity extends SalesEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "customer_id", length = 36, nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private SalesQuoteStatus status;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalAmount;

    @Column(length = 500)
    private String note;

    @Column(name = "requester_user_id", length = 64, nullable = false)
    private String requesterUserId;

    @Column(name = "workflow_task_id", length = 36)
    private String workflowTaskId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "converted_at")
    private Instant convertedAt;

    protected SalesQuoteEntity() {
    }

    public SalesQuoteEntity(String organizationId, String number, String customerId, SalesQuoteStatus status, String currencyCode, LocalDate validUntil, BigDecimal totalAmount, String note, String requesterUserId) {
        super(organizationId);
        this.number = number;
        this.customerId = customerId;
        this.status = status;
        this.currencyCode = currencyCode;
        this.validUntil = validUntil;
        this.totalAmount = totalAmount;
        this.note = note;
        this.requesterUserId = requesterUserId;
    }

    public String number() { return number; }
    public String customerId() { return customerId; }
    public SalesQuoteStatus status() { return status; }
    public String currencyCode() { return currencyCode; }
    public LocalDate validUntil() { return validUntil; }
    public BigDecimal totalAmount() { return totalAmount; }
    public String note() { return note; }
    public String requesterUserId() { return requesterUserId; }
    public String workflowTaskId() { return workflowTaskId; }
    public Instant approvedAt() { return approvedAt; }
    public Instant convertedAt() { return convertedAt; }

    public void submitForApproval(String workflowTaskId) {
        this.workflowTaskId = workflowTaskId;
        this.status = SalesQuoteStatus.SUBMITTED;
    }

    public void approve() {
        this.status = SalesQuoteStatus.APPROVED;
        this.approvedAt = Instant.now();
    }

    public void reject() { this.status = SalesQuoteStatus.REJECTED; }

    public void markConverted() {
        if (status != SalesQuoteStatus.APPROVED) throw new IllegalStateException("Only an approved quote can be converted");
        this.status = SalesQuoteStatus.CONVERTED;
        this.convertedAt = Instant.now();
    }
}
