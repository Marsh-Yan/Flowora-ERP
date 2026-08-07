package com.flowora.erp.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_payment")
public class PaymentEntity extends SalesEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "receivable_id", length = 36, nullable = false)
    private String receivableId;

    @Column(name = "customer_id", length = 36, nullable = false)
    private String customerId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16, nullable = false)
    private PaymentMethod method;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(length = 120)
    private String reference;

    @Column(name = "actor_user_id", length = 64, nullable = false)
    private String actorUserId;

    protected PaymentEntity() {
    }

    public PaymentEntity(String organizationId, String number, String receivableId, String customerId, BigDecimal amount, String currencyCode, PaymentMethod method, LocalDate paymentDate, String reference, String actorUserId) {
        super(organizationId);
        this.number = number;
        this.receivableId = receivableId;
        this.customerId = customerId;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.method = method;
        this.paymentDate = paymentDate;
        this.reference = reference;
        this.actorUserId = actorUserId;
    }

    public String number() { return number; }
    public String receivableId() { return receivableId; }
    public String customerId() { return customerId; }
    public BigDecimal amount() { return amount; }
    public String currencyCode() { return currencyCode; }
    public PaymentMethod method() { return method; }
    public LocalDate paymentDate() { return paymentDate; }
    public String reference() { return reference; }
    public String actorUserId() { return actorUserId; }
}
