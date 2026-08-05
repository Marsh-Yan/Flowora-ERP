package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "flowora_account")
public class AccountEntity extends MasterDataEntity {
    @Column(length = 48, nullable = false)
    private String code;

    @Column(length = 160, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 16, nullable = false)
    private AccountType type;

    @Column(name = "parent_code", length = 48)
    private String parentCode;

    @Column(name = "posting_allowed", nullable = false)
    private boolean postingAllowed;

    @Column(nullable = false)
    private boolean active;

    protected AccountEntity() {
    }

    public AccountEntity(String organizationId, String code, String name, AccountType type, String parentCode, boolean postingAllowed, boolean active) {
        super(organizationId);
        update(code, name, type, parentCode, postingAllowed, active);
    }

    public String code() { return code; }
    public String name() { return name; }
    public AccountType type() { return type; }
    public String parentCode() { return parentCode; }
    public boolean postingAllowed() { return postingAllowed; }
    public boolean active() { return active; }

    public void update(String code, String name, AccountType type, String parentCode, boolean postingAllowed, boolean active) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.parentCode = parentCode;
        this.postingAllowed = postingAllowed;
        this.active = active;
    }
}
