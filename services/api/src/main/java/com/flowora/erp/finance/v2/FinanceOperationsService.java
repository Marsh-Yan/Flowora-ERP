package com.flowora.erp.finance.v2;

import com.flowora.erp.finance.v2.FinancePostingPolicy.PostingLine;
import com.flowora.erp.finance.v2.FinanceV2Dtos.BudgetCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.BudgetExecutionView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.CloseCheckView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.FinanceDashboard;
import com.flowora.erp.finance.v2.FinanceV2Dtos.PeriodCloseView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.ReconciliationCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.ReconciliationView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.RevaluationCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.RevaluationView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.StatementImport;
import com.flowora.erp.finance.v2.FinanceV2Dtos.StatementLineView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.TrialBalanceRow;
import com.flowora.erp.identity.FloworaPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.flowora.erp.finance.v2.FinanceLedgerService.conflict;
import static com.flowora.erp.finance.v2.FinanceLedgerService.notFound;

@Service
public class FinanceOperationsService {
    private final JdbcTemplate jdbc;
    private final FinanceLedgerService ledger;

    public FinanceOperationsService(JdbcTemplate jdbc, FinanceLedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Transactional
    public PeriodCloseView closePeriod(FloworaPrincipal actor, String periodId, String reason, String requestId) {
        Period period = periodForUpdate(actor.organizationId(), periodId);
        if (!"OPEN".equals(period.status())) throw conflict("PERIOD_NOT_OPEN", Map.of("status", period.status()));
        List<CloseCheckView> checks = closeChecks(actor.organizationId(), period);
        for (CloseCheckView check : checks) {
            jdbc.update("""
                    INSERT INTO flowora_period_close_check
                    (id,organization_id,period_id,check_code,result_status,result_count,details_json,checked_by)
                    VALUES (?,?,?,?,?,?,JSON_OBJECT('details',?),?)
                    """, UUID.randomUUID().toString(), actor.organizationId(), periodId, check.code(),
                    check.status(), check.count(), check.details(), actor.userId());
        }
        int failed = (int) checks.stream().filter(item -> "FAILED".equals(item.status())).count();
        if (failed > 0) return new PeriodCloseView(periodId, "OPEN", failed, checks);
        jdbc.update("UPDATE flowora_accounting_period SET status='CLOSED',version_no=version_no+1 WHERE id=? AND organization_id=?",
                periodId, actor.organizationId());
        statusEvent(actor, periodId, "OPEN", "CLOSED", reason, requestId);
        return new PeriodCloseView(periodId, "CLOSED", 0, checks);
    }

    @Transactional
    public PeriodCloseView reopenPeriod(FloworaPrincipal actor, String periodId, String reason, String requestId) {
        Period period = periodForUpdate(actor.organizationId(), periodId);
        if (!"CLOSED".equals(period.status())) throw conflict("PERIOD_NOT_CLOSED", Map.of("status", period.status()));
        jdbc.update("UPDATE flowora_accounting_period SET status='OPEN',version_no=version_no+1 WHERE id=? AND organization_id=?",
                periodId, actor.organizationId());
        statusEvent(actor, periodId, "CLOSED", "OPEN", reason, requestId);
        return new PeriodCloseView(periodId, "OPEN", 0, List.of());
    }

    public List<FinanceV2Dtos.BankAccountView> bankAccounts(String organizationId) {
        return jdbc.query("""
                SELECT id,code,name,currency_code FROM flowora_bank_account
                WHERE organization_id=? AND active=TRUE ORDER BY code,id
                """, (rs, row) -> new FinanceV2Dtos.BankAccountView(rs.getString("id"),
                rs.getString("code"), rs.getString("name"), rs.getString("currency_code")), organizationId);
    }

    @Transactional
    public List<StatementLineView> importStatements(FloworaPrincipal actor, StatementImport body) {
        requireBank(actor.organizationId(), body.bankAccountId());
        String batch = UUID.randomUUID().toString();
        List<String> ids = new ArrayList<>();
        for (var line : body.lines()) {
            String id = UUID.randomUUID().toString();
            int changed = jdbc.update("""
                    INSERT IGNORE INTO flowora_bank_statement_line
                    (id,organization_id,bank_account_id,transaction_date,value_date,amount,currency_code,
                     external_reference,counterparty,description,import_batch_id,imported_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, actor.organizationId(), body.bankAccountId(), line.transactionDate(), line.valueDate(),
                    line.amount(), line.currencyCode().toUpperCase(), line.externalReference().trim(),
                    clean(line.counterparty()), clean(line.description()), batch, actor.userId());
            if (changed == 1) ids.add(id);
        }
        return ids.stream().map(id -> statement(actor.organizationId(), id)).toList();
    }

    public List<StatementLineView> statements(String organizationId, String bankAccountId, String status) {
        String state = clean(status);
        return jdbc.query("""
                SELECT id,bank_account_id,transaction_date,value_date,amount,currency_code,external_reference,
                       counterparty,description,reconciliation_status,import_batch_id
                FROM flowora_bank_statement_line
                WHERE organization_id=? AND (?='' OR bank_account_id=?) AND (?='' OR reconciliation_status=?)
                ORDER BY transaction_date DESC,created_at DESC
                """, (rs, row) -> statementRow(rs), organizationId, clean(bankAccountId), clean(bankAccountId), state, state);
    }

    @Transactional
    public ReconciliationView reconcile(FloworaPrincipal actor, String requestId, ReconciliationCreate body) {
        requireBank(actor.organizationId(), body.bankAccountId());
        List<String> existing = jdbc.query("SELECT id FROM flowora_bank_reconciliation WHERE organization_id=? AND request_id=?",
                (rs, row) -> rs.getString(1), actor.organizationId(), required(requestId));
        if (!existing.isEmpty()) return reconciliation(actor.organizationId(), existing.getFirst());
        BigDecimal statementTotal = BigDecimal.ZERO;
        BigDecimal paymentTotal = BigDecimal.ZERO;
        Set<String> statementIds = new HashSet<>();
        Map<String, BigDecimal> paymentMatches = new HashMap<>();
        for (var link : body.links()) {
            if (!statementIds.add(link.statementLineId())) {
                throw conflict("RECONCILIATION_DUPLICATE_STATEMENT", Map.of("statementLineId", link.statementLineId()));
            }
            StatementLineView statement = statementForUpdate(actor.organizationId(), link.statementLineId());
            PaymentBasis payment = paymentForUpdate(actor.organizationId(), link.paymentId());
            if (!body.bankAccountId().equals(statement.bankAccountId()) || !body.bankAccountId().equals(payment.bankAccountId())) {
                throw conflict("RECONCILIATION_BANK_MISMATCH", Map.of());
            }
            if (!"UNMATCHED".equals(statement.reconciliationStatus()) || !"POSTED".equals(payment.status())) {
                throw conflict("RECONCILIATION_STATE_CONFLICT", Map.of());
            }
            if (!statement.currencyCode().equals(payment.currencyCode())) {
                throw conflict("RECONCILIATION_CURRENCY_MISMATCH", Map.of());
            }
            boolean incoming = Set.of("RECEIPT", "SUPPLIER_REFUND").contains(payment.paymentType());
            if (statement.amount().signum() != (incoming ? 1 : -1))
                throw conflict("RECONCILIATION_DIRECTION_MISMATCH", Map.of());
            BigDecimal statementAmount = statement.amount().abs();
            if (link.matchedAmount().compareTo(statementAmount) != 0) {
                throw conflict("RECONCILIATION_STATEMENT_AMOUNT_MISMATCH", Map.of("statementAmount", statementAmount));
            }
            BigDecimal requested = paymentMatches.merge(payment.id(), link.matchedAmount(), BigDecimal::add);
            BigDecimal alreadyMatched = scalar("""
                    SELECT COALESCE(SUM(l.matched_amount),0) FROM flowora_bank_reconciliation_link l
                    JOIN flowora_bank_reconciliation r ON r.id=l.reconciliation_id
                    WHERE l.organization_id=? AND l.payment_id=? AND r.status='CONFIRMED'
                    """, actor.organizationId(), payment.id());
            if (alreadyMatched.add(requested).compareTo(payment.amount()) > 0) {
                throw conflict("RECONCILIATION_PAYMENT_AMOUNT_EXCEEDED", Map.of("paymentRemaining", payment.amount().subtract(alreadyMatched)));
            }
            statementTotal = statementTotal.add(statementAmount);
            paymentTotal = paymentTotal.add(link.matchedAmount());
        }
        BigDecimal difference = statementTotal.subtract(paymentTotal).setScale(4, RoundingMode.HALF_UP);
        if (difference.signum() != 0) throw conflict("RECONCILIATION_NOT_BALANCED", Map.of("difference", difference));
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO flowora_bank_reconciliation
                (id,organization_id,number,bank_account_id,status,total_statement_amount,total_payment_amount,
                 difference_amount,confirmed_by,request_id)
                VALUES (?,?,?,?,'CONFIRMED',?,?,?,?,?)
                """, id, actor.organizationId(), "BR-" + System.currentTimeMillis(), body.bankAccountId(),
                statementTotal, paymentTotal, difference, actor.userId(), required(requestId));
        for (var link : body.links()) {
            jdbc.update("""
                    INSERT INTO flowora_bank_reconciliation_link
                    (id,organization_id,reconciliation_id,statement_line_id,payment_id,matched_amount)
                    VALUES (?,?,?,?,?,?)
                    """, UUID.randomUUID().toString(), actor.organizationId(), id, link.statementLineId(),
                    link.paymentId(), link.matchedAmount());
            jdbc.update("UPDATE flowora_bank_statement_line SET reconciliation_status='MATCHED' WHERE id=? AND organization_id=?",
                    link.statementLineId(), actor.organizationId());
        }
        return reconciliation(actor.organizationId(), id);
    }

    @Transactional
    public ReconciliationView reverseReconciliation(FloworaPrincipal actor, String id, String reason) {
        ReconciliationView view = reconciliationForUpdate(actor.organizationId(), id);
        if (!"CONFIRMED".equals(view.status())) throw conflict("RECONCILIATION_NOT_CONFIRMED", Map.of());
        jdbc.update("""
                UPDATE flowora_bank_reconciliation SET status='REVERSED',reversed_by=?,reversed_at=CURRENT_TIMESTAMP,
                reversal_reason=?,version_no=version_no+1 WHERE id=? AND organization_id=?
                """, actor.userId(), reason.trim(), id, actor.organizationId());
        jdbc.update("""
                UPDATE flowora_bank_statement_line s JOIN flowora_bank_reconciliation_link l ON l.statement_line_id=s.id
                SET s.reconciliation_status='UNMATCHED' WHERE l.reconciliation_id=? AND l.organization_id=?
                """, id, actor.organizationId());
        return reconciliation(actor.organizationId(), id);
    }

    public List<ReconciliationView> reconciliations(String organizationId, String bankAccountId) {
        List<String> ids = jdbc.query("""
                SELECT id FROM flowora_bank_reconciliation
                WHERE organization_id=? AND (?='' OR bank_account_id=?) ORDER BY confirmed_at DESC
                """, (rs, row) -> rs.getString(1), organizationId, clean(bankAccountId), clean(bankAccountId));
        return ids.stream().map(id -> reconciliation(organizationId, id)).toList();
    }

    @Transactional
    public String createBudget(FloworaPrincipal actor, BudgetCreate body) {
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no_value),0)+1 FROM flowora_budget_version WHERE organization_id=? AND fiscal_year=?",
                Integer.class, actor.organizationId(), body.fiscalYear());
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO flowora_budget_version
                (id,organization_id,name,fiscal_year,version_no_value,status,control_policy,approved_by,approved_at,created_by)
                VALUES (?,?,?,?,?,'APPROVED',?,?,CURRENT_TIMESTAMP,?)
                """, id, actor.organizationId(), body.name().trim(), body.fiscalYear(), next,
                body.controlPolicy().toUpperCase(), actor.userId(), actor.userId());
        for (var line : body.lines()) {
            jdbc.update("""
                    INSERT INTO flowora_budget_line_v2
                    (id,organization_id,budget_version_id,month,account_code,department_id,project_id,amount)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID().toString(), actor.organizationId(), id, line.month(), line.accountCode(),
                    nullable(line.departmentId()), nullable(line.projectId()), line.amount());
        }
        return id;
    }

    public List<BudgetExecutionView> budgetExecution(String organizationId, int fiscalYear) {
        return jdbc.query("""
                SELECT b.account_code,b.project_id,SUM(b.amount) budget,
                       COALESCE((SELECT SUM(l.base_debit-l.base_credit) FROM flowora_journal_line l
                         JOIN flowora_journal_entry e ON e.id=l.journal_entry_id
                         WHERE l.organization_id=b.organization_id AND l.account_code=b.account_code
                           AND (b.project_id IS NULL OR l.project_id=b.project_id)
                           AND YEAR(e.accounting_date)=v.fiscal_year AND e.status IN ('POSTED','REVERSED')),0) actual,
                       0 committed
                FROM flowora_budget_line_v2 b JOIN flowora_budget_version v ON v.id=b.budget_version_id
                WHERE b.organization_id=? AND v.fiscal_year=? AND v.status='APPROVED'
                GROUP BY b.account_code,b.project_id,v.fiscal_year,b.organization_id
                """, (rs, row) -> {
            BigDecimal budget = rs.getBigDecimal("budget");
            BigDecimal actual = rs.getBigDecimal("actual");
            BigDecimal committed = rs.getBigDecimal("committed");
            return new BudgetExecutionView(rs.getString("account_code"), rs.getString("project_id"), budget,
                    actual, committed, budget.subtract(actual).subtract(committed));
        }, organizationId, fiscalYear);
    }

    @Transactional
    public RevaluationView revalue(FloworaPrincipal actor, String requestId, RevaluationCreate body) {
        String key = required(requestId);
        // Serialize valuation runs for this organization, including different request keys.
        jdbc.query("SELECT organization_id FROM flowora_finance_setting WHERE organization_id=? FOR UPDATE", (rs, row) -> rs.getString(1), actor.organizationId());
        List<String> existing = jdbc.query("SELECT id FROM flowora_currency_revaluation WHERE organization_id=? AND request_id=?",
                (rs, row) -> rs.getString(1), actor.organizationId(), key);
        if (!existing.isEmpty()) return revaluation(actor.organizationId(), existing.getFirst());
        String base = ledger.baseCurrency(actor.organizationId());
        if (base.equalsIgnoreCase(body.currencyCode())) throw conflict("BASE_CURRENCY_REVALUATION_NOT_REQUIRED", Map.of());
        if (body.reversalDate() != null && body.reversalDate().isBefore(body.accountingDate())) throw conflict("INVALID_REVERSAL_DATE", Map.of());
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flowora_currency_revaluation r JOIN flowora_journal_entry e ON e.id=r.journal_entry_id
                WHERE r.organization_id=? AND r.currency_code=? AND e.status='POSTED'
                """, Integer.class, actor.organizationId(), body.currencyCode().toUpperCase());
        if (active > 0) throw conflict("ACTIVE_REVALUATION_REQUIRES_REVERSAL", Map.of());
        ledger.requireOpenAccountingPeriod(actor.organizationId(), body.accountingDate());
        String id = UUID.randomUUID().toString();
        List<OpenBalance> balances = jdbc.query("""
                SELECT id,party_type,party_id,project_id,(total_amount-allocated_amount-credited_amount) open_amount,
                       exchange_rate FROM flowora_finance_invoice
                WHERE organization_id=? AND status='POSTED' AND settlement_status<>'PAID' AND currency_code=?
                  AND document_type IN ('SALES_INVOICE','SUPPLIER_INVOICE')
                FOR UPDATE
                """, (rs, row) -> new OpenBalance(rs.getString("id"), rs.getString("party_type"),
                rs.getString("party_id"), rs.getString("project_id"), rs.getBigDecimal("open_amount"),
                rs.getBigDecimal("exchange_rate")), actor.organizationId(), body.currencyCode().toUpperCase());
        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        List<RevaluationLine> lines = new ArrayList<>();
        List<PostingLine> postings = new ArrayList<>();
        for (OpenBalance item : balances) {
            BigDecimal oldBase = FinancePostingPolicy.base(item.openAmount(), item.oldRate());
            BigDecimal newBase = FinancePostingPolicy.base(item.openAmount(), body.rate());
            BigDecimal difference = newBase.subtract(oldBase).setScale(4, RoundingMode.HALF_UP);
            boolean receivable = "CUSTOMER".equals(item.partyType());
            boolean isGain = receivable ? difference.signum() > 0 : difference.signum() < 0;
            BigDecimal transactionDifference = difference.abs();
            if (isGain) gain = gain.add(difference.abs()); else loss = loss.add(difference.abs());
            if (transactionDifference.signum() > 0) {
                String control = ledger.mapping(actor.organizationId(), receivable ? "RECEIVABLE" : "PAYABLE");
                String fx = ledger.mapping(actor.organizationId(), isGain ? "FX_GAIN" : "FX_LOSS");
                if (isGain) {
                    postings.add(PostingLine.debit(control, "Foreign balance adjustment", transactionDifference,
                            item.projectId(), item.partyType(), item.partyId(), item.id()));
                    postings.add(PostingLine.credit(fx, "Unrealized exchange gain", transactionDifference,
                            item.projectId(), item.partyType(), item.partyId(), item.id()));
                } else {
                    postings.add(PostingLine.debit(fx, "Unrealized exchange loss", transactionDifference,
                            item.projectId(), item.partyType(), item.partyId(), item.id()));
                    postings.add(PostingLine.credit(control, "Foreign balance adjustment", transactionDifference,
                            item.projectId(), item.partyType(), item.partyId(), item.id()));
                }
            }
            lines.add(new RevaluationLine(item, oldBase, newBase, difference));
        }
        String journalId = null;
        if (!postings.isEmpty()) {
            journalId = ledger.post(actor.organizationId(), actor.userId(), "CURRENCY_REVALUATION", id, null,
                    body.accountingDate(), body.accountingDate(), body.accountingDate(), base,
                    base, BigDecimal.ONE, "Currency revaluation", "revaluation:" + key, postings).id();
        }
        jdbc.update("""
                INSERT INTO flowora_currency_revaluation
                (id,organization_id,number,accounting_date,currency_code,rate,total_gain,total_loss,journal_entry_id,
                 reversal_date,created_by,request_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, actor.organizationId(), "RV-" + System.currentTimeMillis(), body.accountingDate(),
                body.currencyCode().toUpperCase(), body.rate(), gain, loss, journalId, body.reversalDate(),
                actor.userId(), required(requestId));
        for (RevaluationLine line : lines) {
            jdbc.update("""
                    INSERT INTO flowora_currency_revaluation_line
                    (id,organization_id,revaluation_id,source_type,source_id,original_base_balance,
                     revalued_base_balance,difference_amount,project_id)
                    VALUES (?,?,?,'INVOICE',?,?,?,?,?)
                    """, UUID.randomUUID().toString(), actor.organizationId(), id, line.balance().id(),
                    line.oldBase(), line.newBase(), line.difference(), line.balance().projectId());
        }
        return revaluation(actor.organizationId(), id);
    }

    @Transactional
    public FinanceV2Dtos.JournalView reverseRevaluation(FloworaPrincipal actor, String id, LocalDate accountingDate, String reason, String requestId) {
        jdbc.query("SELECT organization_id FROM flowora_finance_setting WHERE organization_id=? FOR UPDATE", (rs, row) -> rs.getString(1), actor.organizationId());
        RevaluationView original = revaluation(actor.organizationId(), id);
        if (accountingDate.isBefore(original.accountingDate())) throw conflict("INVALID_REVERSAL_DATE", Map.of());
        if (original.journalEntryId() == null) throw conflict("REVALUATION_HAS_NO_JOURNAL", Map.of());
        List<String> replay = jdbc.query("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND request_id=? AND reversal_of_id=?",
                (rs, row) -> rs.getString(1), actor.organizationId(), required(requestId), original.journalEntryId());
        if (!replay.isEmpty()) return ledger.journal(actor.organizationId(), replay.getFirst());
        return ledger.reverse(actor.organizationId(), actor.userId(), original.journalEntryId(), accountingDate, reason, required(requestId));
    }

    public FinanceDashboard dashboard(String organizationId, LocalDate from, LocalDate to) {
        BigDecimal receivables = scalar("SELECT COALESCE(SUM(base_total_amount-(allocated_amount+credited_amount)*exchange_rate),0) FROM flowora_finance_invoice WHERE organization_id=? AND party_type='CUSTOMER' AND document_type='SALES_INVOICE' AND status='POSTED'", organizationId);
        BigDecimal payables = scalar("SELECT COALESCE(SUM(base_total_amount-(allocated_amount+credited_amount)*exchange_rate),0) FROM flowora_finance_invoice WHERE organization_id=? AND party_type='SUPPLIER' AND document_type='SUPPLIER_INVOICE' AND status='POSTED'", organizationId);
        List<TrialBalanceRow> trial = ledger.trialBalance(organizationId, from, to);
        BigDecimal debit = trial.stream().map(TrialBalanceRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = trial.stream().map(TrialBalanceRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cash = balance(trial, ledger.mapping(organizationId, "CASH"));
        BigDecimal revenue = accountTypeTotal(organizationId, trial, "REVENUE").negate();
        BigDecimal expense = accountTypeTotal(organizationId, trial, "EXPENSE");
        Integer unmatched = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_bank_statement_line WHERE organization_id=? AND reconciliation_status='UNMATCHED'", Integer.class, organizationId);
        Integer exceptions = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_finance_invoice WHERE organization_id=? AND match_status='EXCEPTION'", Integer.class, organizationId);
        return new FinanceDashboard(receivables, payables, cash, revenue, expense, revenue.subtract(expense),
                debit, credit, unmatched == null ? 0 : unmatched, exceptions == null ? 0 : exceptions);
    }

    private BigDecimal accountTypeTotal(String organizationId, List<TrialBalanceRow> trial, String type) {
        Set<String> codes = new HashSet<>(jdbc.query("SELECT code FROM flowora_account WHERE organization_id=? AND account_type=?",
                (rs, row) -> rs.getString(1), organizationId, type));
        return trial.stream().filter(row -> codes.contains(row.accountCode())).map(TrialBalanceRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<CloseCheckView> closeChecks(String organizationId, Period period) {
        int drafts = count("SELECT COUNT(*) FROM flowora_finance_invoice WHERE organization_id=? AND status='DRAFT' AND accounting_date BETWEEN ? AND ?", organizationId, period.start(), period.end());
        int exceptions = count("SELECT COUNT(*) FROM flowora_finance_invoice WHERE organization_id=? AND match_status='EXCEPTION' AND accounting_date BETWEEN ? AND ?", organizationId, period.start(), period.end());
        int unmatched = count("SELECT COUNT(*) FROM flowora_bank_statement_line WHERE organization_id=? AND reconciliation_status='UNMATCHED' AND transaction_date BETWEEN ? AND ?", organizationId, period.start(), period.end());
        List<TrialBalanceRow> trial = ledger.trialBalance(organizationId, period.start(), period.end());
        BigDecimal debit = trial.stream().map(TrialBalanceRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = trial.stream().map(TrialBalanceRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return List.of(check("UNPOSTED_INVOICES", drafts), check("MATCH_EXCEPTIONS", exceptions),
                check("UNMATCHED_BANK_LINES", unmatched), new CloseCheckView("TRIAL_BALANCE",
                        debit.compareTo(credit) == 0 ? "PASSED" : "FAILED", debit.compareTo(credit) == 0 ? 0 : 1,
                        "debit=" + debit + ",credit=" + credit));
    }

    private static CloseCheckView check(String code, int count) {
        return new CloseCheckView(code, count == 0 ? "PASSED" : "FAILED", count, "count=" + count);
    }

    private void statusEvent(FloworaPrincipal actor, String periodId, String from, String to, String reason, String requestId) {
        jdbc.update("""
                INSERT INTO flowora_period_status_event
                (id,organization_id,period_id,from_status,to_status,reason,actor_user_id,request_id)
                VALUES (?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), actor.organizationId(), periodId, from, to,
                reason.trim(), actor.userId(), required(requestId));
    }

    private Period periodForUpdate(String organizationId, String id) {
        List<Period> periods = jdbc.query("""
                SELECT id,status,start_date,end_date FROM flowora_accounting_period
                WHERE organization_id=? AND id=? FOR UPDATE
                """, (rs, row) -> new Period(rs.getString("id"), rs.getString("status"),
                rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate()), organizationId, id);
        if (periods.isEmpty()) throw notFound("accountingPeriod", id);
        return periods.getFirst();
    }

    private StatementLineView statement(String organizationId, String id) {
        List<StatementLineView> values = jdbc.query("""
                SELECT id,bank_account_id,transaction_date,value_date,amount,currency_code,external_reference,
                       counterparty,description,reconciliation_status,import_batch_id
                FROM flowora_bank_statement_line WHERE organization_id=? AND id=?
                """, (rs, row) -> statementRow(rs), organizationId, id);
        if (values.isEmpty()) throw notFound("bankStatementLine", id);
        return values.getFirst();
    }

    private StatementLineView statementForUpdate(String organizationId, String id) {
        jdbc.query("SELECT id FROM flowora_bank_statement_line WHERE organization_id=? AND id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), organizationId, id);
        return statement(organizationId, id);
    }

    private static StatementLineView statementRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date valueDate = rs.getDate("value_date");
        return new StatementLineView(rs.getString("id"), rs.getString("bank_account_id"),
                rs.getDate("transaction_date").toLocalDate(), valueDate == null ? null : valueDate.toLocalDate(),
                rs.getBigDecimal("amount"), rs.getString("currency_code"), rs.getString("external_reference"),
                rs.getString("counterparty"), rs.getString("description"), rs.getString("reconciliation_status"),
                rs.getString("import_batch_id"));
    }

    private PaymentBasis paymentForUpdate(String organizationId, String id) {
        List<PaymentBasis> values = jdbc.query("""
                SELECT id,bank_account_id,status,amount,currency_code,payment_type FROM flowora_payment_v2
                WHERE organization_id=? AND id=? FOR UPDATE
                """, (rs, row) -> new PaymentBasis(rs.getString("id"), rs.getString("bank_account_id"),
                rs.getString("status"), rs.getBigDecimal("amount"), rs.getString("currency_code"), rs.getString("payment_type")), organizationId, id);
        if (values.isEmpty()) throw notFound("payment", id);
        return values.getFirst();
    }

    private ReconciliationView reconciliation(String organizationId, String id) {
        List<ReconciliationView> values = jdbc.query("""
                SELECT id,number,bank_account_id,status,total_statement_amount,total_payment_amount,difference_amount,
                       confirmed_at,reversed_at FROM flowora_bank_reconciliation WHERE organization_id=? AND id=?
                """, (rs, row) -> new ReconciliationView(rs.getString("id"), rs.getString("number"),
                rs.getString("bank_account_id"), rs.getString("status"), rs.getBigDecimal("total_statement_amount"),
                rs.getBigDecimal("total_payment_amount"), rs.getBigDecimal("difference_amount"),
                timestamp(rs.getTimestamp("confirmed_at")), timestamp(rs.getTimestamp("reversed_at")),
                reconciliationLinks(organizationId, rs.getString("id"))), organizationId, id);
        if (values.isEmpty()) throw notFound("bankReconciliation", id);
        return values.getFirst();
    }

    private List<FinanceV2Dtos.ReconciliationLinkView> reconciliationLinks(String organizationId, String id) {
        return jdbc.query("""
                SELECT statement_line_id,payment_id,matched_amount FROM flowora_bank_reconciliation_link
                WHERE organization_id=? AND reconciliation_id=? ORDER BY statement_line_id,payment_id
                """, (rs, row) -> new FinanceV2Dtos.ReconciliationLinkView(rs.getString("statement_line_id"),
                rs.getString("payment_id"), rs.getBigDecimal("matched_amount")), organizationId, id);
    }

    private ReconciliationView reconciliationForUpdate(String organizationId, String id) {
        jdbc.query("SELECT id FROM flowora_bank_reconciliation WHERE organization_id=? AND id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), organizationId, id);
        return reconciliation(organizationId, id);
    }

    public List<FinanceV2Dtos.RevaluationHistoryView> revaluations(String organizationId) {
        return jdbc.query("""
                SELECT r.id,r.number,r.accounting_date,r.currency_code,r.rate,r.total_gain,r.total_loss,
                       r.journal_entry_id,
                       CASE WHEN r.journal_entry_id IS NULL THEN 'NO_ADJUSTMENT'
                            ELSE COALESCE(e.status,'UNAVAILABLE') END journal_status
                FROM flowora_currency_revaluation r
                LEFT JOIN flowora_journal_entry e ON e.id=r.journal_entry_id AND e.organization_id=r.organization_id
                WHERE r.organization_id=? ORDER BY r.accounting_date DESC,r.created_at DESC,r.id
                """, (rs, row) -> new FinanceV2Dtos.RevaluationHistoryView(rs.getString("id"),rs.getString("number"),
                rs.getDate("accounting_date").toLocalDate(),rs.getString("currency_code"),rs.getBigDecimal("rate"),
                rs.getBigDecimal("total_gain"),rs.getBigDecimal("total_loss"),rs.getString("journal_entry_id"),
                rs.getString("journal_status")),organizationId);
    }

    private RevaluationView revaluation(String organizationId, String id) {
        List<RevaluationView> values = jdbc.query("""
                SELECT id,number,accounting_date,currency_code,rate,total_gain,total_loss,journal_entry_id,reversal_date
                FROM flowora_currency_revaluation WHERE organization_id=? AND id=?
                """, (rs, row) -> new RevaluationView(rs.getString("id"), rs.getString("number"),
                rs.getDate("accounting_date").toLocalDate(), rs.getString("currency_code"), rs.getBigDecimal("rate"),
                rs.getBigDecimal("total_gain"), rs.getBigDecimal("total_loss"), rs.getString("journal_entry_id"),
                rs.getDate("reversal_date") == null ? null : rs.getDate("reversal_date").toLocalDate()), organizationId, id);
        if (values.isEmpty()) throw notFound("currencyRevaluation", id);
        return values.getFirst();
    }

    private void requireBank(String organizationId, String bankAccountId) {
        int count = count("SELECT COUNT(*) FROM flowora_bank_account WHERE organization_id=? AND id=? AND active=TRUE", organizationId, bankAccountId);
        if (count == 0) throw notFound("bankAccount", bankAccountId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal scalar(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal balance(List<TrialBalanceRow> trial, String account) {
        return trial.stream().filter(row -> account.equals(row.accountCode())).map(TrialBalanceRow::balance)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw conflict("IDEMPOTENCY_KEY_REQUIRED", Map.of());
        return value.trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullable(String value) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record Period(String id, String status, LocalDate start, LocalDate end) {
    }

    private record PaymentBasis(String id, String bankAccountId, String status, BigDecimal amount, String currencyCode, String paymentType) {
    }

    private record OpenBalance(String id, String partyType, String partyId, String projectId,
                               BigDecimal openAmount, BigDecimal oldRate) {
    }

    private record RevaluationLine(OpenBalance balance, BigDecimal oldBase, BigDecimal newBase,
                                   BigDecimal difference) {
    }
}
