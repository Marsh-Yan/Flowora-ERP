package com.flowora.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransferEntity, String> {
    Optional<StockTransferEntity> findByIdAndOrganizationId(String id, String organizationId);
}
