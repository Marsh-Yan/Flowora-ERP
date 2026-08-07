package com.flowora.erp.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SalesQuoteRepository extends JpaRepository<SalesQuoteEntity, String> {
    @Query("select q from SalesQuoteEntity q where q.organizationId = :organizationId and (:query = '' or lower(q.number) like lower(concat('%', :query, '%')) or q.customerId = :query) order by q.createdAt desc")
    Page<SalesQuoteEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    Optional<SalesQuoteEntity> findByIdAndOrganizationId(String id, String organizationId);
}
