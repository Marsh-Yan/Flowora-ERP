package com.flowora.erp.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "FLOWORA_R2_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13306/audit_flowora\\?.*")
class OrganizationFinanceMySqlTest {
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    final String actorId = UUID.randomUUID().toString();

    @BeforeAll static void connect() {
        source = new DriverManagerDataSource(System.getenv("FLOWORA_R2_MYSQL_URL"), "root",
                Objects.requireNonNullElse(System.getenv("FLOWORA_R2_MYSQL_PASSWORD"), ""));
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }

    PlatformDirectoryService service(JdbcTemplate template) {
        var target = new PlatformDirectoryService(template, null, null, mock(DatabaseAccountService.class), new ObjectMapper());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (PlatformDirectoryService) proxy.getProxy();
    }

    FloworaPrincipal actor() {
        return new FloworaPrincipal(actorId, "r5g-admin@audit.invalid", "Admin", "org-demo", "Demo",
                "membership-admin", null, DataScope.ALL, List.of("ADMIN"), List.of("organization:configure"), false);
    }

    PlatformDirectoryService.CreateOrganization command(String name, String parent) {
        return new PlatformDirectoryService.CreateOrganization(parent, name, " eur ", "Asia/Shanghai", 4,
                3, 6, 5, "HALF_UP", 90, 45, "REQUIRED");
    }

    @Test void initializesFinanceInTheSameTransactionWithTheOrganizationsCurrencyAndFiscalYear() {
        new TransactionTemplate(manager).execute(status -> {
            jdbc.update("INSERT INTO flowora_user_account(id,organization_id,username,display_name,password_hash,active) VALUES (?,'org-demo',?,'R5G synthetic admin','unused-test-hash',TRUE)", actorId, "r5g-" + actorId + "@audit.invalid");
            var child = service(jdbc).createOrganization(actor(), command("R5G finance " + UUID.randomUUID(), "org-demo"));
            var settings = jdbc.queryForMap("SELECT * FROM flowora_finance_setting WHERE organization_id=?", child.id());
            assertThat(child.baseCurrencyCode()).isEqualTo("EUR");
            assertThat(child.fiscalYearStartMonth()).isEqualTo(4);
            assertThat(settings.get("base_currency_code")).isEqualTo("EUR");
            assertThat(settings.get("fiscal_year_start_month")).isEqualTo(4);
            for (String key : List.of("match_quantity_tolerance", "match_price_tolerance_rate", "match_tax_tolerance"))
                assertThat((java.math.BigDecimal) settings.get(key)).isEqualByComparingTo("0");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_role WHERE organization_id=? AND code='ADMIN'", Integer.class, child.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_organization_membership WHERE organization_id=? AND user_id=?", Integer.class, child.id(), actor().userId())).isEqualTo(1);
            status.setRollbackOnly();
            return null;
        });
    }

    @Test void aLaterProvisioningFailureRollsBackOrganizationAndFinanceTogether() {
        String name = "R5G rollback " + UUID.randomUUID();
        var failing = new JdbcTemplate(source) {
            @Override public int update(String sql, Object... args) {
                if (sql.contains("INSERT INTO flowora_role (")) throw new DataIntegrityViolationException("Synthetic provisioning failure");
                return super.update(sql, args);
            }
        };
        int settings = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_finance_setting", Integer.class);
        assertThatThrownBy(() -> service(failing).createOrganization(actor(), command(name, "org-demo")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_organization WHERE name=?", Integer.class, name)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_finance_setting", Integer.class)).isEqualTo(settings);
    }

    @Test void rejectsForeignParentWithoutCreatingFinancialState() {
        String name = "R5G foreign " + UUID.randomUUID();
        assertThatThrownBy(() -> service(jdbc).createOrganization(actor(), command(name, UUID.randomUUID().toString())))
                .isInstanceOfSatisfying(PlatformApiException.class, ex -> assertThat(ex.code()).isEqualTo("ORGANIZATION_ACCESS_DENIED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flowora_organization WHERE name=?", Integer.class, name)).isZero();
    }

    @Test void backfillIsRepeatableAndPreservesExistingFinanceConfiguration() {
        new TransactionTemplate(manager).execute(status -> {
            String missing = UUID.randomUUID().toString(), existing = UUID.randomUUID().toString();
            for (String id : List.of(missing, existing))
                jdbc.update("INSERT INTO flowora_organization(id,name,base_currency_code,fiscal_year_start_month) VALUES (?,'R5G backfill','JPY',10)", id);
            jdbc.update("INSERT INTO flowora_finance_setting(organization_id,base_currency_code,fiscal_year_start_month,match_quantity_tolerance) VALUES (?,'GBP',7,3)", existing);
            var backfill = new ResourceDatabasePopulator(new ClassPathResource("db/migration/V19__organization_finance_initialization.sql"));
            backfill.execute(source); backfill.execute(source);
            assertThat(jdbc.queryForObject("SELECT base_currency_code FROM flowora_finance_setting WHERE organization_id=?", String.class, missing)).isEqualTo("JPY");
            assertThat(jdbc.queryForObject("SELECT fiscal_year_start_month FROM flowora_finance_setting WHERE organization_id=?", Integer.class, missing)).isEqualTo(10);
            assertThat(jdbc.queryForObject("SELECT base_currency_code FROM flowora_finance_setting WHERE organization_id=?", String.class, existing)).isEqualTo("GBP");
            assertThat(jdbc.queryForObject("SELECT fiscal_year_start_month FROM flowora_finance_setting WHERE organization_id=?", Integer.class, existing)).isEqualTo(7);
            assertThat(jdbc.queryForObject("SELECT match_quantity_tolerance FROM flowora_finance_setting WHERE organization_id=?", java.math.BigDecimal.class, existing)).isEqualByComparingTo("3");
            status.setRollbackOnly(); return null;
        });
    }
}
