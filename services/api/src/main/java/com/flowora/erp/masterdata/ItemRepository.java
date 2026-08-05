package com.flowora.erp.masterdata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<ItemEntity, String> {
    @Query("select i from ItemEntity i where i.organizationId = :organizationId and (:query = '' or lower(i.code) like lower(concat('%', :query, '%')) or lower(i.name) like lower(concat('%', :query, '%'))) order by i.code")
    Page<ItemEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    boolean existsByOrganizationIdAndCode(String organizationId, String code);

    Optional<ItemEntity> findByIdAndOrganizationId(String id, String organizationId);
}
