package com.flowora.erp.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flowora_journal_entry")
public class JournalEntryEntity extends AccountingEntity {
    @Column(length = 32, nullable = false, unique = true)
    private String number;

    @Column(name = "period_id", length = 36, nullable = false)
    private String periodId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "source_type", length = 48, nullable = false)
    private String sourceType;

    @Column(name = "source_id", length = 64, nullable = false)
    private String sourceId;

    @Column(length = 500, nullable = false)
    private String memo;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "total_debit", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalCredit;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private JournalEntryStatus status;

    protected JournalEntryEntity() {
    }

    public JournalEntryEntity(String organizationId, String number, String periodId, LocalDate entryDate, String sourceType, String sourceId, String memo, String currencyCode, BigDecimal totalDebit, BigDecimal totalCredit) {
        super(organizationId);
        this.number = number;
        this.periodId = periodId;
        this.entryDate = entryDate;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.memo = memo;
        this.currencyCode = currencyCode;
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.status = JournalEntryStatus.POSTED;
    }

    public String number() { return number; }
    public String periodId() { return periodId; }
    public LocalDate entryDate() { return entryDate; }
    public String sourceType() { return sourceType; }
    public String sourceId() { return sourceId; }
    public String memo() { return memo; }
    public String currencyCode() { return currencyCode; }
    public BigDecimal totalDebit() { return totalDebit; }
    public BigDecimal totalCredit() { return totalCredit; }
    public JournalEntryStatus status() { return status; }

    public void voidEntry() {
        if (status != JournalEntryStatus.POSTED) throw new IllegalStateException("Only posted journal entries can be voided");
        status = JournalEntryStatus.VOID;
    }
}
