package com.flowora.erp.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLedgerEntryRepository extends JpaRepository<StockLedgerEntryEntity, String> {
    boolean existsByOrganizationIdAndDocumentTypeAndDocumentIdAndMovementType(String organizationId, String documentType, String documentId, InventoryMovementType movementType);

    @Query("select l from StockLedgerEntryEntity l where l.organizationId = :organizationId and (:warehouseId = '' or l.warehouseId = :warehouseId) and (:itemId = '' or l.itemId = :itemId) order by l.createdAt desc")
    Page<StockLedgerEntryEntity> search(@Param("organizationId") String organizationId, @Param("warehouseId") String warehouseId, @Param("itemId") String itemId, Pageable pageable);
}
