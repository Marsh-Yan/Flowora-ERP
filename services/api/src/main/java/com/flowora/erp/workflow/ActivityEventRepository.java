package com.flowora.erp.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityEventRepository extends JpaRepository<ActivityEventEntity, String> {
    @Query("select a from ActivityEventEntity a where a.organizationId = :organizationId and a.resourceType = :resourceType and a.resourceId = :resourceId order by a.createdAt desc")
    Page<ActivityEventEntity> byResource(
            @Param("organizationId") String organizationId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            Pageable pageable
    );
}
