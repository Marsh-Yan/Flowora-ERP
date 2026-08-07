package com.flowora.erp.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, String> {
    @Query("select j from JournalEntryEntity j where j.organizationId = :organizationId and j.entryDate between :from and :to order by j.entryDate desc, j.createdAt desc")
    Page<JournalEntryEntity> search(@Param("organizationId") String organizationId, @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("select j from JournalEntryEntity j where j.organizationId = :organizationId and j.entryDate between :from and :to order by j.entryDate, j.createdAt")
    List<JournalEntryEntity> inRange(@Param("organizationId") String organizationId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<JournalEntryEntity> findByOrganizationIdAndSourceTypeAndSourceId(String organizationId, String sourceType, String sourceId);
}
