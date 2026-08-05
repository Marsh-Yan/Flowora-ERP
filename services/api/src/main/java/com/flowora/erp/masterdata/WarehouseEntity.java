package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "flowora_warehouse")
public class WarehouseEntity extends MasterDataEntity {
    @Column(length = 48, nullable = false)
    private String code;

    @Column(length = 160, nullable = false)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(nullable = false)
    private boolean active;

    protected WarehouseEntity() {
    }

    public WarehouseEntity(String organizationId, String code, String name, String address, boolean active) {
        super(organizationId);
        update(code, name, address, active);
    }

    public String code() { return code; }
    public String name() { return name; }
    public String address() { return address; }
    public boolean active() { return active; }

    public void update(String code, String name, String address, boolean active) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.active = active;
    }
}
