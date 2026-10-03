package com.flowora.erp.workflow.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static com.flowora.erp.workflow.v2.WorkflowV2Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL and annotation-driven nested transaction proxies, including rollback control cases. */
@EnabledIfEnvironmentVariable(named="FLOWORA_R2_MYSQL_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:13306/audit_flowora\\?.*")
class WorkflowFallbackMySqlTest {
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static TransactionTemplate tx;
    String org; String user;
    final ObjectMapper mapper=new ObjectMapper();
    WorkflowTemplateService templates;
    WorkflowApproverResolver resolver;
    WorkflowEngineService engine;
    @BeforeAll static void connect() {
        var ds=new DriverManagerDataSource(System.getenv("FLOWORA_R2_MYSQL_URL"),"root",Objects.requireNonNullElse(System.getenv("FLOWORA_R2_MYSQL_PASSWORD"),""));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds); manager=new DataSourceTransactionManager(ds); tx=new TransactionTemplate(manager);
    }
    @BeforeEach void fixture() {
        org=UUID.randomUUID().toString(); jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'Fallback fixture','USD')",org);
        user=UUID.randomUUID().toString(); jdbc.update("INSERT INTO flowora_user_account(id,organization_id,username,display_name,password_hash) VALUES (?,?,?,'Fallback user','disabled-test-hash')",user,org,user);
        templates=proxy(WorkflowTemplateService.class,new WorkflowTemplateService(jdbc,mapper,new WorkflowConditionEvaluator()));
        resolver=mock(WorkflowApproverResolver.class);
        engine=proxy(WorkflowEngineService.class,new WorkflowEngineService(jdbc,mapper,templates,resolver,mock(WorkflowOutboxService.class),new WorkflowResourceProjectionService(jdbc)));
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM flowora_audit_event WHERE organization_id=?",org);
        jdbc.update("UPDATE flowora_workflow_template SET current_version_id=NULL WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_workflow_step_definition WHERE workflow_version_id IN (SELECT v.id FROM flowora_workflow_version v JOIN flowora_workflow_template t ON t.id=v.template_id WHERE t.organization_id=?)",org);
        jdbc.update("DELETE FROM flowora_workflow_version WHERE template_id IN (SELECT id FROM flowora_workflow_template WHERE organization_id=?)",org);
        jdbc.update("DELETE FROM flowora_workflow_template WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_user_account WHERE organization_id=?",org);
        jdbc.update("DELETE FROM flowora_organization WHERE id=?",org);
    }
    @Test void absentTemplateDoesNotPoisonCallerTransaction() {
        tx.executeWithoutResult(status->{
            jdbc.update("UPDATE flowora_organization SET name='Fallback committed' WHERE id=?",org);
            assertThatThrownBy(()->engine.start(actor(),request(),"test")).isInstanceOf(WorkflowTemplateNotFoundException.class);
            assertThat(status.isRollbackOnly()).isFalse();
        });
        assertThat(name()).isEqualTo("Fallback committed"); assertThat(instances()).isZero(); verifyNoInteractions(resolver);
    }
    @Test void ambiguousTemplateStillRollsBackCallerWrites() {
        template(); template(new ConditionGroup(Logic.ALL,List.of(new Condition("amount",Operator.GTE,BigDecimal.ZERO))));
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            jdbc.update("UPDATE flowora_organization SET name='Must roll back' WHERE id=?",org);
            assertThatThrownBy(()->engine.start(actor(),request(),"test")).isInstanceOf(PlatformApiException.class)
                    .hasMessage("WORKFLOW_TEMPLATE_AMBIGUOUS");
            assertThat(status.isRollbackOnly()).isTrue();
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(name()).isEqualTo("Fallback fixture"); assertThat(instances()).isZero();
    }
    @Test void failureAfterEngineInsertStillRollsBackInstanceAndCallerWrites() {
        template();
        when(resolver.resolve(any(),any(),any(),any(),any(),any(),any())).thenThrow(new PlatformApiException(HttpStatus.CONFLICT,"APPROVER_FAILURE","test"));
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            jdbc.update("UPDATE flowora_organization SET name='Must roll back' WHERE id=?",org);
            assertThatThrownBy(()->engine.start(actor(),request(),"test")).isInstanceOf(PlatformApiException.class).hasMessage("APPROVER_FAILURE");
            assertThat(status.isRollbackOnly()).isTrue();
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(name()).isEqualTo("Fallback fixture"); assertThat(instances()).isZero();
    }
    void template() { template(ConditionGroup.always()); }
    void template(ConditionGroup condition) {
        var t=templates.create(actor(),new TemplateRequest(UUID.randomUUID().toString(),"Fallback test","GENERAL",1,"test"),"test");
        var v=templates.createVersion(actor(),t.id(),new VersionRequest(condition,List.of(new StepRequest("ONE","Review",1,CompletionMode.SERIAL,ApproverType.USER,"missing",1)),false),"test");
        templates.publish(actor(),t.id(),v.id(),"test","test");
    }
    StartRequest request() { return new StartRequest("GENERAL","resource",1,"user",BigDecimal.ONE,"USD",Map.of()); }
    FloworaPrincipal actor() { return new FloworaPrincipal(user,"user","User",org,"Org",List.of("ADMIN")); }
    String name() { return jdbc.queryForObject("SELECT name FROM flowora_organization WHERE id=?",String.class,org); }
    int instances() { return jdbc.queryForObject("SELECT COUNT(*) FROM flowora_workflow_instance WHERE organization_id=?",Integer.class,org); }
    <T> T proxy(Class<T> type,T target) {
        var factory=new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource())); return type.cast(factory.getProxy());
    }
}
