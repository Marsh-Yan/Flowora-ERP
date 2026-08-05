package com.flowora.erp.masterdata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<CurrencyEntity, String> {
    @Query("select c from CurrencyEntity c where c.organizationId = :organizationId and (:query = '' or lower(c.code) like lower(concat('%', :query, '%')) or lower(c.name) like lower(concat('%', :query, '%'))) order by c.code")
    Page<CurrencyEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    boolean existsByOrganizationIdAndCode(String organizationId, String code);

    Optional<CurrencyEntity> findByIdAndOrganizationId(String id, String organizationId);
}
