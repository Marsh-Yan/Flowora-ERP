package com.flowora.erp.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {
    @Query("select n from NotificationEntity n where n.organizationId = :organizationId and n.recipientUserId in :recipients order by n.createdAt desc")
    Page<NotificationEntity> inbox(@Param("organizationId") String organizationId, @Param("recipients") Collection<String> recipients, Pageable pageable);

    Optional<NotificationEntity> findByIdAndOrganizationId(String id, String organizationId);
}
