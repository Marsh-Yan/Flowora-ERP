package com.flowora.erp.finance.v2;

import com.flowora.erp.finance.v2.FinancePostingPolicy.PostingLine;
import com.flowora.erp.finance.v2.FinanceV2Dtos.AllocationCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.AllocationReverse;
import com.flowora.erp.finance.v2.FinanceV2Dtos.AllocationView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceLineCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceLineView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceSourceCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceSourceView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.PaymentCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.PaymentView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.RefundSourceRecovery;
import com.flowora.erp.finance.v2.FinanceV2Dtos.RefundSourceRecoveryView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.JournalView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.RefundCorrection;
import com.flowora.erp.finance.v2.FinanceV2Dtos.PaymentCorrection;
import com.flowora.erp.finance.v2.FinanceV2Dtos.PaymentCorrectionView;
import com.flowora.erp.finance.v2.FinanceV2Dtos.RefundCorrectionView;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.trade.v2.TradeAmountPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.flowora.erp.finance.v2.FinanceLedgerService.conflict;
import static com.flowora.erp.finance.v2.FinanceLedgerService.notFound;

@Service
public class FinanceDocumentService {
    private static final Set<String> TYPES = Set.of("SALES_INVOICE", "SUPPLIER_INVOICE", "CUSTOMER_CREDIT", "SUPPLIER_CREDIT");
    private static final Set<String> PAYMENT_TYPES = Set.of("RECEIPT", "PAYMENT", "CUSTOMER_REFUND", "SUPPLIER_REFUND");

    private final JdbcTemplate jdbc;
    private final FinanceLedgerService ledger;

    public FinanceDocumentService(JdbcTemplate jdbc, FinanceLedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    public List<FinanceV2Dtos.StockInvoiceSourceView> stockInvoiceSources(String organizationId) {
        return jdbc.query("""
                SELECT b.*,GREATEST(0,b.quantity-COALESCE((
                    SELECT SUM(s.quantity*(il.quantity-il.credited_quantity)/il.quantity)
                    FROM flowora_finance_invoice_source s
                    JOIN flowora_finance_invoice_line il ON il.id=s.invoice_line_id AND il.organization_id=s.organization_id
                    JOIN flowora_finance_invoice i ON i.id=il.invoice_id AND i.organization_id=s.organization_id
                    WHERE s.organization_id=? AND s.source_type=b.source_type AND s.source_line_id=b.source_line_id
                      AND i.document_type=b.document_type AND i.status='POSTED'),0)) remaining_quantity
                FROM (
                    SELECT 'SUPPLIER_INVOICE' document_type,'PURCHASE_RECEIPT_LINE' source_type,
                           h.id source_id,l.id source_line_id,h.number source_number,o.number order_number,
                           p.id party_id,p.name party_name,l.item_id,COALESCE(item.name,l.item_id) description,
                           o.currency_code,l.accepted_quantity quantity,ol.unit_price,ol.discount_rate,ol.tax_rate
                    FROM flowora_purchase_receipt_line l
                    JOIN flowora_purchase_receipt h ON h.id=l.purchase_receipt_id AND h.organization_id=l.organization_id
                    JOIN flowora_purchase_order o ON o.id=h.purchase_order_id AND o.organization_id=l.organization_id
                    JOIN flowora_purchase_order_line ol ON ol.id=l.purchase_order_line_id AND ol.purchase_order_id=o.id AND ol.organization_id=l.organization_id
                    JOIN flowora_supplier p ON p.id=o.supplier_id AND p.organization_id=l.organization_id AND p.active=TRUE
                    LEFT JOIN flowora_item item ON item.id=l.item_id AND item.organization_id=l.organization_id
                    WHERE l.organization_id=? AND h.status='POSTED' AND l.accepted_quantity>0
                    UNION ALL
                    SELECT 'SALES_INVOICE','SALES_DELIVERY_LINE',h.id,l.id,h.number,o.number,
                           p.id,p.name,l.item_id,COALESCE(item.name,l.item_id),o.currency_code,
                           l.quantity,ol.unit_price,ol.discount_rate,ol.tax_rate
                    FROM flowora_sales_delivery_line l
                    JOIN flowora_sales_delivery h ON h.id=l.delivery_id AND h.organization_id=l.organization_id
                    JOIN flowora_sales_order o ON o.id=h.sales_order_id AND o.organization_id=l.organization_id
                    JOIN flowora_sales_order_line ol ON ol.id=l.sales_order_line_id AND ol.sales_order_id=o.id AND ol.organization_id=l.organization_id
                    JOIN flowora_customer p ON p.id=o.customer_id AND p.organization_id=l.organization_id AND p.active=TRUE
                    LEFT JOIN flowora_item item ON item.id=l.item_id AND item.organization_id=l.organization_id
                    WHERE l.organization_id=? AND h.status='POSTED' AND l.quantity>0
                ) b ORDER BY b.source_number,b.source_line_id
                """, (rs,row)->new FinanceV2Dtos.StockInvoiceSourceView(rs.getString("document_type"),rs.getString("source_type"),
                rs.getString("source_id"),rs.getString("source_line_id"),rs.getString("source_number"),rs.getString("order_number"),
                rs.getString("party_id"),rs.getString("party_name"),rs.getString("item_id"),rs.getString("description"),
                rs.getString("currency_code"),rs.getBigDecimal("quantity"),rs.getBigDecimal("remaining_quantity"),
                rs.getBigDecimal("unit_price"),rs.getBigDecimal("discount_rate"),rs.getBigDecimal("tax_rate")),
                organizationId,organizationId,organizationId);
    }

    @Transactional
    public InvoiceView createInvoice(FloworaPrincipal actor, String requestId, InvoiceCreate body) {
        String type = upper(body.documentType());
        if (!TYPES.contains(type)) throw conflict("INVALID_INVOICE_TYPE", Map.of("documentType", type));
        String key = requiredKey(requestId);
        List<String> existing = jdbc.query("SELECT id FROM flowora_finance_invoice WHERE organization_id=? AND request_id=?",
                (rs, row) -> rs.getString(1), actor.organizationId(), key);
        if (!existing.isEmpty()) return invoice(actor.organizationId(), existing.getFirst());
        boolean sales = type.equals("SALES_INVOICE") || type.equals("CUSTOMER_CREDIT");
        boolean credit = type.endsWith("CREDIT");
        String partyType = sales ? "CUSTOMER" : "SUPPLIER";
        requireParty(actor.organizationId(), partyType, body.partyId());
        Original original = credit ? requireOriginal(actor.organizationId(), body.originalInvoiceId(), partyType) : null;
        if (credit && !original.partyId().equals(body.partyId())) throw conflict("CREDIT_PARTY_MISMATCH", Map.of());
        String baseCurrency = ledger.baseCurrency(actor.organizationId());
        String id = UUID.randomUUID().toString();
        List<CalculatedLine> lines = calculateLines(actor.organizationId(), type, body.lines(), body.exchangeRate());
        BigDecimal net = sum(lines.stream().map(CalculatedLine::net).toList());
        BigDecimal tax = sum(lines.stream().map(CalculatedLine::tax).toList());
        BigDecimal total = sum(lines.stream().map(CalculatedLine::total).toList());
        BigDecimal baseTotal = FinancePostingPolicy.base(total, body.exchangeRate());
        String matchStatus = type.equals("SUPPLIER_INVOICE") ? matchStatus(actor.organizationId(), lines) : "NOT_REQUIRED";
        String number = prefix(type) + "-" + System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO flowora_finance_invoice
                (id,organization_id,number,document_type,party_type,party_id,original_invoice_id,project_id,status,
                 settlement_status,credit_status,business_date,accounting_date,due_date,exchange_rate_date,
                 currency_code,base_currency_code,exchange_rate,net_amount,tax_amount,total_amount,base_total_amount,
                 match_status,created_by,request_id)
                VALUES (?,?,?,?,?,?,?,?,?,'UNPAID','NONE',?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, actor.organizationId(), number, type, partyType, body.partyId(),
                credit ? original.id() : null, nullable(body.projectId()), "DRAFT", body.businessDate(),
                body.accountingDate(), body.dueDate(), body.exchangeRateDate(), upper(body.currencyCode()),
                baseCurrency, body.exchangeRate(), net, tax, total, baseTotal, matchStatus, actor.userId(), key);
        int lineNo = 0;
        for (CalculatedLine line : lines) {
            lineNo++;
            String lineId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO flowora_finance_invoice_line
                    (id,organization_id,invoice_id,line_no,item_id,description,quantity,unit_price,discount_rate,tax_rate,
                     net_amount,tax_amount,total_amount,base_total_amount,revenue_expense_account_code,project_id,
                     match_quantity_variance,match_price_variance_rate,match_tax_variance)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, lineId, actor.organizationId(), id, lineNo, nullable(line.input().itemId()),
                    line.input().description().trim(), line.input().quantity(), line.input().unitPrice(),
                    line.input().discountRate(), line.input().taxRate(), line.net(), line.tax(), line.total(),
                    FinancePostingPolicy.base(line.total(), body.exchangeRate()), nullable(line.input().accountCode()),
                    nullable(line.input().projectId()), line.quantityVariance(), line.priceVarianceRate(), line.taxVariance());
            List<InvoiceSourceCreate> sources = line.input().sources() == null ? List.of() : line.input().sources();
            if (credit && sources.isEmpty()) {
                throw conflict("CREDIT_SOURCE_REQUIRED", Map.of("line", lineNo));
            }
            for (InvoiceSourceCreate source : sources) {
                validateSource(actor.organizationId(), type, body.partyId(), original, source);
                jdbc.update("""
                        INSERT INTO flowora_finance_invoice_source
                        (id,organization_id,invoice_line_id,source_type,source_id,source_line_id,quantity,amount)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, UUID.randomUUID().toString(), actor.organizationId(), lineId, upper(source.sourceType()),
                        source.sourceId(), nullable(source.sourceLineId()), source.quantity(), source.amount());
            }
        }
        return invoice(actor.organizationId(), id);
    }

    @Transactional
    public InvoiceView approveMatchException(FloworaPrincipal actor, String invoiceId, String reason) {
        InvoiceView invoice = invoiceForUpdate(actor.organizationId(), invoiceId);
        if (!"SUPPLIER_INVOICE".equals(invoice.documentType()) || !"EXCEPTION".equals(invoice.matchStatus())) {
            throw conflict("INVOICE_HAS_NO_MATCH_EXCEPTION", Map.of("invoiceId", invoiceId));
        }
        jdbc.update("""
                UPDATE flowora_finance_invoice SET match_status='APPROVED_EXCEPTION',match_exception_approved_by=?,
                match_exception_reason=?,version_no=version_no+1 WHERE id=? AND organization_id=?
                """, actor.userId(), reason.trim(), invoiceId, actor.organizationId());
        return invoice(actor.organizationId(), invoiceId);
    }

    @Transactional
    public InvoiceView postInvoice(FloworaPrincipal actor, String invoiceId, long version) {
        ledger.lockOrganizationFinance(actor.organizationId());
        InvoiceView invoice = invoiceForUpdate(actor.organizationId(), invoiceId);
        if (invoice.version() != version) throw conflict("OPTIMISTIC_LOCK_CONFLICT", Map.of("expectedVersion", version));
        if (!"DRAFT".equals(invoice.status())) throw conflict("INVOICE_NOT_DRAFT", Map.of("status", invoice.status()));
        if ("EXCEPTION".equals(invoice.matchStatus())) throw conflict("MATCH_EXCEPTION_NOT_APPROVED", Map.of());
        requireSourceCapacity(actor.organizationId(), invoice);
        List<PostingLine> postings = invoicePostings(actor.organizationId(), invoice);
        ledger.post(actor.organizationId(), actor.userId(), invoice.documentType(), invoice.id(), null,
                invoice.businessDate(), invoice.accountingDate(), invoice.exchangeRateDate(), invoice.currencyCode(),
                invoice.baseCurrencyCode(), invoice.exchangeRate(), invoice.documentType() + " " + invoice.number(),
                "invoice-post:" + invoice.id(), postings);
        int changed = jdbc.update("""
                UPDATE flowora_finance_invoice SET status='POSTED',posted_at=CURRENT_TIMESTAMP,posted_by=?,version_no=version_no+1
                WHERE id=? AND organization_id=? AND status='DRAFT' AND version_no=?
                """, actor.userId(), invoiceId, actor.organizationId(), version);
        if (changed != 1) throw conflict("OPTIMISTIC_LOCK_CONFLICT", Map.of());
        if (invoice.documentType().endsWith("CREDIT")) applyCredit(actor.organizationId(), invoice);
        return invoice(actor.organizationId(), invoiceId);
    }

    public List<InvoiceView> invoices(String organizationId, String documentType, String status) {
        String type = clean(documentType);
        String state = clean(status);
        List<String> ids = jdbc.query("""
                SELECT id FROM flowora_finance_invoice
                WHERE organization_id=? AND (?='' OR document_type=?) AND (?='' OR status=?)
                ORDER BY accounting_date DESC,created_at DESC
                """, (rs, row) -> rs.getString(1), organizationId, type, type, state, state);
        return ids.stream().map(id -> invoice(organizationId, id)).toList();
    }

    public InvoiceView invoice(String organizationId, String id) {
        List<InvoiceView> values = jdbc.query("""
                SELECT id,number,document_type,party_type,party_id,original_invoice_id,project_id,status,
                       settlement_status,credit_status,business_date,accounting_date,due_date,exchange_rate_date,
                       currency_code,base_currency_code,exchange_rate,net_amount,tax_amount,total_amount,
                       base_total_amount,allocated_amount,credited_amount,match_status,match_exception_approved_by,match_exception_reason,posted_at,version_no
                FROM flowora_finance_invoice WHERE organization_id=? AND id=?
                """, (rs, row) -> new InvoiceView(rs.getString("id"), rs.getString("number"),
                rs.getString("document_type"), rs.getString("party_type"), rs.getString("party_id"),
                rs.getString("original_invoice_id"), rs.getString("project_id"), rs.getString("status"),
                rs.getString("settlement_status"), rs.getString("credit_status"),
                rs.getDate("business_date").toLocalDate(), rs.getDate("accounting_date").toLocalDate(),
                rs.getDate("due_date").toLocalDate(), rs.getDate("exchange_rate_date").toLocalDate(),
                rs.getString("currency_code"), rs.getString("base_currency_code"), rs.getBigDecimal("exchange_rate"),
                rs.getBigDecimal("net_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("base_total_amount"), rs.getBigDecimal("allocated_amount"),
                rs.getBigDecimal("credited_amount"), rs.getString("match_status"), rs.getString("match_exception_approved_by"), rs.getString("match_exception_reason"),
                timestamp(rs.getTimestamp("posted_at")), rs.getLong("version_no"), List.of()), organizationId, id);
        if (values.isEmpty()) throw notFound("invoice", id);
        InvoiceView head = values.getFirst();
        List<InvoiceLineView> lines = jdbc.query("""
                SELECT id,line_no,item_id,description,quantity,unit_price,discount_rate,tax_rate,net_amount,tax_amount,
                       total_amount,base_total_amount,revenue_expense_account_code,project_id,credited_quantity,
                       match_quantity_variance,match_price_variance_rate,match_tax_variance
                FROM flowora_finance_invoice_line WHERE organization_id=? AND invoice_id=? ORDER BY line_no
                """, (rs, row) -> new InvoiceLineView(rs.getString("id"), rs.getInt("line_no"),
                rs.getString("item_id"), rs.getString("description"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("discount_rate"), rs.getBigDecimal("tax_rate"),
                rs.getBigDecimal("net_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("base_total_amount"), rs.getString("revenue_expense_account_code"),
                rs.getString("project_id"), rs.getBigDecimal("credited_quantity"),
                reversalAccount(organizationId, head, rs.getString("id")),
                rs.getBigDecimal("match_quantity_variance"), rs.getBigDecimal("match_price_variance_rate"),
                rs.getBigDecimal("match_tax_variance"), sources(organizationId, rs.getString("id"))), organizationId, id);
        return new InvoiceView(head.id(), head.number(), head.documentType(), head.partyType(), head.partyId(),
                head.originalInvoiceId(), head.projectId(), head.status(), head.settlementStatus(), head.creditStatus(),
                head.businessDate(), head.accountingDate(), head.dueDate(), head.exchangeRateDate(), head.currencyCode(),
                head.baseCurrencyCode(), head.exchangeRate(), head.netAmount(), head.taxAmount(), head.totalAmount(),
                head.baseTotalAmount(), head.allocatedAmount(), head.creditedAmount(), head.matchStatus(), head.matchExceptionApprovedBy(), head.matchExceptionReason(), head.postedAt(),
                head.version(), lines);
    }

    private String reversalAccount(String organizationId, InvoiceView head, String lineId) {
        if (!"POSTED".equals(head.status()) || !Set.of("SALES_INVOICE", "SUPPLIER_INVOICE").contains(head.documentType())) return null;
        List<String> accounts = jdbc.query("""
                SELECT DISTINCT l.account_code FROM flowora_journal_line l
                JOIN flowora_journal_entry e ON e.id=l.journal_entry_id AND e.organization_id=l.organization_id
                WHERE e.organization_id=? AND e.source_type=? AND e.source_id=? AND e.status='POSTED'
                  AND l.source_line_id=?
                """, (rs, row) -> rs.getString(1), organizationId, head.documentType(), head.id(), lineId);
        return accounts.size() == 1 ? accounts.getFirst() : null;
    }

    @Transactional
    public PaymentView createPayment(FloworaPrincipal actor, String requestId, PaymentCreate body) {
        ledger.lockOrganizationFinance(actor.organizationId());
        String type = upper(body.paymentType());
        if (!PAYMENT_TYPES.contains(type)) throw conflict("INVALID_PAYMENT_TYPE", Map.of("paymentType", type));
        if (type.endsWith("REFUND") && body.amount().stripTrailingZeros().scale() > 4)
            throw conflict("REFUND_AMOUNT_PRECISION", Map.of());
        String key = requiredKey(requestId);
        List<String> existing = jdbc.query("SELECT id FROM flowora_payment_v2 WHERE organization_id=? AND request_id=?",
                (rs, row) -> rs.getString(1), actor.organizationId(), key);
        if (!existing.isEmpty()) {
            PaymentView replay = payment(actor.organizationId(), existing.getFirst());
            if ((type.endsWith("REFUND") || replay.paymentType().endsWith("REFUND"))
                    && (!type.equals(replay.paymentType()) || !Objects.equals(body.originalPaymentId(), replay.originalPaymentId())
                    || !body.partyId().equals(replay.partyId()) || !Objects.equals(nullable(body.bankAccountId()), replay.bankAccountId())
                    || !body.businessDate().equals(replay.businessDate()) || !body.accountingDate().equals(replay.accountingDate())
                    || !body.exchangeRateDate().equals(replay.exchangeRateDate()) || !upper(body.currencyCode()).equals(replay.currencyCode())
                    || body.exchangeRate().compareTo(replay.exchangeRate()) != 0 || body.amount().compareTo(replay.amount()) != 0
                    || !Objects.equals(clean(body.reference()), replay.reference()))) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", Map.of());
            }
            return replay;
        }
        String partyType = type.equals("RECEIPT") || type.equals("CUSTOMER_REFUND") ? "CUSTOMER" : "SUPPLIER";
        requireParty(actor.organizationId(), partyType, body.partyId());
        if (body.bankAccountId() != null) requireBank(actor.organizationId(), body.bankAccountId(), body.currencyCode());
        String baseCurrency = ledger.baseCurrency(actor.organizationId());
        BigDecimal sourceBase = null;
        if (type.endsWith("REFUND")) {
            requireRefundBasis(actor.organizationId(), type, body.partyId(), body.originalPaymentId(),
                    body.currencyCode(), baseCurrency, body.exchangeRate(), body.exchangeRateDate(), body.accountingDate());
            requireRefundCapacity(actor.organizationId(), body.originalPaymentId(), null, body.amount());
            sourceBase = refundSourceBase(actor.organizationId(), body.originalPaymentId(), body.amount());
        } else if (nullable(body.originalPaymentId()) != null) {
            throw conflict("PAYMENT_SOURCE_NOT_APPLICABLE", Map.of());
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO flowora_payment_v2
                (id,organization_id,number,payment_type,party_type,party_id,bank_account_id,status,allocation_status,
                 business_date,accounting_date,exchange_rate_date,currency_code,base_currency_code,exchange_rate,
                 amount,base_amount,created_by,request_id,reference,original_payment_id)
                VALUES (?,?,?,?,?,?,?,'DRAFT','UNALLOCATED',?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, actor.organizationId(), paymentPrefix(type) + "-" + System.currentTimeMillis(), type,
                partyType, body.partyId(), nullable(body.bankAccountId()), body.businessDate(), body.accountingDate(),
                body.exchangeRateDate(), upper(body.currencyCode()), baseCurrency, body.exchangeRate(), body.amount(),
                FinancePostingPolicy.base(body.amount(), body.exchangeRate()), actor.userId(), key, clean(body.reference()), nullable(body.originalPaymentId()));
        if (sourceBase != null) jdbc.update("UPDATE flowora_payment_v2 SET refund_source_base_amount=? WHERE organization_id=? AND id=?", sourceBase, actor.organizationId(), id);
        return payment(actor.organizationId(), id);
    }

    @Transactional
    public PaymentView postPayment(FloworaPrincipal actor, String paymentId, long version) {
        ledger.lockOrganizationFinance(actor.organizationId());
        PaymentView payment = paymentForUpdate(actor.organizationId(), paymentId);
        if (!"DRAFT".equals(payment.status()) || payment.version() != version) {
            throw conflict("PAYMENT_STATE_CONFLICT", Map.of("status", payment.status()));
        }
        if (payment.paymentType().endsWith("REFUND")) {
            requireRefundBasis(actor.organizationId(), payment.paymentType(), payment.partyId(), payment.originalPaymentId(),
                    payment.currencyCode(), payment.baseCurrencyCode(), payment.exchangeRate(), payment.exchangeRateDate(), payment.accountingDate());
            requireRefundCapacity(actor.organizationId(), payment.originalPaymentId(), payment.id(), payment.amount());
        }
        String cash = payment.bankAccountId() == null ? ledger.mapping(actor.organizationId(), "CASH") : bankLedger(actor.organizationId(), payment.bankAccountId());
        String control = ledger.mapping(actor.organizationId(), "CUSTOMER".equals(payment.partyType()) ? "RECEIVABLE" : "PAYABLE");
        boolean inbound = payment.paymentType().equals("RECEIPT") || payment.paymentType().equals("SUPPLIER_REFUND");
        List<PostingLine> lines = inbound
                ? List.of(PostingLine.debit(cash, "Cash received", payment.amount(), null, payment.partyType(), payment.partyId(), null),
                PostingLine.credit(control, "Settlement control", payment.amount(), null, payment.partyType(), payment.partyId(), null))
                : List.of(PostingLine.debit(control, "Settlement control", payment.amount(), null, payment.partyType(), payment.partyId(), null),
                PostingLine.credit(cash, "Cash paid", payment.amount(), null, payment.partyType(), payment.partyId(), null));
        ledger.post(actor.organizationId(), actor.userId(), payment.paymentType(), payment.id(), null,
                payment.businessDate(), payment.accountingDate(), payment.exchangeRateDate(), payment.currencyCode(),
                payment.baseCurrencyCode(), payment.exchangeRate(), payment.paymentType() + " " + payment.number(),
                "payment-post:" + payment.id(), lines);
        if (payment.realizedExchangeDifference() != null && payment.realizedExchangeDifference().signum() != 0) {
            String exchangeJournal = postRefundExchange(actor, payment, control).id();
            jdbc.update("UPDATE flowora_payment_v2 SET refund_exchange_journal_id=? WHERE organization_id=? AND id=?", exchangeJournal, actor.organizationId(), payment.id());
        }
        jdbc.update("""
                UPDATE flowora_payment_v2 SET status='POSTED',posted_at=CURRENT_TIMESTAMP,posted_by=?,version_no=version_no+1
                WHERE id=? AND organization_id=? AND status='DRAFT' AND version_no=?
                """, actor.userId(), paymentId, actor.organizationId(), version);
        return payment(actor.organizationId(), paymentId);
    }

    @Transactional
    public AllocationView allocate(FloworaPrincipal actor, String paymentId, AllocationCreate body) {
        ledger.lockOrganizationFinance(actor.organizationId());
        PaymentView payment = paymentForUpdate(actor.organizationId(), paymentId);
        InvoiceView invoice = invoiceForUpdate(actor.organizationId(), body.invoiceId());
        ledger.requireNoActiveRevaluation(actor.organizationId(), invoice.id());
        if (!"POSTED".equals(payment.status()) || !"POSTED".equals(invoice.status())) throw conflict("ALLOCATION_REQUIRES_POSTED_DOCUMENTS", Map.of());
        if (!payment.partyType().equals(invoice.partyType()) || !payment.partyId().equals(invoice.partyId())) throw conflict("ALLOCATION_PARTY_MISMATCH", Map.of());
        if (!payment.currencyCode().equals(invoice.currencyCode())) throw conflict("ALLOCATION_CURRENCY_MISMATCH", Map.of());
        String expectedInvoice = switch (payment.paymentType()) {
            case "RECEIPT" -> "SALES_INVOICE";
            case "PAYMENT" -> "SUPPLIER_INVOICE";
            default -> "";
        };
        if (!expectedInvoice.equals(invoice.documentType())) throw conflict("ALLOCATION_DOCUMENT_TYPE_MISMATCH", Map.of());
        if (body.amount().compareTo(refundRemaining(actor.organizationId(), payment)) > 0)
            throw conflict("ALLOCATION_CONSUMES_REFUND_RESERVATION", Map.of());
        BigDecimal paymentRemaining = payment.amount().subtract(payment.allocatedAmount());
        BigDecimal invoiceRemaining = invoice.totalAmount().subtract(invoice.allocatedAmount()).subtract(invoice.creditedAmount());
        if (body.amount().compareTo(paymentRemaining) > 0 || body.amount().compareTo(invoiceRemaining) > 0) {
            throw conflict("ALLOCATION_EXCEEDS_REMAINING", Map.of("paymentRemaining", paymentRemaining, "invoiceRemaining", invoiceRemaining));
        }
        String id = UUID.randomUUID().toString();
        BigDecimal base = FinancePostingPolicy.base(body.amount(), payment.exchangeRate());
        BigDecimal invoiceBase = FinancePostingPolicy.base(body.amount(), invoice.exchangeRate());
        BigDecimal difference = base.subtract(invoiceBase).setScale(4, RoundingMode.HALF_UP);
        jdbc.update("""
                INSERT INTO flowora_payment_allocation
                (id,organization_id,payment_id,invoice_id,amount,base_amount,realized_exchange_difference,status,allocated_by)
                VALUES (?,?,?,?,?,?,?,'ACTIVE',?)
                """, id, actor.organizationId(), paymentId, invoice.id(), body.amount(), base, difference, actor.userId());
        jdbc.update("UPDATE flowora_payment_v2 SET allocated_amount=allocated_amount+?,version_no=version_no+1 WHERE id=?", body.amount(), paymentId);
        jdbc.update("UPDATE flowora_finance_invoice SET allocated_amount=allocated_amount+?,version_no=version_no+1 WHERE id=?", body.amount(), invoice.id());
        refreshSettlement(paymentId, invoice.id());
        if (difference.signum() != 0) postExchangeDifference(actor, invoice, id, difference);
        return allocation(actor.organizationId(), id);
    }

    @Transactional
    public AllocationView reverseAllocation(FloworaPrincipal actor, String allocationId, String requestId, AllocationReverse body) {
        ledger.lockOrganizationFinance(actor.organizationId());
        String key = requiredKey(requestId);
        AllocationView reference = allocation(actor.organizationId(), allocationId);
        paymentForUpdate(actor.organizationId(), reference.paymentId());
        InvoiceView invoice = invoiceForUpdate(actor.organizationId(), reference.invoiceId());
        AllocationView allocation = allocationForUpdate(actor.organizationId(), allocationId);
        List<String> replay = jdbc.query("SELECT allocation_id FROM flowora_allocation_reversal WHERE organization_id=? AND request_id=?",
                (rs, row) -> rs.getString(1), actor.organizationId(), key);
        if (!replay.isEmpty()) {
            if (!replay.getFirst().equals(allocationId)) throw conflict("IDEMPOTENCY_KEY_REUSED", Map.of());
            return allocation;
        }
        if (!"ACTIVE".equals(allocation.status())) throw conflict("ALLOCATION_ALREADY_REVERSED", Map.of());
        ledger.requireOpenAccountingPeriod(actor.organizationId(), invoice.accountingDate());
        if (allocation.realizedExchangeDifference().signum() != 0) {
            List<String> journals = jdbc.query("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND source_type='REALIZED_EXCHANGE' AND source_id=?",
                    (rs, row) -> rs.getString(1), actor.organizationId(), allocationId);
            if (journals.size() != 1) throw conflict("ALLOCATION_FX_JOURNAL_MISSING", Map.of("allocationId", allocationId));
            ledger.reverse(actor.organizationId(), actor.userId(), journals.getFirst(), invoice.accountingDate(),
                    body.reason().trim(), "allocation-fx-reverse:" + allocationId);
        }
        jdbc.update("""
                INSERT INTO flowora_allocation_reversal(id,organization_id,allocation_id,reason,actor_user_id,request_id)
                VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), actor.organizationId(), allocationId, body.reason().trim(),
                actor.userId(), key);
        jdbc.update("UPDATE flowora_payment_allocation SET status='REVERSED',reversed_at=CURRENT_TIMESTAMP,version_no=version_no+1 WHERE id=?", allocationId);
        jdbc.update("UPDATE flowora_payment_v2 SET allocated_amount=allocated_amount-?,version_no=version_no+1 WHERE id=?", allocation.amount(), allocation.paymentId());
        jdbc.update("UPDATE flowora_finance_invoice SET allocated_amount=allocated_amount-?,version_no=version_no+1 WHERE id=?", allocation.amount(), allocation.invoiceId());
        refreshSettlement(allocation.paymentId(), allocation.invoiceId());
        return allocation(actor.organizationId(), allocationId);
    }

    public List<PaymentView> payments(String organizationId, String status) {
        String state = clean(status);
        List<String> ids = jdbc.query("""
                SELECT id FROM flowora_payment_v2 WHERE organization_id=? AND (?='' OR status=?)
                ORDER BY accounting_date DESC,created_at DESC
                """, (rs, row) -> rs.getString(1), organizationId, state, state);
        return ids.stream().map(id -> payment(organizationId, id)).toList();
    }

    public PaymentView payment(String organizationId, String id) {
        List<PaymentView> values = jdbc.query("""
                SELECT id,number,payment_type,party_type,party_id,bank_account_id,status,allocation_status,business_date,
                       accounting_date,exchange_rate_date,currency_code,base_currency_code,exchange_rate,amount,
                       base_amount,allocated_amount,reference,reversal_of_id,posted_at,version_no,original_payment_id,refund_source_base_amount,refund_exchange_journal_id
                FROM flowora_payment_v2 WHERE organization_id=? AND id=?
                """, (rs, row) -> new PaymentView(rs.getString("id"), rs.getString("number"),
                rs.getString("payment_type"), rs.getString("party_type"), rs.getString("party_id"),
                rs.getString("bank_account_id"), rs.getString("status"), rs.getString("allocation_status"),
                rs.getDate("business_date").toLocalDate(), rs.getDate("accounting_date").toLocalDate(),
                rs.getDate("exchange_rate_date").toLocalDate(), rs.getString("currency_code"),
                rs.getString("base_currency_code"), rs.getBigDecimal("exchange_rate"), rs.getBigDecimal("amount"),
                rs.getBigDecimal("base_amount"), rs.getBigDecimal("allocated_amount"), rs.getString("reference"),
                rs.getString("reversal_of_id"), timestamp(rs.getTimestamp("posted_at")), rs.getLong("version_no"), rs.getString("original_payment_id"),
                rs.getBigDecimal("refund_source_base_amount"), rs.getBigDecimal("refund_source_base_amount") == null ? null : rs.getBigDecimal("base_amount").subtract(rs.getBigDecimal("refund_source_base_amount")), rs.getString("refund_exchange_journal_id"),
                refundSourceRecovery(organizationId, rs.getString("id")), refundCorrection(organizationId, rs.getString("id")), paymentCorrection(organizationId, rs.getString("id")), allocations(organizationId, rs.getString("id"))), organizationId, id);
        if (values.isEmpty()) throw notFound("payment", id);
        return values.getFirst();
    }

    public RefundSourceRecoveryView refundSourceRecovery(String organizationId, String paymentId) {
        var rows = jdbc.query("""
                SELECT original_payment_id,refund_journal_entry_id,original_journal_entry_id,reason,actor_user_id,created_at
                FROM flowora_refund_source_recovery WHERE organization_id=? AND refund_id=?
                """, (rs, row) -> new RefundSourceRecoveryView(rs.getString("original_payment_id"),
                rs.getString("refund_journal_entry_id"), rs.getString("original_journal_entry_id"), rs.getString("reason"),
                rs.getString("actor_user_id"), timestamp(rs.getTimestamp("created_at"))), organizationId, paymentId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional
    public PaymentView recoverRefundSource(FloworaPrincipal actor, String paymentId, String requestId, RefundSourceRecovery body) {
        ledger.lockOrganizationFinance(actor.organizationId());
        String key = requiredKey(requestId), reason = body.reason().trim();
        PaymentView refund = paymentForUpdate(actor.organizationId(), paymentId);
        var replay = jdbc.query("""
                SELECT refund_id,original_payment_id,reason,refund_version,original_version
                FROM flowora_refund_source_recovery WHERE organization_id=? AND request_id=?
                """, (rs, row) -> paymentId.equals(rs.getString("refund_id"))
                && body.originalPaymentId().equals(rs.getString("original_payment_id")) && reason.equals(rs.getString("reason"))
                && body.version() == rs.getLong("refund_version") && body.originalVersion() == rs.getLong("original_version"), actor.organizationId(), key);
        if (!replay.isEmpty()) {
            if (!replay.getFirst()) throw conflict("IDEMPOTENCY_KEY_REUSED", Map.of());
            return refund;
        }
        if (!Set.of("CUSTOMER_REFUND", "SUPPLIER_REFUND").contains(refund.paymentType()) || !"POSTED".equals(refund.status())
                || refund.originalPaymentId() != null || refund.version() != body.version() || refund.sourceRecovery() != null)
            throw conflict("REFUND_RECOVERY_STATE_CONFLICT", Map.of());
        if (reason.isEmpty() || reason.length() > 500) throw conflict("REFUND_RECOVERY_REASON_REQUIRED", Map.of());
        if (refund.amount().signum() <= 0 || refund.baseAmount().signum() < 0 || refund.allocatedAmount().signum() != 0
                || (refund.refundSourceBaseAmount() != null && refund.refundSourceBaseAmount().compareTo(refund.baseAmount()) != 0)
                || refund.allocations().stream().anyMatch(a -> "ACTIVE".equals(a.status()))
                || refund.refundExchangeJournalId() != null || jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND source_id=? AND source_type='REFUND_EXCHANGE'", Integer.class, actor.organizationId(), paymentId) != 0)
            throw conflict("REFUND_RECOVERY_REQUIRES_RECONCILIATION", Map.of());
        PaymentView original = paymentForUpdate(actor.organizationId(), body.originalPaymentId());
        String expected = "CUSTOMER_REFUND".equals(refund.paymentType()) ? "RECEIPT" : "PAYMENT";
        if (!expected.equals(original.paymentType()) || !"POSTED".equals(original.status()) || original.version() != body.originalVersion()
                || !("CUSTOMER_REFUND".equals(refund.paymentType()) ? "CUSTOMER" : "SUPPLIER").equals(refund.partyType())
                || original.originalPaymentId() != null || original.reversalOfId() != null || refund.reversalOfId() != null
                || !refund.partyType().equals(original.partyType()) || !refund.partyId().equals(original.partyId())
                || !refund.currencyCode().equals(original.currencyCode()) || !refund.baseCurrencyCode().equals(original.baseCurrencyCode())
                || !refund.baseCurrencyCode().equals(ledger.baseCurrency(actor.organizationId()))
                || refund.exchangeRate().compareTo(original.exchangeRate()) != 0
                || refund.exchangeRateDate().isBefore(original.exchangeRateDate()) || refund.exchangeRateDate().isAfter(refund.accountingDate())
                || refund.accountingDate().isBefore(original.accountingDate())
                || (refund.currencyCode().equals(refund.baseCurrencyCode()) && refund.exchangeRate().compareTo(BigDecimal.ONE) != 0))
            throw conflict("REFUND_RECOVERY_SOURCE_MISMATCH", Map.of());
        BigDecimal activeAllocatedAmount = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM flowora_payment_allocation WHERE organization_id=? AND payment_id=? AND status='ACTIVE'", BigDecimal.class, actor.organizationId(), original.id());
        if (original.amount().signum() <= 0 || original.allocatedAmount().signum() < 0 || original.allocatedAmount().compareTo(activeAllocatedAmount) != 0)
            throw conflict("REFUND_RECOVERY_REQUIRES_RECONCILIATION", Map.of());
        requireRefundCapacity(actor.organizationId(), original.id(), null, refund.amount());
        BigDecimal used = jdbc.queryForObject("""
                SELECT COALESCE(SUM(COALESCE(refund_source_base_amount,base_amount)),0) FROM flowora_payment_v2
                WHERE organization_id=? AND original_payment_id=? AND status IN ('DRAFT','POSTED')
                  AND payment_type IN ('CUSTOMER_REFUND','SUPPLIER_REFUND')
                """, BigDecimal.class, actor.organizationId(), original.id());
        BigDecimal allocated = jdbc.queryForObject("SELECT COALESCE(SUM(base_amount),0) FROM flowora_payment_allocation WHERE organization_id=? AND payment_id=? AND status='ACTIVE'", BigDecimal.class, actor.organizationId(), original.id());
        if (refund.baseAmount().compareTo(original.baseAmount().subtract(used).subtract(allocated)) > 0)
            throw conflict("REFUND_BOOK_VALUE_CONFLICT", Map.of());
        JournalView sourceJournal = recoveryJournal(actor.organizationId(), original);
        JournalView refundJournal = recoveryJournal(actor.organizationId(), refund);
        boolean customer = "CUSTOMER".equals(refund.partyType());
        String sourceControl = sourceJournal.lines().stream().filter(l -> (customer ? l.credit() : l.debit()).signum() > 0).findFirst().orElseThrow().accountCode();
        String refundControl = refundJournal.lines().stream().filter(l -> (customer ? l.debit() : l.credit()).signum() > 0).findFirst().orElseThrow().accountCode();
        if (!sourceControl.equals(refundControl)) throw conflict("REFUND_RECOVERY_JOURNAL_MISMATCH", Map.of());
        jdbc.update("""
                INSERT INTO flowora_refund_source_recovery(id,organization_id,refund_id,original_payment_id,
                refund_journal_entry_id,original_journal_entry_id,reason,actor_user_id,refund_version,original_version,request_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), actor.organizationId(), paymentId, original.id(), refundJournal.id(), sourceJournal.id(),
                reason, actor.userId(), body.version(), body.originalVersion(), key);
        jdbc.update("UPDATE flowora_payment_v2 SET original_payment_id=?,refund_source_base_amount=?,version_no=version_no+1 WHERE organization_id=? AND id=?", original.id(), refund.baseAmount(), actor.organizationId(), paymentId);
        jdbc.update("UPDATE flowora_payment_v2 SET version_no=version_no+1 WHERE organization_id=? AND id=?", actor.organizationId(), original.id());
        return payment(actor.organizationId(), paymentId);
    }

    private JournalView recoveryJournal(String organizationId, PaymentView payment) {
        // Lock and check the current status, even if this transaction's earlier
        // metadata reads have already established a repeatable-read snapshot.
        var entries = jdbc.query("SELECT id,status FROM flowora_journal_entry WHERE organization_id=? AND source_type=? AND source_id=? FOR UPDATE",
                (rs, row) -> new String[]{rs.getString("id"),rs.getString("status")}, organizationId, payment.paymentType(), payment.id());
        if (entries.size() != 1 || !"POSTED".equals(entries.getFirst()[1])) throw conflict("REFUND_RECOVERY_JOURNAL_MISMATCH", Map.of());
        JournalView journal = ledger.journal(organizationId, entries.getFirst()[0]);
        if (!"POSTED".equals(journal.status()) || journal.reversalOfId() != null || journal.lines().size() != 2
                || !payment.currencyCode().equals(journal.currencyCode()) || !payment.baseCurrencyCode().equals(journal.baseCurrencyCode())
                || !payment.accountingDate().equals(journal.accountingDate()) || !payment.exchangeRateDate().equals(journal.exchangeRateDate())
                || payment.exchangeRate().compareTo(journal.exchangeRate()) != 0
                || payment.amount().compareTo(journal.totalDebit()) != 0 || payment.amount().compareTo(journal.totalCredit()) != 0
                || payment.baseAmount().compareTo(FinancePostingPolicy.base(payment.amount(), payment.exchangeRate())) != 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND reversal_of_id=?", Integer.class, organizationId, journal.id()) != 0)
            throw conflict("REFUND_RECOVERY_JOURNAL_MISMATCH", Map.of());
        boolean debit = false, credit = false;
        for (var line : journal.lines()) {
            if (!payment.partyId().equals(line.partyId()) || !payment.partyType().equals(line.partyType())
                    || !payment.currencyCode().equals(line.currencyCode())
                    || line.baseDebit().compareTo(FinancePostingPolicy.base(line.debit(), payment.exchangeRate())) != 0
                    || line.baseCredit().compareTo(FinancePostingPolicy.base(line.credit(), payment.exchangeRate())) != 0)
                throw conflict("REFUND_RECOVERY_JOURNAL_MISMATCH", Map.of());
            debit |= line.debit().compareTo(payment.amount()) == 0 && line.credit().signum() == 0;
            credit |= line.credit().compareTo(payment.amount()) == 0 && line.debit().signum() == 0;
        }
        if (!debit || !credit || journal.lines().getFirst().accountCode().equals(journal.lines().getLast().accountCode()))
            throw conflict("REFUND_RECOVERY_JOURNAL_MISMATCH", Map.of());
        return journal;
    }

    public PaymentCorrectionView paymentCorrection(String organizationId, String paymentId) {
        var rows = jdbc.query("""
                SELECT action,reason,actor_user_id,accounting_date,journal_entry_id,created_at
                FROM flowora_payment_correction WHERE organization_id=? AND payment_id=?
                """, (rs, row) -> new PaymentCorrectionView(rs.getString("action"), rs.getString("reason"),
                rs.getString("actor_user_id"), rs.getDate("accounting_date").toLocalDate(),
                rs.getString("journal_entry_id"), timestamp(rs.getTimestamp("created_at"))), organizationId, paymentId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional
    public PaymentView correctPayment(FloworaPrincipal actor, String paymentId, String requestId,
                                      PaymentCorrection body, boolean reverse) {
        String org = actor.organizationId(), key = requiredKey(requestId), action = reverse ? "REVERSE" : "CANCEL";
        ledger.lockOrganizationFinance(org);
        PaymentView payment = paymentForUpdate(org, paymentId);
        if (!Set.of("RECEIPT", "PAYMENT").contains(payment.paymentType()))
            throw conflict("PAYMENT_CORRECTION_TYPE_MISMATCH", Map.of());
        String reason = body.reason().trim();
        var replay = jdbc.query("""
                SELECT payment_id,action,reason,accounting_date,source_version FROM flowora_payment_correction
                WHERE organization_id=? AND request_id=? FOR UPDATE
                """, (rs, row) -> rs.getString("payment_id").equals(paymentId)
                && rs.getString("action").equals(action) && rs.getString("reason").equals(reason)
                && rs.getDate("accounting_date").toLocalDate().equals(body.accountingDate())
                && rs.getLong("source_version") == body.version(), org, key);
        if (!replay.isEmpty()) {
            if (!replay.getFirst()) throw conflict("IDEMPOTENCY_KEY_REUSED", Map.of());
            return payment;
        }
        var current = jdbc.queryForMap("SELECT status,version_no FROM flowora_payment_v2 WHERE organization_id=? AND id=? FOR UPDATE", org, paymentId);
        if (!(reverse ? "POSTED" : "DRAFT").equals(current.get("status"))
                || ((Number) current.get("version_no")).longValue() != body.version()
                || payment.version() != body.version()) throw conflict("PAYMENT_CORRECTION_STATE_CONFLICT", Map.of());
        if (reason.isEmpty() || reason.length() > 500) throw conflict("PAYMENT_CORRECTION_REASON_REQUIRED", Map.of());
        if (body.accountingDate().isBefore(payment.accountingDate())) throw conflict("PAYMENT_CORRECTION_DATE_INVALID", Map.of());
        if (payment.originalPaymentId() != null || payment.reversalOfId() != null
                || !payment.partyType().equals("RECEIPT".equals(payment.paymentType()) ? "CUSTOMER" : "SUPPLIER"))
            throw conflict("PAYMENT_CORRECTION_TYPE_MISMATCH", Map.of());
        // Locking reads deliberately inspect current dependent rows, even after an earlier snapshot.
        var allocations = jdbc.query("SELECT id FROM flowora_payment_allocation WHERE organization_id=? AND payment_id=? AND status='ACTIVE' FOR UPDATE",
                (rs,row)->rs.getString(1), org, paymentId);
        if (payment.allocatedAmount().signum() != 0 || !allocations.isEmpty()) throw conflict("PAYMENT_HAS_ALLOCATIONS", Map.of());
        var refunds = jdbc.query("""
                SELECT id FROM flowora_payment_v2 WHERE organization_id=? AND status IN ('DRAFT','POSTED')
                AND payment_type IN ('CUSTOMER_REFUND','SUPPLIER_REFUND')
                AND (original_payment_id=? OR (original_payment_id IS NULL AND party_type=? AND party_id=? AND currency_code=?)) FOR UPDATE
                """, (rs,row)->rs.getString(1), org, paymentId, payment.partyType(), payment.partyId(), payment.currencyCode());
        if (!refunds.isEmpty()) throw conflict("PAYMENT_HAS_ACTIVE_REFUNDS", Map.of());
        var matches = jdbc.query("""
                SELECT l.id FROM flowora_bank_reconciliation_link l JOIN flowora_bank_reconciliation h
                ON h.id=l.reconciliation_id AND h.organization_id=l.organization_id
                WHERE l.organization_id=? AND l.payment_id=? AND h.status='CONFIRMED' FOR UPDATE
                """, (rs,row)->rs.getString(1), org, paymentId);
        if (!matches.isEmpty()) throw conflict("PAYMENT_RECONCILIATION_MUST_BE_REVERSED", Map.of());
        String reversalId = null;
        var journals = jdbc.query("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND source_type=? AND source_id=? FOR UPDATE",
                (rs,row)->rs.getString(1), org, payment.paymentType(), paymentId);
        if (reverse) {
            if (journals.size() != 1) throw conflict("PAYMENT_JOURNAL_MISMATCH", Map.of());
            // Reuse the full amount, date, currency, party and actual-line consistency checks.
            JournalView original = recoveryJournal(org, payment);
            reversalId = ledger.reversePaymentJournal(org, actor.userId(), original.id(), body.accountingDate(), reason,
                    "payment-reverse:" + paymentId).id();
        } else if (!journals.isEmpty()) throw conflict("PAYMENT_DRAFT_HAS_JOURNAL", Map.of());
        jdbc.update("""
                INSERT INTO flowora_payment_correction(id,organization_id,payment_id,action,reason,actor_user_id,
                accounting_date,journal_entry_id,source_version,request_id) VALUES (?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), org, paymentId, action, reason, actor.userId(), body.accountingDate(), reversalId, body.version(), key);
        jdbc.update("UPDATE flowora_payment_v2 SET status=?,version_no=version_no+1 WHERE organization_id=? AND id=?",
                reverse ? "REVERSED" : "CANCELLED", org, paymentId);
        return payment(org, paymentId);
    }

    public RefundCorrectionView refundCorrection(String organizationId, String paymentId) {
        var rows = jdbc.query("""
                SELECT action,reason,actor_user_id,accounting_date,journal_entry_id,exchange_journal_entry_id,created_at
                FROM flowora_refund_correction WHERE organization_id=? AND payment_id=?
                """, (rs, row) -> new RefundCorrectionView(rs.getString("action"), rs.getString("reason"),
                rs.getString("actor_user_id"), rs.getDate("accounting_date").toLocalDate(),
                rs.getString("journal_entry_id"), rs.getString("exchange_journal_entry_id"), timestamp(rs.getTimestamp("created_at"))), organizationId, paymentId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional
    public PaymentView correctRefund(FloworaPrincipal actor, String paymentId, String requestId,
                                     RefundCorrection body, boolean reverse) {
        ledger.lockOrganizationFinance(actor.organizationId());
        String key = requiredKey(requestId), action = reverse ? "REVERSE" : "CANCEL";
        PaymentView refund = paymentForUpdate(actor.organizationId(), paymentId);
        if (!Set.of("CUSTOMER_REFUND", "SUPPLIER_REFUND").contains(refund.paymentType()))
            throw conflict("REFUND_CORRECTION_TYPE_MISMATCH", Map.of());
        String reason = body.reason().trim();
        var replay = jdbc.query("""
                SELECT payment_id,action,reason,accounting_date,source_version
                FROM flowora_refund_correction WHERE organization_id=? AND request_id=?
                """, (rs, row) -> rs.getString("payment_id").equals(paymentId)
                && rs.getString("action").equals(action) && rs.getString("reason").equals(reason)
                && rs.getDate("accounting_date").toLocalDate().equals(body.accountingDate())
                && rs.getLong("source_version") == body.version(), actor.organizationId(), key);
        if (!replay.isEmpty()) {
            if (!replay.getFirst()) throw conflict("IDEMPOTENCY_KEY_REUSED", Map.of());
            return refund;
        }
        if (!(reverse ? "POSTED" : "DRAFT").equals(refund.status()) || refund.version() != body.version())
            throw conflict("REFUND_CORRECTION_STATE_CONFLICT", Map.of());
        if (reason.isEmpty() || reason.length() > 500) throw conflict("REFUND_CORRECTION_REASON_REQUIRED", Map.of());
        if (body.accountingDate().isBefore(refund.accountingDate())) throw conflict("REFUND_CORRECTION_DATE_INVALID", Map.of());
        if (refund.allocatedAmount().signum() != 0) throw conflict("REFUND_HAS_ALLOCATIONS", Map.of());
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flowora_bank_reconciliation_link l
                JOIN flowora_bank_reconciliation h ON h.id=l.reconciliation_id AND h.organization_id=l.organization_id
                WHERE l.organization_id=? AND l.payment_id=? AND h.status='CONFIRMED'
                """, Integer.class, actor.organizationId(), paymentId);
        if (matches != null && matches > 0) throw conflict("REFUND_RECONCILIATION_MUST_BE_REVERSED", Map.of());
        String reversalJournalId = null, exchangeReversalId = null;
        var journals = jdbc.query("""
                SELECT id FROM flowora_journal_entry WHERE organization_id=? AND source_type=? AND source_id=?
                """, (rs, row) -> rs.getString(1), actor.organizationId(), refund.paymentType(), paymentId);
        if (reverse) {
            if (refund.originalPaymentId() == null) throw conflict("REFUND_SOURCE_REQUIRED", Map.of());
            if (journals.size() != 1) throw conflict("REFUND_JOURNAL_MISSING", Map.of());
            reversalJournalId = ledger.reverseRefundJournal(actor.organizationId(), actor.userId(), journals.getFirst(),
                    body.accountingDate(), reason, "refund-reverse:" + paymentId).id();
            if (refund.realizedExchangeDifference() != null && refund.realizedExchangeDifference().signum() != 0) {
                if (refund.refundExchangeJournalId() == null) throw conflict("REFUND_EXCHANGE_JOURNAL_MISSING", Map.of());
                exchangeReversalId = ledger.reverseRefundJournal(actor.organizationId(), actor.userId(), refund.refundExchangeJournalId(),
                        body.accountingDate(), reason, "refund-fx-reverse:" + paymentId).id();
            }
        } else if (!journals.isEmpty()) throw conflict("REFUND_DRAFT_HAS_JOURNAL", Map.of());
        jdbc.update("""
                INSERT INTO flowora_refund_correction
                (id,organization_id,payment_id,action,reason,actor_user_id,accounting_date,journal_entry_id,exchange_journal_entry_id,source_version,request_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), actor.organizationId(), paymentId, action, reason, actor.userId(),
                body.accountingDate(), reversalJournalId, exchangeReversalId, body.version(), key);
        jdbc.update("UPDATE flowora_payment_v2 SET status=?,version_no=version_no+1 WHERE organization_id=? AND id=?",
                reverse ? "REVERSED" : "CANCELLED", actor.organizationId(), paymentId);
        return payment(actor.organizationId(), paymentId);
    }

    // All callers hold the organization finance lock, including receipt allocation.
    private PaymentView requireRefundBasis(String organizationId, String type, String partyId, String originalId,
                                           String currency, String baseCurrency, BigDecimal rate,
                                           LocalDate rateDate, LocalDate accountingDate) {
        if (nullable(originalId) == null) throw conflict("REFUND_SOURCE_REQUIRED", Map.of());
        PaymentView original = paymentForUpdate(organizationId, originalId);
        requireNoUnlinkedRefunds(organizationId, original);
        String expected = "CUSTOMER_REFUND".equals(type) ? "RECEIPT" : "PAYMENT";
        if (!expected.equals(original.paymentType()) || !"POSTED".equals(original.status())
                || !partyId.equals(original.partyId()) || !upper(currency).equals(original.currencyCode())
                || !baseCurrency.equals(original.baseCurrencyCode())
                || rate == null || rate.signum() <= 0 || rate.stripTrailingZeros().scale() > 8
                || (upper(currency).equals(baseCurrency) && (rate.compareTo(BigDecimal.ONE) != 0 || original.exchangeRate().compareTo(BigDecimal.ONE) != 0))
                || rateDate.isBefore(original.exchangeRateDate()) || rateDate.isAfter(accountingDate)
                || accountingDate.isBefore(original.accountingDate())) {
            throw conflict("REFUND_SOURCE_MISMATCH", Map.of());
        }
        return original;
    }

    // Reserve the source's carrying value along with its transaction amount. The final
    // refund takes the exact residual, including four-decimal translation rounding.
    private BigDecimal refundSourceBase(String organizationId, String originalId, BigDecimal amount) {
        PaymentView original = paymentForUpdate(organizationId, originalId);
        BigDecimal used = jdbc.queryForObject("""
                SELECT COALESCE(SUM(COALESCE(refund_source_base_amount,base_amount)),0)
                FROM flowora_payment_v2 WHERE organization_id=? AND original_payment_id=?
                AND payment_type IN ('CUSTOMER_REFUND','SUPPLIER_REFUND') AND status IN ('DRAFT','POSTED')
                """, BigDecimal.class, organizationId, originalId);
        BigDecimal allocated = jdbc.queryForObject("""
                SELECT COALESCE(SUM(base_amount),0) FROM flowora_payment_allocation
                WHERE organization_id=? AND payment_id=? AND status='ACTIVE'
                """, BigDecimal.class, organizationId, originalId);
        BigDecimal available = original.baseAmount().subtract(used).subtract(allocated);
        BigDecimal result = amount.compareTo(refundRemaining(organizationId, original)) == 0
                ? available : FinancePostingPolicy.base(amount, original.exchangeRate());
        if (result.signum() < 0 || result.compareTo(available) > 0) throw conflict("REFUND_BOOK_VALUE_CONFLICT", Map.of());
        return result;
    }

    private FinanceV2Dtos.JournalView postRefundExchange(FloworaPrincipal actor, PaymentView refund, String control) {
        BigDecimal difference = refund.realizedExchangeDifference(), amount = difference.abs();
        boolean supplier = "SUPPLIER_REFUND".equals(refund.paymentType());
        boolean gain = supplier == (difference.signum() > 0);
        String fx = ledger.mapping(actor.organizationId(), gain ? "FX_GAIN" : "FX_LOSS");
        // Base currency only: do not divide then multiply the difference by an FX rate.
        List<PostingLine> lines = gain
                ? List.of(PostingLine.debit(control, "Refund carrying-value adjustment", amount, null, refund.partyType(), refund.partyId(), null),
                          PostingLine.credit(fx, "Refund exchange gain", amount, null, refund.partyType(), refund.partyId(), null))
                : List.of(PostingLine.debit(fx, "Refund exchange loss", amount, null, refund.partyType(), refund.partyId(), null),
                          PostingLine.credit(control, "Refund carrying-value adjustment", amount, null, refund.partyType(), refund.partyId(), null));
        return ledger.post(actor.organizationId(), actor.userId(), "REFUND_EXCHANGE", refund.id(), null,
                refund.businessDate(), refund.accountingDate(), refund.exchangeRateDate(), refund.baseCurrencyCode(),
                refund.baseCurrencyCode(), BigDecimal.ONE, "Refund exchange difference " + refund.number(), "refund-fx:" + refund.id(), lines);
    }

    private void requireNoUnlinkedRefunds(String organizationId, PaymentView original) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flowora_payment_v2
                WHERE organization_id=? AND party_type=? AND party_id=? AND currency_code=?
                  AND payment_type IN ('CUSTOMER_REFUND','SUPPLIER_REFUND') AND status IN ('DRAFT','POSTED')
                  AND original_payment_id IS NULL
                """, Integer.class, organizationId, original.partyType(), original.partyId(), original.currencyCode());
        if (count != null && count > 0) throw conflict("REFUND_HISTORY_REQUIRES_RECONCILIATION", Map.of());
    }

    private BigDecimal reservedRefunds(String organizationId, String originalId, String excludedId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM flowora_payment_v2
                WHERE organization_id=? AND original_payment_id=?
                  AND payment_type IN ('CUSTOMER_REFUND','SUPPLIER_REFUND') AND status IN ('DRAFT','POSTED')
                  AND (? IS NULL OR id<>?)
                """, BigDecimal.class, organizationId, originalId, excludedId, excludedId);
    }

    private BigDecimal refundRemaining(String organizationId, PaymentView original) {
        requireNoUnlinkedRefunds(organizationId, original);
        return original.amount().subtract(original.allocatedAmount())
                .subtract(reservedRefunds(organizationId, original.id(), null));
    }

    private void requireRefundCapacity(String organizationId, String originalId, String excludedId, BigDecimal amount) {
        PaymentView original = paymentForUpdate(organizationId, originalId);
        BigDecimal remaining = original.amount().subtract(original.allocatedAmount())
                .subtract(reservedRefunds(organizationId, originalId, excludedId));
        if (amount.compareTo(remaining) > 0) throw conflict("REFUND_EXCEEDS_REMAINING", Map.of("remaining", remaining));
    }

    private List<CalculatedLine> calculateLines(String organizationId, String type, List<InvoiceLineCreate> inputs,
                                                BigDecimal exchangeRate) {
        List<CalculatedLine> result = new ArrayList<>();
        for (InvoiceLineCreate input : inputs) {
            TradeAmountPolicy.Amounts amounts = TradeAmountPolicy.calculate(input.quantity(), input.unitPrice(),
                    input.discountRate(), input.taxRate());
            MatchVariance variance = type.equals("SUPPLIER_INVOICE") ? supplierVariance(organizationId, input, amounts) : MatchVariance.ZERO;
            result.add(new CalculatedLine(input, amounts.net(), amounts.tax(), amounts.gross(),
                    variance.quantity(), variance.priceRate(), variance.tax()));
        }
        return result;
    }

    private MatchVariance supplierVariance(String organizationId, InvoiceLineCreate line, TradeAmountPolicy.Amounts amounts) {
        List<InvoiceSourceCreate> sources = line.sources() == null ? List.of() : line.sources();
        BigDecimal quantityVariance = BigDecimal.ZERO;
        BigDecimal priceVariance = BigDecimal.ZERO;
        BigDecimal taxVariance = BigDecimal.ZERO;
        boolean matched = false;
        for (InvoiceSourceCreate source : sources) {
            if (!"PURCHASE_RECEIPT_LINE".equals(upper(source.sourceType())) || source.sourceLineId() == null) continue;
            List<ReceiptBasis> basis = jdbc.query("""
                    SELECT rl.accepted_quantity,rl.unit_cost,pol.unit_price,pol.tax_rate,
                           COALESCE((SELECT SUM(s.quantity*(il.quantity-il.credited_quantity)/il.quantity) FROM flowora_finance_invoice_source s
                             JOIN flowora_finance_invoice_line il ON il.id=s.invoice_line_id
                             JOIN flowora_finance_invoice i ON i.id=il.invoice_id
                             WHERE s.organization_id=rl.organization_id AND s.source_type='PURCHASE_RECEIPT_LINE'
                               AND s.source_line_id=rl.id AND i.document_type='SUPPLIER_INVOICE' AND i.status='POSTED'),0) invoiced
                    FROM flowora_purchase_receipt_line rl JOIN flowora_purchase_order_line pol ON pol.id=rl.purchase_order_line_id
                    WHERE rl.organization_id=? AND rl.id=?
                    """, (rs, row) -> new ReceiptBasis(rs.getBigDecimal("accepted_quantity"),
                    rs.getBigDecimal("unit_cost"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("tax_rate"),
                    rs.getBigDecimal("invoiced")), organizationId, source.sourceLineId());
            if (basis.isEmpty()) throw notFound("purchaseReceiptLine", source.sourceLineId());
            ReceiptBasis item = basis.getFirst();
            quantityVariance = quantityVariance.add(source.quantity().subtract(item.accepted().subtract(item.invoiced())).max(BigDecimal.ZERO));
            if (item.orderPrice().signum() > 0) {
                priceVariance = priceVariance.max(line.unitPrice().subtract(item.orderPrice()).abs()
                        .divide(item.orderPrice(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")));
            }
            BigDecimal expectedTax = amounts.net().multiply(item.taxRate()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            taxVariance = taxVariance.add(amounts.tax().subtract(expectedTax));
            matched = true;
        }
        return matched ? new MatchVariance(quantityVariance, priceVariance, taxVariance) : MatchVariance.ZERO;
    }

    private String matchStatus(String organizationId, List<CalculatedLine> lines) {
        MatchTolerance tolerance = jdbc.query("""
                SELECT match_quantity_tolerance,match_price_tolerance_rate,match_tax_tolerance
                FROM flowora_finance_setting WHERE organization_id=?
                """, rs -> rs.next() ? new MatchTolerance(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)) : MatchTolerance.ZERO,
                organizationId);
        boolean exception = lines.stream().anyMatch(line -> line.quantityVariance().abs().compareTo(tolerance.quantity()) > 0
                || line.priceVarianceRate().abs().compareTo(tolerance.priceRate()) > 0
                || line.taxVariance().abs().compareTo(tolerance.tax()) > 0);
        return exception ? "EXCEPTION" : "MATCHED";
    }

    private List<PostingLine> invoicePostings(String organizationId, InvoiceView invoice) {
        boolean sales = "CUSTOMER".equals(invoice.partyType());
        boolean credit = invoice.documentType().endsWith("CREDIT");
        String control = ledger.mapping(organizationId, sales ? "RECEIVABLE" : "PAYABLE");
        String tax = ledger.mapping(organizationId, sales ? "TAX_PAYABLE" : "TAX_RECEIVABLE");
        List<PostingLine> normal = new ArrayList<>();
        if (sales) {
            normal.add(PostingLine.debit(control, "Customer receivable", invoice.totalAmount(), invoice.projectId(), invoice.partyType(), invoice.partyId(), null));
            for (InvoiceLineView line : invoice.lines()) {
                String account = line.accountCode() == null ? ledger.mapping(organizationId, "REVENUE") : line.accountCode();
                normal.add(PostingLine.credit(account, line.description(), line.netAmount(), line.projectId(), invoice.partyType(), invoice.partyId(), line.id()));
            }
            if (invoice.taxAmount().signum() > 0) normal.add(PostingLine.credit(tax, "Output tax", invoice.taxAmount(), invoice.projectId(), invoice.partyType(), invoice.partyId(), null));
        } else {
            normal.add(PostingLine.credit(control, "Supplier payable", invoice.totalAmount(), invoice.projectId(), invoice.partyType(), invoice.partyId(), null));
            for (InvoiceLineView line : invoice.lines()) {
                String semantic = line.sources().stream().anyMatch(s -> "PURCHASE_RECEIPT_LINE".equals(s.sourceType())) ? "ACCRUED_PAYABLE" : "EXPENSE";
                String account = line.accountCode() == null ? supplierSourceAccount(organizationId, line, semantic) : line.accountCode();
                normal.add(PostingLine.debit(account, line.description(), line.netAmount(), line.projectId(), invoice.partyType(), invoice.partyId(), line.id()));
            }
            if (invoice.taxAmount().signum() > 0) normal.add(PostingLine.debit(tax, "Input tax", invoice.taxAmount(), invoice.projectId(), invoice.partyType(), invoice.partyId(), null));
        }
        if (!credit) return normal;
        return normal.stream().map(line -> new PostingLine(line.accountCode(), "Credit: " + line.description(),
                line.credit(), line.debit(), line.projectId(), line.partyType(), line.partyId(), line.sourceLineId())).toList();
    }

    private void applyCredit(String organizationId, InvoiceView credit) {
        InvoiceView original = invoiceForUpdate(organizationId, credit.originalInvoiceId());
        ledger.requireNoActiveRevaluation(organizationId, original.id());
        // Allocation and credit posting share the organization finance lock. Inspect
        // current allocation rows as well as the header before consuming the remainder.
        var amounts = jdbc.query("""
                SELECT amount FROM flowora_payment_allocation
                WHERE organization_id=? AND invoice_id=? AND status='ACTIVE' FOR UPDATE
                """, (rs, row) -> rs.getBigDecimal("amount"), organizationId, original.id());
        BigDecimal activeAllocated = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!"POSTED".equals(original.status()) || original.allocatedAmount().signum() < 0
                || original.creditedAmount().signum() < 0
                || original.allocatedAmount().compareTo(activeAllocated) != 0)
            throw conflict("CREDIT_SETTLEMENT_STATE_CONFLICT", Map.of());
        BigDecimal remaining = original.totalAmount().subtract(original.creditedAmount()).subtract(activeAllocated);
        if (credit.totalAmount().compareTo(remaining) > 0)
            throw conflict(activeAllocated.signum() > 0 ? "CREDIT_REQUIRES_ALLOCATION_RELEASE" : "CREDIT_EXCEEDS_REMAINING", Map.of("remaining", remaining));
        jdbc.update("""
                UPDATE flowora_finance_invoice SET
                settlement_status=CASE WHEN allocated_amount+credited_amount+?=total_amount THEN 'PAID' ELSE 'PARTIAL' END,
                credit_status=CASE WHEN credited_amount+?=total_amount THEN 'FULL' ELSE 'PARTIAL' END,credited_amount=credited_amount+?,
                version_no=version_no+1 WHERE id=? AND organization_id=?
                """, credit.totalAmount(), credit.totalAmount(), credit.totalAmount(), original.id(), organizationId);
        for (InvoiceLineView line : credit.lines()) {
            for (InvoiceSourceView source : line.sources()) {
                if (!"ORIGINAL_INVOICE_LINE".equals(source.sourceType()) || source.sourceLineId() == null) continue;
                int changed = jdbc.update("""
                        UPDATE flowora_finance_invoice_line SET credited_quantity=credited_quantity+?,version_no=version_no+1
                        WHERE id=? AND organization_id=? AND credited_quantity+?<=quantity
                        """, source.quantity(), source.sourceLineId(), organizationId, source.quantity());
                if (changed != 1) throw conflict("CREDIT_QUANTITY_EXCEEDS_REMAINING", Map.of("sourceLineId", source.sourceLineId()));
            }
        }
    }

    private String supplierSourceAccount(String organizationId, InvoiceLineView line, String semantic) {
        Set<String> accounts = new java.util.HashSet<>();
        for (InvoiceSourceView source : line.sources()) {
            if (!"PURCHASE_RECEIPT_LINE".equals(source.sourceType())) continue;
            // Compatibility receipts may have accrued directly to the payable account.
            // Clear their actual posted accrual instead of creating a second liability.
            accounts.addAll(jdbc.query("""
                    SELECT DISTINCT l.account_code FROM flowora_journal_line l
                    JOIN flowora_journal_entry e ON e.id=l.journal_entry_id
                    WHERE e.organization_id=? AND l.organization_id=e.organization_id
                      AND e.source_type='PURCHASE_RECEIPT' AND e.source_id=? AND e.status='POSTED' AND l.credit>0
                    """, (rs, row) -> rs.getString(1), organizationId, source.sourceId()));
        }
        if (accounts.size() > 1) throw conflict("INVOICE_SOURCE_ACCOUNTS_REQUIRE_SEPARATE_LINES", Map.of("lineId", line.id()));
        return accounts.isEmpty() ? ledger.mapping(organizationId, semantic) : accounts.iterator().next();
    }

    private void requireSourceCapacity(String organizationId, InvoiceView invoice) {
        if (invoice.documentType().endsWith("CREDIT")) return;
        Map<String, BigDecimal> quantities = new java.util.TreeMap<>();
        Map<String, InvoiceSourceView> references = new java.util.HashMap<>();
        for (InvoiceLineView line : invoice.lines()) {
            List<InvoiceSourceView> stock = line.sources().stream().filter(source ->
                    Set.of("PURCHASE_RECEIPT_LINE", "SALES_DELIVERY_LINE").contains(source.sourceType())).toList();
            if (!stock.isEmpty() && sum(stock.stream().map(InvoiceSourceView::quantity).toList()).compareTo(line.quantity()) != 0)
                throw conflict("INVOICE_SOURCE_QUANTITY_MISMATCH", Map.of("lineId", line.id()));
            for (InvoiceSourceView source : stock) {
                String key = source.sourceType() + ":" + source.sourceLineId();
                quantities.merge(key, source.quantity(), BigDecimal::add); references.put(key, source);
            }
        }
        for (var entry : quantities.entrySet()) {
            InvoiceSourceView source = references.get(entry.getKey());
            boolean purchase = source.sourceType().equals("PURCHASE_RECEIPT_LINE");
            String table = purchase ? "flowora_purchase_receipt_line" : "flowora_sales_delivery_line";
            String quantity = purchase ? "accepted_quantity" : "quantity";
            List<BigDecimal> capacity = jdbc.query("SELECT " + quantity + "-returned_quantity FROM " + table + " WHERE organization_id=? AND id=? FOR UPDATE",
                    (rs, row) -> rs.getBigDecimal(1), organizationId, source.sourceLineId());
            if (capacity.isEmpty()) throw conflict("INVALID_INVOICE_SOURCE", Map.of());
            BigDecimal consumed = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(s.quantity*(il.quantity-il.credited_quantity)/il.quantity),0)
                    FROM flowora_finance_invoice_source s JOIN flowora_finance_invoice_line il ON il.id=s.invoice_line_id
                    JOIN flowora_finance_invoice i ON i.id=il.invoice_id
                    WHERE s.organization_id=? AND s.source_type=? AND s.source_line_id=?
                      AND i.status='POSTED' AND i.document_type=?
                    FOR UPDATE
                    """, BigDecimal.class, organizationId, source.sourceType(), source.sourceLineId(), invoice.documentType());
            if (consumed.add(entry.getValue()).compareTo(capacity.getFirst()) > 0)
                throw conflict("INVOICE_SOURCE_QUANTITY_EXCEEDED", Map.of("sourceLineId", source.sourceLineId()));
        }
    }

    private void postExchangeDifference(FloworaPrincipal actor, InvoiceView invoice, String allocationId, BigDecimal difference) {
        String control = ledger.mapping(actor.organizationId(), "CUSTOMER".equals(invoice.partyType()) ? "RECEIVABLE" : "PAYABLE");
        boolean gain = "CUSTOMER".equals(invoice.partyType()) ? difference.signum() > 0 : difference.signum() < 0;
        String fx = ledger.mapping(actor.organizationId(), gain ? "FX_GAIN" : "FX_LOSS");
        // Realized FX is already a base-currency difference. Record it directly to
        // avoid divide/multiply rounding changing the realized amount.
        BigDecimal amount = difference.abs();
        boolean debitControl = gain;
        List<PostingLine> lines = debitControl
                ? List.of(PostingLine.debit(control, "Realized exchange", amount, invoice.projectId(), invoice.partyType(), invoice.partyId(), null),
                PostingLine.credit(fx, "Realized exchange gain", amount, invoice.projectId(), invoice.partyType(), invoice.partyId(), null))
                : List.of(PostingLine.debit(fx, "Realized exchange loss", amount, invoice.projectId(), invoice.partyType(), invoice.partyId(), null),
                PostingLine.credit(control, "Realized exchange", amount, invoice.projectId(), invoice.partyType(), invoice.partyId(), null));
        ledger.post(actor.organizationId(), actor.userId(), "REALIZED_EXCHANGE", allocationId, null,
                invoice.accountingDate(), invoice.accountingDate(), invoice.exchangeRateDate(), invoice.baseCurrencyCode(),
                invoice.baseCurrencyCode(), BigDecimal.ONE, "Realized exchange difference", "allocation-fx:" + allocationId, lines);
    }

    private void validateSource(String organizationId, String invoiceType, String partyId, Original original,
                                InvoiceSourceCreate source) {
        String type = upper(source.sourceType());
        boolean sales = invoiceType.equals("SALES_INVOICE");
        boolean supplier = invoiceType.equals("SUPPLIER_INVOICE");
        if ((type.equals("SALES_DELIVERY_LINE") || type.equals("PROJECT_BILLING_BASIS")) && !sales
                || type.equals("PURCHASE_RECEIPT_LINE") && !supplier
                || type.equals("ORIGINAL_INVOICE_LINE") && original == null) {
            throw conflict("INVALID_INVOICE_SOURCE", Map.of("sourceType", type));
        }
        int count;
        count = switch (type) {
            case "SALES_DELIVERY_LINE" -> jdbc.queryForObject("""
                    SELECT COUNT(*) FROM flowora_sales_delivery_line l JOIN flowora_sales_delivery d ON d.id=l.delivery_id
                    JOIN flowora_sales_order o ON o.id=d.sales_order_id
                    WHERE l.organization_id=? AND d.organization_id=l.organization_id AND o.organization_id=l.organization_id
                      AND l.id=? AND d.id=? AND o.customer_id=? AND d.status='POSTED'
                    """, Integer.class, organizationId, source.sourceLineId(), source.sourceId(), partyId);
            case "PURCHASE_RECEIPT_LINE" -> jdbc.queryForObject("""
                    SELECT COUNT(*) FROM flowora_purchase_receipt_line l JOIN flowora_purchase_receipt r ON r.id=l.purchase_receipt_id
                    JOIN flowora_purchase_order o ON o.id=r.purchase_order_id
                    WHERE l.organization_id=? AND r.organization_id=l.organization_id AND o.organization_id=l.organization_id
                      AND l.id=? AND r.id=? AND o.supplier_id=? AND r.status='POSTED'
                    """, Integer.class, organizationId, source.sourceLineId(), source.sourceId(), partyId);
            case "PROJECT_BILLING_BASIS" -> jdbc.queryForObject("""
                    SELECT COUNT(*) FROM flowora_project_billing_basis b JOIN flowora_project p ON p.id=b.project_id
                    WHERE b.organization_id=? AND p.organization_id=b.organization_id AND b.id=? AND p.id=?
                      AND p.customer_id=? AND b.status IN ('AVAILABLE','PARTIAL')
                    """, Integer.class, organizationId, source.sourceLineId(), source.sourceId(), partyId);
            case "ORIGINAL_INVOICE_LINE" -> original == null ? 0 : jdbc.queryForObject("""
                    SELECT COUNT(*) FROM flowora_finance_invoice_line WHERE organization_id=? AND id=? AND invoice_id=? AND invoice_id=?
                    """, Integer.class, organizationId, source.sourceLineId(), original.id(), source.sourceId());
            default -> 0;
        };
        if (count == 0) throw conflict("INVALID_INVOICE_SOURCE", Map.of("sourceType", type, "sourceLineId", String.valueOf(source.sourceLineId())));
    }

    private List<InvoiceSourceView> sources(String organizationId, String lineId) {
        return jdbc.query("""
                SELECT source_type,source_id,source_line_id,quantity,amount FROM flowora_finance_invoice_source
                WHERE organization_id=? AND invoice_line_id=? ORDER BY created_at
                """, (rs, row) -> new InvoiceSourceView(rs.getString("source_type"), rs.getString("source_id"),
                rs.getString("source_line_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("amount")), organizationId, lineId);
    }

    private List<AllocationView> allocations(String organizationId, String paymentId) {
        return jdbc.query("""
                SELECT id,payment_id,invoice_id,amount,base_amount,realized_exchange_difference,status,allocated_at,reversed_at
                FROM flowora_payment_allocation WHERE organization_id=? AND payment_id=? ORDER BY allocated_at
                """, (rs, row) -> allocationRow(rs), organizationId, paymentId);
    }

    private AllocationView allocation(String organizationId, String id) {
        List<AllocationView> values = jdbc.query("""
                SELECT id,payment_id,invoice_id,amount,base_amount,realized_exchange_difference,status,allocated_at,reversed_at
                FROM flowora_payment_allocation WHERE organization_id=? AND id=?
                """, (rs, row) -> allocationRow(rs), organizationId, id);
        if (values.isEmpty()) throw notFound("allocation", id);
        return values.getFirst();
    }

    private static AllocationView allocationRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AllocationView(rs.getString("id"), rs.getString("payment_id"), rs.getString("invoice_id"),
                rs.getBigDecimal("amount"), rs.getBigDecimal("base_amount"),
                rs.getBigDecimal("realized_exchange_difference"), rs.getString("status"),
                timestamp(rs.getTimestamp("allocated_at")), timestamp(rs.getTimestamp("reversed_at")));
    }

    private InvoiceView invoiceForUpdate(String organizationId, String id) {
        List<String> locked = jdbc.query("SELECT id FROM flowora_finance_invoice WHERE organization_id=? AND id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), organizationId, id);
        if (locked.isEmpty()) throw notFound("invoice", id);
        return invoice(organizationId, id);
    }

    private PaymentView paymentForUpdate(String organizationId, String id) {
        List<String> locked = jdbc.query("SELECT id FROM flowora_payment_v2 WHERE organization_id=? AND id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), organizationId, id);
        if (locked.isEmpty()) throw notFound("payment", id);
        return payment(organizationId, id);
    }

    private AllocationView allocationForUpdate(String organizationId, String id) {
        List<String> locked = jdbc.query("SELECT id FROM flowora_payment_allocation WHERE organization_id=? AND id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), organizationId, id);
        if (locked.isEmpty()) throw notFound("allocation", id);
        return allocation(organizationId, id);
    }

    private Original requireOriginal(String organizationId, String originalId, String partyType) {
        List<Original> values = jdbc.query("""
                SELECT id,party_id FROM flowora_finance_invoice
                WHERE organization_id=? AND id=? AND party_type=? AND document_type IN ('SALES_INVOICE','SUPPLIER_INVOICE') AND status='POSTED'
                """, (rs, row) -> new Original(rs.getString("id"), rs.getString("party_id")), organizationId, originalId, partyType);
        if (values.isEmpty()) throw conflict("POSTED_ORIGINAL_INVOICE_REQUIRED", Map.of());
        return values.getFirst();
    }

    private void requireParty(String organizationId, String partyType, String id) {
        String table = "CUSTOMER".equals(partyType) ? "flowora_customer" : "flowora_supplier";
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE organization_id=? AND id=? AND active=TRUE",
                Integer.class, organizationId, id);
        if (count == null || count == 0) throw notFound(partyType.toLowerCase(), id);
    }

    private void requireBank(String organizationId, String bankAccountId, String currencyCode) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_bank_account WHERE organization_id=? AND id=? AND currency_code=? AND active=TRUE",
                Integer.class, organizationId, bankAccountId, upper(currencyCode));
        if (count == null || count == 0) throw notFound("bankAccount", bankAccountId);
    }

    private String bankLedger(String organizationId, String bankId) {
        List<String> values = jdbc.query("SELECT ledger_account_code FROM flowora_bank_account WHERE organization_id=? AND id=?",
                (rs, row) -> rs.getString(1), organizationId, bankId);
        if (values.isEmpty()) throw notFound("bankAccount", bankId);
        return values.getFirst();
    }

    private void refreshSettlement(String paymentId, String invoiceId) {
        jdbc.update("""
                UPDATE flowora_payment_v2 SET allocation_status=CASE WHEN allocated_amount=0 THEN 'UNALLOCATED'
                WHEN allocated_amount=amount THEN 'ALLOCATED' ELSE 'PARTIAL' END WHERE id=?
                """, paymentId);
        jdbc.update("""
                UPDATE flowora_finance_invoice SET settlement_status=CASE
                WHEN allocated_amount+credited_amount=0 THEN 'UNPAID'
                WHEN allocated_amount+credited_amount=total_amount THEN 'PAID' ELSE 'PARTIAL' END WHERE id=?
                """, invoiceId);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
    }

    private static String requiredKey(String value) {
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

    private static String upper(String value) {
        return clean(value).toUpperCase(java.util.Locale.ROOT);
    }

    private static String prefix(String type) {
        return switch (type) {
            case "SALES_INVOICE" -> "SI";
            case "SUPPLIER_INVOICE" -> "PI";
            case "CUSTOMER_CREDIT" -> "CC";
            default -> "SC";
        };
    }

    private static String paymentPrefix(String type) {
        return switch (type) {
            case "RECEIPT" -> "RC";
            case "PAYMENT" -> "PY";
            case "CUSTOMER_REFUND" -> "CR";
            default -> "SR";
        };
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record CalculatedLine(InvoiceLineCreate input, BigDecimal net, BigDecimal tax, BigDecimal total,
                                  BigDecimal quantityVariance, BigDecimal priceVarianceRate, BigDecimal taxVariance) {
    }

    private record MatchVariance(BigDecimal quantity, BigDecimal priceRate, BigDecimal tax) {
        private static final MatchVariance ZERO = new MatchVariance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private record MatchTolerance(BigDecimal quantity, BigDecimal priceRate, BigDecimal tax) {
        private static final MatchTolerance ZERO = new MatchTolerance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private record ReceiptBasis(BigDecimal accepted, BigDecimal cost, BigDecimal orderPrice,
                                BigDecimal taxRate, BigDecimal invoiced) {
    }

    private record Original(String id, String partyId) {
    }
}
