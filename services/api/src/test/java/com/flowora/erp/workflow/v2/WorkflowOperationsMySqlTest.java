package com.flowora.erp.workflow.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.analytics.AnalyticsService;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.system.OperationalMetrics;
import com.flowora.erp.system.DiagnosticsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static com.flowora.erp.workflow.v2.WorkflowV2Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in real database regression; never accepts a business database URL. */
@EnabledIfEnvironmentVariable(named="FLOWORA_R2_MYSQL_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:13306/audit_flowora\\?.*")
class WorkflowOperationsMySqlTest {
    static DataSource ds;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static TransactionTemplate tx;
    final ObjectMapper mapper = new ObjectMapper();
    String org;
    final Map<String,String> users=new HashMap<>();
    @BeforeAll static void connect() {
        ds = new DriverManagerDataSource(System.getenv("FLOWORA_R2_MYSQL_URL"), "root", Objects.requireNonNullElse(System.getenv("FLOWORA_R2_MYSQL_PASSWORD"), ""));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds); manager = new DataSourceTransactionManager(ds); tx = new TransactionTemplate(manager);
    }
    @BeforeEach void fixture() {
        org=key();
        jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'R4 isolated workflow','USD')",org);
        jdbc.update("INSERT INTO flowora_finance_setting(organization_id,base_currency_code) VALUES (?,'USD')",org);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM flowora_delivery_attempt WHERE outbox_event_id IN (SELECT id FROM flowora_outbox_event WHERE organization_id=?)",org);
        jdbc.update("UPDATE flowora_workflow_template SET current_version_id=NULL WHERE organization_id=?",org);
        for(String table:List.of("flowora_notification","flowora_outbox_event","flowora_workflow_decision","flowora_workflow_approval_task","flowora_workflow_task","flowora_activity_event","flowora_audit_event")) jdbc.update("DELETE FROM "+table+" WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_workflow_step_instance WHERE workflow_instance_id IN (SELECT id FROM flowora_workflow_instance WHERE organization_id=?)",org);
        jdbc.update("DELETE FROM flowora_workflow_instance WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_workflow_transition_definition WHERE workflow_version_id IN (SELECT v.id FROM flowora_workflow_version v JOIN flowora_workflow_template t ON t.id=v.template_id WHERE t.organization_id=?)",org);
        jdbc.update("DELETE FROM flowora_workflow_step_definition WHERE workflow_version_id IN (SELECT v.id FROM flowora_workflow_version v JOIN flowora_workflow_template t ON t.id=v.template_id WHERE t.organization_id=?)",org);
        jdbc.update("DELETE FROM flowora_workflow_version WHERE template_id IN (SELECT id FROM flowora_workflow_template WHERE organization_id=?)",org);
        jdbc.update("DELETE FROM flowora_workflow_template WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_finance_setting WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_user_account WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_organization WHERE id=?",org);
    }
    @Test void scheduledEntryKeepsLocksThroughDeliveryAcrossTwoWorkers() throws Exception {
        var outbox = new WorkflowOutboxService(jdbc,mapper,manager);
        for(int i=0;i<20;i++) outbox.enqueue(org,"R4_TEST","GENERAL",key(),"r4-user",Map.of("title","test"));
        var barrier=new CyclicBarrier(2);
        JdbcTemplate checked=new JdbcTemplate(ds) {
            @Override public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                if(sql.contains("SKIP LOCKED")) {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    try { barrier.await(10,TimeUnit.SECONDS); } catch(Exception e) { throw new IllegalStateException(e); }
                }
                return super.query(sql,rowMapper,args);
            }
        };
        var worker1=new WorkflowOutboxService(checked,mapper,manager);
        var worker2=new WorkflowOutboxService(checked,mapper,manager);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(worker1::scheduledDispatch); var b=pool.submit(worker2::scheduledDispatch);
            a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(count("flowora_outbox_event","status='DELIVERED'")).isEqualTo(20);
        assertThat(count("flowora_notification","1=1")).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_delivery_attempt a JOIN flowora_outbox_event e ON e.id=a.outbox_event_id WHERE e.organization_id=?",Integer.class,org)).isEqualTo(20);
    }
    @Test void interruptionRollsBackNotificationAndReplayDoesNotDuplicateIt() {
        var outbox=new WorkflowOutboxService(jdbc,mapper,manager);
        String event=outbox.enqueue(org,"R4_TEST","GENERAL",key(),"r4-user",Map.of());
        JdbcTemplate interrupted=new JdbcTemplate(ds) {
            @Override public int update(String sql,Object... args) {
                int result=super.update(sql,args);
                if(sql.contains("INSERT INTO flowora_notification") && Arrays.asList(args).contains(event)) throw new AssertionError("simulated process interruption");
                return result;
            }
        };
        assertThatThrownBy(()->new WorkflowOutboxService(interrupted,mapper,manager).scheduledDispatch()).isInstanceOf(AssertionError.class);
        assertThat(count("flowora_notification","1=1")).isZero();
        assertThat(count("flowora_outbox_event","status='PENDING' AND attempts=0")).isEqualTo(1);
        outbox.scheduledDispatch();
        jdbc.update("UPDATE flowora_outbox_event SET status='DEAD' WHERE id=?",event);
        run(()->{outbox.replay(org,event);return null;}); outbox.scheduledDispatch();
        assertThat(count("flowora_notification","1=1")).isEqualTo(1);
        assertThat(count("flowora_outbox_event","status='DELIVERED'")).isEqualTo(1);
    }
    @Test void retryAndDeadMetricsFollowEventsAndClearAfterRecovery() {
        var outbox=new WorkflowOutboxService(jdbc,mapper,manager);
        String event=outbox.enqueue(org,"R4_TEST","GENERAL",key(),"r4-user",Map.of());
        var registry=new SimpleMeterRegistry();
        var factory=new StaticListableBeanFactory(Map.of("jdbc",jdbc));
        var metrics=new OperationalMetrics(registry,factory.getBeanProvider(JdbcTemplate.class));
        double deadBefore=registry.get("flowora.outbox.failed").gauge().value();
        double retryBefore=registry.get("flowora.outbox.retry").gauge().value();
        // Valid JSON with an invalid object shape fails payload decoding deterministically.
        jdbc.update("UPDATE flowora_outbox_event SET payload_json='[]' WHERE id=?",event);
        outbox.scheduledDispatch();
        assertThat(count("flowora_outbox_event","status='RETRY' AND attempts=1")).isEqualTo(1);
        assertThat(registry.get("flowora.outbox.retry").gauge().value()).isEqualTo(retryBefore+1);
        for(int i=0;i<4;i++) { jdbc.update("UPDATE flowora_outbox_event SET available_at=CURRENT_TIMESTAMP WHERE id=?",event); outbox.scheduledDispatch(); }
        assertThat(registry.get("flowora.outbox.failed").gauge().value()).isEqualTo(deadBefore+1);
        var diagnostics=new DiagnosticsService(factory.getBeanProvider(JdbcTemplate.class),factory.getBeanProvider(StringRedisTemplate.class),mapper,"test","R4",false,".",".");
        assertThat(diagnostics.snapshot().taskBacklog().get("outboxFailed")).isEqualTo((long)deadBefore+1);
        assertThat(new AnalyticsService(jdbc,mapper).workspace(actor("r4-user")).risks()).contains("OUTBOX_FAILURES");
        jdbc.update("UPDATE flowora_outbox_event SET payload_json='{}' WHERE id=?",event);
        run(()->{outbox.replay(org,event);return null;}); outbox.scheduledDispatch();
        assertThat(registry.get("flowora.outbox.failed").gauge().value()).isEqualTo(deadBefore);
        assertThat(diagnostics.snapshot().taskBacklog().get("outboxFailed")).isEqualTo((long)deadBefore);
        assertThat(registry.get("flowora.outbox.retry").gauge().value()).isEqualTo(retryBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_delivery_attempt WHERE outbox_event_id=?",Integer.class,event)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT outcome FROM flowora_delivery_attempt WHERE outbox_event_id=? ORDER BY attempt_number DESC LIMIT 1",String.class,event)).isEqualTo("DELIVERED");
        assertThat(new AnalyticsService(jdbc,mapper).workspace(actor("r4-user")).risks()).doesNotContain("OUTBOX_FAILURES");
        java.lang.ref.Reference.reachabilityFence(metrics);
    }
    @Test void v2TasksCountOnlyForTheVisibleAssigneeAndTerminalActionsClearThem() {
        var templates=new WorkflowTemplateService(jdbc,mapper,new WorkflowConditionEvaluator());
        var requester=actor("r4-requester"); var approver=actor("r4-approver");
        var template=run(()->templates.create(requester,new TemplateRequest(key(),"R4 task","GENERAL",1,"isolated test"),key()));
        var version=run(()->templates.createVersion(requester,template.id(),new VersionRequest(ConditionGroup.always(),List.of(new StepRequest("ONE","Review",1,CompletionMode.SERIAL,ApproverType.USER,approver.userId(),1)),false),key()));
        run(()->templates.publish(requester,template.id(),version.id(),"isolated test",key()));
        var resolver=mock(WorkflowApproverResolver.class);
        when(resolver.resolve(anyString(),anyString(),any(),any(),anyString(),any(),any())).thenReturn(List.of(new WorkflowApproverResolver.ResolvedApprover(approver.userId(),approver.userId())));
        var engine=new WorkflowEngineService(jdbc,mapper,templates,resolver,new WorkflowOutboxService(jdbc,mapper,manager),new WorkflowResourceProjectionService(jdbc));
        var analytics=new AnalyticsService(jdbc,mapper);
        for(WorkflowAction action:List.of(WorkflowAction.APPROVE,WorkflowAction.REJECT,WorkflowAction.WITHDRAW)) {
            var instance=run(()->engine.start(requester,new StartRequest("GENERAL",key(),1,null,BigDecimal.ONE,"USD",Map.of()),key()));
            assertThat(approvals(analytics,approver)).isEqualByComparingTo("1");
            assertThat(approvals(analytics,requester)).isZero();
            var task=engine.inbox(approver,"MINE",false).getFirst();
            jdbc.update("UPDATE flowora_workflow_approval_task SET due_at='2020-01-01 00:00:00' WHERE id=?",task.id());
            assertThat(engine.inbox(approver,"MINE",true)).hasSize(1);
            if(action==WorkflowAction.WITHDRAW) run(()->engine.actOnInstance(requester,instance.id(),new ActionRequest(action,"test",null),key()));
            else run(()->engine.actOnTask(approver,task.id(),task.version(),new ActionRequest(action,"test",null),key()));
            assertThat(approvals(analytics,approver)).isZero();
            assertThat(engine.inbox(approver,"MINE",true)).isEmpty();
        }
        jdbc.update("INSERT INTO flowora_workflow_task(id,organization_id,resource_type,resource_id,title,requester_user_id,assignee_role,status) VALUES (?,?,'GENERAL','r4','Legacy role task','other','ADMIN','OPEN')",key(),org);
        assertThat(approvals(analytics,approver)).isZero();
        var hidden=new FloworaPrincipal("r4-approver","hidden","hidden",org,"R4","membership",null,com.flowora.erp.identity.DataScope.SELF,List.of(),List.of(),false);
        assertThat(analytics.workspace(hidden).cards()).isEmpty();
        assertThat(analytics.workspace(hidden).risks()).isEmpty();
    }
    BigDecimal approvals(AnalyticsService service,FloworaPrincipal actor) { return service.workspace(actor).cards().stream().filter(c->c.code().equals("MY_APPROVALS")).findFirst().orElseThrow().value(); }
    int count(String table,String predicate) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE organization_id=? AND "+predicate,Integer.class,org); }
    FloworaPrincipal actor(String user) {
        String id=users.computeIfAbsent(user,label->{String value=key(); jdbc.update("INSERT INTO flowora_user_account(id,organization_id,username,display_name,password_hash) VALUES (?,?,?,?,'disabled-test-hash')",value,org,label,label); return value;});
        return new FloworaPrincipal(id,user,user,org,"R4",List.of("ADMIN"));
    }
    <T>T run(Supplier<T> action) { return tx.execute(status->action.get()); }
    static String key() { return UUID.randomUUID().toString(); }
}
