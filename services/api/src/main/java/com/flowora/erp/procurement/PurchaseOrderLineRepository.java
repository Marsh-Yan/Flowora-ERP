package com.flowora.erp.procurement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLineEntity, String> {
    List<PurchaseOrderLineEntity> findAllByOrganizationIdAndPurchaseOrderId(String organizationId, String purchaseOrderId);
    Optional<PurchaseOrderLineEntity> findFirstByOrganizationIdAndPurchaseOrderId(String organizationId, String purchaseOrderId);
    Optional<PurchaseOrderLineEntity> findByIdAndOrganizationId(String id, String organizationId);
}
