package com.flowora.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseReceiptLineRepository extends JpaRepository<PurchaseReceiptLineEntity, String> {
    List<PurchaseReceiptLineEntity> findAllByOrganizationIdAndPurchaseReceiptId(String organizationId, String purchaseReceiptId);
}
