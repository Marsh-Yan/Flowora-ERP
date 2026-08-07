package com.flowora.erp.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectBudgetRepository extends JpaRepository<ProjectBudgetEntity, String> {
    List<ProjectBudgetEntity> findByOrganizationIdAndProjectIdOrderByCategoryAsc(String organizationId, String projectId);
}
