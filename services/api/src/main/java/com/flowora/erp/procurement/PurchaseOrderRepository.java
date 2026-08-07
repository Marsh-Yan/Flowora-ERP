package com.flowora.erp.procurement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, String> {
    @Query("select o from PurchaseOrderEntity o where o.organizationId = :organizationId and (:query = '' or lower(o.number) like lower(concat('%', :query, '%'))) order by o.createdAt desc")
    Page<PurchaseOrderEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    Optional<PurchaseOrderEntity> findByIdAndOrganizationId(String id, String organizationId);
}
