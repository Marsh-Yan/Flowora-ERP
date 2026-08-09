package com.flowora.erp.demo;

import com.flowora.erp.common.api.WorkflowPermissionException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.AuditEventEntity;
import com.flowora.erp.workflow.AuditEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.time.Instant;

@Service
@Conditional(DemoDataEnabledCondition.class)
public class DemoDataService {
    private static final String ORGANIZATION_ID = "org-demo";
    private static final String SEED_SCRIPT = "db/demo/seed.sql";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final AuditEventRepository auditRepository;
    private final boolean seedOnStart;

    public DemoDataService(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            AuditEventRepository auditRepository,
            @Value("${flowora.demo.seed-on-start:false}") boolean seedOnStart
    ) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.auditRepository = auditRepository;
        this.seedOnStart = seedOnStart;
    }

    @Transactional
    public DemoDataStatus reset(FloworaPrincipal actor, String requestId) {
        requireAdmin(actor);
        DemoDataStatus result = resetInternal();
        auditRepository.save(new AuditEventEntity(
                actor.organizationId(), actor.userId(), "DEMO_DATA_RESET", "DEMO_DATA", ORGANIZATION_ID,
                requestId, "{\"resetCount\":" + result.resetCount() + "}"
        ));
        return result;
    }

    @Transactional(readOnly = true)
    public DemoDataStatus status(FloworaPrincipal actor) {
        requireAdmin(actor);
        return statusInternal();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnStartup() {
        if (seedOnStart) {
            resetInternal();
        }
    }

    private DemoDataStatus resetInternal() {
        lockControlRow();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(SEED_SCRIPT));
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, dataSource);
        jdbcTemplate.update(
                "UPDATE flowora_demo_control SET last_reset_at = CURRENT_TIMESTAMP, reset_count = reset_count + 1 WHERE organization_id = ?",
                ORGANIZATION_ID
        );
        return statusInternal();
    }

    private DemoDataStatus statusInternal() {
        Instant lastResetAt = jdbcTemplate.query(
                "SELECT last_reset_at FROM flowora_demo_control WHERE organization_id = ?",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null,
                ORGANIZATION_ID
        );
        Long resetCount = jdbcTemplate.query(
                "SELECT reset_count FROM flowora_demo_control WHERE organization_id = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L,
                ORGANIZATION_ID
        );
        return new DemoDataStatus(
                true,
                count("flowora_customer"),
                count("flowora_supplier"),
                count("flowora_item"),
                count("flowora_warehouse"),
                count("flowora_purchase_order"),
                count("flowora_purchase_receipt"),
                count("flowora_sales_order"),
                count("flowora_sales_delivery"),
                count("flowora_journal_entry"),
                count("flowora_project"),
                lastResetAt,
                resetCount == null ? 0L : resetCount
        );
    }

    private long count(String table) {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE organization_id = ?",
                Long.class,
                ORGANIZATION_ID
        );
        return result == null ? 0L : result;
    }

    private void lockControlRow() {
        jdbcTemplate.queryForObject(
                "SELECT organization_id FROM flowora_demo_control WHERE organization_id = ? FOR UPDATE",
                String.class,
                ORGANIZATION_ID
        );
    }

    private void requireAdmin(FloworaPrincipal actor) {
        if (actor == null || !ORGANIZATION_ID.equals(actor.organizationId()) || !actor.roles().contains("ADMIN")) {
            throw new WorkflowPermissionException("Only administrators can reset demo data");
        }
    }
}
