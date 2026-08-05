package com.flowora.erp.masterdata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<SupplierEntity, String> {
    @Query("select s from SupplierEntity s where s.organizationId = :organizationId and (:query = '' or lower(s.code) like lower(concat('%', :query, '%')) or lower(s.name) like lower(concat('%', :query, '%'))) order by s.code")
    Page<SupplierEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    boolean existsByOrganizationIdAndCode(String organizationId, String code);

    Optional<SupplierEntity> findByIdAndOrganizationId(String id, String organizationId);
}
