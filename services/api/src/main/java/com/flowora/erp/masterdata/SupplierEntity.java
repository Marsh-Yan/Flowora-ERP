package com.flowora.erp.masterdata;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "flowora_supplier")
public class SupplierEntity extends BusinessPartnerEntity {
    protected SupplierEntity() {
    }

    public SupplierEntity(
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
        super(organizationId, code, name, contactName, email, phone, address, currencyCode, paymentTermsDays, active);
    }
}
