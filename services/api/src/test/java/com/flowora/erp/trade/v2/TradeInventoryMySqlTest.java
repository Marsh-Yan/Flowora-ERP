package com.flowora.erp.trade.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.common.idempotency.IdempotencyService;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.identity.DataScope;
import org.springframework.security.access.AccessDeniedException;
import com.flowora.erp.inventory.CanonicalInventoryReader;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static com.flowora.erp.trade.v2.TradeDocumentDtos.*;
import static com.flowora.erp.trade.v2.TradeInventoryDtos.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in only: the URL is restricted to the explicitly isolated audit database. */
@EnabledIfEnvironmentVariable(named = "FLOWORA_R2_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13306/audit_flowora\\?.*")
class TradeInventoryMySqlTest {
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    TradeDocumentService documents;
    TradeInventoryService inventory;
    FloworaPrincipal actor;
    String org, warehouse, target, item, customer, supplier;

    @BeforeAll
    static void connectAndMigrate() {
        var source = new DriverManagerDataSource(System.getenv("FLOWORA_R2_MYSQL_URL"), "root",
                Objects.requireNonNullElse(System.getenv("FLOWORA_R2_MYSQL_PASSWORD"), ""));
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        if ("true".equals(System.getenv("FLOWORA_R2_EMPTY_CI_DATABASE"))) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("16").load().migrate();
            historicalFixtures();
        }
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        if ("true".equals(System.getenv("FLOWORA_R2_EMPTY_CI_DATABASE"))) {
            verifyHistoricalCutoverAndDemo(source);
            // A second migrate must not add the opening balance again.
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            assertThat(jdbc.queryForObject("SELECT quantity FROM flowora_inventory_summary_v2 WHERE warehouse_id='r2-migration-overlap'", BigDecimal.class))
                    .isEqualByComparingTo("16");
        }
    }

    static void historicalFixtures() {
        // Missing opening, partially represented legacy history, and fully represented history.
        for (String warehouse : List.of("r2-migration-opening", "r2-migration-overlap", "r2-migration-duplicate")) {
            jdbc.update("INSERT INTO flowora_stock_balance(id,organization_id,warehouse_id,item_id,quantity,average_cost) VALUES (?,'org-demo',?,'r2-migration-item',16,40)", warehouse,warehouse);
            if (warehouse.endsWith("opening")) continue;
            jdbc.update("INSERT INTO flowora_stock_ledger_entry(id,organization_id,warehouse_id,item_id,movement_type,document_type,document_id,quantity_delta,unit_cost,value_delta,balance_quantity,balance_value,actor_user_id) VALUES (?,'org-demo',?,'r2-migration-item','RECEIPT','PURCHASE_RECEIPT',?,20,40,800,20,800,'r2-ci')",warehouse+"-in",warehouse,warehouse+"-receipt");
            jdbc.update("INSERT INTO flowora_stock_ledger_entry(id,organization_id,warehouse_id,item_id,movement_type,document_type,document_id,quantity_delta,unit_cost,value_delta,balance_quantity,balance_value,actor_user_id) VALUES (?,'org-demo',?,'r2-migration-item','SHIPMENT','SALES_DELIVERY',?,-4,40,-160,16,640,'r2-ci')",warehouse+"-out",warehouse,warehouse+"-delivery");
            jdbc.update("INSERT INTO flowora_inventory_balance_v2(id,organization_id,warehouse_id,item_id,on_hand_quantity,average_cost) VALUES (?,'org-demo',?,'r2-migration-item',?,40)",warehouse,warehouse,warehouse.endsWith("duplicate")?16:20);
            historicalMovement(warehouse,"RECEIPT","PURCHASE_RECEIPT",warehouse+"-receipt",20);
            if (warehouse.endsWith("duplicate")) historicalMovement(warehouse,"SHIPMENT","SALES_DELIVERY",warehouse+"-delivery",-4);
        }
    }

    static void historicalMovement(String warehouse,String type,String sourceType,String sourceId,int quantity) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO flowora_stock_movement(id,organization_id,number,movement_type,source_type,source_id,actor_user_id,request_id) VALUES (?,'org-demo',?,?,?,?, 'r2-ci',?)",id,id,type,sourceType,sourceId,id);
        jdbc.update("INSERT INTO flowora_stock_movement_line(id,organization_id,movement_id,sequence_no,item_id,from_warehouse_id,to_warehouse_id,quantity,unit_cost,value_amount) VALUES (?,'org-demo',?,1,'r2-migration-item',?,?,?,40,?)",UUID.randomUUID().toString(),id,quantity<0?warehouse:null,quantity>0?warehouse:null,Math.abs(quantity),Math.abs(quantity)*40);
    }

    static void verifyHistoricalCutoverAndDemo(javax.sql.DataSource source) {
        for (String warehouse : List.of("r2-migration-opening", "r2-migration-overlap", "r2-migration-duplicate")) {
            assertThat(jdbc.queryForObject("SELECT quantity FROM flowora_inventory_summary_v2 WHERE warehouse_id=?",BigDecimal.class,warehouse)).isEqualByComparingTo("16");
            assertThat(jdbc.queryForObject("SELECT inventory_value FROM flowora_inventory_summary_v2 WHERE warehouse_id=?",BigDecimal.class,warehouse)).isEqualByComparingTo("640");
        }
        assertThat(jdbc.queryForObject("SELECT imported_quantity FROM flowora_inventory_cutover WHERE warehouse_id='r2-migration-duplicate'",BigDecimal.class)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT imported_quantity FROM flowora_inventory_cutover WHERE warehouse_id='r2-migration-overlap'",BigDecimal.class)).isEqualByComparingTo("-4");
        tx.execute(status -> {
            var seed = new ResourceDatabasePopulator(new ClassPathResource("db/demo/seed.sql"));
            for (int i=0;i<2;i++) {
                seed.execute(source);
                assertThat(jdbc.queryForObject("SELECT quantity FROM flowora_inventory_summary_v2 WHERE organization_id='org-demo'",BigDecimal.class)).isEqualByComparingTo("16");
                assertThat(jdbc.queryForObject("SELECT inventory_value FROM flowora_inventory_summary_v2 WHERE organization_id='org-demo'",BigDecimal.class)).isEqualByComparingTo("640");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_stock_movement WHERE organization_id='org-demo'",Integer.class)).isEqualTo(1);
            }
            status.setRollbackOnly(); return null;
        });
    }

    @BeforeEach
    void fixtures() {
        org = UUID.randomUUID().toString(); warehouse = UUID.randomUUID().toString(); target = UUID.randomUUID().toString();
        item = UUID.randomUUID().toString(); customer = UUID.randomUUID().toString(); supplier = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'R2 isolated test','CNY')", org);
        for (String id : List.of(warehouse, target))
            jdbc.update("INSERT INTO flowora_warehouse(id,organization_id,code,name) VALUES (?,?,?,'R2 warehouse')", id, org, id);
        jdbc.update("INSERT INTO flowora_item(id,organization_id,code,name,item_type,unit,inventory_managed) VALUES (?,?,?,'R2 item','GOODS','EA',TRUE)", item, org, item);
        jdbc.update("INSERT INTO flowora_customer(id,organization_id,code,name,currency_code) VALUES (?,?,?,'R2 customer','CNY')", customer, org, customer);
        jdbc.update("INSERT INTO flowora_supplier(id,organization_id,code,name,currency_code) VALUES (?,?,?,'R2 supplier','CNY')", supplier, org, supplier);
        actor = new FloworaPrincipal("system:r2-test", "r2-test", "R2 test", org, "R2 isolated test", List.of("ADMIN"));
        var idempotency = new IdempotencyService(jdbc);
        documents = transactionalDocuments(new TradeDocumentService(jdbc, idempotency));
        inventory = new TradeInventoryService(jdbc, idempotency, new ObjectMapper());
    }

    static TradeDocumentService transactionalDocuments(TradeDocumentService service) {
        var proxy = new org.springframework.aop.framework.ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                tx.getTransactionManager(), new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        return (TradeDocumentService) proxy.getProxy();
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        jdbc.update("UPDATE flowora_stock_movement SET reversal_of_id=NULL WHERE organization_id=?", org);
        for (String table : List.of("flowora_financial_source_event", "flowora_idempotency_record", "flowora_stock_movement_line",
                "flowora_stock_movement", "flowora_stock_reservation", "flowora_sales_return_line", "flowora_sales_return",
                "flowora_purchase_return_line", "flowora_purchase_return", "flowora_stock_transfer_line", "flowora_stock_transfer",
                "flowora_stock_count_line", "flowora_stock_count", "flowora_sales_delivery_line", "flowora_sales_delivery",
                "flowora_purchase_receipt_line", "flowora_purchase_receipt", "flowora_sales_order_line", "flowora_sales_order",
                "flowora_purchase_order_line", "flowora_purchase_order", "flowora_trade_source_line_link", "flowora_sales_quote_line", "flowora_sales_quote", "flowora_purchase_request_line", "flowora_purchase_request", "flowora_inventory_balance_v2",
                "flowora_customer", "flowora_supplier", "flowora_item", "flowora_warehouse"))
            jdbc.update("DELETE FROM " + table + " WHERE organization_id=?", org);
        jdbc.update("DELETE FROM flowora_organization WHERE id=?", org);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sourceQuantitySupportsSplitOrdersAndCancellationRelease(boolean sales) {
        LineRequest source = source(sales, "APPROVED");
        DocumentView first = sourcedOrder(actor, sales, List.of(sourceQuantity(source, "0.4")), key());
        DocumentView second = sourcedOrder(actor, sales, List.of(sourceQuantity(source, "0.6")), key());
        run(() -> sales ? documents.confirmSalesOrder(org, second.id(), 0)
                : documents.confirmPurchaseOrder(org, second.id(), 0));
        var before = snapshot();
        assertThatThrownBy(() -> sourcedOrder(actor, sales, List.of(sourceQuantity(source, "0.0001")), key()))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("SOURCE_QUANTITY_EXCEEDED"));
        assertThat(snapshot()).isEqualTo(before);
        run(() -> sales ? documents.cancelSalesOrder(org, first.id(), 0)
                : documents.cancelPurchaseOrder(org, first.id(), 0));
        assertThat(sourcedOrder(actor, sales, List.of(sourceQuantity(source, "0.4")), key()).status()).isEqualTo("DRAFT");
        run(() -> sales ? documents.cancelSalesOrder(org, second.id(), 1)
                : documents.cancelPurchaseOrder(org, second.id(), 1));
        assertThat(sourcedOrder(actor, sales, List.of(sourceQuantity(source, "0.6")), key()).status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_trade_source_line_link WHERE organization_id=?", Integer.class, org)).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sourceAllocationUsesPersistedPerLineQuantityPrecision(boolean sales) {
        LineRequest source = source(sales, "APPROVED");
        var before = snapshot();
        assertThatThrownBy(() -> sourcedOrder(actor, sales,
                List.of(sourceQuantity(source, "0.33335"), sourceQuantity(source, "0.33335"), sourceQuantity(source, "0.33329")), key()))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("SOURCE_QUANTITY_EXCEEDED"));
        assertThat(snapshot()).isEqualTo(before);
        sourcedOrder(actor, sales, List.of(sourceQuantity(source, "0.33334"), sourceQuantity(source, "0.33334"), sourceQuantity(source, "0.33334")), key());
        assertThat(jdbc.queryForObject("SELECT SUM(linked_quantity) FROM flowora_trade_source_line_link WHERE organization_id=?", BigDecimal.class, org))
                .isEqualByComparingTo("0.9999");
        assertThat(sourcedOrder(actor, sales, List.of(sourceQuantity(source, "0.0001")), key()).status()).isEqualTo("DRAFT");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void repeatedSourceLinesAreCheckedAsOneQuantity(boolean sales) {
        LineRequest source = source(sales, "APPROVED");
        var before = snapshot();
        assertThatThrownBy(() -> sourcedOrder(actor, sales,
                List.of(sourceQuantity(source, "0.6"), sourceQuantity(source, "0.5")), key()))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("SOURCE_QUANTITY_EXCEEDED"));
        assertThat(snapshot()).isEqualTo(before);
        DocumentView order = sourcedOrder(actor, sales,
                List.of(sourceQuantity(source, "0.4"), sourceQuantity(source, "0.6")), key());
        assertThat(order.lines()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentSplitOrdersObserveCurrentAllocation(boolean sales) throws Exception {
        LineRequest source = source(sales, "APPROVED");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> create = () -> {
                start.await();
                try { sourcedOrder(actor, sales, List.of(sourceQuantity(source, "0.6")), key()); return "CREATED"; }
                catch (PlatformApiException failure) { return failure.code(); }
            };
            var first = pool.submit(create); var second = pool.submit(create); start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "SOURCE_QUANTITY_EXCEEDED");
        }
        assertThat(jdbc.queryForObject("SELECT SUM(linked_quantity) FROM flowora_trade_source_line_link WHERE organization_id=?", BigDecimal.class, org))
                .isEqualByComparingTo("0.6");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void independentSourcesCanBeCreatedTogetherInOppositeOrder(boolean sales) throws Exception {
        LineRequest firstSource = source(sales, "APPROVED"), secondSource = source(sales, "APPROVED");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return sourcedOrder(actor, sales,
                    List.of(sourceQuantity(firstSource, "0.5"), sourceQuantity(secondSource, "0.5")), key()); });
            var second = pool.submit(() -> { start.await(); return sourcedOrder(actor, sales,
                    List.of(sourceQuantity(secondSource, "0.5"), sourceQuantity(firstSource, "0.5")), key()); });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).id()).isNotEqualTo(second.get(20, TimeUnit.SECONDS).id());
        }
        assertThat(jdbc.queryForObject("SELECT SUM(linked_quantity) FROM flowora_trade_source_line_link WHERE organization_id=?", BigDecimal.class, org))
                .isEqualByComparingTo("2");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void disjointSourceOrdersCanAllocateConcurrently(boolean sales) throws Exception {
        LineRequest firstSource = source(sales, "APPROVED"), secondSource = source(sales, "APPROVED");
        var readTogether = new CyclicBarrier(2);
        var coordinated = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public <T> List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
                List<T> rows = super.query(sql, mapper, args);
                if (sql.contains("SELECT link.linked_quantity")) {
                    try { readTogether.await(10, TimeUnit.SECONDS); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }
                return rows;
            }
        };
        documents = transactionalDocuments(new TradeDocumentService(coordinated, new IdempotencyService(jdbc)));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> sourcedOrder(actor, sales, List.of(firstSource), key()));
            var second = pool.submit(() -> sourcedOrder(actor, sales, List.of(secondSource), key()));
            assertThat(first.get(20, TimeUnit.SECONDS).id()).isNotEqualTo(second.get(20, TimeUnit.SECONDS).id());
        }
        assertThat(jdbc.queryForObject("SELECT SUM(linked_quantity) FROM flowora_trade_source_line_link WHERE organization_id=?", BigDecimal.class, org))
                .isEqualByComparingTo("2");
    }

    LineRequest sourceQuantity(LineRequest source, String quantity) {
        return new LineRequest(source.itemId(), amount(quantity), source.unitPrice(), source.discountRate(), source.taxRate(),
                source.sourceDocumentType(), source.sourceDocumentId(), source.sourceLineId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nativeSourcesUseRealApprovedLinesAndReplayDoesNotDuplicateLinks(boolean sales) {
        LineRequest source = source(sales, "APPROVED");
        String requestKey = key();
        DocumentView order = sourcedOrder(actor, sales, List.of(source, line("2")), requestKey);
        var links = jdbc.queryForList("SELECT * FROM flowora_trade_source_line_link WHERE organization_id=?", org);
        assertThat(links).hasSize(1);
        assertThat(links.getFirst().get("source_line_id")).isEqualTo(source.sourceLineId());
        assertThat(links.getFirst().get("source_document_id")).isEqualTo(source.sourceDocumentId());
        assertThat(links.getFirst().get("target_document_id")).isEqualTo(order.id());
        assertThat(links.getFirst().get("linked_quantity")).isEqualTo(amount("1.0000"));
        var before = snapshot();
        assertThat(sourcedOrder(actor, sales, List.of(source, line("2")), requestKey).id()).isEqualTo(order.id());
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nativeSourceShapeAndRealLineIdentityRejectWithoutRowsOrKeys(boolean sales) {
        LineRequest valid = source(sales, "APPROVED");
        LineRequest other = source(sales, "APPROVED");
        for (LineRequest invalid : List.of(
                referenced(valid.itemId(), "UNSUPPORTED", valid.sourceDocumentId(), valid.sourceLineId()),
                referenced(valid.itemId(), valid.sourceDocumentType(), valid.sourceDocumentId(), null),
                referenced(valid.itemId(), null, null, valid.sourceLineId()),
                referenced(valid.itemId(), valid.sourceDocumentType(), valid.sourceDocumentId(), valid.sourceDocumentId()),
                referenced(valid.itemId(), valid.sourceDocumentType(), valid.sourceDocumentId(), other.sourceLineId()),
                referenced(valid.itemId(), valid.sourceDocumentType(), key(), valid.sourceLineId()))) {
            var before = snapshot();
            assertThatThrownBy(() -> sourcedOrder(actor, sales, List.of(line("2"), invalid), key())).isInstanceOf(PlatformApiException.class);
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nativeSourcesRequireSharedReadButUnlinkedCreateOnlyDraftsRemainAvailable(boolean sales) {
        LineRequest valid = source(sales, "APPROVED");
        String permission = sales ? "sales:view" : "procurement:view";
        for (DataScope scope : DataScope.values()) {
            var scoped = new FloworaPrincipal(actor.userId(), actor.username(), actor.displayName(), org, actor.organizationName(),
                    actor.membershipId(), null, scope, List.of("CUSTOM"), List.of(permission), false);
            if (scope == DataScope.ALL) continue;
            var before = snapshot();
            assertThatThrownBy(() -> sourcedOrder(scoped, sales, List.of(valid), key())).isInstanceOf(AccessDeniedException.class);
            assertThat(snapshot()).isEqualTo(before);
            assertThat(sourcedOrder(scoped, sales, List.of(line("1")), key()).status()).isEqualTo("DRAFT");
        }
        var blind = new FloworaPrincipal(actor.userId(), actor.username(), actor.displayName(), org, actor.organizationName(),
                actor.membershipId(), null, DataScope.ALL, List.of("CUSTOM"), List.of(), false);
        var before = snapshot();
        assertThatThrownBy(() -> sourcedOrder(blind, sales, List.of(valid), key())).isInstanceOf(AccessDeniedException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nativeSourcesRequireApprovedStatusMatchingPartnerAndItem(boolean sales) {
        LineRequest valid = source(sales, "APPROVED");
        String header = sales ? "flowora_sales_quote" : "flowora_purchase_request";
        for (String status : List.of("DRAFT", "SUBMITTED", "REJECTED", "CANCELLED")) {
            jdbc.update("UPDATE " + header + " SET status=? WHERE organization_id=? AND id=?", status, org, valid.sourceDocumentId());
            var before = snapshot();
            assertThatThrownBy(() -> sourcedOrder(actor, sales, List.of(valid), key())).isInstanceOfSatisfying(PlatformApiException.class,
                    failure -> assertThat(failure.code()).isEqualTo("DOCUMENT_STATE_CONFLICT"));
            assertThat(snapshot()).isEqualTo(before);
        }
        jdbc.update("UPDATE " + header + " SET status='APPROVED'," + (sales ? "customer_id" : "supplier_id") + "='other' WHERE id=?", valid.sourceDocumentId());
        assertThatThrownBy(() -> sourcedOrder(actor, sales, List.of(valid), key())).isInstanceOfSatisfying(PlatformApiException.class,
                failure -> assertThat(failure.code()).isEqualTo("REFERENCE_CONFLICT"));
        jdbc.update("UPDATE " + header + " SET " + (sales ? "customer_id" : "supplier_id") + "=? WHERE id=?", sales ? customer : supplier, valid.sourceDocumentId());
        jdbc.update("UPDATE " + (sales ? "flowora_sales_quote_line" : "flowora_purchase_request_line") + " SET item_id='other' WHERE id=?", valid.sourceLineId());
        var before = snapshot();
        assertThatThrownBy(() -> sourcedOrder(actor, sales, List.of(valid), key())).isInstanceOf(PlatformApiException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void quoteSourceCurrencyCannotBeRelabelled() {
        LineRequest valid = source(true, "APPROVED");
        jdbc.update("UPDATE flowora_sales_quote SET currency_code='USD' WHERE id=?", valid.sourceDocumentId());
        var before = snapshot();
        assertThatThrownBy(() -> sourcedOrder(actor, true, List.of(valid), key())).isInstanceOfSatisfying(PlatformApiException.class,
                failure -> assertThat(failure.code()).isEqualTo("REFERENCE_CONFLICT"));
        assertThat(snapshot()).isEqualTo(before);
    }

    LineRequest source(boolean sales, String status) {
        String id = key(), lineId = key();
        if (sales) {
            jdbc.update("INSERT INTO flowora_sales_quote(id,organization_id,number,customer_id,status,currency_code,valid_until,total_amount,requester_user_id) VALUES (?,?,?,?,?,'CNY',CURRENT_DATE,10,?)", id, org, id.replace("-", ""), customer, status, actor.userId());
            jdbc.update("INSERT INTO flowora_sales_quote_line(id,organization_id,quote_id,item_id,quantity,unit_price) VALUES (?,?,?,?,1,10)", lineId, org, id, item);
        } else {
            jdbc.update("INSERT INTO flowora_purchase_request(id,organization_id,number,supplier_id,warehouse_id,requester_user_id,status) VALUES (?,?,?,?,?,?,?)", id, org, id.replace("-", ""), supplier, warehouse, actor.userId(), status);
            jdbc.update("INSERT INTO flowora_purchase_request_line(id,organization_id,purchase_request_id,item_id,quantity,estimated_unit_cost) VALUES (?,?,?,?,1,10)", lineId, org, id, item);
        }
        return referenced(item, sales ? "SALES_QUOTE" : "PURCHASE_REQUEST", id, lineId);
    }
    LineRequest referenced(String itemId, String type, String documentId, String lineId) {
        return new LineRequest(itemId, amount("1"), amount("10"), BigDecimal.ZERO, BigDecimal.ZERO, type, documentId, lineId);
    }
    DocumentView sourcedOrder(FloworaPrincipal creator, boolean sales, List<LineRequest> lines, String requestKey) {
        return sales
                ? documents.createSalesOrder(creator, new SalesOrderRequest(customer, warehouse, "CNY", null, null, lines), requestKey)
                : documents.createPurchaseOrder(creator, new PurchaseOrderRequest(supplier, warehouse, "CNY", null, null, lines), requestKey);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void orderCreateReplayBelongsToCreatorAndKeepsCurrentState(boolean sales) {
        String requestKey = key();
        DocumentView original = createOrder(actor, sales, requestKey);
        run(() -> sales ? documents.confirmSalesOrder(org, original.id(), 0)
                : documents.confirmPurchaseOrder(org, original.id(), 0));
        var before = snapshot();
        DocumentView replay = createOrder(actor, sales, "  " + requestKey + "  ");
        assertThat(replay.id()).isEqualTo(original.id());
        assertThat(replay.lines()).isEqualTo(original.lines());
        assertThat(replay.status()).isEqualTo("CONFIRMED");
        assertThat(replay.version()).isEqualTo(1);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void anotherCreatorCannotReplayEvenWithAllScopeAndSamePayload(boolean sales) {
        String requestKey = key();
        createOrder(actor, sales, requestKey);
        var before = snapshot();
        var other = new FloworaPrincipal("system:r5l-other", "r5l-other", "Other creator", org,
                "R2 isolated test", List.of("ADMIN"));
        assertThatThrownBy(() -> createOrder(other, sales, requestKey))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(createOrder(other, sales, key()).id()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void historicalBlankOwnerOrderCannotBeClaimedByReplay(boolean sales) {
        String requestKey = key();
        DocumentView original = createOrder(actor, sales, requestKey);
        String table = sales ? "flowora_sales_order" : "flowora_purchase_order";
        String column = sales ? "sales_user_id" : "buyer_user_id";
        jdbc.update("UPDATE " + table + " SET " + column + "='' WHERE id=? AND organization_id=?", original.id(), org);
        var before = snapshot();
        assertThatThrownBy(() -> createOrder(actor, sales, requestKey))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentCreatorsCannotShareOneCreateResult(boolean sales) throws Exception {
        String requestKey = key();
        var other = new FloworaPrincipal("system:r5l-other", "r5l-other", "Other creator", org,
                "R2 isolated test", List.of("ADMIN"));
        race(() -> createOrder(actor, sales, requestKey), () -> createOrder(other, sales, requestKey));
        String table = sales ? "flowora_sales_order" : "flowora_purchase_order";
        String column = sales ? "sales_user_id" : "buyer_user_id";
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE organization_id=? AND request_id=?",
                Integer.class, org, requestKey)).isEqualTo(1);
        String creator = jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE organization_id=? AND request_id=?",
                String.class, org, requestKey);
        var winner = actor.userId().equals(creator) ? actor : other;
        var loser = actor.userId().equals(creator) ? other : actor;
        var before = snapshot();
        assertThat(createOrder(winner, sales, requestKey).id()).isNotBlank();
        assertThatThrownBy(() -> createOrder(loser, sales, requestKey)).isInstanceOf(PlatformApiException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    DocumentView createOrder(FloworaPrincipal creator, boolean sales, String requestKey) {
        return run(() -> sales
                ? documents.createSalesOrder(creator, new SalesOrderRequest(customer, warehouse, "CNY", null, null, List.of(line("1"))), requestKey)
                : documents.createPurchaseOrder(creator, new PurchaseOrderRequest(supplier, warehouse, "CNY", null, null, List.of(line("1"))), requestKey));
    }

    @Test
    void inventorySummaryIncludesEveryPageAndGroupsTrackingDimensionsWithinTheOrganization() {
        tx.execute(status -> {
            for (int i = 0; i < 51; i++) {
                String dimensionItem = i == 0 ? item : UUID.randomUUID().toString();
                jdbc.update("INSERT INTO flowora_inventory_balance_v2(id,organization_id,warehouse_id,item_id,on_hand_quantity,average_cost) VALUES (?,?,?,?,1,?)",
                        UUID.randomUUID().toString(), org, warehouse, dimensionItem, i + 1);
                String movement = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO flowora_stock_movement(id,organization_id,number,movement_type,source_type,source_id,actor_user_id,request_id) VALUES (?,?,?,'COUNT','STOCK_COUNT',?,?,?)",
                        movement, org, movement, movement, actor.userId(), movement);
                jdbc.update("INSERT INTO flowora_stock_movement_line(id,organization_id,movement_id,sequence_no,item_id,to_warehouse_id,quantity,unit_cost,value_amount) VALUES (?,?,?,1,?,?,1,?,?)",
                        UUID.randomUUID().toString(), org, movement, dimensionItem, warehouse, i + 1, i + 1);
            }
            // A second location adds value to the same warehouse/item summary row.
            jdbc.update("INSERT INTO flowora_inventory_balance_v2(id,organization_id,warehouse_id,location_id,item_id,on_hand_quantity,average_cost) VALUES (?,?,?,'second-location',?,2,3)",
                    UUID.randomUUID().toString(), org, warehouse, item);
            String otherOrg = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'Summary other org','USD')", otherOrg);
            jdbc.update("INSERT INTO flowora_inventory_balance_v2(id,organization_id,warehouse_id,item_id,on_hand_quantity,average_cost) VALUES (?,?,?,?,100,999)",
                    UUID.randomUUID().toString(), otherOrg, warehouse, item);
            var reader = new CanonicalInventoryReader(jdbc);
            var first = reader.balances(org, "", PageRequest.of(0, 50));
            var last = reader.balances(org, "", PageRequest.of(1, 50));
            var summary = reader.summary(org);
            assertThat(first.content()).hasSize(50); assertThat(last.content()).hasSize(1);
            assertThat(summary.balanceCount()).isEqualTo(51);
            assertThat(summary.ledgerCount()).isEqualTo(51);
            assertThat(summary.inventoryValue()).isEqualByComparingTo("1332");
            assertThat(java.util.stream.Stream.concat(first.content().stream(), last.content().stream())
                    .map(com.flowora.erp.inventory.InventoryDtos.StockBalanceResponse::inventoryValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(summary.inventoryValue());
            var firstLedger = reader.ledger(org, "", "", PageRequest.of(0, 50));
            var lastLedger = reader.ledger(org, "", "", PageRequest.of(1, 50));
            assertThat(firstLedger.content()).hasSize(50); assertThat(lastLedger.content()).hasSize(1);
            assertThat(java.util.stream.Stream.concat(firstLedger.content().stream(), lastLedger.content().stream())
                    .map(com.flowora.erp.inventory.InventoryDtos.StockLedgerResponse::id).distinct().count()).isEqualTo(51);
            assertThat(reader.summary(otherOrg).inventoryValue()).isEqualByComparingTo("99900");
            assertThat(reader.summary(otherOrg).ledgerCount()).isZero();
            status.setRollbackOnly(); return null;
        });
    }

    @Test
    void emptyInventorySummaryReturnsActualZeroValues() {
        var summary = new CanonicalInventoryReader(jdbc).summary(org);
        assertThat(summary.inventoryValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.balanceCount()).isZero(); assertThat(summary.ledgerCount()).isZero();
    }

    @Test
    void rejectedFulfillmentLeavesEveryBusinessTableUnchanged() {
        for (String state : List.of("DRAFT", "CANCELLED", "CLOSED", "RECEIVED")) {
            var po = purchase("2");
            jdbc.update("UPDATE flowora_purchase_order SET status=? WHERE id=?", state, po.id());
            var before = snapshot();
            assertThatThrownBy(() -> run(() -> inventory.receive(actor, receipt(po, "1"), key())))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("DOCUMENT_STATE_CONFLICT");
            assertThat(snapshot()).isEqualTo(before);
        }
        for (String state : List.of("DRAFT", "CANCELLED", "CLOSED", "FULFILLED")) {
            var so = sales("2");
            jdbc.update("UPDATE flowora_sales_order SET status=? WHERE id=?", state, so.id());
            var before = snapshot();
            assertThatThrownBy(() -> run(() -> inventory.reserve(actor, reservation(so, "1"))))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("DOCUMENT_STATE_CONFLICT");
            assertThatThrownBy(() -> run(() -> inventory.ship(actor,
                    new ShipmentRequest(so.id(), List.of(new ShipmentLineRequest("missing", so.lines().getFirst().id(), amount("1")))), key())))
                    .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("DOCUMENT_STATE_CONFLICT");
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    @Test
    void partialFullReturnsTransfersAndCountsShareTheCanonicalLedger() {
        var po = purchase("10");
        run(() -> documents.confirmPurchaseOrder(org, po.id(), po.version()));
        var receipt = run(() -> inventory.receive(actor, receipt(po, "4"), key()));
        assertThat(documents.purchaseOrder(org, po.id()).status()).isEqualTo("PARTIALLY_RECEIVED");
        run(() -> inventory.receive(actor, receipt(po, "6"), key()));
        assertThat(documents.purchaseOrder(org, po.id()).status()).isEqualTo("RECEIVED");
        checkBalances("10", "100");
        var before = snapshot();
        assertThatThrownBy(() -> run(() -> inventory.receive(actor, receipt(po, "1"), key()))).isInstanceOf(PlatformApiException.class);
        assertThat(snapshot()).isEqualTo(before);

        var so = sales("4");
        run(() -> documents.confirmSalesOrder(org, so.id(), so.version()));
        var reserve = run(() -> inventory.reserve(actor, reservation(so, "2"))).getFirst();
        var shipment = run(() -> inventory.ship(actor, shipment(so, reserve, "1"), key()));
        assertThat(documents.salesOrder(org, so.id()).status()).isEqualTo("PARTIALLY_FULFILLED");
        run(() -> inventory.reserve(actor, reservation(so, "2")));
        assertThat(documents.salesOrder(org, so.id()).status()).isEqualTo("PARTIALLY_FULFILLED");
        run(() -> inventory.ship(actor, shipment(so, reserve, "1"), key()));
        var second = jdbc.queryForObject("SELECT id FROM flowora_stock_reservation WHERE sales_order_id=? AND status='ACTIVE'", String.class, so.id());
        run(() -> inventory.ship(actor, new ShipmentRequest(so.id(), List.of(new ShipmentLineRequest(second, so.lines().getFirst().id(), amount("2")))), key()));
        assertThat(documents.salesOrder(org, so.id()).status()).isEqualTo("FULFILLED");
        checkBalances("6", "60");

        run(() -> inventory.salesReturn(actor, new ReturnRequest(shipment.sourceId(), shipment.id(), "SELLABLE",
                List.of(new ReturnLineRequest(shipment.lines().getFirst().id(), amount("1")))), key()));
        run(() -> inventory.purchaseReturn(actor, new ReturnRequest(receipt.sourceId(), receipt.id(), "SELLABLE",
                List.of(new ReturnLineRequest(receipt.lines().getFirst().id(), amount("1")))), key()));
        checkBalances("6", "60");
        run(() -> inventory.transfer(actor, new TransferRequest(warehouse, target,
                List.of(new TransferLineRequest(item, null, null, null, null, amount("2")))), key()));
        checkBalances("6", "60");
        run(() -> inventory.count(actor, new CountRequest(target, null,
                List.of(new CountLineRequest(item, null, null, amount("3")))), key()));
        checkBalances("7", "70");
    }

    @Test
    void excessQuantityAndWrongWarehouseRollBackStockAndFulfillment() {
        var po = purchase("2"); run(() -> documents.confirmPurchaseOrder(org, po.id(), po.version()));
        var before = snapshot();
        assertThatThrownBy(() -> run(() -> inventory.receive(actor, receipt(po, "3"), key()))).isInstanceOf(PlatformApiException.class);
        assertThatThrownBy(() -> run(() -> inventory.receive(actor, new ReceiptRequest(po.id(), target, receipt(po,"1").lines()), key())))
                .isInstanceOf(PlatformApiException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void cancellationRacesWithReceivingReservationAndShipping() throws Exception {
        for (int i=0; i<5; i++) {
            var po = purchase("2"); run(() -> documents.confirmPurchaseOrder(org, po.id(), po.version()));
            race(() -> documents.cancelPurchaseOrder(org, po.id(), 1), () -> inventory.receive(actor, receipt(po, "2"), key()));
            var result = documents.purchaseOrder(org, po.id());
            assertThat(result.status()).isIn("CANCELLED", "RECEIVED");
            assertThat(result.lines().getFirst().fulfilledQuantity()).isEqualByComparingTo("CANCELLED".equals(result.status()) ? "0" : "2");

            var source = purchase("2"); run(() -> documents.confirmPurchaseOrder(org, source.id(), 0));
            run(() -> inventory.receive(actor, receipt(source,"2"), key()));
            var so = sales("2"); run(() -> documents.confirmSalesOrder(org, so.id(), 0));
            race(() -> documents.cancelSalesOrder(org, so.id(), 1), () -> inventory.reserve(actor, reservation(so,"2")));
            if ("RESERVED".equals(documents.salesOrder(org, so.id()).status())) {
                var reservation = jdbc.queryForObject("SELECT id FROM flowora_stock_reservation WHERE sales_order_id=?", String.class, so.id());
                race(() -> documents.cancelSalesOrder(org, so.id(), 2), () -> inventory.ship(actor,
                        new ShipmentRequest(so.id(), List.of(new ShipmentLineRequest(reservation, so.lines().getFirst().id(), amount("2")))), key()));
            }
            var sales = documents.salesOrder(org, so.id());
            assertThat(sales.status()).isIn("CANCELLED", "FULFILLED");
            assertThat(sales.lines().getFirst().reservedQuantity()).isEqualByComparingTo("0");
            assertThat(sales.lines().getFirst().fulfilledQuantity()).isEqualByComparingTo("CANCELLED".equals(sales.status()) ? "0" : "2");
        }
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_inventory_balance_v2 WHERE organization_id=? AND (reserved_quantity<0 OR on_hand_quantity<reserved_quantity)", Integer.class, org);
        assertThat(count).isZero();
    }

    @Test
    void compatibilityStockEngineWritesOnlyCanonicalBalancesAndProtectsReservations() {
        String receiptId=key();
        run(() -> inventory.postCompatibilityDelta(org,warehouse,item,amount("5"),amount("10"),"RECEIPT","PURCHASE_RECEIPT",receiptId,actor.userId()));
        run(() -> inventory.postCompatibilityDelta(org,warehouse,item,amount("5"),amount("10"),"RECEIPT","PURCHASE_RECEIPT",receiptId,actor.userId()));
        checkBalances("5","50");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_stock_balance WHERE organization_id=?",Integer.class,org)).isZero();
        var so=sales("4"); run(() -> documents.confirmSalesOrder(org,so.id(),0));
        run(() -> inventory.reserve(actor,reservation(so,"4")));
        var before=snapshot();
        assertThatThrownBy(() -> run(() -> { inventory.requireCompatibilitySalesQuantity(org,so.id(),so.lines().getFirst().id(),amount("1")); return null; }))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("RESERVATION_QUANTITY_EXCEEDED");
        assertThatThrownBy(() -> run(() -> inventory.postCompatibilityDelta(org,warehouse,item,amount("-2"),BigDecimal.ZERO,"SHIPMENT","SALES_DELIVERY",key(),actor.userId())))
                .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("INSUFFICIENT_AVAILABLE_STOCK");
        assertThat(snapshot()).isEqualTo(before);
        run(() -> inventory.postCompatibilityDelta(org,warehouse,item,amount("-1"),BigDecimal.ZERO,"SHIPMENT","SALES_DELIVERY",key(),actor.userId()));
        checkBalances("4","40");
    }

    void race(Supplier<?> first, Supplier<?> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> compete(barrier, first));
            var b = pool.submit(() -> compete(barrier, second));
            assertThat(List.of(a.get(20,TimeUnit.SECONDS), b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
    }
    boolean compete(CyclicBarrier barrier, Supplier<?> work) throws Exception {
        barrier.await(10,TimeUnit.SECONDS);
        try { run(work); return true; } catch (PlatformApiException conflict) { return false; }
    }
    void checkBalances(String quantity, String value) {
        var reader = new CanonicalInventoryReader(jdbc);
        var page = reader.balances(org,"",PageRequest.of(0,20));
        assertThat(page.content().stream().map(row -> row.quantity()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(quantity);
        assertThat(page.content().stream().map(row -> row.inventoryValue()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(value);
        var availability = inventory.availability(org,"",item);
        assertThat(availability.stream().map(AvailabilityView::onHand).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(quantity);
        var ledger = reader.ledger(org,"",item,PageRequest.of(0,100));
        assertThat(ledger.content().stream().map(row -> row.quantityDelta()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(quantity);
        assertThat(ledger.content().stream().map(row -> row.valueDelta()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(value);
    }
    Map<String,Object> snapshot() {
        var state = new LinkedHashMap<String,Object>();
        for (String table : List.of("flowora_inventory_balance_v2", "flowora_purchase_order", "flowora_purchase_order_line", "flowora_sales_order", "flowora_sales_order_line",
                "flowora_stock_movement", "flowora_stock_movement_line", "flowora_stock_reservation", "flowora_purchase_receipt", "flowora_purchase_receipt_line", "flowora_financial_source_event", "flowora_idempotency_record", "flowora_trade_source_line_link"))
            state.put(table,jdbc.queryForList("SELECT * FROM " + table + " WHERE organization_id=? ORDER BY id",org));
        return state;
    }
    DocumentView purchase(String quantity) { return run(() -> documents.createPurchaseOrder(actor,new PurchaseOrderRequest(supplier,warehouse,"CNY",null,null,List.of(line(quantity))),key())); }
    DocumentView sales(String quantity) { return run(() -> documents.createSalesOrder(actor,new SalesOrderRequest(customer,warehouse,"CNY",null,null,List.of(line(quantity))),key())); }
    LineRequest line(String quantity) { return new LineRequest(item,amount(quantity),amount("10"),BigDecimal.ZERO,BigDecimal.ZERO,null,null,null); }
    ReceiptRequest receipt(DocumentView po,String quantity) { return new ReceiptRequest(po.id(),warehouse,List.of(new ReceiptLineRequest(po.lines().getFirst().id(),item,null,null,null,null,null,null,amount(quantity),amount("10"),BigDecimal.ZERO))); }
    ReservationRequest reservation(DocumentView so,String quantity) { return new ReservationRequest(so.id(),null,List.of(new ReservationLineRequest(so.lines().getFirst().id(),warehouse,null,item,null,null,amount(quantity)))); }
    ShipmentRequest shipment(DocumentView so,ReservationView reserve,String quantity) { return new ShipmentRequest(so.id(),List.of(new ShipmentLineRequest(reserve.id(),so.lines().getFirst().id(),amount(quantity)))); }
    <T> T run(Supplier<T> work) { return tx.execute(status -> work.get()); }
    static BigDecimal amount(String value) { return new BigDecimal(value); }
    static String key() { return UUID.randomUUID().toString(); }
}
