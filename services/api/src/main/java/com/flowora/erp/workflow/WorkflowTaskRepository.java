package com.flowora.erp.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTaskEntity, String> {
    @Query("select t from WorkflowTaskEntity t where t.organizationId = :organizationId and (t.assigneeUserId = :userId or t.assigneeRole in :roles or t.requesterUserId = :userId) order by t.createdAt desc")
    Page<WorkflowTaskEntity> inbox(
            @Param("organizationId") String organizationId,
            @Param("userId") String userId,
            @Param("roles") java.util.Collection<String> roles,
            Pageable pageable
    );

    Optional<WorkflowTaskEntity> findByIdAndOrganizationId(String id, String organizationId);
}
