package com.flowora.erp.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReceivableRepository extends JpaRepository<ReceivableEntity, String> {
    @Query("select r from ReceivableEntity r where r.organizationId = :organizationId and (:query = '' or lower(r.number) like lower(concat('%', :query, '%')) or r.customerId = :query) order by r.dueDate asc, r.createdAt desc")
    Page<ReceivableEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    Optional<ReceivableEntity> findByIdAndOrganizationId(String id, String organizationId);
    Optional<ReceivableEntity> findFirstByOrganizationIdAndSalesOrderId(String organizationId, String salesOrderId);
}
