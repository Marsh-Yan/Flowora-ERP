package com.flowora.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockCountRepository extends JpaRepository<StockCountEntity, String> {
    Optional<StockCountEntity> findByIdAndOrganizationId(String id, String organizationId);
}
