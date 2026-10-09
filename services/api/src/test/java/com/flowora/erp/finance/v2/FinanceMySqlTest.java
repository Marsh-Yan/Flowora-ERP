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
        for(String table:List.of("flowora_currency_revaluation_line","flowora_currency_revaluation","flowora_bank_reconciliation_link","flowora_bank_reconciliation","flowora_bank_statement_line","flowora_allocation_reversal","flowora_payment_allocation","flowora_payment_v2","flowora_bank_account","flowora_finance_invoice_source","flowora_finance_invoice_line","flowora_finance_invoice","flowora_journal_line","flowora_journal_entry","flowora_accounting_period","flowora_posting_mapping","flowora_finance_setting","flowora_sales_delivery_line","flowora_sales_delivery","flowora_sales_order_line","flowora_sales_order","flowora_purchase_receipt_line","flowora_purchase_receipt","flowora_purchase_order_line","flowora_purchase_order","flowora_supplier","flowora_customer"))
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
        String request=key();
        var refund=run(()->documents.createPayment(actor,request,new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.3"),n("40"),"refund",original.id())));
        assertThatThrownBy(()->run(()->documents.createPayment(actor,request,new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.3"),n("41"),"refund",original.id()))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(()->run(()->documents.createPayment(actor,key(),new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.3"),n("61"),"too much",original.id()))))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("REFUND_EXCEEDS_REMAINING");
        assertThatThrownBy(()->run(()->documents.createPayment(actor,key(),new PaymentCreate("CUSTOMER_REFUND",customer,null,date,date,date,"EUR",n("1.4"),n("10"),"different rate",original.id()))))
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
