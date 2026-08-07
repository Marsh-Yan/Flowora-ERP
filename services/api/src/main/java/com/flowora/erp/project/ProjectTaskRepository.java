package com.flowora.erp.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectTaskRepository extends JpaRepository<ProjectTaskEntity, String> {
    Page<ProjectTaskEntity> findByOrganizationIdAndProjectIdOrderByDueDateAscCreatedAtAsc(String organizationId, String projectId, Pageable pageable);
    List<ProjectTaskEntity> findByOrganizationIdAndProjectIdOrderByDueDateAscCreatedAtAsc(String organizationId, String projectId);
    Optional<ProjectTaskEntity> findByIdAndOrganizationId(String id, String organizationId);
}
