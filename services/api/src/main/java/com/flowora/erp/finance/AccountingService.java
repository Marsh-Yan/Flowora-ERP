package com.flowora.erp.finance;

import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.ResourceNotFoundException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.AccountEntity;
import com.flowora.erp.masterdata.AccountRepository;
import com.flowora.erp.sales.ReceivableEntity;
import com.flowora.erp.sales.ReceivableRepository;
import com.flowora.erp.finance.FinanceDtos.AgingRow;
import com.flowora.erp.finance.FinanceDtos.AccountingPeriodResponse;
import com.flowora.erp.finance.FinanceDtos.FinancialStatementResponse;
import com.flowora.erp.finance.FinanceDtos.JournalEntryResponse;
import com.flowora.erp.finance.FinanceDtos.JournalLineInput;
import com.flowora.erp.finance.FinanceDtos.JournalLineResponse;
import com.flowora.erp.finance.FinanceDtos.ManualJournalCreate;
import com.flowora.erp.finance.FinanceDtos.PayableResponse;
import com.flowora.erp.finance.FinanceDtos.StatementRow;
import com.flowora.erp.finance.FinanceDtos.SupplierPaymentCreate;
import com.flowora.erp.finance.FinanceDtos.SupplierPaymentResponse;
import com.flowora.erp.finance.FinanceDtos.TrialBalanceResponse;
import com.flowora.erp.finance.FinanceDtos.TrialBalanceRow;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountingService {
    private static final String CASH = "1000";
    private static final String RECEIVABLE = "1100";
    private static final String INVENTORY = "1400";
    private static final String PAYABLE = "2000";
    private static final String REVENUE = "4000";
    private static final String EXPENSE = "5000";

    private final AccountingPeriodRepository periodRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final PayableRepository payableRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final AccountRepository accountRepository;
    private final ReceivableRepository receivableRepository;

    public AccountingService(
            AccountingPeriodRepository periodRepository,
            JournalEntryRepository journalEntryRepository,
            JournalLineRepository journalLineRepository,
            PayableRepository payableRepository,
            SupplierPaymentRepository supplierPaymentRepository,
            AccountRepository accountRepository,
            ReceivableRepository receivableRepository
    ) {
        this.periodRepository = periodRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.payableRepository = payableRepository;
        this.supplierPaymentRepository = supplierPaymentRepository;
        this.accountRepository = accountRepository;
        this.receivableRepository = receivableRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<JournalEntryResponse> journals(String organizationId, LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.from(journalEntryRepository.search(organizationId, from, to, pageable).map(this::journalResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountingPeriodResponse> periods(String organizationId, Pageable pageable) {
        return PageResponse.from(periodRepository.findByOrganizationIdOrderByYearDescMonthDesc(organizationId, pageable).map(this::periodResponse));
    }

    @Transactional
    public JournalEntryResponse manual(FloworaPrincipal actor, ManualJournalCreate body) {
        JournalEntryEntity entry = postEntry(actor.organizationId(), actor.userId(), "MANUAL", UUID.randomUUID().toString(), body.entryDate(), body.memo(), body.currencyCode(), body.lines().stream().map(line -> new PostingLine(line.accountCode(), line.description(), line.debit(), line.credit())).toList());
        return journalResponse(entry);
    }

    @Transactional
    public AccountingPeriodResponse closePeriod(FloworaPrincipal actor, String periodId) {
        AccountingPeriodEntity period = periodRepository.findByIdAndOrganizationId(periodId, actor.organizationId()).orElseThrow(() -> new ResourceNotFoundException("accountingPeriod", periodId));
        period.close();
        return periodResponse(periodRepository.save(period));
    }

    @Transactional
    public void postPurchaseReceipt(String organizationId, String userId, String receiptId, String supplierId, BigDecimal amount, String currencyCode, LocalDate dueDate) {
        if (payableRepository.findByOrganizationIdAndSourceTypeAndSourceId(organizationId, "PURCHASE_RECEIPT", receiptId).isEmpty()) {
            payableRepository.save(new PayableEntity(organizationId, nextNumber("AP"), receiptId, supplierId, "PURCHASE_RECEIPT", receiptId, currencyCode, amount, dueDate));
        }
        postEntry(organizationId, userId, "PURCHASE_RECEIPT", receiptId, dueDate, "Purchase receipt " + receiptId, currencyCode, List.of(
                debit(INVENTORY, "Inventory received", amount),
                credit(PAYABLE, "Accrued payable", amount)
        ));
    }

    @Transactional
    public void postSalesOrder(String organizationId, String userId, String orderId, BigDecimal amount, String currencyCode, LocalDate entryDate) {
        postEntry(organizationId, userId, "SALES_ORDER", orderId, entryDate, "Sales order receivable " + orderId, currencyCode, List.of(
                debit(RECEIVABLE, "Customer receivable", amount),
                credit(REVENUE, "Sales revenue", amount)
        ));
    }

    @Transactional
    public void postSalesDelivery(String organizationId, String userId, String deliveryId, BigDecimal cost, String currencyCode, LocalDate entryDate) {
        if (cost.signum() == 0) return;
        postEntry(organizationId, userId, "SALES_DELIVERY", deliveryId, entryDate, "Cost of sales for delivery " + deliveryId, currencyCode, List.of(
                debit(EXPENSE, "Cost of sales", cost),
                credit(INVENTORY, "Inventory issued", cost)
        ));
    }

    @Transactional
    public void postCustomerPayment(String organizationId, String userId, String paymentId, BigDecimal amount, String currencyCode, LocalDate entryDate) {
        postEntry(organizationId, userId, "CUSTOMER_PAYMENT", paymentId, entryDate, "Customer payment " + paymentId, currencyCode, List.of(
                debit(CASH, "Cash received", amount),
                credit(RECEIVABLE, "Receivable settlement", amount)
        ));
    }

    @Transactional
    public void postInventoryDelta(String organizationId, String userId, String sourceType, String sourceId, BigDecimal quantityDelta, BigDecimal unitCost, String currencyCode, LocalDate entryDate) {
        BigDecimal value = quantityDelta.abs().multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
        if (value.signum() == 0) return;
        List<PostingLine> lines = quantityDelta.signum() > 0
                ? List.of(debit(INVENTORY, "Inventory increase", value), credit(EXPENSE, "Inventory variance", value))
                : List.of(debit(EXPENSE, "Inventory variance", value), credit(INVENTORY, "Inventory decrease", value));
        postEntry(organizationId, userId, sourceType, sourceId, entryDate, "Inventory adjustment " + sourceId, currencyCode, lines);
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(String organizationId, LocalDate from, LocalDate to) {
        List<JournalEntryEntity> entries = journalEntryRepository.inRange(organizationId, from, to).stream().filter(entry -> entry.status() == JournalEntryStatus.POSTED).toList();
        Map<String, Amount> amounts = amounts(organizationId, entries);
        List<TrialBalanceRow> rows = accountRepository.findByOrganizationIdOrderByCode(organizationId).stream()
                .map(account -> trialRow(account, amounts.getOrDefault(account.code(), new Amount())))
                .filter(row -> row.debit().signum() != 0 || row.credit().signum() != 0)
                .toList();
        BigDecimal debit = rows.stream().map(TrialBalanceRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = rows.stream().map(TrialBalanceRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TrialBalanceResponse(from, to, rows, debit, credit);
    }

    @Transactional(readOnly = true)
    public FinancialStatementResponse incomeStatement(String organizationId, LocalDate from, LocalDate to) {
        List<TrialBalanceRow> rows = trialBalance(organizationId, from, to).rows();
        List<StatementRow> statementRows = rows.stream().filter(row -> "REVENUE".equals(row.accountType()) || "EXPENSE".equals(row.accountType())).map(row -> new StatementRow(row.accountCode(), row.accountName(), row.accountType(), "REVENUE".equals(row.accountType()) ? row.credit().subtract(row.debit()) : row.debit().subtract(row.credit()))).toList();
        BigDecimal total = statementRows.stream().map(StatementRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinancialStatementResponse(from, to, statementRows, total);
    }

    @Transactional(readOnly = true)
    public FinancialStatementResponse balanceSheet(String organizationId, LocalDate from, LocalDate to) {
        List<TrialBalanceRow> rows = trialBalance(organizationId, from, to).rows();
        List<StatementRow> statementRows = rows.stream().filter(row -> List.of("ASSET", "LIABILITY", "EQUITY").contains(row.accountType())).map(row -> new StatementRow(row.accountCode(), row.accountName(), row.accountType(), "ASSET".equals(row.accountType()) ? row.debit().subtract(row.credit()) : row.credit().subtract(row.debit()))).toList();
        BigDecimal total = statementRows.stream().map(StatementRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinancialStatementResponse(from, to, statementRows, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<PayableResponse> payables(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(payableRepository.search(organizationId, clean(query), pageable).map(this::payableResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierPaymentResponse> supplierPayments(String organizationId, String payableId, Pageable pageable) {
        return PageResponse.from(supplierPaymentRepository.findByOrganizationIdAndPayableIdOrderByPaymentDateDesc(organizationId, clean(payableId), pageable).map(this::supplierPaymentResponse));
    }

    @Transactional
    public SupplierPaymentResponse paySupplier(FloworaPrincipal actor, SupplierPaymentCreate body) {
        PayableEntity payable = payableRepository.findByIdAndOrganizationId(body.payableId(), actor.organizationId()).orElseThrow(() -> new ResourceNotFoundException("payable", body.payableId()));
        payable.recordPayment(body.amount());
        SupplierPaymentEntity payment = supplierPaymentRepository.save(new SupplierPaymentEntity(actor.organizationId(), nextNumber("SP"), payable.id(), payable.supplierId(), body.amount(), payable.currencyCode(), body.method(), body.paymentDate(), clean(body.reference()), actor.userId()));
        payableRepository.save(payable);
        postEntry(actor.organizationId(), actor.userId(), "SUPPLIER_PAYMENT", payment.id(), body.paymentDate(), "Supplier payment " + payment.number(), payable.currencyCode(), List.of(
                debit(PAYABLE, "Payable settlement", body.amount()),
                credit(CASH, "Cash paid", body.amount())
        ));
        return supplierPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<AgingRow> receivableAging(String organizationId, LocalDate asOf) {
        return receivableRepository.search(organizationId, "", Pageable.unpaged()).getContent().stream().filter(item -> item.remainingAmount().signum() > 0).map(item -> aging("RECEIVABLE", item.number(), item.customerId(), item.dueDate(), item.remainingAmount(), asOf)).sorted(Comparator.comparing(AgingRow::dueDate)).toList();
    }

    @Transactional(readOnly = true)
    public List<AgingRow> payableAging(String organizationId, LocalDate asOf) {
        return payableRepository.search(organizationId, "", Pageable.unpaged()).getContent().stream().filter(item -> item.remainingAmount().signum() > 0).map(item -> aging("PAYABLE", item.number(), item.supplierId(), item.dueDate(), item.remainingAmount(), asOf)).sorted(Comparator.comparing(AgingRow::dueDate)).toList();
    }

    private JournalEntryEntity postEntry(String organizationId, String userId, String sourceType, String sourceId, LocalDate entryDate, String memo, String currencyCode, List<PostingLine> lines) {
        var existing = journalEntryRepository.findByOrganizationIdAndSourceTypeAndSourceId(organizationId, sourceType, sourceId);
        if (existing.isPresent()) return existing.get();
        if (lines == null || lines.size() < 2) throw new IllegalArgumentException("A journal entry requires at least two lines");
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (PostingLine line : lines) {
            if (line.debit().signum() < 0 || line.credit().signum() < 0 || (line.debit().signum() > 0 && line.credit().signum() > 0) || (line.debit().signum() == 0 && line.credit().signum() == 0)) throw new IllegalArgumentException("Each journal line must contain either a debit or a credit");
            requirePostingAccount(organizationId, line.accountCode());
            totalDebit = totalDebit.add(line.debit());
            totalCredit = totalCredit.add(line.credit());
        }
        if (totalDebit.signum() <= 0 || totalDebit.compareTo(totalCredit) != 0) throw new IllegalArgumentException("Journal entry debits and credits must balance");
        AccountingPeriodEntity period = period(entryDate, organizationId);
        if (period.status() == AccountingPeriodStatus.CLOSED) throw new IllegalStateException("Accounting period is closed");
        String normalizedCurrency = clean(currencyCode).toUpperCase();
        JournalEntryEntity entry = journalEntryRepository.save(new JournalEntryEntity(organizationId, nextNumber("JE"), period.id(), entryDate, sourceType, sourceId, clean(memo), normalizedCurrency, totalDebit, totalCredit));
        for (int index = 0; index < lines.size(); index++) {
            PostingLine line = lines.get(index);
            journalLineRepository.save(new JournalLineEntity(organizationId, entry.id(), index + 1, line.accountCode(), clean(line.description()), line.debit(), line.credit(), normalizedCurrency));
        }
        return entry;
    }

    private AccountingPeriodEntity period(LocalDate date, String organizationId) {
        YearMonth yearMonth = YearMonth.from(date);
        return periodRepository.findByOrganizationIdAndYearAndMonth(organizationId, yearMonth.getYear(), yearMonth.getMonthValue()).orElseGet(() -> periodRepository.save(new AccountingPeriodEntity(organizationId, yearMonth.getYear(), yearMonth.getMonthValue(), yearMonth.atDay(1), yearMonth.atEndOfMonth())));
    }

    private Map<String, Amount> amounts(String organizationId, List<JournalEntryEntity> entries) {
        Map<String, Amount> result = new HashMap<>();
        if (entries.isEmpty()) return result;
        for (JournalLineEntity line : journalLineRepository.byJournalEntryIds(organizationId, entries.stream().map(JournalEntryEntity::id).toList())) {
            Amount amount = result.computeIfAbsent(line.accountCode(), ignored -> new Amount());
            amount.debit = amount.debit.add(line.debit());
            amount.credit = amount.credit.add(line.credit());
        }
        return result;
    }

    private TrialBalanceRow trialRow(AccountEntity account, Amount amount) {
        BigDecimal balance = List.of("ASSET", "EXPENSE").contains(account.type().name()) ? amount.debit.subtract(amount.credit) : amount.credit.subtract(amount.debit);
        return new TrialBalanceRow(account.code(), account.name(), account.type().name(), amount.debit, amount.credit, balance);
    }

    private void requirePostingAccount(String organizationId, String code) {
        accountRepository.findByOrganizationIdAndCode(organizationId, clean(code)).filter(account -> account.active() && account.postingAllowed()).orElseThrow(() -> new ResourceNotFoundException("postingAccount", code));
    }

    private JournalEntryResponse journalResponse(JournalEntryEntity entry) {
        List<JournalLineResponse> lines = journalLineRepository.findByOrganizationIdAndJournalEntryIdOrderByLineNo(entry.organizationId(), entry.id()).stream().map(line -> new JournalLineResponse(line.lineNo(), line.accountCode(), line.description(), line.debit(), line.credit(), line.currencyCode())).toList();
        return new JournalEntryResponse(entry.id(), entry.number(), entry.periodId(), entry.entryDate(), entry.sourceType(), entry.sourceId(), entry.memo(), entry.currencyCode(), entry.totalDebit(), entry.totalCredit(), entry.status(), lines);
    }

    private AccountingPeriodResponse periodResponse(AccountingPeriodEntity entity) { return new AccountingPeriodResponse(entity.id(), entity.year(), entity.month(), entity.startDate(), entity.endDate(), entity.status()); }
    private PayableResponse payableResponse(PayableEntity entity) { return new PayableResponse(entity.id(), entity.number(), entity.purchaseReceiptId(), entity.supplierId(), entity.sourceType(), entity.currencyCode(), entity.totalAmount(), entity.paidAmount(), entity.remainingAmount(), entity.status(), entity.dueDate()); }
    private SupplierPaymentResponse supplierPaymentResponse(SupplierPaymentEntity entity) { return new SupplierPaymentResponse(entity.id(), entity.number(), entity.payableId(), entity.supplierId(), entity.amount(), entity.currencyCode(), entity.method(), entity.paymentDate(), entity.reference()); }
    private AgingRow aging(String type, String number, String partyId, LocalDate dueDate, BigDecimal amount, LocalDate asOf) { long days = Math.max(0, ChronoUnit.DAYS.between(dueDate, asOf)); return new AgingRow(type, number, partyId, dueDate, amount, days, days == 0 ? "CURRENT" : days <= 30 ? "1_30" : days <= 60 ? "31_60" : days <= 90 ? "61_90" : "90_PLUS"); }
    private PostingLine debit(String accountCode, String description, BigDecimal amount) { return new PostingLine(accountCode, description, amount, BigDecimal.ZERO); }
    private PostingLine credit(String accountCode, String description, BigDecimal amount) { return new PostingLine(accountCode, description, BigDecimal.ZERO, amount); }
    private String nextNumber(String prefix) { return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private record PostingLine(String accountCode, String description, BigDecimal debit, BigDecimal credit) { }
    private static final class Amount { private BigDecimal debit = BigDecimal.ZERO; private BigDecimal credit = BigDecimal.ZERO; }
}
