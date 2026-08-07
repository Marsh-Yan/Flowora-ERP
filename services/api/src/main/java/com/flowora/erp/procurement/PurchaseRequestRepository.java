package com.flowora.erp.procurement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequestEntity, String> {
    @Query("select r from PurchaseRequestEntity r where r.organizationId = :organizationId and (:query = '' or lower(r.number) like lower(concat('%', :query, '%'))) order by r.createdAt desc")
    Page<PurchaseRequestEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    Optional<PurchaseRequestEntity> findByIdAndOrganizationId(String id, String organizationId);
}
