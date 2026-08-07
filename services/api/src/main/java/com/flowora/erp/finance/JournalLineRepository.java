package com.flowora.erp.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLineEntity, String> {
    List<JournalLineEntity> findByOrganizationIdAndJournalEntryIdOrderByLineNo(String organizationId, String journalEntryId);

    @Query("select l from JournalLineEntity l where l.organizationId = :organizationId and l.journalEntryId in :journalEntryIds order by l.journalEntryId, l.lineNo")
    List<JournalLineEntity> byJournalEntryIds(@Param("organizationId") String organizationId, @Param("journalEntryIds") Collection<String> journalEntryIds);
}
