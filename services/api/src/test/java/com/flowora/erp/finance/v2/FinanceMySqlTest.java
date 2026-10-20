package com.flowora.erp.finance.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static com.flowora.erp.finance.v2.FinanceV2Dtos.*;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL only, restricted to the authorized disposable database. */
@EnabledIfEnvironmentVariable(named="FLOWORA_R2_MYSQL_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:13306/audit_flowora\\?.*")
class FinanceMySqlTest {
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    FinanceLedgerService ledger;
    FinanceDocumentService documents;
    FloworaPrincipal actor;
    String org, customer, supplier;
    final LocalDate date = LocalDate.of(2026,10,1);
    @BeforeAll static void connect() {
        var ds=new DriverManagerDataSource(System.getenv("FLOWORA_R2_MYSQL_URL"),"root",Objects.requireNonNullElse(System.getenv("FLOWORA_R2_MYSQL_PASSWORD"),""));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @BeforeEach void fixtures() {
        org=key(); customer=key(); supplier=key();
        jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'R3 isolated test','USD')",org);
        jdbc.update("INSERT INTO flowora_customer(id,organization_id,code,name,currency_code) VALUES (?,?,?,'R3 customer','EUR')",customer,org,customer);
        jdbc.update("INSERT INTO flowora_supplier(id,organization_id,code,name,currency_code) VALUES (?,?,?,'R3 supplier','EUR')",supplier,org,supplier);
        jdbc.update("INSERT INTO flowora_finance_setting(organization_id,base_currency_code) VALUES (?,'USD')",org);
        for(String semantic:List.of("RECEIVABLE","PAYABLE","ACCRUED_PAYABLE","REVENUE","EXPENSE","TAX_PAYABLE","TAX_RECEIVABLE","CASH","FX_GAIN","FX_LOSS"))
            jdbc.update("INSERT INTO flowora_posting_mapping(id,organization_id,semantic_code,account_code) VALUES (?,?,?,?)",key(),org,semantic,semantic);
        ledger=new FinanceLedgerService(jdbc); documents=new FinanceDocumentService(jdbc,ledger);
        actor=new FloworaPrincipal("system:r3-test","r3","R3 test",org,"R3 isolated test",List.of("ADMIN"));
    }
    @AfterEach void cleanup() {
        jdbc.update("UPDATE flowora_journal_entry SET reversal_of_id=NULL WHERE organization_id=?",org);
        jdbc.update("UPDATE flowora_finance_invoice SET original_invoice_id=NULL WHERE organization_id=?",org);
        for(String table:List.of("flowora_payment_correction","flowora_refund_source_recovery","flowora_refund_correction","flowora_currency_revaluation_line","flowora_currency_revaluation","flowora_bank_reconciliation_link","flowora_bank_reconciliation","flowora_bank_statement_line","flowora_allocation_reversal","flowora_payment_allocation","flowora_payment_v2","flowora_bank_account","flowora_finance_invoice_source","flowora_finance_invoice_line","flowora_finance_invoice","flowora_journal_line","flowora_journal_entry","flowora_accounting_period","flowora_posting_mapping","flowora_finance_setting","flowora_sales_delivery_line","flowora_sales_delivery","flowora_sales_order_line","flowora_sales_order","flowora_purchase_receipt_line","flowora_purchase_receipt","flowora_purchase_order_line","flowora_purchase_order","flowora_project","flowora_supplier","flowora_customer"))
            jdbc.update("DELETE FROM "+table+" WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_organization WHERE id=?",org);
    }
    @Test void receiptSupplierIsResolvedThroughOrderAndSourceBoundariesAreEnforced() throws Exception {
        String po=key(),pol=key(),receipt=key(),rl=key();
        jdbc.update("INSERT INTO flowora_purchase_order(id,organization_id,number,supplier_id,warehouse_id,status,buyer_user_id,order_date) VALUES (?,?,?,?,?,'RECEIVED','system:r3-test','2026-10-01')",po,org,po.substring(0,30),supplier,"r3-warehouse");
        jdbc.update("INSERT INTO flowora_purchase_order_line(id,organization_id,purchase_order_id,item_id,ordered_quantity,received_quantity,unit_price) VALUES (?,?,?,?,1,1,10)",pol,org,po,"r3-item");
        jdbc.update("INSERT INTO flowora_purchase_receipt(id,organization_id,number,purchase_order_id,warehouse_id,received_by,status) VALUES (?,?,?,?,?,'system:r3-test','POSTED')",receipt,org,receipt.substring(0,30),po,"r3-warehouse");
        jdbc.update("INSERT INTO flowora_purchase_receipt_line(id,organization_id,purchase_receipt_id,purchase_order_line_id,item_id,quantity,accepted_quantity,unit_cost) VALUES (?,?,?,?,?,1,1,10)",rl,org,receipt,pol,"r3-item");
        assertThat(documents.stockInvoiceSources(org).getFirst().remainingQuantity()).isEqualByComparingTo("1");
        assertThat(documents.stockInvoiceSources(key())).isEmpty();
        var source=new InvoiceSourceCreate("PURCHASE_RECEIPT_LINE",receipt,rl,n("1"),n("10"));
        var invoice=run(()->documents.createInvoice(actor,key(),input("SUPPLIER_INVOICE",supplier,"1",List.of(line("10",List.of(source))))));
        var duplicate=run(()->documents.createInvoice(actor,key(),input("SUPPLIER_INVOICE",supplier,"1",List.of(line("10",List.of(source))))));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        try {
            var first=pool.submit(()->postAtBarrier(invoice.id(),barrier));
            var second=pool.submit(()->postAtBarrier(duplicate.id(),barrier));
            assertThat(List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("POSTED","INVOICE_SOURCE_QUANTITY_EXCEEDED");
        } finally { pool.shutdownNow(); }
        assertThat(documents.stockInvoiceSources(org).getFirst().remainingQuantity()).isEqualByComparingTo("0");
        String otherSupplier=key();
        jdbc.update("INSERT INTO flowora_supplier(id,organization_id,code,name,currency_code) VALUES (?,?,?,'R3 other supplier','EUR')",otherSupplier,org,otherSupplier);
        assertThatThrownBy(()->run(()->documents.createInvoice(actor,key(),input("SUPPLIER_INVOICE",otherSupplier,"1",List.of(line("10",List.of(source))))))).isInstanceOf(PlatformApiException.class);
        var wrongHeader=new InvoiceSourceCreate("PURCHASE_RECEIPT_LINE",key(),rl,n("1"),n("10"));
        assertThatThrownBy(()->run(()->documents.createInvoice(actor,key(),input("SUPPLIER_INVOICE",supplier,"1",List.of(line("10",List.of(wrongHeader))))))).isInstanceOf(PlatformApiException.class);
        assertThatThrownBy(()->run(()->documents.createInvoice(actor,key(),input("SALES_INVOICE",customer,"1",List.of(line("10",List.of(source))))))).isInstanceOf(PlatformApiException.class);
        String foreignOrg=key();
        jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'R3 foreign fixture','USD')",foreignOrg);
        try {
            assertThatThrownBy(()->run(()->{
                jdbc.update("UPDATE flowora_purchase_receipt SET organization_id=? WHERE id=?",foreignOrg,receipt);
                return documents.createInvoice(actor,key(),input("SUPPLIER_INVOICE",supplier,"1",List.of(line("10",List.of(source)))));
            })).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("INVALID_INVOICE_SOURCE");
        } finally { jdbc.update("DELETE FROM flowora_organization WHERE id=?",foreignOrg); }
        var posted=documents.invoices(org,"SUPPLIER_INVOICE","POSTED").getFirst();
        assertThat(posted.lines().getFirst().reversalAccountCode()).isEqualTo("ACCRUED_PAYABLE");
        var creditSource=new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",posted.id(),posted.lines().getFirst().id(),n("1"),n("10"));
        var creditLine=new InvoiceLineCreate(null,"R3 original accrual reversal",n("1"),n("10"),n("0"),n("0"),posted.lines().getFirst().reversalAccountCode(),null,List.of(creditSource));
        var credit=run(()->documents.createInvoice(actor,key(),new InvoiceCreate("SUPPLIER_CREDIT",supplier,null,posted.id(),date,date,date,date,"EUR",n("1"),List.of(creditLine))));
        run(()->documents.postInvoice(actor,credit.id(),0));
        assertThat(documents.stockInvoiceSources(org).getFirst().remainingQuantity()).isEqualByComparingTo("1");
        var remaining=documents.invoices(org,"SUPPLIER_INVOICE","DRAFT").getFirst();
        assertThat(run(()->documents.postInvoice(actor,remaining.id(),0)).status()).isEqualTo("POSTED");
    }
    String postAtBarrier(String invoiceId,java.util.concurrent.CyclicBarrier barrier) throws Exception {
        barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);
        try {return run(()->documents.postInvoice(actor,invoiceId,0)).status();}
        catch (PlatformApiException ex) {if (!"INVOICE_SOURCE_QUANTITY_EXCEEDED".equals(ex.code())) throw ex; return ex.code();}
    }
    @Test void salesSourcePickerUsesOrderTermsAndPostedInvoiceRemainder() {
        String so=key(),sol=key(),delivery=key(),dl=key();
        jdbc.update("INSERT INTO flowora_sales_order(id,organization_id,number,customer_id,warehouse_id,status,sales_user_id,order_date,currency_code,total_amount) VALUES (?,?,?,?,?,'DELIVERED','system:r5w','2026-10-01','EUR',28.5)",so,org,so.substring(0,30),customer,"r5w-warehouse");
        jdbc.update("INSERT INTO flowora_sales_order_line(id,organization_id,sales_order_id,item_id,ordered_quantity,fulfilled_quantity,unit_price,discount_rate,tax_rate) VALUES (?,?,?,?,3,3,10,5,0)",sol,org,so,"r5w-item");
        jdbc.update("INSERT INTO flowora_sales_delivery(id,organization_id,number,sales_order_id,warehouse_id,status,actor_user_id) VALUES (?,?,?,?,?,'POSTED','system:r5w')",delivery,org,delivery.substring(0,30),so,"r5w-warehouse");
        jdbc.update("INSERT INTO flowora_sales_delivery_line(id,organization_id,delivery_id,sales_order_line_id,item_id,quantity,unit_cost) VALUES (?,?,?,?,?,3,8)",dl,org,delivery,sol,"r5w-item");
        var row=documents.stockInvoiceSources(org).getFirst();
        assertThat(row.documentType()).isEqualTo("SALES_INVOICE"); assertThat(row.partyId()).isEqualTo(customer);
        assertThat(row.currencyCode()).isEqualTo("EUR"); assertThat(row.remainingQuantity()).isEqualByComparingTo("3");
        assertThat(row.unitPrice()).isEqualByComparingTo("10"); assertThat(row.discountRate()).isEqualByComparingTo("5");
        var source=new InvoiceSourceCreate("SALES_DELIVERY_LINE",delivery,dl,n("1"),n("9.5"));
        var line=new InvoiceLineCreate("r5w-item","Sales source",n("1"),n("10"),n("5"),n("0"),null,null,List.of(source));
        var draft=run(()->documents.createInvoice(actor,key(),input("SALES_INVOICE",customer,"1",List.of(line))));
        assertThat(documents.stockInvoiceSources(org).getFirst().remainingQuantity()).isEqualByComparingTo("3");
        run(()->documents.postInvoice(actor,draft.id(),0));
        assertThat(documents.stockInvoiceSources(org).getFirst().remainingQuantity()).isEqualByComparingTo("2");
        jdbc.update("UPDATE flowora_sales_delivery SET status='DRAFT' WHERE id=? AND organization_id=?",delivery,org);
        assertThat(documents.stockInvoiceSources(org)).isEmpty();
    }
    @Test void foreignStockInvoicesAndCreditsRetainOriginalRateAndReleaseSourceQuantity() {
        for (boolean purchase : List.of(true, false)) {
            String order=key(), orderLine=key(), stock=key(), stockLine=key();
            String party=purchase?supplier:customer;
            if (purchase) {
                jdbc.update("INSERT INTO flowora_purchase_order(id,organization_id,number,supplier_id,warehouse_id,status,buyer_user_id,order_date,currency_code) VALUES (?,?,?,?,?,'RECEIVED','system:r6c','2026-10-01','EUR')",order,org,order.substring(0,30),party,"r6c-warehouse");
                jdbc.update("INSERT INTO flowora_purchase_order_line(id,organization_id,purchase_order_id,item_id,ordered_quantity,received_quantity,unit_price,discount_rate,tax_rate) VALUES (?,?,?,?,2,2,10,5,10)",orderLine,org,order,"r6c-item");
                jdbc.update("INSERT INTO flowora_purchase_receipt(id,organization_id,number,purchase_order_id,warehouse_id,status,received_by) VALUES (?,?,?,?,?,'POSTED','system:r6c')",stock,org,stock.substring(0,30),order,"r6c-warehouse");
                jdbc.update("INSERT INTO flowora_purchase_receipt_line(id,organization_id,purchase_receipt_id,purchase_order_line_id,item_id,quantity,accepted_quantity,unit_cost) VALUES (?,?,?,?,?,2,2,12.35)",stockLine,org,stock,orderLine,"r6c-item");
            } else {
                jdbc.update("INSERT INTO flowora_sales_order(id,organization_id,number,customer_id,warehouse_id,status,sales_user_id,order_date,currency_code,total_amount) VALUES (?,?,?,?,?,'DELIVERED','system:r6c','2026-10-01','EUR',20.9)",order,org,order.substring(0,30),party,"r6c-warehouse");
                jdbc.update("INSERT INTO flowora_sales_order_line(id,organization_id,sales_order_id,item_id,ordered_quantity,fulfilled_quantity,unit_price,discount_rate,tax_rate) VALUES (?,?,?,?,2,2,10,5,10)",orderLine,org,order,"r6c-item");
                jdbc.update("INSERT INTO flowora_sales_delivery(id,organization_id,number,sales_order_id,warehouse_id,status,actor_user_id) VALUES (?,?,?,?,?,'POSTED','system:r6c')",stock,org,stock.substring(0,30),order,"r6c-warehouse");
                jdbc.update("INSERT INTO flowora_sales_delivery_line(id,organization_id,delivery_id,sales_order_line_id,item_id,quantity,unit_cost) VALUES (?,?,?,?,?,2,8)",stockLine,org,stock,orderLine,"r6c-item");
            }
            var source=new InvoiceSourceCreate(purchase?"PURCHASE_RECEIPT_LINE":"SALES_DELIVERY_LINE",stock,stockLine,n("2"),n("19"));
            var originalLine=new InvoiceLineCreate("r6c-item","R6C FX source",n("2"),n("10"),n("5"),n("10"),null,null,List.of(source));
            var draft=run(()->documents.createInvoice(actor,key(),new InvoiceCreate(purchase?"SUPPLIER_INVOICE":"SALES_INVOICE",party,null,null,date,date,date,date,"EUR",n("1.3"),List.of(originalLine))));
            var posted=run(()->documents.postInvoice(actor,draft.id(),0));
            assertThat(posted.baseTotalAmount()).isEqualByComparingTo("27.17");
            String originalAccount=posted.lines().getFirst().reversalAccountCode();
            assertThat(originalAccount).isEqualTo(purchase?"ACCRUED_PAYABLE":"REVENUE");
            var creditSource=new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",posted.id(),posted.lines().getFirst().id(),n("1"),n("9.5"));
            var creditLine=new InvoiceLineCreate("r6c-item","R6C FX credit",n("1"),n("10"),n("5"),n("10"),originalAccount,null,List.of(creditSource));
            var credit=run(()->documents.createInvoice(actor,key(),new InvoiceCreate(purchase?"SUPPLIER_CREDIT":"CUSTOMER_CREDIT",party,null,posted.id(),date,date,date,posted.exchangeRateDate(),posted.currencyCode(),posted.exchangeRate(),List.of(creditLine))));
            var postedCredit=run(()->documents.postInvoice(actor,credit.id(),0));
            assertThat(postedCredit.baseTotalAmount()).isEqualByComparingTo("13.585");
            assertThat(documents.invoice(org,posted.id()).creditedAmount()).isEqualByComparingTo("10.45");
            assertThat(documents.invoice(org,posted.id()).lines().getFirst().creditedQuantity()).isEqualByComparingTo("1");
            assertThat(documents.stockInvoiceSources(org).stream().filter(row->row.sourceLineId().equals(stockLine)).findFirst().orElseThrow().remainingQuantity()).isEqualByComparingTo("1");
            assertThat(jdbc.queryForObject("SELECT SUM(base_debit-base_credit) FROM flowora_journal_line WHERE organization_id=? AND account_code=?",BigDecimal.class,org,purchase?"PAYABLE":"RECEIVABLE")).isEqualByComparingTo(purchase?"-13.585":"13.585");
        }
    }
    @Test void roundingMismatchIsRejectedBeforeAnyJournalOrInvoicePosting() {
        var invoice=run(()->documents.createInvoice(actor,key(),input("SALES_INVOICE",customer,"0.5",List.of(line("0.0001",List.of()),line("0.0001",List.of())))));
        assertThatThrownBy(()->run(()->documents.postInvoice(actor,invoice.id(),0))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.invoice(org,invoice.id()).status()).isEqualTo("DRAFT");
        assertThat(ledger.journals(org,date,date)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_accounting_period WHERE organization_id=?",Integer.class,org)).isZero();
    }
    @Test void reversingAllocationOffsetsRealizedFxAndSameKeyReplayDoesNotDuplicate() {
        var allocation=allocate();
        String request=key();
        assertThat(run(()->documents.reverseAllocation(actor,allocation.id(),request,new AllocationReverse("R3 undo"))).status()).isEqualTo("REVERSED");
        assertThat(run(()->documents.reverseAllocation(actor,allocation.id(),request,new AllocationReverse("R3 undo"))).status()).isEqualTo("REVERSED");
        assertThatThrownBy(()->run(()->documents.reverseAllocation(actor,allocation.id(),key(),new AllocationReverse("duplicate")))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.payment(org,allocation.paymentId()).allocatedAmount()).isEqualByComparingTo("0");
        assertThat(documents.invoice(org,allocation.invoiceId()).allocatedAmount()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT SUM(base_credit-base_debit) FROM flowora_journal_line WHERE organization_id=? AND account_code='FX_GAIN'",BigDecimal.class,org)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND reversal_of_id IS NOT NULL",Integer.class,org)).isEqualTo(1);
    }
    @Test void closedPeriodRejectsEntireAllocationReversal() {
        var allocation=allocate();
        jdbc.update("UPDATE flowora_accounting_period SET status='CLOSED' WHERE organization_id=?",org);
        assertThatThrownBy(()->run(()->documents.reverseAllocation(actor,allocation.id(),key(),new AllocationReverse("closed")))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.payment(org,allocation.paymentId()).allocatedAmount()).isEqualByComparingTo("100");
        assertThat(documents.invoice(org,allocation.invoiceId()).allocatedAmount()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_allocation_reversal WHERE organization_id=?",Integer.class,org)).isZero();
    }
    @Test void customerAndSupplierFxBothSignsReverseExactly() {
        for (String type : List.of("SALES_INVOICE","SUPPLIER_INVOICE")) for (String rate : List.of("0.7","1.7")) {
            String party=type.equals("SALES_INVOICE")?customer:supplier;
            var invoice=run(()->documents.createInvoice(actor,key(),input(type,party,"1.3",List.of(line("10.1234",List.of())))));
            run(()->documents.postInvoice(actor,invoice.id(),0));
            var payment=run(()->documents.createPayment(actor,key(),new PaymentCreate(type.equals("SALES_INVOICE")?"RECEIPT":"PAYMENT",party,null,date,date,date,"EUR",n(rate),n("10.1234"),"FX matrix")));
            run(()->documents.postPayment(actor,payment.id(),0));
            var allocation=run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("10.1234"))));
            var journal=ledger.journals(org,date,date).stream().filter(j->j.sourceId().equals(allocation.id())).findFirst().orElseThrow();
            boolean gain=type.equals("SALES_INVOICE")?n(rate).compareTo(n("1.3"))>0:n(rate).compareTo(n("1.3"))<0;
            assertThat(journal.lines().stream().anyMatch(l->l.accountCode().equals(gain?"FX_GAIN":"FX_LOSS"))).isTrue();
            assertThat(journal.totalDebit()).isEqualByComparingTo(allocation.realizedExchangeDifference().abs());
            run(()->documents.reverseAllocation(actor,allocation.id(),key(),new AllocationReverse("matrix reversal")));
        }
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(base_debit-base_credit),0) FROM flowora_journal_line WHERE organization_id=? AND account_code IN ('FX_GAIN','FX_LOSS')",BigDecimal.class,org)).isEqualByComparingTo("0");
    }
    @Test void revaluationCannotBeRepeatedUntilExplicitReversalAndReplaysAreSafe() {
        var operations=new FinanceOperationsService(jdbc,ledger);
        var invoice=run(()->documents.createInvoice(actor,key(),input("SALES_INVOICE",customer,"1",List.of(line("100",List.of())))));
        run(()->documents.postInvoice(actor,invoice.id(),0));
        String request=key(); var body=new RevaluationCreate(date,"EUR",n("2"),date.plusDays(1));
        var valuation=run(()->operations.revalue(actor,request,body));
        assertThat(operations.revaluations(org)).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(valuation.id());
            assertThat(row.status()).isEqualTo("POSTED");
            assertThat(row.totalGain()).isEqualByComparingTo("100");
        });
        assertThat(operations.revaluations(key())).isEmpty();
        assertThat(run(()->operations.revalue(actor,request,body)).id()).isEqualTo(valuation.id());
        assertThatThrownBy(()->run(()->operations.revalue(actor,key(),body))).isInstanceOf(PlatformApiException.class);
        String reverseKey=key();
        var reversed=run(()->operations.reverseRevaluation(actor,valuation.id(),date.plusDays(1),"R3 valuation undo",reverseKey));
        assertThat(run(()->operations.reverseRevaluation(actor,valuation.id(),date.plusDays(1),"R3 valuation undo",reverseKey)).id()).isEqualTo(reversed.id());
        assertThat(operations.revaluations(org)).singleElement().extracting(RevaluationHistoryView::status).isEqualTo("REVERSED");
        var noAdjustment=run(()->operations.revalue(actor,key(),new RevaluationCreate(date.plusDays(1),"EUR",n("1"),null)));
        assertThat(noAdjustment.journalEntryId()).isNull();
        assertThat(operations.revaluations(org)).filteredOn(row -> row.id().equals(noAdjustment.id()))
                .singleElement().extracting(RevaluationHistoryView::status).isEqualTo("NO_ADJUSTMENT");
        assertThat(jdbc.queryForObject("SELECT SUM(base_credit-base_debit) FROM flowora_journal_line WHERE organization_id=? AND account_code='FX_GAIN'",BigDecimal.class,org)).isEqualByComparingTo("0");
    }
    @Test void bankReconciliationRequiresCorrectDirectionAndSameBankAndCurrency() {
        var operations=new FinanceOperationsService(jdbc,ledger); String bank=key();
        jdbc.update("INSERT INTO flowora_bank_account(id,organization_id,code,name,bank_name,account_number_masked,currency_code,ledger_account_code) VALUES (?,?,?,'R3 bank','Test Bank','****0001','EUR','CASH')",bank,org,bank);
        var payment=run(()->documents.createPayment(actor,key(),new PaymentCreate("RECEIPT",customer,bank,date,date,date,"EUR",n("1"),n("100"),"R3 bank")));
        run(()->documents.postPayment(actor,payment.id(),0));
        var out=run(()->operations.importStatements(actor,new StatementImport(bank,List.of(new StatementLineCreate(date,date,n("-100"),"EUR",key(),"R3","out"))))).getFirst();
        assertThatThrownBy(()->run(()->operations.reconcile(actor,key(),new ReconciliationCreate(bank,List.of(new ReconciliationLinkCreate(out.id(),payment.id(),n("100"))))))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("RECONCILIATION_DIRECTION_MISMATCH");
        assertThat(operations.statements(org,bank,"").getFirst().reconciliationStatus()).isEqualTo("UNMATCHED");
        var in=run(()->operations.importStatements(actor,new StatementImport(bank,List.of(new StatementLineCreate(date,date,n("100"),"EUR",key(),"R3","in"))))).getFirst();
        String otherBank=key();
        jdbc.update("INSERT INTO flowora_bank_account(id,organization_id,code,name,bank_name,account_number_masked,currency_code,ledger_account_code) VALUES (?,?,?,'R3 second bank','Test Bank','****0002','EUR','CASH')",otherBank,org,otherBank);
        assertThatThrownBy(()->run(()->operations.reconcile(actor,key(),new ReconciliationCreate(otherBank,List.of(new ReconciliationLinkCreate(in.id(),payment.id(),n("100"))))))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("RECONCILIATION_BANK_MISMATCH");
        var foreignCurrency=run(()->operations.importStatements(actor,new StatementImport(bank,List.of(new StatementLineCreate(date,date,n("100"),"USD",key(),"R3","different currency"))))).getFirst();
        assertThatThrownBy(()->run(()->operations.reconcile(actor,key(),new ReconciliationCreate(bank,List.of(new ReconciliationLinkCreate(foreignCurrency.id(),payment.id(),n("100"))))))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("RECONCILIATION_CURRENCY_MISMATCH");
        assertThat(run(()->operations.reconcile(actor,key(),new ReconciliationCreate(bank,List.of(new ReconciliationLinkCreate(in.id(),payment.id(),n("100")))))).status()).isEqualTo("CONFIRMED");
    }
    @Test void bankHistoryLinksExplainPartialMatchesAndSurviveReversal() {
        var operations=new FinanceOperationsService(jdbc,ledger); String bank=key();
        jdbc.update("INSERT INTO flowora_bank_account(id,organization_id,code,name,bank_name,account_number_masked,currency_code,ledger_account_code) VALUES (?,?,?,'R5 bank','Synthetic','****0000','EUR','CASH')",bank,org,bank);
        var payment=run(()->documents.createPayment(actor,key(),new PaymentCreate("RECEIPT",customer,bank,date,date,date,"EUR",n("1"),n("30"),"bank history")));
        run(()->documents.postPayment(actor,payment.id(),0));
        var lines=run(()->operations.importStatements(actor,new StatementImport(bank,List.of(new StatementLineCreate(date,date,n("10"),"EUR",key(),null,null),new StatementLineCreate(date,date,n("20"),"EUR",key(),null,null)))));
        var first=run(()->operations.reconcile(actor,key(),new ReconciliationCreate(bank,List.of(new ReconciliationLinkCreate(lines.get(0).id(),payment.id(),n("10"))))));
        run(()->operations.reconcile(actor,key(),new ReconciliationCreate(bank,List.of(new ReconciliationLinkCreate(lines.get(1).id(),payment.id(),n("20"))))));
        assertThat(first.links()).containsExactly(new ReconciliationLinkView(lines.get(0).id(),payment.id(),n("10.0000")));
        assertThat(operations.reconciliations(org,bank)).hasSize(2);
        var reversed=run(()->operations.reverseReconciliation(actor,first.id(),"Correct match"));
        assertThat(reversed.status()).isEqualTo("REVERSED"); assertThat(reversed.links()).isEqualTo(first.links());
        assertThat(operations.statements(org,bank,"UNMATCHED")).extracting(StatementLineView::id).containsExactly(lines.get(0).id());
        assertThat(operations.reconciliations(key(),bank)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=?",Integer.class,org)).isEqualTo(1);
    }
    @Test void bankPickerReturnsOnlyActiveAccountsInCurrentOrganization() {
        String active=key(), inactive=key(), other=key();
        jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'Other bank org','USD')",other);
        try {
            for (String id : List.of(active,inactive)) jdbc.update("INSERT INTO flowora_bank_account(id,organization_id,code,name,bank_name,account_number_masked,currency_code,active) VALUES (?,?,?,'Bank','Synthetic','****0000','USD',?)",id,org,id,id.equals(active));
            jdbc.update("INSERT INTO flowora_bank_account(id,organization_id,code,name,bank_name,account_number_masked,currency_code) VALUES (?,?,?,'Other','Synthetic','****1111','USD')",key(),other,key());
            var result=new FinanceOperationsService(jdbc,ledger).bankAccounts(org);
            assertThat(result).extracting(BankAccountView::id).containsExactly(active);
            assertThat(result.getFirst().currencyCode()).isEqualTo("USD");
        } finally {
            jdbc.update("DELETE FROM flowora_bank_account WHERE organization_id=?",other);
            jdbc.update("DELETE FROM flowora_organization WHERE id=?",other);
        }
    }
    @Test void supplierExceptionApprovalRecordsReasonAndAllowsSeparatePosting() {
        String po=key(),pol=key(),receipt=key(),rl=key();
        jdbc.update("INSERT INTO flowora_purchase_order(id,organization_id,number,supplier_id,warehouse_id,status,buyer_user_id,order_date) VALUES (?,?,?,?,?,'RECEIVED','system:r3-test','2026-10-01')",po,org,po.substring(0,30),supplier,"r3-warehouse");
        jdbc.update("INSERT INTO flowora_purchase_order_line(id,organization_id,purchase_order_id,item_id,ordered_quantity,received_quantity,unit_price) VALUES (?,?,?,?,1,1,10)",pol,org,po,"r3-item");
        jdbc.update("INSERT INTO flowora_purchase_receipt(id,organization_id,number,purchase_order_id,warehouse_id,received_by,status) VALUES (?,?,?,?,?,'system:r3-test','POSTED')",receipt,org,receipt.substring(0,30),po,"r3-warehouse");
        jdbc.update("INSERT INTO flowora_purchase_receipt_line(id,organization_id,purchase_receipt_id,purchase_order_line_id,item_id,quantity,accepted_quantity,unit_cost) VALUES (?,?,?,?,?,1,1,10)",rl,org,receipt,pol,"r3-item");
        var source=new InvoiceSourceCreate("PURCHASE_RECEIPT_LINE",receipt,rl,n("1"),n("12"));
        var draft=run(()->documents.createInvoice(actor,key(),input("SUPPLIER_INVOICE",supplier,"1",List.of(line("12",List.of(source))))));
        assertThat(draft.matchStatus()).isEqualTo("EXCEPTION");
        assertThat(draft.lines().getFirst().matchPriceVarianceRate()).isEqualByComparingTo("20");
        assertThatThrownBy(()->run(()->documents.postInvoice(actor,draft.id(),draft.version()))).isInstanceOf(PlatformApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=?",Integer.class,org)).isZero();
        var approved=run(()->documents.approveMatchException(actor,draft.id(),"  Supplier price adjustment verified  "));
        assertThat(approved.status()).isEqualTo("DRAFT");
        assertThat(approved.matchStatus()).isEqualTo("APPROVED_EXCEPTION");
        assertThat(approved.matchExceptionApprovedBy()).isEqualTo(actor.userId());
        assertThat(approved.matchExceptionReason()).isEqualTo("Supplier price adjustment verified");
        assertThat(approved.version()).isEqualTo(draft.version()+1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=?",Integer.class,org)).isZero();
        assertThatThrownBy(()->run(()->documents.approveMatchException(actor,draft.id(),"different"))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.invoice(org,draft.id()).matchExceptionReason()).isEqualTo(approved.matchExceptionReason());
        var posted=run(()->documents.postInvoice(actor,draft.id(),approved.version()));
        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.matchExceptionApprovedBy()).isEqualTo(actor.userId());
        assertThat(ledger.journals(org,date,date).getFirst().totalDebit()).isEqualByComparingTo("12");
        assertThat(ledger.journals(org,date,date).getFirst().totalCredit()).isEqualByComparingTo("12");
        assertThat(documents.stockInvoiceSources(org).getFirst().remainingQuantity()).isEqualByComparingTo("0");
    }
    @Test void payableDashboardUsesAllPostedBaseBalancesLessAllocationsAndCreditsRegardlessOfRange() {
        var invoice=run(()->documents.createInvoice(actor,key(),input("SUPPLIER_INVOICE",supplier,"2",List.of(line("100",List.of())))));
        run(()->documents.postInvoice(actor,invoice.id(),invoice.version()));
        run(()->documents.createInvoice(actor,key(),input("SUPPLIER_INVOICE",supplier,"2",List.of(line("999",List.of())))));
        var payment=run(()->documents.createPayment(actor,key(),new PaymentCreate("PAYMENT",supplier,null,date,date,date,"EUR",n("2"),n("25"),"balance test")));
        run(()->documents.postPayment(actor,payment.id(),payment.version()));
        run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("25"))));
        var original=documents.invoice(org,invoice.id());
        var source=new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",original.id(),original.lines().getFirst().id(),n("0.1"),n("10"));
        var creditLine=new InvoiceLineCreate(null,"credit",n("0.1"),n("100"),n("0"),n("0"),null,null,List.of(source));
        var credit=run(()->documents.createInvoice(actor,key(),new InvoiceCreate("SUPPLIER_CREDIT",supplier,null,invoice.id(),date,date,date.plusDays(30),date,"EUR",n("2"),List.of(creditLine))));
        run(()->documents.postInvoice(actor,credit.id(),credit.version()));
        var operations=new FinanceOperationsService(jdbc,ledger);
        assertThat(operations.dashboard(org,date,date).payables()).isEqualByComparingTo("130");
        assertThat(operations.dashboard(org,date.plusDays(6),date.plusDays(6)).payables()).isEqualByComparingTo("130");
        assertThat(documents.invoice(org,invoice.id()).allocatedAmount()).isEqualByComparingTo("25");
        assertThat(documents.invoice(org,invoice.id()).creditedAmount()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_payable_document WHERE organization_id=?",Integer.class,org)).isZero();
    }
    @Test void partialRefundsReserveOriginalUnallocatedBalanceAndPreserveLedgerBothSides() {
        for (boolean purchase : List.of(false, true)) {
            String party=purchase?supplier:customer;
            String type=purchase?"PAYMENT":"RECEIPT", refundType=purchase?"SUPPLIER_REFUND":"CUSTOMER_REFUND";
            var original=run(()->documents.createPayment(actor,key(),new PaymentCreate(type,party,null,date,date,date,"EUR",n("1.3"),n("100"),"original")));
            run(()->documents.postPayment(actor,original.id(),0));
            String request=key();
            var input=new PaymentCreate(refundType,party,null,date,date,date,"EUR",n("1.3"),n("40"),"partial refund",original.id());
            var refund=run(()->documents.createPayment(actor,request,input));
            assertThat(refund.originalPaymentId()).isEqualTo(original.id());
            assertThat(run(()->documents.createPayment(actor,request,input)).id()).isEqualTo(refund.id());
            var invoice=run(()->documents.createInvoice(actor,key(),input(purchase?"SUPPLIER_INVOICE":"SALES_INVOICE",party,"1.3",List.of(line("100",List.of())))));
            run(()->documents.postInvoice(actor,invoice.id(),0));
            // A draft refund already reserves 40; ordinary allocation may use the remaining 60.
            var allocation=run(()->documents.allocate(actor,original.id(),new AllocationCreate(invoice.id(),n("60"))));
            var posted=run(()->documents.postPayment(actor,refund.id(),0));
            assertThat(posted.status()).isEqualTo("POSTED");
            assertThat(posted.baseAmount()).isEqualByComparingTo("52");
            assertThat(documents.payment(org,original.id()).allocatedAmount()).isEqualByComparingTo("60");
            run(()->documents.reverseAllocation(actor,allocation.id(),key(),new AllocationReverse("Release balance for final refund")));
            var rest=run(()->documents.createPayment(actor,key(),new PaymentCreate(refundType,party,null,date,date,date,"EUR",n("1.3"),n("60"),"final refund",original.id())));
            run(()->documents.postPayment(actor,rest.id(),0));
            assertThat(jdbc.queryForObject("SELECT SUM(base_debit-base_credit) FROM flowora_journal_line WHERE organization_id=? AND account_code='CASH' AND party_id=?",BigDecimal.class,org,party)).isEqualByComparingTo("0");
            for (var journal:ledger.journals(org,date,date)) assertThat(journal.totalDebit()).isEqualByComparingTo(journal.totalCredit());
        }
    }
    @Test void refundReservationSerializesConcurrentDrafts() throws Exception {
        var original=run(()->documents.createPayment(actor,key(),new PaymentCreate("RECEIPT",customer,null,date,date,date,"EUR",n("1"),n("100"),"original")));
        run(()->documents.postPayment(actor,original.id(),0));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        try {
            java.util.concurrent.Callable<String> work=()->{
                barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);
                try { return run(()->documents.createPayment(actor,key(),new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1"),n("60"),"concurrent partial",original.id()))).status(); }
                catch (PlatformApiException ex) { return ex.code(); }
            };
            var a=pool.submit(work); var b=pool.submit(work);
            assertThat(List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("DRAFT","REFUND_EXCEEDS_REMAINING");
            assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM flowora_payment_v2 WHERE organization_id=? AND original_payment_id=?",BigDecimal.class,org,original.id())).isEqualByComparingTo("60");
        } finally { pool.shutdownNow(); }
    }
    @Test void refundSourceBoundsReplayAndAllocationRemainConsistent() {
        var original=run(()->documents.createPayment(actor,key(),new PaymentCreate("RECEIPT",customer,null,date,date,date,"EUR",n("1.3"),n("100"),"original")));
        run(()->documents.postPayment(actor,original.id(),0));
        assertThatThrownBy(()->run(()->documents.createPayment(actor,key(),new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.3"),n("0.00001"),"overprecision",original.id()))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_AMOUNT_PRECISION");
        String request=key();
        var refund=run(()->documents.createPayment(actor,request,new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.3"),n("40"),"refund",original.id())));
        assertThatThrownBy(()->run(()->documents.createPayment(actor,request,new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.3"),n("41"),"refund",original.id()))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(()->run(()->documents.createPayment(actor,key(),new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.3"),n("61"),"too much",original.id()))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_EXCEEDS_REMAINING");
        assertThatThrownBy(()->run(()->documents.createPayment(actor,key(),new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date.plusDays(1),"EUR",n("1.4"),n("10"),"rate date after accounting",original.id()))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_SOURCE_MISMATCH");
        var invoice=run(()->documents.createInvoice(actor,key(),input("SALES_INVOICE",customer,"1.3",List.of(line("100",List.of())))));
        run(()->documents.postInvoice(actor,invoice.id(),0));
        assertThatThrownBy(()->run(()->documents.allocate(actor,original.id(),new AllocationCreate(invoice.id(),n("61")))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("ALLOCATION_CONSUMES_REFUND_RESERVATION");
        run(()->documents.postPayment(actor,refund.id(),0));
        assertThatThrownBy(()->run(()->documents.allocate(actor,refund.id(),new AllocationCreate(invoice.id(),n("10")))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("ALLOCATION_DOCUMENT_TYPE_MISMATCH");
        assertThat(documents.invoice(org,invoice.id()).allocatedAmount()).isEqualByComparingTo("0");
        assertThat(documents.payment(org,original.id()).allocatedAmount()).isEqualByComparingTo("0");
        // Simulate a pre-migration refund whose source cannot safely be inferred.
        jdbc.update("UPDATE flowora_payment_v2 SET original_payment_id=NULL WHERE organization_id=? AND id=?",org,refund.id());
        assertThatThrownBy(()->run(()->documents.createPayment(actor,key(),new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.3"),n("10"),"new refund",original.id()))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_HISTORY_REQUIRES_RECONCILIATION");
        assertThatThrownBy(()->run(()->documents.allocate(actor,original.id(),new AllocationCreate(invoice.id(),n("10")))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_HISTORY_REQUIRES_RECONCILIATION");
    }
    PaymentView historicalRefund(PaymentView source, String amount) {
        var draft=correctionDraft(source,amount);var posted=run(()->documents.postPayment(actor,draft.id(),0));
        // Explicit disposable migration fixture: old refunds predate source/basis columns.
        jdbc.update("UPDATE flowora_payment_v2 SET original_payment_id=NULL,refund_source_base_amount=NULL WHERE organization_id=? AND id=?",org,posted.id());
        return documents.payment(org,posted.id());
    }
    RefundSourceRecovery recoveryInput(PaymentView refund, PaymentView source, String reason) {
        return new RefundSourceRecovery(source.id(),refund.version(),source.version(),reason);
    }
    @Test void explicitHistoricalRecoveryRetainsActualJournalsAndReasonBothSidesEvenInClosedPeriod() {
        for(boolean purchase:List.of(false,true)) {
            var source=originalRefundPayment(purchase,null);var refund=historicalRefund(source,"40");source=documents.payment(org,source.id());
            int journalCount=ledger.journals(org,date,date).size();
            var before=ledger.journals(org,date,date);
            jdbc.update("UPDATE flowora_accounting_period SET status='CLOSED' WHERE organization_id=?",org);
            var input=recoveryInput(refund,source,"Checked original remittance evidence");String request=key();
            var restored=run(()->documents.recoverRefundSource(actor,refund.id(),request,input));
            assertThat(restored.originalPaymentId()).isEqualTo(source.id());assertThat(restored.refundSourceBaseAmount()).isEqualByComparingTo("52");
            assertThat(restored.realizedExchangeDifference()).isEqualByComparingTo("0");
            assertThat(restored.sourceRecovery().reason()).isEqualTo(input.reason());assertThat(restored.sourceRecovery().actorUserId()).isEqualTo(actor.userId());
            assertThat(restored.version()).isEqualTo(refund.version()+1);
            assertThat(documents.payment(org,source.id()).version()).isEqualTo(source.version()+1);
            assertThat(ledger.journals(org,date,date)).isEqualTo(before);assertThat(ledger.journals(org,date,date)).hasSize(journalCount);
            assertThat(run(()->documents.recoverRefundSource(actor,refund.id(),request,input)).version()).isEqualTo(restored.version());
            assertThatThrownBy(()->run(()->documents.recoverRefundSource(actor,refund.id(),request,new RefundSourceRecovery(input.originalPaymentId(),input.version(),input.originalVersion(),"Different reason"))))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
            assertThatThrownBy(()->run(()->documents.recoverRefundSource(actor,refund.id(),key(),input)))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_RECOVERY_STATE_CONFLICT");
            jdbc.update("UPDATE flowora_accounting_period SET status='OPEN' WHERE organization_id=?",org);
            assertThat(correctionDraft(documents.payment(org,source.id()),"60").status()).isEqualTo("DRAFT");
        }
    }
    @Test void historicalRecoveryChecksExistingTransactionAndCarryingReservationsWithoutMutatingJournals() {
        var source=originalRefundPayment(false,null);var reserved=correctionDraft(source,"80");
        var other=originalRefundPayment(false,null);var refund=historicalRefund(other,"30");
        var body=recoveryInput(refund,documents.payment(org,source.id()),"Source selected after checking records");
        assertThatThrownBy(()->run(()->documents.recoverRefundSource(actor,refund.id(),key(),body)))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_EXCEEDS_REMAINING");
        assertThat(documents.payment(org,refund.id()).originalPaymentId()).isNull();assertThat(documents.payment(org,refund.id()).sourceRecovery()).isNull();
        run(()->documents.correctRefund(actor,reserved.id(),key(),new RefundCorrection(0,date,"Release incorrect reservation"),false));
        var restored=run(()->documents.recoverRefundSource(actor,refund.id(),key(),body));
        assertThat(restored.originalPaymentId()).isEqualTo(source.id());
    }
    @Test void historicalRecoveryRejectsRateMismatchAndMissingOrInconsistentActualJournal() {
        var source=originalRefundPayment(false,null);
        var other=run(()->documents.createPayment(actor,key(),new PaymentCreate("RECEIPT",customer,null,date,date,date,"EUR",n("1.4"),n("100"),"Other original")));
        var otherPosted=run(()->documents.postPayment(actor,other.id(),0));
        var refund=historicalRefund(source,"40");
        assertThatThrownBy(()->run(()->documents.recoverRefundSource(actor,refund.id(),key(),recoveryInput(refund,otherPosted,"Different rate is not safe historical reconciliation"))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_RECOVERY_SOURCE_MISMATCH");
        var body=recoveryInput(refund,documents.payment(org,source.id()),"Actual evidence required");
        String journal=jdbc.queryForObject("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND source_type='CUSTOMER_REFUND' AND source_id=?",String.class,org,refund.id());
        jdbc.update("UPDATE flowora_journal_entry SET source_id=? WHERE organization_id=? AND id=?",key(),org,journal);
        assertThatThrownBy(()->run(()->documents.recoverRefundSource(actor,refund.id(),key(),body))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_RECOVERY_JOURNAL_MISMATCH");
        jdbc.update("UPDATE flowora_journal_entry SET source_id=?,total_debit=41 WHERE organization_id=? AND id=?",refund.id(),org,journal);
        assertThatThrownBy(()->run(()->documents.recoverRefundSource(actor,refund.id(),key(),body))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_RECOVERY_JOURNAL_MISMATCH");
        assertThat(documents.payment(org,refund.id()).sourceRecovery()).isNull();
    }
    @Test void unresolvedHistoryContinuesBlockingUntilEveryRefundIsExplicitlyRecovered() {
        var source=originalRefundPayment(false,null);
        var a=correctionDraft(source,"40");run(()->documents.postPayment(actor,a.id(),0));
        var b=correctionDraft(source,"30");run(()->documents.postPayment(actor,b.id(),0));
        jdbc.update("UPDATE flowora_payment_v2 SET original_payment_id=NULL,refund_source_base_amount=NULL WHERE organization_id=? AND id IN (?,?)",org,a.id(),b.id());
        var first=documents.payment(org,a.id());var second=documents.payment(org,b.id());
        assertThatThrownBy(()->correctionDraft(source,"10")).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_HISTORY_REQUIRES_RECONCILIATION");
        run(()->documents.recoverRefundSource(actor,first.id(),key(),recoveryInput(first,documents.payment(org,source.id()),"First checked source")));
        assertThatThrownBy(()->correctionDraft(source,"10")).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_HISTORY_REQUIRES_RECONCILIATION");
        run(()->documents.recoverRefundSource(actor,second.id(),key(),recoveryInput(second,documents.payment(org,source.id()),"Second checked source")));
        assertThat(correctionDraft(source,"30").status()).isEqualTo("DRAFT");
        var restored=documents.payment(org,first.id());
        var reversed=run(()->documents.correctRefund(actor,restored.id(),key(),new RefundCorrection(restored.version(),date,"Normal reversal after source recovery"),true));
        assertThat(reversed.sourceRecovery().reason()).isEqualTo("First checked source");assertThat(reversed.correction()).isNotNull();
    }
    @Test void legacyPartyClassificationMustMatchTheReceiptAndRefundDirection() {
        var source=originalRefundPayment(false,null);var refund=historicalRefund(source,"40");
        var input=recoveryInput(refund,documents.payment(org,source.id()),"Classification must agree with document direction");
        assertThatThrownBy(()->run(()->{
            // Disposable legacy-import fixture: internally matching but incorrectly classified rows.
            jdbc.update("UPDATE flowora_payment_v2 SET party_type='SUPPLIER' WHERE organization_id=? AND id IN (?,?)",org,source.id(),refund.id());
            jdbc.update("UPDATE flowora_journal_line SET party_type='SUPPLIER' WHERE organization_id=?",org);
            return documents.recoverRefundSource(actor,refund.id(),key(),input);
        })).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_RECOVERY_SOURCE_MISMATCH");
        assertThat(documents.payment(org,refund.id()).sourceRecovery()).isNull();
    }
    @Test void recoveryChecksCurrentJournalStatusAfterAnotherTransactionReversesTheSource() {
        var source=originalRefundPayment(false,null);var refund=historicalRefund(source,"40");
        var input=recoveryInput(refund,documents.payment(org,source.id()),"Must use current journal evidence");
        String journal=jdbc.queryForObject("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND source_type='RECEIPT' AND source_id=?",String.class,org,source.id());
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(()->run(()->{
                documents.payment(org,source.id()); // Establish an earlier consistent-read snapshot. The concurrent SQL below models a legacy import status change.
                try {pool.submit(()->run(()->jdbc.update("UPDATE flowora_journal_entry SET status='REVERSED',version_no=version_no+1 WHERE organization_id=? AND id=?",org,journal))).get(20,java.util.concurrent.TimeUnit.SECONDS);}
                catch(Exception ex){throw new IllegalStateException(ex);}
                return documents.recoverRefundSource(actor,refund.id(),key(),input);
            })).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_RECOVERY_JOURNAL_MISMATCH");
            assertThat(documents.payment(org,refund.id()).originalPaymentId()).isNull();
            assertThat(documents.payment(org,refund.id()).sourceRecovery()).isNull();
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentHistoricalRecoveryToOneOriginalRequiresFreshVersionAndCannotOverReserve() throws Exception {
        var source=originalRefundPayment(false,null);var other=originalRefundPayment(false,null);
        var a=correctionDraft(source,"60");run(()->documents.postPayment(actor,a.id(),0));
        var b=correctionDraft(other,"60");run(()->documents.postPayment(actor,b.id(),0));
        jdbc.update("UPDATE flowora_payment_v2 SET original_payment_id=NULL,refund_source_base_amount=NULL WHERE organization_id=? AND id IN (?,?)",org,a.id(),b.id());
        var first=documents.payment(org,a.id());var second=documents.payment(org,b.id());var original=documents.payment(org,source.id());
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var barrier=new java.util.concurrent.CyclicBarrier(2);
        try {
            var one=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);try{run(()->documents.recoverRefundSource(actor,first.id(),key(),recoveryInput(first,original,"Checked first history")));return "RECOVERED";}catch(PlatformApiException ex){return ex.code();}});
            var two=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);try{run(()->documents.recoverRefundSource(actor,second.id(),key(),recoveryInput(second,original,"Checked second history")));return "RECOVERED";}catch(PlatformApiException ex){return ex.code();}});
            assertThat(List.of(one.get(20,java.util.concurrent.TimeUnit.SECONDS),two.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("RECOVERED","REFUND_RECOVERY_SOURCE_MISMATCH");
            assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM flowora_payment_v2 WHERE organization_id=? AND original_payment_id=?",BigDecimal.class,org,source.id())).isEqualByComparingTo("60");
        }finally{pool.shutdownNow();}
    }

    BigDecimal accountNet(String account, String party) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(base_debit-base_credit),0) FROM flowora_journal_line WHERE organization_id=? AND account_code=? AND party_id=?",BigDecimal.class,org,account,party);
    }
    PaymentView exchangeRefund(PaymentView original, String amount, String rate) {
        return run(()->documents.createPayment(actor,key(),new PaymentCreate(original.paymentType().equals("PAYMENT")?"SUPPLIER_REFUND":"CUSTOMER_REFUND",original.partyId(),null,date,date,date,"EUR",n(rate),n(amount),"Exchange refund",original.id())));
    }
    @Test void differentRateRefundsClearOriginalCarryingValueAndRecognizeGainLossBothSides() {
        for(boolean purchase:List.of(false,true)) {
            var original=originalRefundPayment(purchase,null);
            for(String rate:List.of("1.4","1.2")) {
                var draft=exchangeRefund(original,"100",rate);
                assertThat(draft.refundSourceBaseAmount()).isEqualByComparingTo("130");
                assertThat(draft.realizedExchangeDifference()).isEqualByComparingTo(rate.equals("1.4")?"10":"-10");
                var posted=run(()->documents.postPayment(actor,draft.id(),0));
                var fx=ledger.journal(org,posted.refundExchangeJournalId());
                assertThat(fx.sourceType()).isEqualTo("REFUND_EXCHANGE");assertThat(fx.currencyCode()).isEqualTo("USD");assertThat(fx.exchangeRate()).isEqualByComparingTo("1");
                assertThat(accountNet(purchase?"PAYABLE":"RECEIVABLE",original.partyId())).isEqualByComparingTo("0");
                boolean gain=purchase==rate.equals("1.4");
                assertThat(accountNet(gain?"FX_GAIN":"FX_LOSS",original.partyId())).isEqualByComparingTo(gain?"-10":"10");
                assertThatThrownBy(()->run(()->ledger.reverse(org,actor.userId(),fx.id(),date,"Business action required",key())))
                        .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_REQUIRES_BUSINESS_REVERSAL");
                String request=key();var correction=new RefundCorrection(posted.version(),date,"Reverse cash and exchange together");
                var reversed=run(()->documents.correctRefund(actor,posted.id(),request,correction,true));
                assertThat(ledger.journal(org,reversed.correction().exchangeJournalEntryId()).reversalOfId()).isEqualTo(fx.id());
                assertThat(ledger.journal(org,fx.id()).status()).isEqualTo("REVERSED");
                assertThat(run(()->documents.correctRefund(actor,posted.id(),request,correction,true)).correction().exchangeJournalEntryId()).isEqualTo(reversed.correction().exchangeJournalEntryId());
                assertThat(accountNet("FX_GAIN",original.partyId())).isEqualByComparingTo("0");assertThat(accountNet("FX_LOSS",original.partyId())).isEqualByComparingTo("0");
                assertThat(accountNet("CASH",original.partyId())).isEqualByComparingTo(purchase?"-130":"130");
            }
        }
    }
    @Test void allocatedSourceAndPartialExchangeRefundKeepRemainingInvoiceAtItsCarryingValueBothSides() {
        for(boolean purchase:List.of(false,true)) {
            var original=originalRefundPayment(purchase,null);
            var invoice=run(()->documents.createInvoice(actor,key(),input(purchase?"SUPPLIER_INVOICE":"SALES_INVOICE",original.partyId(),"1.3",List.of(line("100",List.of())))));
            run(()->documents.postInvoice(actor,invoice.id(),0));
            run(()->documents.allocate(actor,original.id(),new AllocationCreate(invoice.id(),n("60"))));
            var draft=exchangeRefund(original,"40","1.4");
            assertThat(draft.refundSourceBaseAmount()).isEqualByComparingTo("52");
            assertThat(draft.realizedExchangeDifference()).isEqualByComparingTo("4");
            run(()->documents.postPayment(actor,draft.id(),0));
            assertThat(accountNet(purchase?"PAYABLE":"RECEIVABLE",original.partyId())).isEqualByComparingTo(purchase?"-52":"52");
            assertThat(documents.invoice(org,invoice.id()).allocatedAmount()).isEqualByComparingTo("60");
            assertThatThrownBy(()->exchangeRefund(original,"0.0001","1.2")).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_EXCEEDS_REMAINING");
        }
    }
    @Test void splitRefundsUseTheirSettlementRatesAndPreserveExactSourceRoundingResidual() {
        var original=run(()->documents.createPayment(actor,key(),new PaymentCreate("RECEIPT",customer,null,date,date,date,"EUR",n("1.33333333"),n("0.0003"),"Rounding source")));
        run(()->documents.postPayment(actor,original.id(),0));
        BigDecimal carrying=BigDecimal.ZERO;
        for(int i=0;i<3;i++) {
            var draft=exchangeRefund(original,"0.0001",i==1?"1.8":"1.1");carrying=carrying.add(draft.refundSourceBaseAmount());
            run(()->documents.postPayment(actor,draft.id(),0));
        }
        assertThat(carrying).isEqualByComparingTo(original.baseAmount());
        assertThat(accountNet("RECEIVABLE",customer)).isEqualByComparingTo("0");
        for(var row:ledger.journals(org,date,date)) {
            assertThat(row.totalDebit()).isEqualByComparingTo(row.totalCredit());
            assertThat(row.lines().stream().map(JournalLineView::baseDebit).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(row.lines().stream().map(JournalLineView::baseCredit).reduce(BigDecimal.ZERO,BigDecimal::add));
        }
    }
    @Test void exchangePostingAndReversalRollBackBothJournalsOnMissingMappingOrClosedPeriod() {
        var original=originalRefundPayment(false,null);var draft=exchangeRefund(original,"40","1.4");
        jdbc.update("UPDATE flowora_posting_mapping SET active=FALSE WHERE organization_id=? AND semantic_code='FX_LOSS'",org);
        assertThatThrownBy(()->run(()->documents.postPayment(actor,draft.id(),0))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.payment(org,draft.id()).status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND source_id=?",Integer.class,org,draft.id())).isZero();
        jdbc.update("UPDATE flowora_posting_mapping SET active=TRUE WHERE organization_id=? AND semantic_code='FX_LOSS'",org);
        var posted=run(()->documents.postPayment(actor,draft.id(),0));
        jdbc.update("UPDATE flowora_accounting_period SET status='CLOSED' WHERE organization_id=?",org);
        assertThatThrownBy(()->run(()->documents.correctRefund(actor,posted.id(),key(),new RefundCorrection(posted.version(),date,"Closed period"),true))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.payment(org,posted.id()).correction()).isNull();
        assertThat(ledger.journal(org,posted.refundExchangeJournalId()).status()).isEqualTo("POSTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND source_type='JOURNAL_REVERSAL'",Integer.class,org)).isZero();
    }
    @Test void changedRateReplayIsBoundToContentAndCancelledDraftReleasesCarryingReservation() {
        var original=originalRefundPayment(false,null);String request=key();
        var body=new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.4"),n("40"),"Explicit rate",original.id());
        var draft=run(()->documents.createPayment(actor,request,body));
        assertThat(run(()->documents.createPayment(actor,request,body)).id()).isEqualTo(draft.id());
        assertThatThrownBy(()->run(()->documents.createPayment(actor,request,new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.2"),n("40"),"Explicit rate",original.id()))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        run(()->documents.correctRefund(actor,draft.id(),key(),new RefundCorrection(0,date,"Cancel FX draft"),false));
        assertThat(exchangeRefund(original,"100","1.2").refundSourceBaseAmount()).isEqualByComparingTo("130");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND source_type='REFUND_EXCHANGE'",Integer.class,org)).isZero();
    }

    PaymentView originalRefundPayment(boolean purchase, String bank) {
        var row=run(()->documents.createPayment(actor,key(),new PaymentCreate(purchase?"PAYMENT":"RECEIPT",purchase?supplier:customer,bank,date,date,date,"EUR",n("1.3"),n("100"),"refund correction source")));
        return run(()->documents.postPayment(actor,row.id(),0));
    }
    PaymentView correctionDraft(PaymentView original, String amount) {
        return run(()->documents.createPayment(actor,key(),new PaymentCreate(original.paymentType().equals("RECEIPT")?"CUSTOMER_REFUND":"SUPPLIER_REFUND",original.partyId(),original.bankAccountId(),date,date,date,"EUR",n("1.3"),n(amount),"correction draft",original.id())));
    }
    @Test void cancelledRefundDraftReleasesBalanceAndKeepsImmutableReasonBothSides() {
        for(boolean purchase:List.of(false,true)) {
            var original=originalRefundPayment(purchase,null); var draft=correctionDraft(original,"100");
            String request=key();var body=new RefundCorrection(0,date,"Cancel incorrect draft");
            var cancelled=run(()->documents.correctRefund(actor,draft.id(),request,body,false));
            assertThat(cancelled.status()).isEqualTo("CANCELLED");
            assertThat(cancelled.correction().reason()).isEqualTo("Cancel incorrect draft");
            assertThat(cancelled.correction().actorUserId()).isEqualTo(actor.userId());
            assertThat(cancelled.correction().journalEntryId()).isNull();
            assertThat(run(()->documents.correctRefund(actor,draft.id(),request,body,false)).version()).isEqualTo(1);
            assertThatThrownBy(()->run(()->documents.correctRefund(actor,draft.id(),request,new RefundCorrection(0,date,"changed"),false)))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
            assertThat(correctionDraft(original,"100").status()).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND source_id=?",Integer.class,org,draft.id())).isZero();
        }
    }
    @Test void refundBusinessReversalRestoresCashAndCapacityBothSidesWithoutDeletingJournals() {
        for(boolean purchase:List.of(false,true)) {
            var original=originalRefundPayment(purchase,null);var draft=correctionDraft(original,"40");
            var posted=run(()->documents.postPayment(actor,draft.id(),0));
            String journal=jdbc.queryForObject("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND source_id=?",String.class,org,posted.id());
            assertThatThrownBy(()->run(()->ledger.reverse(org,actor.userId(),journal,date,"use business correction",key())))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_REQUIRES_BUSINESS_REVERSAL");
            String request=key();var body=new RefundCorrection(posted.version(),date,"Reverse incorrect refund");
            var reversed=run(()->documents.correctRefund(actor,posted.id(),request,body,true));
            assertThat(reversed.status()).isEqualTo("REVERSED");
            assertThat(reversed.correction().action()).isEqualTo("REVERSE");
            assertThat(run(()->documents.correctRefund(actor,posted.id(),request,body,true)).version()).isEqualTo(reversed.version());
            assertThat(ledger.journal(org,journal).status()).isEqualTo("REVERSED");
            var reversal=ledger.journal(org,reversed.correction().journalEntryId());
            assertThat(reversal.reversalOfId()).isEqualTo(journal);
            assertThat(reversal.exchangeRate()).isEqualByComparingTo("1.3");
            assertThatThrownBy(()->run(()->ledger.reverse(org,actor.userId(),reversal.id(),date,"do not reverse correction alone",key())))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_REQUIRES_BUSINESS_REVERSAL");
            assertThat(jdbc.queryForObject("SELECT SUM(base_debit-base_credit) FROM flowora_journal_line WHERE organization_id=? AND account_code='CASH' AND party_id=?",BigDecimal.class,org,original.partyId())).isEqualByComparingTo(purchase?"-130":"130");
            assertThat(correctionDraft(original,"100").status()).isEqualTo("DRAFT");
            for(var row:ledger.journals(org,date,date))assertThat(row.totalDebit()).isEqualByComparingTo(row.totalCredit());
        }
    }
    @Test void refundReversalRequiresOpenPeriodAndReleasedBankReconciliation() {
        String bank=key();jdbc.update("INSERT INTO flowora_bank_account(id,organization_id,code,name,bank_name,account_number_masked,currency_code,ledger_account_code) VALUES (?,?,?,'Refund bank','Synthetic','****0000','EUR','CASH')",bank,org,bank);
        var original=originalRefundPayment(false,bank);var draft=correctionDraft(original,"40");var posted=run(()->documents.postPayment(actor,draft.id(),0));
        var ops=new FinanceOperationsService(jdbc,ledger);
        var lines=run(()->ops.importStatements(actor,new StatementImport(bank,List.of(new StatementLineCreate(date,null,n("-40"),"EUR","refund-bank",null,null)))));
        var match=run(()->ops.reconcile(actor,key(),new ReconciliationCreate(bank,List.of(new ReconciliationLinkCreate(lines.getFirst().id(),posted.id(),n("40"))))));
        var body=new RefundCorrection(posted.version(),date,"Reverse after bank correction");
        assertThatThrownBy(()->run(()->documents.correctRefund(actor,posted.id(),key(),body,true)))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_RECONCILIATION_MUST_BE_REVERSED");
        run(()->ops.reverseReconciliation(actor,match.id(),"Release incorrect match"));
        jdbc.update("UPDATE flowora_accounting_period SET status='CLOSED' WHERE organization_id=?",org);
        assertThatThrownBy(()->run(()->documents.correctRefund(actor,posted.id(),key(),body,true))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.payment(org,posted.id()).status()).isEqualTo("POSTED");
        assertThat(documents.payment(org,posted.id()).correction()).isNull();
        jdbc.update("UPDATE flowora_accounting_period SET status='OPEN' WHERE organization_id=?",org);
        assertThat(run(()->documents.correctRefund(actor,posted.id(),key(),body,true)).status()).isEqualTo("REVERSED");
        assertThat(ops.reconciliations(org,bank).stream().filter(row->row.id().equals(match.id())).findFirst().orElseThrow().status()).isEqualTo("REVERSED");
    }
    @Test void simultaneousRefundPostAndCancelHaveExactlyOneConsistentOutcome() throws Exception {
        var original=originalRefundPayment(false,null);var draft=correctionDraft(original,"40");
        var barrier=new java.util.concurrent.CyclicBarrier(2);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var post=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);try{return run(()->documents.postPayment(actor,draft.id(),0)).status();}catch(PlatformApiException ex){return ex.code();}});
            var cancel=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);try{return run(()->documents.correctRefund(actor,draft.id(),key(),new RefundCorrection(0,date,"Concurrent cancellation"),false)).status();}catch(PlatformApiException ex){return ex.code();}});
            var results=List.of(post.get(15,java.util.concurrent.TimeUnit.SECONDS),cancel.get(15,java.util.concurrent.TimeUnit.SECONDS));
            var result=documents.payment(org,draft.id());
            if(result.status().equals("CANCELLED")) {
                assertThat(results).containsExactlyInAnyOrder("CANCELLED","PAYMENT_STATE_CONFLICT");
                assertThat(result.correction()).isNotNull();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND source_id=?",Integer.class,org,draft.id())).isZero();
            } else {assertThat(results).containsExactlyInAnyOrder("POSTED","REFUND_CORRECTION_STATE_CONFLICT");assertThat(result.correction()).isNull();}
        } finally {pool.shutdownNow();}
    }
    @Test void originalPaymentCancellationAndBusinessReversalKeepAuditAndExactBalancesBothSides() {
        for(boolean purchase:List.of(false,true)) {
            var draft=run(()->documents.createPayment(actor,key(),new PaymentCreate(purchase?"PAYMENT":"RECEIPT",purchase?supplier:customer,null,date,date,date,"EUR",n("1.3"),n("100"),"Original correction")));
            String cancelKey=key();var cancelBody=new PaymentCorrection(draft.version(),date,"Cancel unused original");
            var cancelled=run(()->documents.correctPayment(actor,draft.id(),cancelKey,cancelBody,false));
            assertThat(cancelled.status()).isEqualTo("CANCELLED");assertThat(cancelled.paymentCorrection().journalEntryId()).isNull();
            assertThat(run(()->documents.correctPayment(actor,draft.id(),cancelKey,cancelBody,false)).version()).isEqualTo(cancelled.version());
            assertThatThrownBy(()->run(()->documents.correctPayment(actor,draft.id(),cancelKey,new PaymentCorrection(draft.version(),date,"Changed reason"),false)))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
            assertThatThrownBy(()->run(()->documents.postPayment(actor,draft.id(),cancelled.version()))).isInstanceOf(PlatformApiException.class);
            var original=originalRefundPayment(purchase,null);
            String journal=jdbc.queryForObject("SELECT id FROM flowora_journal_entry WHERE organization_id=? AND source_type=? AND source_id=?",String.class,org,original.paymentType(),original.id());
            assertThatThrownBy(()->run(()->ledger.reverse(org,actor.userId(),journal,date,"Use business correction",key())))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("PAYMENT_REQUIRES_BUSINESS_REVERSAL");
            String request=key();var body=new PaymentCorrection(original.version(),date.plusDays(1),"Reverse incorrect original");
            var reversed=run(()->documents.correctPayment(actor,original.id(),request,body,true));
            assertThat(reversed.status()).isEqualTo("REVERSED");assertThat(reversed.paymentCorrection().actorUserId()).isEqualTo(actor.userId());
            assertThat(run(()->documents.correctPayment(actor,original.id(),request,body,true)).version()).isEqualTo(reversed.version());
            assertThat(ledger.journal(org,journal).status()).isEqualTo("REVERSED");
            var reversal=ledger.journal(org,reversed.paymentCorrection().journalEntryId());
            assertThat(reversal.reversalOfId()).isEqualTo(journal);assertThat(reversal.exchangeRate()).isEqualByComparingTo("1.3");
            assertThat(reversal.accountingDate()).isEqualTo(date.plusDays(1));
            assertThatThrownBy(()->run(()->ledger.reverse(org,actor.userId(),reversal.id(),date.plusDays(1),"Cannot undo correction alone",key())))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("PAYMENT_REQUIRES_BUSINESS_REVERSAL");
            assertThat(accountNet("CASH",original.partyId())).isEqualByComparingTo("0");
            assertThat(accountNet(purchase?"PAYABLE":"RECEIVABLE",original.partyId())).isEqualByComparingTo("0");
            assertThatThrownBy(()->correctionDraft(original,"1")).isInstanceOf(PlatformApiException.class);
        }
    }
    @Test void originalCorrectionRequiresAllocationReleaseEvenWhenHeaderWasInconsistent() {
        var allocation=allocate();var payment=documents.payment(org,allocation.paymentId());
        var body=new PaymentCorrection(payment.version(),date,"Reverse only after explicit allocation reversal");
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,payment.id(),key(),body,true)))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("PAYMENT_HAS_ALLOCATIONS");
        jdbc.update("UPDATE flowora_payment_v2 SET allocated_amount=0 WHERE organization_id=? AND id=?",org,payment.id());
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,payment.id(),key(),body,true)))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("PAYMENT_HAS_ALLOCATIONS");
        jdbc.update("UPDATE flowora_payment_v2 SET allocated_amount=? WHERE organization_id=? AND id=?",payment.allocatedAmount(),org,payment.id());
        run(()->documents.reverseAllocation(actor,allocation.id(),key(),new AllocationReverse("Release allocation first")));
        var current=documents.payment(org,payment.id());
        assertThat(run(()->documents.correctPayment(actor,current.id(),key(),new PaymentCorrection(current.version(),date,"Released allocation"),true)).status()).isEqualTo("REVERSED");
        assertThat(accountNet("CASH",customer)).isEqualByComparingTo("0");
    }
    @Test void originalCorrectionRequiresAllRefundReservationsAndUnlinkedHistoryToBeResolved() {
        for(boolean purchase:List.of(false,true)) {
            var original=originalRefundPayment(purchase,null);var refund=correctionDraft(original,"30");
            var body=new PaymentCorrection(original.version(),date,"Reverse after refunds resolved");
            assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),body,true)))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("PAYMENT_HAS_ACTIVE_REFUNDS");
            var posted=run(()->documents.postPayment(actor,refund.id(),0));

            assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),body,true))).isInstanceOf(PlatformApiException.class);
            run(()->documents.correctRefund(actor,posted.id(),key(),new RefundCorrection(posted.version(),date,"Undo refund first"),true));
            var legacy=historicalRefund(original,"10");
            assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),body,true)))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("PAYMENT_HAS_ACTIVE_REFUNDS");
            var recovered=run(()->documents.recoverRefundSource(actor,legacy.id(),key(),recoveryInput(legacy,documents.payment(org,original.id()),"Verified legacy history")));
            run(()->documents.correctRefund(actor,recovered.id(),key(),new RefundCorrection(recovered.version(),date,"Undo recovered refund"),true));
            var fresh=documents.payment(org,original.id());
            assertThat(run(()->documents.correctPayment(actor,fresh.id(),key(),new PaymentCorrection(fresh.version(),date,"All refunds reversed"),true)).status()).isEqualTo("REVERSED");
            assertThat(accountNet("CASH",original.partyId())).isEqualByComparingTo("0");
        }
    }
    @Test void originalCorrectionRequiresOpenPeriodAndBankReleaseAndRollsBackAtomically() {
        String bank=key();jdbc.update("INSERT INTO flowora_bank_account(id,organization_id,code,name,bank_name,account_number_masked,currency_code,ledger_account_code) VALUES (?,?,?,'Original bank','Synthetic','****0000','EUR','CASH')",bank,org,bank);
        var original=originalRefundPayment(false,bank);var ops=new FinanceOperationsService(jdbc,ledger);
        var lines=run(()->ops.importStatements(actor,new StatementImport(bank,List.of(new StatementLineCreate(date,null,n("100"),"EUR","original-bank",null,null)))));
        var match=run(()->ops.reconcile(actor,key(),new ReconciliationCreate(bank,List.of(new ReconciliationLinkCreate(lines.getFirst().id(),original.id(),n("100"))))));
        var body=new PaymentCorrection(original.version(),date,"Correction after bank release");
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),body,true)))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("PAYMENT_RECONCILIATION_MUST_BE_REVERSED");
        run(()->ops.reverseReconciliation(actor,match.id(),"Release match"));
        jdbc.update("UPDATE flowora_accounting_period SET status='CLOSED' WHERE organization_id=?",org);
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),body,true))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.payment(org,original.id()).status()).isEqualTo("POSTED");assertThat(documents.payment(org,original.id()).paymentCorrection()).isNull();
        assertThat(ledger.journals(org,date,date)).hasSize(1);
        jdbc.update("UPDATE flowora_accounting_period SET status='OPEN' WHERE organization_id=?",org);
        assertThat(run(()->documents.correctPayment(actor,original.id(),key(),body,true)).status()).isEqualTo("REVERSED");
    }
    @Test void originalCorrectionRejectsMissingActualEvidenceWrongVersionTypeAndDate() {
        var original=originalRefundPayment(false,null);
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),new PaymentCorrection(0,date,"Stale version"),true))).isInstanceOf(PlatformApiException.class);
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),new PaymentCorrection(original.version(),date.minusDays(1),"Earlier date"),true))).isInstanceOf(PlatformApiException.class);
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),new PaymentCorrection(original.version(),date," "),true))).isInstanceOf(PlatformApiException.class);
        var refund=correctionDraft(original,"10");
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,refund.id(),key(),new PaymentCorrection(0,date,"Wrong action"),false))).isInstanceOf(PlatformApiException.class);
        run(()->documents.correctRefund(actor,refund.id(),key(),new RefundCorrection(0,date,"Cancel reservation"),false));
        // Scoped disposable legacy fixture: header still POSTED but the actual original journal is inconsistent.
        jdbc.update("UPDATE flowora_journal_line SET base_debit=base_debit+1 WHERE organization_id=? AND debit>0",org);
        assertThatThrownBy(()->run(()->documents.correctPayment(actor,original.id(),key(),new PaymentCorrection(original.version(),date,"Needs manual reconciliation"),true))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.payment(org,original.id()).paymentCorrection()).isNull();assertThat(ledger.journals(org,date,date)).hasSize(1);
    }
    @Test void concurrentOriginalReversalAndRefundReservationCannotBothSucceed() throws Exception {
        var original=originalRefundPayment(false,null);var barrier=new java.util.concurrent.CyclicBarrier(2);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var reverse=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);try{return run(()->documents.correctPayment(actor,original.id(),key(),new PaymentCorrection(original.version(),date,"Concurrent correction"),true)).status();}catch(PlatformApiException ex){return ex.code();}});
            var refund=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);try{return correctionDraft(original,"10").status();}catch(PlatformApiException ex){return ex.code();}});
            var results=List.of(reverse.get(20,java.util.concurrent.TimeUnit.SECONDS),refund.get(20,java.util.concurrent.TimeUnit.SECONDS));
            var current=documents.payment(org,original.id());
            if("REVERSED".equals(current.status())) {assertThat(results).containsExactlyInAnyOrder("REVERSED","REFUND_SOURCE_MISMATCH");assertThat(current.paymentCorrection()).isNotNull();}
            else {assertThat(results).containsExactlyInAnyOrder("PAYMENT_HAS_ACTIVE_REFUNDS","DRAFT");assertThat(current.paymentCorrection()).isNull();}
        } finally {pool.shutdownNow();}
    }
    InvoiceView refundInvoice(boolean purchase) {
        var draft=run(()->documents.createInvoice(actor,key(),input(purchase?"SUPPLIER_INVOICE":"SALES_INVOICE",purchase?supplier:customer,"1.2",List.of(line("100",List.of())))));
        return run(()->documents.postInvoice(actor,draft.id(),0));
    }
    InvoiceView creditFor(InvoiceView original,String amount) {
        var row=original.lines().getFirst();BigDecimal quantity=n(amount).divide(n("100"));
        var source=new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",original.id(),row.id(),quantity,n(amount));
        var line=new InvoiceLineCreate(null,"Returned invoiced quantity",quantity,n("100"),n("0"),n("0"),row.reversalAccountCode(),null,List.of(source));
        return run(()->documents.createInvoice(actor,key(),new InvoiceCreate(original.partyType().equals("CUSTOMER")?"CUSTOMER_CREDIT":"SUPPLIER_CREDIT",original.partyId(),null,original.id(),date,date,date,date,"EUR",n("1.2"),List.of(line))));
    }
    @Test void fullyPaidInvoiceCannotBeCreditedUntilAllocationIsExplicitlyReversedBothSides() {
        for(boolean purchase:List.of(false,true)) {
            var invoice=refundInvoice(purchase);var payment=originalRefundPayment(purchase,null);
            var allocation=run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("100"))));
            var credit=creditFor(invoice,"30");
            int before=ledger.journals(org,date,date).size();
            assertThatThrownBy(()->run(()->documents.postInvoice(actor,credit.id(),0)))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("CREDIT_REQUIRES_ALLOCATION_RELEASE");
            assertThat(documents.invoice(org,credit.id()).status()).isEqualTo("DRAFT");
            assertThat(documents.invoice(org,invoice.id()).creditedAmount()).isEqualByComparingTo("0");
            assertThat(documents.invoice(org,invoice.id()).lines().getFirst().creditedQuantity()).isEqualByComparingTo("0");
            assertThat(ledger.journals(org,date,date)).hasSize(before);
            assertThatThrownBy(()->exchangeRefund(payment,"30","1.4")).isInstanceOf(PlatformApiException.class);
            run(()->documents.reverseAllocation(actor,allocation.id(),key(),new AllocationReverse("Paid invoice return")));
            assertThat(documents.invoice(org,invoice.id()).settlementStatus()).isEqualTo("UNPAID");
            run(()->documents.postInvoice(actor,credit.id(),0));
            assertThat(documents.invoice(org,invoice.id()).settlementStatus()).isEqualTo("PARTIAL");
            assertThat(documents.invoice(org,invoice.id()).creditedAmount()).isEqualByComparingTo("30");
        }
    }
    @Test void partiallyReturnedPaidInvoiceRetainsSeventyAllocatedAndRefundsThirtyWithExactFxBothSides() {
        for(boolean purchase:List.of(false,true)) {
            var invoice=refundInvoice(purchase);var payment=originalRefundPayment(purchase,null);
            var allocation=run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("100"))));
            String reversalKey=key();var reason=new AllocationReverse("Return thirty and retain seventy");
            run(()->documents.reverseAllocation(actor,allocation.id(),reversalKey,reason));
            run(()->documents.reverseAllocation(actor,allocation.id(),reversalKey,reason));
            run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("70"))));
            var credit=creditFor(invoice,"30");run(()->documents.postInvoice(actor,credit.id(),0));
            var settled=documents.invoice(org,invoice.id());
            assertThat(settled.allocatedAmount()).isEqualByComparingTo("70");assertThat(settled.creditedAmount()).isEqualByComparingTo("30");
            assertThat(settled.settlementStatus()).isEqualTo("PAID");assertThat(settled.lines().getFirst().creditedQuantity()).isEqualByComparingTo("0.3");
            var refund=exchangeRefund(payment,"30","1.4");
            assertThat(refund.refundSourceBaseAmount()).isEqualByComparingTo("39");
            var posted=run(()->documents.postPayment(actor,refund.id(),0));
            assertThat(posted.baseAmount()).isEqualByComparingTo("42");assertThat(posted.realizedExchangeDifference()).isEqualByComparingTo("3");
            assertThat(accountNet(purchase?"PAYABLE":"RECEIVABLE",payment.partyId())).isEqualByComparingTo("0");
            assertThat(accountNet("CASH",payment.partyId())).isEqualByComparingTo(purchase?"-88":"88");
            assertThat(accountNet(purchase?"EXPENSE":"REVENUE",payment.partyId())).isEqualByComparingTo(purchase?"84":"-84");
            assertThat(accountNet("FX_GAIN",payment.partyId()).add(accountNet("FX_LOSS",payment.partyId()))).isEqualByComparingTo(purchase?"4":"-4");
            assertThat(documents.payment(org,payment.id()).allocatedAmount()).isEqualByComparingTo("70");
            assertThat(documents.payment(org,payment.id()).status()).isEqualTo("POSTED");
            assertThatThrownBy(()->exchangeRefund(payment,"0.0001","1.4")).isInstanceOf(PlatformApiException.class);
            assertThatThrownBy(()->run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("1"))))).isInstanceOf(PlatformApiException.class);
            for(var journal:ledger.journals(org,date,date))assertThat(journal.totalDebit()).isEqualByComparingTo(journal.totalCredit());
        }
    }
    @Test void fullPaidInvoiceCreditAndRefundCloseControlBalancesAtBothSettlementRateDirections() {
        for(boolean purchase:List.of(false,true))for(String rate:List.of("1.1","1.4")) {
            var invoice=refundInvoice(purchase);var payment=originalRefundPayment(purchase,null);
            BigDecimal beforeCash=accountNet("CASH",payment.partyId());
            var allocation=run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("100"))));
            run(()->documents.reverseAllocation(actor,allocation.id(),key(),new AllocationReverse("Full financial return")));
            var credit=creditFor(invoice,"100");run(()->documents.postInvoice(actor,credit.id(),0));
            assertThat(documents.invoice(org,invoice.id()).settlementStatus()).isEqualTo("PAID");
            assertThat(documents.invoice(org,invoice.id()).creditedAmount()).isEqualByComparingTo("100");
            var refund=exchangeRefund(payment,"100",rate);run(()->documents.postPayment(actor,refund.id(),0));
            assertThat(accountNet(purchase?"PAYABLE":"RECEIVABLE",payment.partyId())).isEqualByComparingTo("0");
            BigDecimal refundBase=n(rate).multiply(n("100"));
            // beforeCash already includes this original payment's 130 base units.
            assertThat(accountNet("CASH",payment.partyId())).isEqualByComparingTo(purchase?beforeCash.add(refundBase):beforeCash.subtract(refundBase));
            assertThat(accountNet(purchase?"EXPENSE":"REVENUE",payment.partyId())).isEqualByComparingTo("0");
            assertThat(documents.payment(org,payment.id()).allocatedAmount()).isEqualByComparingTo("0");
        }
    }
    @Test void creditChecksActualActiveAllocationsAndRejectsInconsistentHeaderWithoutPartialPosting() {
        var invoice=refundInvoice(false);var payment=originalRefundPayment(false,null);
        run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("80"))));
        var credit=creditFor(invoice,"30");
        assertThatThrownBy(()->run(()->documents.postInvoice(actor,credit.id(),0))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("CREDIT_REQUIRES_ALLOCATION_RELEASE");
        // Disposable legacy fixture: header lost its allocation total but actual active rows remain.
        jdbc.update("UPDATE flowora_finance_invoice SET allocated_amount=0 WHERE organization_id=? AND id=?",org,invoice.id());
        assertThatThrownBy(()->run(()->documents.postInvoice(actor,credit.id(),0))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("CREDIT_SETTLEMENT_STATE_CONFLICT");
        assertThat(documents.invoice(org,credit.id()).status()).isEqualTo("DRAFT");
        assertThat(documents.invoice(org,invoice.id()).creditedAmount()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id=? AND source_id=?",Integer.class,org,credit.id())).isZero();
    }
    @Test void concurrentCreditAndAllocationCannotExceedInvoiceAmount() throws Exception {
        var invoice=refundInvoice(false);var payment=originalRefundPayment(false,null);var credit=creditFor(invoice,"60");
        var barrier=new java.util.concurrent.CyclicBarrier(2);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var creditPost=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);try{return run(()->documents.postInvoice(actor,credit.id(),0)).status();}catch(PlatformApiException ex){return ex.code();}});
            var allocate=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);try{return run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("60")))).status();}catch(PlatformApiException ex){return ex.code();}});
            var results=List.of(creditPost.get(20,java.util.concurrent.TimeUnit.SECONDS),allocate.get(20,java.util.concurrent.TimeUnit.SECONDS));
            var current=documents.invoice(org,invoice.id());
            assertThat(current.allocatedAmount().add(current.creditedAmount())).isEqualByComparingTo("60");
            if(documents.invoice(org,credit.id()).status().equals("POSTED"))assertThat(results).containsExactlyInAnyOrder("POSTED","ALLOCATION_EXCEEDS_REMAINING");
            else assertThat(results).containsExactlyInAnyOrder("CREDIT_REQUIRES_ALLOCATION_RELEASE","ACTIVE");
        } finally {pool.shutdownNow();}
    }
    @Test void closedPeriodCreditLeavesReleasedAllocationAndAllCreditBalancesUnchanged() {
        var invoice=refundInvoice(false);var payment=originalRefundPayment(false,null);
        var allocation=run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("100"))));
        run(()->documents.reverseAllocation(actor,allocation.id(),key(),new AllocationReverse("Release before closing period")));
        var credit=creditFor(invoice,"100");int count=ledger.journals(org,date,date).size();
        jdbc.update("UPDATE flowora_accounting_period SET status='CLOSED' WHERE organization_id=?",org);
        assertThatThrownBy(()->run(()->documents.postInvoice(actor,credit.id(),0))).isInstanceOf(PlatformApiException.class);
        assertThat(documents.invoice(org,credit.id()).status()).isEqualTo("DRAFT");
        var original=documents.invoice(org,invoice.id());assertThat(original.allocatedAmount()).isEqualByComparingTo("0");assertThat(original.creditedAmount()).isEqualByComparingTo("0");assertThat(original.settlementStatus()).isEqualTo("UNPAID");
        assertThat(original.lines().getFirst().creditedQuantity()).isEqualByComparingTo("0");assertThat(ledger.journals(org,date,date)).hasSize(count);
    }
    String legacyProjectFixture() {
        String id=key();jdbc.update("INSERT INTO flowora_project(id,organization_id,number,name,manager_user_id,target_date,currency_code,status) VALUES (?,?,?,'R6L legacy project','system:r6l',CURRENT_DATE,'USD','AT_RISK')",id,org,"R6L-"+id.substring(0,20));
        return id;
    }
    String postCreditAtBarrier(String id,java.util.concurrent.CyclicBarrier barrier) throws Exception {
        barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);
        try {return run(()->documents.postInvoice(actor,id,0)).status();}catch(PlatformApiException ex){return ex.code();}
    }
    InvoiceView termsOriginal(boolean purchase) {
        var line=new InvoiceLineCreate(null,"Original discounted taxed line",n("2"),n("12"),n("10"),n("10"),null,null,List.of());
        var draft=run(()->documents.createInvoice(actor,key(),input(purchase?"SUPPLIER_INVOICE":"SALES_INVOICE",purchase?supplier:customer,"1.3",List.of(line))));
        return run(()->documents.postInvoice(actor,draft.id(),0));
    }
    InvoiceCreate termsCredit(InvoiceView original) {
        var line=original.lines().getFirst();var source=new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",original.id(),line.id(),n("1"),n("10.8"));
        return new InvoiceCreate(original.partyType().equals("CUSTOMER")?"CUSTOMER_CREDIT":"SUPPLIER_CREDIT",original.partyId(),original.projectId(),original.id(),date,date,date,date,"EUR",n("1.3"),List.of(new InvoiceLineCreate(line.itemId(),"Original terms credit",n("1"),line.unitPrice(),line.discountRate(),line.taxRate(),null,line.projectId(),List.of(source))));
    }
    InvoiceCreate termsWithLine(InvoiceCreate body,InvoiceLineCreate line) {return new InvoiceCreate(body.documentType(),body.partyId(),body.projectId(),body.originalInvoiceId(),body.businessDate(),body.accountingDate(),body.dueDate(),body.exchangeRateDate(),body.currencyCode(),body.exchangeRate(),List.of(line));}
    @Test void creditRetainsActualLineControlAndTaxAccountsEvenAfterAllCurrentMappingsAreRemovedBothSides() {
        for(boolean purchase:List.of(false,true)) {
            // Recreate mappings for the next independent original; remove them only after it is posted.
            if(purchase)for(String semantic:List.of("RECEIVABLE","PAYABLE","ACCRUED_PAYABLE","REVENUE","EXPENSE","TAX_PAYABLE","TAX_RECEIVABLE","CASH","FX_GAIN","FX_LOSS"))
                jdbc.update("INSERT INTO flowora_posting_mapping(id,organization_id,semantic_code,account_code) VALUES (?,?,?,?)",key(),org,semantic,semantic);
            var original=termsOriginal(purchase);var body=termsCredit(original);String account=original.lines().getFirst().reversalAccountCode();
            jdbc.update("DELETE FROM flowora_posting_mapping WHERE organization_id=?",org);
            var draft=run(()->documents.createInvoice(actor,key(),body));assertThat(draft.lines().getFirst().accountCode()).isEqualTo(account);
            assertThat(draft.netAmount()).isEqualByComparingTo("10.8");assertThat(draft.taxAmount()).isEqualByComparingTo("1.08");
            assertThat(draft.baseTotalAmount()).isEqualByComparingTo("15.444");
            var posted=run(()->documents.postInvoice(actor,draft.id(),draft.version()));assertThat(posted.status()).isEqualTo("POSTED");
            var journal=ledger.journals(org,date,date).stream().filter(j->j.sourceId().equals(posted.id())).findFirst().orElseThrow();
            assertThat(journal.lines().stream().map(JournalLineView::accountCode)).containsExactlyInAnyOrder(purchase?"PAYABLE":"RECEIVABLE",account,purchase?"TAX_RECEIVABLE":"TAX_PAYABLE");
            assertThat(accountNet(purchase?"PAYABLE":"RECEIVABLE",original.partyId())).isEqualByComparingTo(purchase?"-15.444":"15.444");
        }
    }
    @Test void creditHeaderMustRetainOriginalCurrencyRateDateProjectAndDirection() {
        var original=termsOriginal(false);var body=termsCredit(original);
        for(var candidate:List.of(
                new InvoiceCreate(body.documentType(),body.partyId(),null,body.originalInvoiceId(),date,date,date,date,"USD",n("1.3"),body.lines()),
                new InvoiceCreate(body.documentType(),body.partyId(),null,body.originalInvoiceId(),date,date,date,date,"EUR",n("1.4"),body.lines()),
                new InvoiceCreate(body.documentType(),body.partyId(),null,body.originalInvoiceId(),date,date,date,date.plusDays(1),"EUR",n("1.3"),body.lines()),
                new InvoiceCreate(body.documentType(),body.partyId(),"other-project",body.originalInvoiceId(),date,date,date,date,"EUR",n("1.3"),body.lines()),
                new InvoiceCreate(body.documentType(),body.partyId(),null,body.originalInvoiceId(),date,date.minusDays(1),date,date,"EUR",n("1.3"),body.lines()))) {
            assertThatThrownBy(()->run(()->documents.createInvoice(actor,key(),candidate))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("CREDIT_ORIGINAL_TERMS_MISMATCH");
        }
        assertThat(documents.invoices(org,null,null)).hasSize(1);assertThat(ledger.journals(org,date,date)).hasSize(1);
    }
    @Test void creditLineTermsAndSingleActualSourceMustMatchAndAggregateQuantityCannotBeRepeated() {
        var original=termsOriginal(false);var body=termsCredit(original);var l=body.lines().getFirst();var source=l.sources().getFirst();
        for(var input:List.of(
                new InvoiceLineCreate(null,l.description(),n("1"),n("13"),n("10"),n("10"),null,null,l.sources()),
                new InvoiceLineCreate(null,l.description(),n("1"),n("12"),n("0"),n("10"),null,null,l.sources()),
                new InvoiceLineCreate(null,l.description(),n("1"),n("12"),n("10"),n("0"),null,null,l.sources()),
                new InvoiceLineCreate("other-item",l.description(),n("1"),n("12"),n("10"),n("10"),null,null,l.sources()),
                new InvoiceLineCreate(null,l.description(),n("1"),n("12"),n("10"),n("10"),null,"other-project",l.sources()),
                new InvoiceLineCreate(null,l.description(),n("1"),n("12"),n("10"),n("10"),"OTHER_ACCOUNT",null,l.sources()),
                new InvoiceLineCreate(null,l.description(),n("1"),n("12"),n("10"),n("10"),null,null,List.of(new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",original.id(),source.sourceLineId(),n("0.5"),n("10.8")))),
                new InvoiceLineCreate(null,l.description(),n("1"),n("12"),n("10"),n("10"),null,null,List.of(new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",original.id(),source.sourceLineId(),n("1"),n("12")))),
                new InvoiceLineCreate(null,l.description(),n("1"),n("12"),n("10"),n("10"),null,null,List.of(source,source)))) {
            assertThatThrownBy(()->run(()->documents.createInvoice(actor,key(),termsWithLine(body,input)))).isInstanceOf(PlatformApiException.class);
        }
        var repeated=new InvoiceCreate(body.documentType(),body.partyId(),null,original.id(),date,date,date,date,"EUR",n("1.3"),List.of(l,l,l));
        assertThatThrownBy(()->run(()->documents.createInvoice(actor,key(),repeated))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("CREDIT_QUANTITY_EXCEEDS_REMAINING");
        assertThat(documents.invoices(org,null,null)).hasSize(1);
    }
    @Test void creditReplayIsBoundToActualHeaderLinesAndSourcesBothBeforeAndAfterPosting() {
        var original=termsOriginal(false);var body=termsCredit(original);String request=key();
        var draft=run(()->documents.createInvoice(actor,request,body));assertThat(run(()->documents.createInvoice(actor,request,body)).id()).isEqualTo(draft.id());
        var l=body.lines().getFirst();var changed=termsWithLine(body,new InvoiceLineCreate(l.itemId(),"Changed credit reason",l.quantity(),l.unitPrice(),l.discountRate(),l.taxRate(),null,l.projectId(),l.sources()));
        assertThatThrownBy(()->run(()->documents.createInvoice(actor,request,changed))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        run(()->documents.postInvoice(actor,draft.id(),0));assertThat(run(()->documents.createInvoice(actor,request,body)).status()).isEqualTo("POSTED");
        var changedSource=termsWithLine(body,new InvoiceLineCreate(l.itemId(),l.description(),l.quantity(),l.unitPrice(),l.discountRate(),l.taxRate(),null,l.projectId(),List.of(new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",original.id(),l.sources().getFirst().sourceLineId(),n("1"),n("1")))));
        assertThatThrownBy(()->run(()->documents.createInvoice(actor,request,changedSource))).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(documents.invoices(org,null,null)).hasSize(2);assertThat(ledger.journals(org,date,date)).hasSize(2);
    }
    @Test void postingRechecksLegacyDraftTermsAndStoredAmountsWithoutPartialJournalsOrSourceConsumption() {
        var original=termsOriginal(false);var draft=run(()->documents.createInvoice(actor,key(),termsCredit(original)));
        String line=draft.lines().getFirst().id();
        // Each precise disposable legacy-import mutation rolls back with the rejected posting.
        for(String change:List.of("unit_price=13","discount_rate=0","tax_rate=0","item_id='other-item'","project_id='other-project'","revenue_expense_account_code='OTHER'","net_amount=99","tax_amount=99","total_amount=99","base_total_amount=99")) {
            assertThatThrownBy(()->run(()->{jdbc.update("UPDATE flowora_finance_invoice_line SET "+(change.contains("project_id")?change.replace("other-project",legacyProjectFixture()):change)+" WHERE organization_id=? AND id=?",org,line);return documents.postInvoice(actor,draft.id(),0);})).isInstanceOf(PlatformApiException.class);
        }
        assertThatThrownBy(()->run(()->{jdbc.update("UPDATE flowora_finance_invoice_source SET quantity=0.5 WHERE organization_id=? AND invoice_line_id=?",org,line);return documents.postInvoice(actor,draft.id(),0);})).isInstanceOf(PlatformApiException.class);
        assertThatThrownBy(()->run(()->{jdbc.update("UPDATE flowora_finance_invoice SET exchange_rate=1.4 WHERE organization_id=? AND id=?",org,draft.id());return documents.postInvoice(actor,draft.id(),0);})).isInstanceOf(PlatformApiException.class);
        assertThat(documents.invoice(org,draft.id()).status()).isEqualTo("DRAFT");assertThat(documents.invoice(org,original.id()).creditedAmount()).isEqualByComparingTo("0");assertThat(ledger.journals(org,date,date)).hasSize(1);
    }
    @Test void postingRequiresOriginalActualJournalStillPresentPostedAndConsistent() {
        var original=termsOriginal(false);var draft=run(()->documents.createInvoice(actor,key(),termsCredit(original)));
        String journal=ledger.journals(org,date,date).getFirst().id();
        for(String change:List.of("status='REVERSED'","total_debit=999","currency_code='USD'","exchange_rate=1.4","source_id='missing-original'")) {
            assertThatThrownBy(()->run(()->{jdbc.update("UPDATE flowora_journal_entry SET "+change+" WHERE organization_id=? AND id=?",org,journal);return documents.postInvoice(actor,draft.id(),0);})).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("CREDIT_ORIGINAL_JOURNAL_MISMATCH");
        }
        for(String change:List.of("base_credit=base_credit+1","credit=credit+1","party_id='other-party'","project_id='other-project'","source_line_id='missing-line'")) {
            assertThatThrownBy(()->run(()->{jdbc.update("UPDATE flowora_journal_line SET "+(change.contains("project_id")?change.replace("other-project",legacyProjectFixture()):change)+" WHERE organization_id=? AND journal_entry_id=? AND source_line_id IS NOT NULL",org,journal);return documents.postInvoice(actor,draft.id(),0);})).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("CREDIT_ORIGINAL_JOURNAL_MISMATCH");
        }
        assertThat(documents.invoice(org,draft.id()).status()).isEqualTo("DRAFT");assertThat(ledger.journals(org,date,date)).hasSize(1);
    }
    @Test void twoValidCreditDraftsDoNotReserveQuantityAndConcurrentPostingConsumesOriginalOnlyOnce() throws Exception {
        var original=termsOriginal(false);var one=termsCredit(original);var l=one.lines().getFirst();
        var input=termsWithLine(one,new InvoiceLineCreate(l.itemId(),l.description(),n("2"),l.unitPrice(),l.discountRate(),l.taxRate(),null,l.projectId(),List.of(new InvoiceSourceCreate("ORIGINAL_INVOICE_LINE",original.id(),l.sources().getFirst().sourceLineId(),n("2"),n("21.6")))));
        var first=run(()->documents.createInvoice(actor,key(),input));var second=run(()->documents.createInvoice(actor,key(),input));
        assertThat(documents.invoice(org,original.id()).lines().getFirst().creditedQuantity()).isEqualByComparingTo("0");
        var barrier=new java.util.concurrent.CyclicBarrier(2);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->postCreditAtBarrier(first.id(),barrier));var b=pool.submit(()->postCreditAtBarrier(second.id(),barrier));
            assertThat(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("POSTED","CREDIT_QUANTITY_EXCEEDS_REMAINING");
            assertThat(documents.invoice(org,original.id()).creditedAmount()).isEqualByComparingTo("23.76");assertThat(ledger.journals(org,date,date)).hasSize(2);
        } finally {pool.shutdownNow();}
    }
    @Test void creditPostingUsesCurrentOriginalJournalStatusAfterAnEarlierConsistentRead() {
        var original=termsOriginal(false);var draft=run(()->documents.createInvoice(actor,key(),termsCredit(original)));
        String journal=ledger.journals(org,date,date).getFirst().id();var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(()->run(()->{
                documents.invoice(org,original.id()); // Establish the earlier consistent snapshot.
                // Precise disposable legacy-import status change on another real connection.
                try {pool.submit(()->run(()->jdbc.update("UPDATE flowora_journal_entry SET status='REVERSED' WHERE organization_id=? AND id=?",org,journal))).get(20,java.util.concurrent.TimeUnit.SECONDS);}
                catch(Exception ex){throw new IllegalStateException(ex);}
                return documents.postInvoice(actor,draft.id(),0);
            })).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("CREDIT_ORIGINAL_JOURNAL_MISMATCH");
            assertThat(documents.invoice(org,draft.id()).status()).isEqualTo("DRAFT");assertThat(documents.invoice(org,original.id()).creditedAmount()).isEqualByComparingTo("0");
        } finally {pool.shutdownNow();}
    }
    AllocationView allocate() {
        var invoice=run(()->documents.createInvoice(actor,key(),input("SALES_INVOICE",customer,"1",List.of(line("100",List.of())))));
        run(()->documents.postInvoice(actor,invoice.id(),0));
        var payment=run(()->documents.createPayment(actor,key(),new PaymentCreate("RECEIPT",customer,null,date,date,date,"EUR",n("2"),n("100"),"R3")));
        run(()->documents.postPayment(actor,payment.id(),0));
        return run(()->documents.allocate(actor,payment.id(),new AllocationCreate(invoice.id(),n("100"))));
    }
    InvoiceCreate input(String type,String party,String rate,List<InvoiceLineCreate> lines) {return new InvoiceCreate(type,party,null,null,date,date,date.plusDays(30),date,"EUR",n(rate),lines);}
    InvoiceLineCreate line(String price,List<InvoiceSourceCreate> sources) {return new InvoiceLineCreate(null,"R3",n("1"),n(price),n("0"),n("0"),null,null,sources);}
    <T>T run(Supplier<T> work){return tx.execute(status->work.get());}
    static BigDecimal n(String s){return new BigDecimal(s);} static String key(){return UUID.randomUUID().toString();}
}
