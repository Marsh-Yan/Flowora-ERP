package com.flowora.erp.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, String> {
    @Query(value = """
            SELECT o.* FROM flowora_sales_order o
            WHERE o.organization_id = :organizationId
              AND (:query = '' OR LOWER(o.number) LIKE LOWER(CONCAT('%', :query, '%')) OR o.customer_id = :query)
              AND (:scope = 'ALL' OR (:scope = 'SELF' AND o.sales_user_id = :userId)
                   OR (:scope = 'DEPARTMENT' AND o.sales_user_id IN (
                       SELECT m.user_id FROM flowora_organization_membership m
                       WHERE m.organization_id = :organizationId AND m.department_id = :departmentId
                         AND m.status = 'ACTIVE')))
            ORDER BY o.order_date DESC, o.created_at DESC
            """, countQuery = """
            SELECT COUNT(*) FROM flowora_sales_order o
            WHERE o.organization_id = :organizationId
              AND (:query = '' OR LOWER(o.number) LIKE LOWER(CONCAT('%', :query, '%')) OR o.customer_id = :query)
              AND (:scope = 'ALL' OR (:scope = 'SELF' AND o.sales_user_id = :userId)
                   OR (:scope = 'DEPARTMENT' AND o.sales_user_id IN (
                       SELECT m.user_id FROM flowora_organization_membership m
                       WHERE m.organization_id = :organizationId AND m.department_id = :departmentId
                         AND m.status = 'ACTIVE')))
            """, nativeQuery = true)
    Page<SalesOrderEntity> scopedSearch(@Param("organizationId") String organizationId,
            @Param("query") String query, @Param("scope") String scope,
            @Param("userId") String userId, @Param("departmentId") String departmentId, Pageable pageable);

    Optional<SalesOrderEntity> findByIdAndOrganizationId(String id, String organizationId);
}
