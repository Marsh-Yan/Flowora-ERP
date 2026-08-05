package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_tax_rate")
public class TaxRateEntity extends MasterDataEntity {
    @Column(length = 48, nullable = false)
    private String code;

    @Column(length = 120, nullable = false)
    private String name;

    @Column(precision = 9, scale = 4, nullable = false)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean exempt;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false)
    private boolean active;

    protected TaxRateEntity() {
    }

    public TaxRateEntity(
            String organizationId,
            String code,
            String name,
            BigDecimal rate,
            boolean exempt,
            LocalDate effectiveDate,
            boolean active
    ) {
        super(organizationId);
        update(code, name, rate, exempt, effectiveDate, active);
    }

    public String code() { return code; }
    public String name() { return name; }
    public BigDecimal rate() { return rate; }
    public boolean exempt() { return exempt; }
    public LocalDate effectiveDate() { return effectiveDate; }
    public boolean active() { return active; }

    public void update(String code, String name, BigDecimal rate, boolean exempt, LocalDate effectiveDate, boolean active) {
        this.code = code;
        this.name = name;
        this.rate = rate;
        this.exempt = exempt;
        this.effectiveDate = effectiveDate;
        this.active = active;
    }
}
