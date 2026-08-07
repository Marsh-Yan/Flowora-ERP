package com.flowora.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceiptEntity, String> {
    Optional<PurchaseReceiptEntity> findByIdAndOrganizationId(String id, String organizationId);
    boolean existsByOrganizationIdAndPurchaseOrderId(String organizationId, String purchaseOrderId);
}
