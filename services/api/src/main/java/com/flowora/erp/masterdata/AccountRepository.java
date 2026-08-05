package com.flowora.erp.masterdata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    @Query("select a from AccountEntity a where a.organizationId = :organizationId and (:query = '' or lower(a.code) like lower(concat('%', :query, '%')) or lower(a.name) like lower(concat('%', :query, '%'))) order by a.code")
    Page<AccountEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    boolean existsByOrganizationIdAndCode(String organizationId, String code);

    Optional<AccountEntity> findByIdAndOrganizationId(String id, String organizationId);
}
