package com.flowora.erp.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PayableRepository extends JpaRepository<PayableEntity, String> {
    @Query("select p from PayableEntity p where p.organizationId = :organizationId and (:query = '' or lower(p.number) like lower(concat('%', :query, '%')) or p.supplierId = :query) order by p.dueDate asc, p.createdAt desc")
    Page<PayableEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    Optional<PayableEntity> findByIdAndOrganizationId(String id, String organizationId);
    Optional<PayableEntity> findByOrganizationIdAndSourceTypeAndSourceId(String organizationId, String sourceType, String sourceId);
}
