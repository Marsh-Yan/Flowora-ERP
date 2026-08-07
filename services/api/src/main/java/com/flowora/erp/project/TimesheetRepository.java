package com.flowora.erp.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimesheetRepository extends JpaRepository<TimesheetEntity, String> {
    Page<TimesheetEntity> findByOrganizationIdAndProjectIdOrderByWorkDateDescCreatedAtDesc(String organizationId, String projectId, Pageable pageable);
    List<TimesheetEntity> findByOrganizationIdAndProjectIdOrderByWorkDateDescCreatedAtDesc(String organizationId, String projectId);
    List<TimesheetEntity> findByOrganizationIdAndTaskIdOrderByWorkDateAsc(String organizationId, String taskId);
    Optional<TimesheetEntity> findByIdAndOrganizationId(String id, String organizationId);
}
