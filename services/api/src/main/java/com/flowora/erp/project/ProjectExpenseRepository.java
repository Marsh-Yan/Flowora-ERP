package com.flowora.erp.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectExpenseRepository extends JpaRepository<ProjectExpenseEntity, String> {
    Page<ProjectExpenseEntity> findByOrganizationIdAndProjectIdOrderByExpenseDateDescCreatedAtDesc(String organizationId, String projectId, Pageable pageable);
    List<ProjectExpenseEntity> findByOrganizationIdAndProjectIdOrderByExpenseDateDescCreatedAtDesc(String organizationId, String projectId);
}
