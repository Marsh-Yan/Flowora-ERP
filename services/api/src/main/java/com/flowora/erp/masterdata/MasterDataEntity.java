package com.flowora.erp.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class MasterDataEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "organization_id", length = 36, nullable = false, updatable = false)
    private String organizationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long version;

    protected MasterDataEntity() {
    }

    protected MasterDataEntity(String organizationId) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = organizationId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String id() {
        return id;
    }

    public String organizationId() {
        return organizationId;
    }

    protected void organizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}
