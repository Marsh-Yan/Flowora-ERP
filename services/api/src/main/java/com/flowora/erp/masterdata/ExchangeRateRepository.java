package com.flowora.erp.masterdata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRateEntity, String> {
    @Query("select e from ExchangeRateEntity e where e.organizationId = :organizationId and (:query = '' or lower(e.baseCurrencyCode) like lower(concat('%', :query, '%')) or lower(e.quoteCurrencyCode) like lower(concat('%', :query, '%'))) order by e.effectiveDate desc")
    Page<ExchangeRateEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    Optional<ExchangeRateEntity> findByIdAndOrganizationId(String id, String organizationId);
}
