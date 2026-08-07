package com.flowora.erp.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, String> {
    @Query("select o from SalesOrderEntity o where o.organizationId = :organizationId and (:query = '' or lower(o.number) like lower(concat('%', :query, '%')) or o.customerId = :query) order by o.orderDate desc, o.createdAt desc")
    Page<SalesOrderEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    Optional<SalesOrderEntity> findByIdAndOrganizationId(String id, String organizationId);
}
