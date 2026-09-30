package com.flowora.erp.procurement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, String> {
    @Query(value = """
            SELECT o.* FROM flowora_purchase_order o
            WHERE o.organization_id = :organizationId
              AND (:query = '' OR LOWER(o.number) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:scope = 'ALL' OR (:scope = 'SELF' AND o.buyer_user_id = :userId)
                   OR (:scope = 'DEPARTMENT' AND o.buyer_user_id IN (
                       SELECT m.user_id FROM flowora_organization_membership m
                       WHERE m.organization_id = :organizationId AND m.department_id = :departmentId
                         AND m.status = 'ACTIVE')))
            ORDER BY o.created_at DESC
            """, countQuery = """
            SELECT COUNT(*) FROM flowora_purchase_order o
            WHERE o.organization_id = :organizationId
              AND (:query = '' OR LOWER(o.number) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:scope = 'ALL' OR (:scope = 'SELF' AND o.buyer_user_id = :userId)
                   OR (:scope = 'DEPARTMENT' AND o.buyer_user_id IN (
                       SELECT m.user_id FROM flowora_organization_membership m
                       WHERE m.organization_id = :organizationId AND m.department_id = :departmentId
                         AND m.status = 'ACTIVE')))
            """, nativeQuery = true)
    Page<PurchaseOrderEntity> scopedSearch(@Param("organizationId") String organizationId,
            @Param("query") String query, @Param("scope") String scope,
            @Param("userId") String userId, @Param("departmentId") String departmentId, Pageable pageable);

    Optional<PurchaseOrderEntity> findByIdAndOrganizationId(String id, String organizationId);
}
