package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "flowora_currency")
public class CurrencyEntity extends MasterDataEntity {
    @Column(length = 3, nullable = false)
    private String code;

    @Column(length = 80, nullable = false)
    private String name;

    @Column(length = 8, nullable = false)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private int decimalPlaces;

    @Column(nullable = false)
    private boolean active;

    protected CurrencyEntity() {
    }

    public CurrencyEntity(String organizationId, String code, String name, String symbol, int decimalPlaces, boolean active) {
        super(organizationId);
        update(code, name, symbol, decimalPlaces, active);
    }

    public String code() { return code; }
    public String name() { return name; }
    public String symbol() { return symbol; }
    public int decimalPlaces() { return decimalPlaces; }
    public boolean active() { return active; }

    public void update(String code, String name, String symbol, int decimalPlaces, boolean active) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
        this.active = active;
    }
}
