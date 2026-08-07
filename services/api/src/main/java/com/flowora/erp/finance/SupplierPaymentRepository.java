package com.flowora.erp.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPaymentEntity, String> {
    Page<SupplierPaymentEntity> findByOrganizationIdAndPayableIdOrderByPaymentDateDesc(String organizationId, String payableId, Pageable pageable);
}
