package com.flowora.erp.finance.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.finance.v2.FinancePostingPolicy.PostingLine;
import com.flowora.erp.finance.v2.FinanceV2Dtos.JournalLineView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.JournalView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.TrialBalanceRow;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinanceLedgerService {
    private final JdbcTemplate jdbc;

    public FinanceLedgerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String mapping(String organizationId, String semanticCode) {
        List<String> values = jdbc.query("""
                SELECT account_code FROM flowora_posting_mapping
                WHERE organization_id=? AND semantic_code=? AND active=TRUE
                ORDER BY rule_version DESC LIMIT 1
                """, (rs, row) -> rs.getString(1), organizationId, semanticCode);
        if (values.isEmpty()) throw conflict("POSTING_MAPPING_MISSING", Map.of("semanticCode", semanticCode));
        return values.getFirst();
    }

    @Transactional
    public JournalView post(String organizationId, String actorUserId, String sourceType, String sourceId,
                            String reversalOfId, LocalDate businessDate, LocalDate accountingDate,
                            LocalDate exchangeRateDate, String currencyCode, String baseCurrencyCode,
                            BigDecimal exchangeRate, String memo, String requestId, List<PostingLine> lines) {
        List<String> existing = jdbc.query("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND request_id=?",
                (rs, row) -> rs.getString(1), organizationId, requestId);
        if (!existing.isEmpty()) return journal(organizationId, existing.getFirst());
        FinancePostingPolicy.requireBalanced(lines);
        String periodId = openPeriod(organizationId, accountingDate);
        BigDecimal debit = lines.stream().map(PostingLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4);
        BigDecimal credit = lines.stream().map(PostingLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4);
        String id = UUID.randomUUID().toString();
        String number = "JE-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);
        jdbc.update("""
                INSERT INTO flowora_journal_entry
                (id,organization_id,number,period_id,entry_date,business_date,accounting_date,exchange_rate_date,
                 source_type,source_id,reversal_of_id,memo,currency_code,base_currency_code,exchange_rate,rule_version,
                 total_debit,total_credit,status,posted_by,posted_at,request_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'POSTED',?,CURRENT_TIMESTAMP,?)
                """, id, organizationId, number, periodId, accountingDate, businessDate, accountingDate,
                exchangeRateDate, sourceType, sourceId, reversalOfId, memo, currencyCode, baseCurrencyCode,
                exchangeRate, 1, debit, credit, actorUserId, requestId);
        int lineNo = 0;
        for (PostingLine line : lines) {
            lineNo++;
            jdbc.update("""
                    INSERT INTO flowora_journal_line
                    (id,organization_id,journal_entry_id,line_no,account_code,description,debit,credit,base_debit,
                     base_credit,currency_code,project_id,party_type,party_id,source_line_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID().toString(), organizationId, id, lineNo, line.accountCode(),
                    line.description(), line.debit(), line.credit(), FinancePostingPolicy.base(line.debit(), exchangeRate),
                    FinancePostingPolicy.base(line.credit(), exchangeRate), currencyCode, line.projectId(),
                    line.partyType(), line.partyId(), line.sourceLineId());
        }
        return journal(organizationId, id);
    }

    @Transactional
    public JournalView reverse(String organizationId, String actorUserId, String journalId,
                               LocalDate accountingDate, String reason, String requestId) {
        JournalView original = journalForUpdate(organizationId, journalId);
        if (!"POSTED".equals(original.status())) throw conflict("JOURNAL_NOT_POSTED", Map.of("journalId", journalId));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND reversal_of_id=?",
                Integer.class, organizationId, journalId) > 0) {
            throw conflict("JOURNAL_ALREADY_REVERSED", Map.of("journalId", journalId));
        }
        List<PostingLine> reversed = original.lines().stream().map(line -> new PostingLine(
                line.accountCode(), "Reversal: " + line.description(), line.credit(), line.debit(),
                line.projectId(), line.partyType(), line.partyId(), line.sourceLineId())).toList();
        JournalView result = post(organizationId, actorUserId, "JOURNAL_REVERSAL", journalId, journalId,
                accountingDate, accountingDate, accountingDate, original.currencyCode(),
                original.baseCurrencyCode(), original.exchangeRate(), reason, requestId, reversed);
        jdbc.update("UPDATE flowora_journal_entry SET status='REVERSED',version_no=version_no+1 WHERE id=? AND organization_id=?",
                journalId, organizationId);
        return result;
    }

    public JournalView journal(String organizationId, String id) {
        List<JournalView> values = jdbc.query("""
                SELECT id,number,source_type,source_id,reversal_of_id,business_date,accounting_date,exchange_rate_date,
                       currency_code,base_currency_code,exchange_rate,total_debit,total_credit,status,posted_at
                FROM flowora_journal_entry WHERE organization_id=? AND id=?
                """, (rs, row) -> new JournalView(rs.getString("id"), rs.getString("number"),
                rs.getString("source_type"), rs.getString("source_id"), rs.getString("reversal_of_id"),
                rs.getDate("business_date").toLocalDate(), rs.getDate("accounting_date").toLocalDate(),
                rs.getDate("exchange_rate_date").toLocalDate(), rs.getString("currency_code"),
                rs.getString("base_currency_code"), rs.getBigDecimal("exchange_rate"),
                rs.getBigDecimal("total_debit"), rs.getBigDecimal("total_credit"), rs.getString("status"),
                timestamp(rs.getTimestamp("posted_at")), List.of()), organizationId, id);
        if (values.isEmpty()) throw notFound("journal", id);
        JournalView head = values.getFirst();
        List<JournalLineView> lines = jdbc.query("""
                SELECT line_no,account_code,description,debit,credit,base_debit,base_credit,currency_code,
                       project_id,party_type,party_id,source_line_id
                FROM flowora_journal_line WHERE organization_id=? AND journal_entry_id=? ORDER BY line_no
                """, (rs, row) -> new JournalLineView(rs.getInt("line_no"), rs.getString("account_code"),
                rs.getString("description"), rs.getBigDecimal("debit"), rs.getBigDecimal("credit"),
                rs.getBigDecimal("base_debit"), rs.getBigDecimal("base_credit"), rs.getString("currency_code"),
                rs.getString("project_id"), rs.getString("party_type"), rs.getString("party_id"),
                rs.getString("source_line_id")), organizationId, id);
        return new JournalView(head.id(), head.number(), head.sourceType(), head.sourceId(), head.reversalOfId(),
                head.businessDate(), head.accountingDate(), head.exchangeRateDate(), head.currencyCode(),
                head.baseCurrencyCode(), head.exchangeRate(), head.totalDebit(), head.totalCredit(), head.status(),
                head.postedAt(), lines);
    }

    public List<JournalView> journals(String organizationId, LocalDate from, LocalDate to) {
        List<String> ids = jdbc.query("""
                SELECT id FROM flowora_journal_entry WHERE organization_id=? AND accounting_date BETWEEN ? AND ?
                ORDER BY accounting_date DESC,created_at DESC
                """, (rs, row) -> rs.getString(1), organizationId, from, to);
        return ids.stream().map(id -> journal(organizationId, id)).toList();
    }

    public List<TrialBalanceRow> trialBalance(String organizationId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT l.account_code,SUM(l.base_debit) debit,SUM(l.base_credit) credit,
                       SUM(l.base_debit-l.base_credit) balance
                FROM flowora_journal_line l JOIN flowora_journal_entry e ON e.id=l.journal_entry_id
                WHERE l.organization_id=? AND e.status IN ('POSTED','REVERSED') AND e.accounting_date BETWEEN ? AND ?
                GROUP BY l.account_code ORDER BY l.account_code
                """, (rs, row) -> new TrialBalanceRow(rs.getString("account_code"), rs.getBigDecimal("debit"),
                rs.getBigDecimal("credit"), rs.getBigDecimal("balance")), organizationId, from, to);
    }

    public String baseCurrency(String organizationId) {
        List<String> values = jdbc.query("SELECT base_currency_code FROM flowora_finance_setting WHERE organization_id=?",
                (rs, row) -> rs.getString(1), organizationId);
        if (values.isEmpty()) throw conflict("FINANCE_SETTING_MISSING", Map.of());
        return values.getFirst();
    }

    private JournalView journalForUpdate(String organizationId, String id) {
        jdbc.query("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), organizationId, id);
        return journal(organizationId, id);
    }

    private String openPeriod(String organizationId, LocalDate date) {
        List<Period> periods = jdbc.query("""
                SELECT id,status FROM flowora_accounting_period
                WHERE organization_id=? AND ? BETWEEN start_date AND end_date FOR UPDATE
                """, (rs, row) -> new Period(rs.getString("id"), rs.getString("status")), organizationId, date);
        if (periods.isEmpty()) {
            String id = UUID.randomUUID().toString();
            LocalDate start = date.withDayOfMonth(1);
            jdbc.update("""
                    INSERT INTO flowora_accounting_period(id,organization_id,year,month,start_date,end_date,status)
                    VALUES (?,?,?,?,?,?,'OPEN')
                    """, id, organizationId, date.getYear(), date.getMonthValue(), start, start.plusMonths(1).minusDays(1));
            return id;
        }
        if (!"OPEN".equals(periods.getFirst().status())) {
            throw conflict("ACCOUNTING_PERIOD_CLOSED", Map.of("accountingDate", date));
        }
        return periods.getFirst().id();
    }

    private static java.time.LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public static PlatformApiException conflict(String code, Map<String, ?> args) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        args.forEach(normalized::put);
        return new PlatformApiException(HttpStatus.CONFLICT, code, "errors.financeConflict", normalized);
    }

    public static PlatformApiException notFound(String resource, String id) {
        return new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound",
                Map.of("resource", resource, "id", id));
    }

    private record Period(String id, String status) {
    }
}
