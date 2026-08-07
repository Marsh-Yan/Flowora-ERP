package com.flowora.erp.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<DeliveryEntity, String> {
    @Query("select d from DeliveryEntity d where d.organizationId = :organizationId and (:query = '' or lower(d.number) like lower(concat('%', :query, '%')) or d.salesOrderId = :query) order by d.postedAt desc")
    Page<DeliveryEntity> search(@Param("organizationId") String organizationId, @Param("query") String query, Pageable pageable);

    Optional<DeliveryEntity> findByIdAndOrganizationId(String id, String organizationId);
}
