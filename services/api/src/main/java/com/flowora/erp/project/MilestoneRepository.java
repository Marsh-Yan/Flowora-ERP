package com.flowora.erp.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<MilestoneEntity, String> {
    List<MilestoneEntity> findByOrganizationIdAndProjectIdOrderBySequenceNoAsc(String organizationId, String projectId);
    Optional<MilestoneEntity> findFirstByOrganizationIdAndProjectIdOrderBySequenceNoDesc(String organizationId, String projectId);
    Optional<MilestoneEntity> findByIdAndOrganizationId(String id, String organizationId);
}
