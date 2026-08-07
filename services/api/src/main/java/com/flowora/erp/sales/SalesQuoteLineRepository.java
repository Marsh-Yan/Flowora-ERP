package com.flowora.erp.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesQuoteLineRepository extends JpaRepository<SalesQuoteLineEntity, String> {
    Optional<SalesQuoteLineEntity> findFirstByOrganizationIdAndQuoteId(String organizationId, String quoteId);
}
