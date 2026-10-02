package com.flowora.erp.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalanceEntity, String> {
    long countByOrganizationId(String organizationId);

    @Query(value = "SELECT COALESCE(SUM(ROUND(quantity*average_cost,4)),0) FROM flowora_stock_balance WHERE organization_id=:organizationId", nativeQuery = true)
    java.math.BigDecimal inventoryValue(@Param("organizationId") String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBalanceEntity b where b.organizationId = :organizationId and b.warehouseId = :warehouseId and b.itemId = :itemId")
    Optional<StockBalanceEntity> findForUpdate(@Param("organizationId") String organizationId, @Param("warehouseId") String warehouseId, @Param("itemId") String itemId);

    @Query("select b from StockBalanceEntity b where b.organizationId = :organizationId and (:warehouseId = '' or b.warehouseId = :warehouseId) order by b.updatedAt desc, b.id desc")
    Page<StockBalanceEntity> search(@Param("organizationId") String organizationId, @Param("warehouseId") String warehouseId, Pageable pageable);
}
