package com.flowora.erp.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<CommentEntity, String> {
    @Query("select c from CommentEntity c where c.organizationId = :organizationId and c.resourceType = :resourceType and c.resourceId = :resourceId order by c.createdAt asc")
    Page<CommentEntity> byResource(
            @Param("organizationId") String organizationId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            Pageable pageable
    );
}
