package com.flowora.erp.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.identity.*;
import com.flowora.erp.project.ProjectReadScope;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real query regression; rollback removes only this test's fixtures. */
@EnabledIfEnvironmentVariable(named="FLOWORA_R2_MYSQL_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:13306/audit_flowora\\?.*")
class AnalyticsScopeMySqlTest {
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    TransactionStatus transaction;
    AnalyticsService service;
    String org, other, user;
    @BeforeAll static void connect() {
        var ds = new DriverManagerDataSource(System.getenv("FLOWORA_R2_MYSQL_URL"), "root", Objects.requireNonNullElse(System.getenv("FLOWORA_R2_MYSQL_PASSWORD"), ""));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds); transactions = new DataSourceTransactionManager(ds);
    }
    @BeforeEach void fixture() {
        transaction = transactions.getTransaction(new DefaultTransactionDefinition());
        service = new AnalyticsService(jdbc, new ObjectMapper());
        org = organization(); other = organization(); user = key();
        jdbc.update("INSERT INTO flowora_user_account(id,organization_id,username,display_name,password_hash) VALUES (?,?,?,'R5C test','not-a-login-hash')", user, org, user+"@audit.invalid");
        for (String id : List.of(org, other)) jdbc.update("INSERT INTO flowora_organization_membership(id,organization_id,user_id,status) VALUES (?,?,?,'ACTIVE')",key(),id,user);
    }
    @AfterEach void rollback() { if (transaction != null) transactions.rollback(transaction); }
    @Test void projectDepartmentAndActiveMembershipCountsAgreeWithDetailPolicy() {
        String department=key(), project=key();
        jdbc.update("INSERT INTO flowora_department(id,organization_id,code,name) VALUES (?,?,'A','R5C A')",department,org);
        jdbc.update("INSERT INTO flowora_project(id,organization_id,number,name,manager_user_id,target_date,currency_code,status,department_id) VALUES (?,?,?,'R5C','different-department-manager',CURRENT_DATE,'USD','AT_RISK',?)",project,org,"R5C-"+project.substring(0,20),department);
        var departmentActor=actor(org,DataScope.DEPARTMENT,department,List.of("project:view"));
        new ProjectReadScope(jdbc).require(departmentActor,project);
        assertThat(risks(departmentActor)).isEqualByComparingTo("1");
        assertThat(risks(actor(org,DataScope.DEPARTMENT,null,List.of("project:view")))).isZero();
        var assigned=actor(org,DataScope.ASSIGNED,null,List.of("project:view"));
        String member=key();
        jdbc.update("INSERT INTO flowora_project_member(id,organization_id,project_id,user_id,project_role) VALUES (?,?,?,?,'MEMBER')",member,org,project,user);
        new ProjectReadScope(jdbc).require(assigned,project);
        assertThat(risks(assigned)).isEqualByComparingTo("1");
        jdbc.update("UPDATE flowora_project_member SET active=FALSE WHERE id=?",member);
        assertThat(risks(assigned)).isZero();
        jdbc.update("UPDATE flowora_project_member SET active=TRUE,organization_id=? WHERE id=?",other,member);
        assertThat(risks(assigned)).isZero();
    }
    @Test void crossOrganizationReloadsTargetPermissionsAndScopeOnEveryRequest() {
        var auth=mock(DatabaseIdentityAuthenticator.class); service.setAuthenticator(auth);
        var current=actor(org,DataScope.ALL,null,List.of("analytics:cross-org","sales:view"));
        when(auth.principalForOrganization(user,org)).thenReturn(current);
        when(auth.principalForOrganization(user,other)).thenReturn(actor(other,DataScope.ALL,null,List.of("analytics:cross-org","finance:view")));
        var rows=service.crossOrganization(current,List.of(),"USD");
        assertThat(rows).hasSize(2);
        var sales=rows.stream().filter(r->r.organizationId().equals(org)).findFirst().orElseThrow();
        var finance=rows.stream().filter(r->r.organizationId().equals(other)).findFirst().orElseThrow();
        assertThat(sales.sales()).isZero(); assertThat(sales.cash()).isNull(); assertThat(sales.receivables()).isNull();
        assertThat(finance.sales()).isNull(); assertThat(finance.cash()).isZero();
        when(auth.principalForOrganization(user,other)).thenReturn(actor(other,DataScope.SELF,null,List.of("analytics:cross-org","finance:view")));
        assertThat(service.crossOrganization(current,List.of(),"USD")).extracting(AnalyticsDtos.OrganizationSummary::organizationId).containsExactly(org);
        when(auth.principalForOrganization(user,other)).thenReturn(actor(other,DataScope.ALL,null,List.of("finance:view")));
        assertThat(service.crossOrganization(current,List.of(),"USD")).hasSize(1);
        jdbc.update("UPDATE flowora_organization_membership SET status='DISABLED' WHERE organization_id=?",other);
        clearInvocations(auth);
        assertThat(service.crossOrganization(current,List.of(other),"USD")).isEmpty();
        verifyNoInteractions(auth);
    }
    @Test void restrictedScopesAndMissingTargetValidatorCannotReadOrganizationAggregates() {
        for(var scope:List.of(DataScope.DEPARTMENT,DataScope.SELF,DataScope.ASSIGNED,DataScope.ALL))
            assertThatThrownBy(()->service.crossOrganization(actor(org,scope,null,List.of("analytics:cross-org")),List.of(),"USD"))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void organizationWideWorkspaceCardsAreAbsentForRestrictedScopes() {
        var actor=actor(org,DataScope.SELF,null,List.of("finance:view","inventory:view"));
        assertThat(service.workspace(actor).cards()).isEmpty();
    }
    java.math.BigDecimal risks(FloworaPrincipal actor) { return service.workspace(actor).cards().stream().filter(c->c.code().equals("AT_RISK_PROJECTS")).findFirst().orElseThrow().value(); }
    FloworaPrincipal actor(String id,DataScope scope,String department,List<String> permissions) { return new FloworaPrincipal(user,user+"@audit.invalid","R5C",id,id,"membership",department,scope,List.of("CUSTOM"),permissions,false); }
    String organization() { String id=key(); jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'R5C isolated','USD')",id); jdbc.update("INSERT INTO flowora_finance_setting(organization_id,base_currency_code) VALUES (?,'USD')",id); return id; }
    static String key() { return UUID.randomUUID().toString(); }
}
