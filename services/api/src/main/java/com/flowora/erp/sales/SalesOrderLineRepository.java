package com.flowora.erp.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLineEntity, String> {
    Optional<SalesOrderLineEntity> findFirstByOrganizationIdAndSalesOrderId(String organizationId, String salesOrderId);
    Optional<SalesOrderLineEntity> findByIdAndOrganizationId(String id, String organizationId);
}
