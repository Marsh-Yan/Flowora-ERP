package com.flowora.erp.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_supplier_payment")
public class SupplierPaymentEntity extends AccountingEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "payable_id", length = 36, nullable = false)
    private String payableId;

    @Column(name = "supplier_id", length = 36, nullable = false)
    private String supplierId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16, nullable = false)
    private FinancePaymentMethod method;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(length = 120)
    private String reference;

    @Column(name = "actor_user_id", length = 64, nullable = false)
    private String actorUserId;

    protected SupplierPaymentEntity() {
    }

    public SupplierPaymentEntity(String organizationId, String number, String payableId, String supplierId, BigDecimal amount, String currencyCode, FinancePaymentMethod method, LocalDate paymentDate, String reference, String actorUserId) {
        super(organizationId);
        this.number = number;
        this.payableId = payableId;
        this.supplierId = supplierId;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.method = method;
        this.paymentDate = paymentDate;
        this.reference = reference;
        this.actorUserId = actorUserId;
    }

    public String number() { return number; }
    public String payableId() { return payableId; }
    public String supplierId() { return supplierId; }
    public BigDecimal amount() { return amount; }
    public String currencyCode() { return currencyCode; }
    public FinancePaymentMethod method() { return method; }
    public LocalDate paymentDate() { return paymentDate; }
    public String reference() { return reference; }
    public String actorUserId() { return actorUserId; }
}
