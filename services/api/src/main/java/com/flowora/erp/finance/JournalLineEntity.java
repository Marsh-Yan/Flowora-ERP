package com.flowora.erp.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "flowora_journal_line")
public class JournalLineEntity extends AccountingEntity {
    @Column(name = "journal_entry_id", length = 36, nullable = false)
    private String journalEntryId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "account_code", length = 48, nullable = false)
    private String accountCode;

    @Column(length = 200)
    private String description;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal debit;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal credit;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    protected JournalLineEntity() {
    }

    public JournalLineEntity(String organizationId, String journalEntryId, int lineNo, String accountCode, String description, BigDecimal debit, BigDecimal credit, String currencyCode) {
        super(organizationId);
        this.journalEntryId = journalEntryId;
        this.lineNo = lineNo;
        this.accountCode = accountCode;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
        this.currencyCode = currencyCode;
    }

    public String journalEntryId() { return journalEntryId; }
    public int lineNo() { return lineNo; }
    public String accountCode() { return accountCode; }
    public String description() { return description; }
    public BigDecimal debit() { return debit; }
    public BigDecimal credit() { return credit; }
    public String currencyCode() { return currencyCode; }
}
