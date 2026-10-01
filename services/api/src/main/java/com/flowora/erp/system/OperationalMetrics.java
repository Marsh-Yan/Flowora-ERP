package com.flowora.erp.system;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {
    private final JdbcTemplate jdbc;

    public OperationalMetrics(MeterRegistry registry, ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jdbc = jdbcProvider.getIfAvailable();
        Gauge.builder("flowora.outbox.pending", this, value -> value.count("SELECT COUNT(*) FROM flowora_outbox_event WHERE status IN ('PENDING','RETRY')"))
                .description("Pending workflow outbox events").register(registry);
        Gauge.builder("flowora.outbox.failed", this, value -> value.count("SELECT COUNT(*) FROM flowora_outbox_event WHERE status='DEAD'"))
                .description("Dead workflow outbox events requiring replay").register(registry);
        Gauge.builder("flowora.outbox.retry", this, value -> value.count("SELECT COUNT(*) FROM flowora_outbox_event WHERE status='RETRY'"))
                .description("Workflow outbox events awaiting retry").register(registry);
        Gauge.builder("flowora.exports.pending", this, value -> value.count("SELECT COUNT(*) FROM flowora_export_job WHERE status IN ('PENDING','RUNNING')"))
                .description("Pending export jobs").register(registry);
        Gauge.builder("flowora.workflow.overdue", this, value -> value.count("SELECT (SELECT COUNT(*) FROM flowora_workflow_approval_task WHERE status='OPEN' AND due_at<CURRENT_TIMESTAMP) + (SELECT COUNT(*) FROM flowora_workflow_task WHERE status='OPEN' AND due_at<CURRENT_TIMESTAMP)"))
                .description("Overdue workflow tasks").register(registry);
    }

    private double count(String sql) {
        if (jdbc == null) return 0;
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0 : value.doubleValue();
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }
}
