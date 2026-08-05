package com.flowora.erp.masterdata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TaxRateRepository extends JpaRepository<TaxRateEntity, String> {
    @Query("select t from TaxRateEntity t where t.organizationId = :organizationId and (:query = '' or lower(t.code) like lower(concat('%', :query, '%')) or lower(t.name) like lower(concat('%', :query, '%'))) order by t.code")
    Page<TaxRateEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    boolean existsByOrganizationIdAndCode(String organizationId, String code);

    Optional<TaxRateEntity> findByIdAndOrganizationId(String id, String organizationId);
}
