package com.flowora.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustmentEntity, String> {
    Optional<StockAdjustmentEntity> findByIdAndOrganizationId(String id, String organizationId);
}
