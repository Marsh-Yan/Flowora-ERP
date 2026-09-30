package com.flowora.erp.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {
    @Query(value = """
            SELECT p.* FROM flowora_project p
            WHERE p.organization_id = :organizationId
              AND (:query = '' OR LOWER(p.number) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:status IS NULL OR p.status = :status)
              AND (:scope = 'ALL' OR (:scope = 'SELF' AND p.manager_user_id = :userId)
                   OR (:scope = 'DEPARTMENT' AND p.department_id = :departmentId)
                   OR (:scope = 'ASSIGNED' AND EXISTS (
                       SELECT 1 FROM flowora_project_member m
                       WHERE m.project_id = p.id AND m.organization_id = :organizationId
                         AND m.user_id = :userId AND m.active = TRUE)))
            ORDER BY p.target_date ASC, p.created_at DESC
            """, countQuery = """
            SELECT COUNT(*) FROM flowora_project p
            WHERE p.organization_id = :organizationId
              AND (:query = '' OR LOWER(p.number) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:status IS NULL OR p.status = :status)
              AND (:scope = 'ALL' OR (:scope = 'SELF' AND p.manager_user_id = :userId)
                   OR (:scope = 'DEPARTMENT' AND p.department_id = :departmentId)
                   OR (:scope = 'ASSIGNED' AND EXISTS (
                       SELECT 1 FROM flowora_project_member m
                       WHERE m.project_id = p.id AND m.organization_id = :organizationId
                         AND m.user_id = :userId AND m.active = TRUE)))
            """, nativeQuery = true)
    Page<ProjectEntity> scopedSearch(@Param("organizationId") String organizationId,
            @Param("query") String query, @Param("status") String status,
            @Param("scope") String scope, @Param("userId") String userId,
            @Param("departmentId") String departmentId, Pageable pageable);

    Optional<ProjectEntity> findByIdAndOrganizationId(String id, String organizationId);
}
