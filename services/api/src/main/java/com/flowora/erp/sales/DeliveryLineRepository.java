package com.flowora.erp.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryLineRepository extends JpaRepository<DeliveryLineEntity, String> {
    Optional<DeliveryLineEntity> findFirstByOrganizationIdAndDeliveryId(String organizationId, String deliveryId);
}
