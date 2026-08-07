package com.flowora.erp.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
    Page<PaymentEntity> findByOrganizationIdAndReceivableIdOrderByPaymentDateDesc(String organizationId, String receivableId, Pageable pageable);
}
