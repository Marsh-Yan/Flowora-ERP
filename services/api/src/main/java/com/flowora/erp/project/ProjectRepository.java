package com.flowora.erp.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {
    @Query("select p from ProjectEntity p where p.organizationId = :organizationId and (:query = '' or lower(p.number) like lower(concat('%', :query, '%')) or lower(p.name) like lower(concat('%', :query, '%'))) and (:status is null or p.status = :status) order by p.targetDate asc, p.createdAt desc")
    Page<ProjectEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, @Param("status") ProjectStatus status, Pageable pageable);

    Optional<ProjectEntity> findByIdAndOrganizationId(String id, String organizationId);
}
