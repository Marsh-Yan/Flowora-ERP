package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_exchange_rate")
public class ExchangeRateEntity extends MasterDataEntity {
    @Column(name = "base_currency_code", length = 3, nullable = false)
    private String baseCurrencyCode;

    @Column(name = "quote_currency_code", length = 3, nullable = false)
    private String quoteCurrencyCode;

    @Column(precision = 19, scale = 8, nullable = false)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false)
    private boolean active;

    protected ExchangeRateEntity() {
    }

    public ExchangeRateEntity(
            String organizationId,
            String baseCurrencyCode,
            String quoteCurrencyCode,
            BigDecimal rate,
            LocalDate effectiveDate,
            boolean active
    ) {
        super(organizationId);
        update(baseCurrencyCode, quoteCurrencyCode, rate, effectiveDate, active);
    }

    public String baseCurrencyCode() { return baseCurrencyCode; }
    public String quoteCurrencyCode() { return quoteCurrencyCode; }
    public BigDecimal rate() { return rate; }
    public LocalDate effectiveDate() { return effectiveDate; }
    public boolean active() { return active; }

    public void update(String baseCurrencyCode, String quoteCurrencyCode, BigDecimal rate, LocalDate effectiveDate, boolean active) {
        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.rate = rate;
        this.effectiveDate = effectiveDate;
        this.active = active;
    }
}
