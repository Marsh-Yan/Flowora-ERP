package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_organization")
public class OrganizationEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 160, nullable = false)
    private String name;

    @Column(name = "base_currency_code", length = 3, nullable = false)
    private String baseCurrencyCode;

    @Column(length = 64, nullable = false)
    private String timezone;

    @Column(name = "approval_threshold", precision = 19, scale = 4, nullable = false)
    private BigDecimal approvalThreshold;

    @Column(name = "default_tax_rate", precision = 9, scale = 4, nullable = false)
    private BigDecimal defaultTaxRate;

    @Column(nullable = false)
    private boolean active;

    protected OrganizationEntity() {
    }

    public OrganizationEntity(String id, String name, String baseCurrencyCode, String timezone, BigDecimal approvalThreshold, BigDecimal defaultTaxRate) {
        this.id = id;
        update(name, baseCurrencyCode, timezone, approvalThreshold, defaultTaxRate);
        this.active = true;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String baseCurrencyCode() { return baseCurrencyCode; }
    public String timezone() { return timezone; }
    public BigDecimal approvalThreshold() { return approvalThreshold; }
    public BigDecimal defaultTaxRate() { return defaultTaxRate; }
    public boolean active() { return active; }

    public void update(String name, String baseCurrencyCode, String timezone, BigDecimal approvalThreshold, BigDecimal defaultTaxRate) {
        this.name = name;
        this.baseCurrencyCode = baseCurrencyCode;
        this.timezone = timezone;
        this.approvalThreshold = approvalThreshold;
        this.defaultTaxRate = defaultTaxRate;
    }
}
