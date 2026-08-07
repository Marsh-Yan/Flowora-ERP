package com.flowora.erp.procurement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequestLineRepository extends JpaRepository<PurchaseRequestLineEntity, String> {
    List<PurchaseRequestLineEntity> findAllByOrganizationIdAndPurchaseRequestId(String organizationId, String purchaseRequestId);
    Optional<PurchaseRequestLineEntity> findFirstByOrganizationIdAndPurchaseRequestId(String organizationId, String purchaseRequestId);
}
