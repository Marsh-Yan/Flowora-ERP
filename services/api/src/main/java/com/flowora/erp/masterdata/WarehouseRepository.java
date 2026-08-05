package com.flowora.erp.masterdata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<WarehouseEntity, String> {
    @Query("select w from WarehouseEntity w where w.organizationId = :organizationId and (:query = '' or lower(w.code) like lower(concat('%', :query, '%')) or lower(w.name) like lower(concat('%', :query, '%'))) order by w.code")
    Page<WarehouseEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    boolean existsByOrganizationIdAndCode(String organizationId, String code);

    Optional<WarehouseEntity> findByIdAndOrganizationId(String id, String organizationId);
}
