package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class BusinessPartnerEntity extends MasterDataEntity {
    @Column(length = 48, nullable = false)
    private String code;

    @Column(length = 160, nullable = false)
    private String name;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(length = 190)
    private String email;

    @Column(length = 48)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(nullable = false)
    private boolean active;

    protected BusinessPartnerEntity() {
    }

    protected BusinessPartnerEntity(
            String organizationId,
            String code,
            String name,
            String contactName,
            String email,
            String phone,
            String address,
            String currencyCode,
            int paymentTermsDays,
            boolean active
    ) {
        super(organizationId);
        this.code = code;
        this.name = name;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.currencyCode = currencyCode;
        this.paymentTermsDays = paymentTermsDays;
        this.active = active;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String contactName() {
        return contactName;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public String address() {
        return address;
    }

    public String currencyCode() {
        return currencyCode;
    }

    public int paymentTermsDays() {
        return paymentTermsDays;
    }

    public boolean active() {
        return active;
    }

    public void update(
            String code,
            String name,
            String contactName,
            String email,
            String phone,
            String address,
            String currencyCode,
            int paymentTermsDays,
            boolean active
    ) {
        this.code = code;
        this.name = name;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.currencyCode = currencyCode;
        this.paymentTermsDays = paymentTermsDays;
        this.active = active;
    }
}
