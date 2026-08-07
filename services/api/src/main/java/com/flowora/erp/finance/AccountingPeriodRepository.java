package com.flowora.erp.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriodEntity, String> {
    Optional<AccountingPeriodEntity> findByOrganizationIdAndYearAndMonth(String organizationId, int year, int month);
    Page<AccountingPeriodEntity> findByOrganizationIdOrderByYearDescMonthDesc(String organizationId, Pageable pageable);
    Optional<AccountingPeriodEntity> findByIdAndOrganizationId(String id, String organizationId);
}
