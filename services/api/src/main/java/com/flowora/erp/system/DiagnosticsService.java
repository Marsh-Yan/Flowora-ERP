package com.flowora.erp.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DiagnosticsService {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String version;
    private final String stage;
    private final boolean mailConfigured;
    private final Path attachmentRoot;
    private final Path exportRoot;

    public DiagnosticsService(ObjectProvider<JdbcTemplate> jdbcProvider,
                              ObjectProvider<StringRedisTemplate> redisProvider,
                              ObjectMapper objectMapper,
                              @Value("${flowora.release.version:2.0.0-SNAPSHOT}") String version,
                              @Value("${flowora.release.stage:M5}") String stage,
                              @Value("${flowora.mail.configured:false}") boolean mailConfigured,
                              @Value("${flowora.attachment.root:.flowora/attachments}") String attachmentRoot,
                              @Value("${flowora.export.root:.flowora/exports}") String exportRoot) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.redis = redisProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.version = version;
        this.stage = stage;
        this.mailConfigured = mailConfigured;
        this.attachmentRoot = Path.of(attachmentRoot).toAbsolutePath().normalize();
        this.exportRoot = Path.of(exportRoot).toAbsolutePath().normalize();
    }

    public DiagnosticSnapshot snapshot() {
        Map<String, DependencyHealth> dependencies = new LinkedHashMap<>();
        dependencies.put("database", database());
        dependencies.put("redis", redis());
        dependencies.put("attachmentStorage", storage(attachmentRoot));
        dependencies.put("exportStorage", storage(exportRoot));
        dependencies.put("mail", new DependencyHealth(mailConfigured ? "CONFIGURED" : "NOT_CONFIGURED", Map.of()));
        Map<String, Long> backlog = jdbc == null ? Map.of() : Map.of(
                "outboxPending", count("SELECT COUNT(*) FROM flowora_outbox_event WHERE status IN ('PENDING','RETRY')"),
                "outboxFailed", count("SELECT COUNT(*) FROM flowora_outbox_event WHERE status='DEAD'"),
                "outboxRetry", count("SELECT COUNT(*) FROM flowora_outbox_event WHERE status='RETRY'"),
                "exportsPending", count("SELECT COUNT(*) FROM flowora_export_job WHERE status IN ('PENDING','RUNNING')"),
                "workflowOverdue", count("SELECT (SELECT COUNT(*) FROM flowora_workflow_approval_task WHERE status='OPEN' AND due_at<CURRENT_TIMESTAMP) + (SELECT COUNT(*) FROM flowora_workflow_task WHERE status='OPEN' AND due_at<CURRENT_TIMESTAMP)")
        );
        return new DiagnosticSnapshot(version, stage, migrationVersion(), Instant.now(), dependencies, backlog);
    }

    public byte[] bundle(String requestId) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of(
                    "generatedAt", Instant.now().toString(),
                    "requestId", requestId,
                    "diagnostics", snapshot(),
                    "safeConfiguration", safeConfiguration()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to create diagnostic bundle", exception);
        }
    }

    private List<Map<String, Object>> safeConfiguration() {
        return jdbc == null ? List.of() : jdbc.queryForList("""
                SELECT setting_key,setting_value,classification,description,updated_at
                FROM flowora_instance_setting WHERE classification IN ('PUBLIC','INTERNAL') ORDER BY setting_key
                """);
    }

    private DependencyHealth database() {
        if (jdbc == null) return new DependencyHealth("DISABLED", Map.of());
        try {
            return new DependencyHealth("UP", Map.of("migrationVersion", migrationVersion()));
        } catch (Exception exception) {
            return new DependencyHealth("DOWN", Map.of("error", exception.getClass().getSimpleName()));
        }
    }

    private DependencyHealth redis() {
        if (redis == null) return new DependencyHealth("DISABLED", Map.of());
        try {
            String pong = redis.getConnectionFactory().getConnection().ping();
            return new DependencyHealth("PONG".equalsIgnoreCase(pong) ? "UP" : "DEGRADED", Map.of());
        } catch (Exception exception) {
            return new DependencyHealth("DOWN", Map.of("error", exception.getClass().getSimpleName()));
        }
    }

    private DependencyHealth storage(Path path) {
        try {
            Path probe = Files.exists(path) ? path : path.getParent();
            return new DependencyHealth(probe != null && Files.isWritable(probe) ? "UP" : "DEGRADED",
                    Map.of("configured", true));
        } catch (Exception exception) {
            return new DependencyHealth("DOWN", Map.of("error", exception.getClass().getSimpleName()));
        }
    }

    private String migrationVersion() {
        if (jdbc == null) return "disabled";
        List<String> versions = jdbc.query("""
                SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1
                """, (rs, row) -> rs.getString("version"));
        return versions.isEmpty() ? "none" : versions.getFirst();
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    public record DiagnosticSnapshot(String productVersion, String deliveryStage, String migrationVersion,
                                     Instant generatedAt, Map<String, DependencyHealth> dependencies,
                                     Map<String, Long> taskBacklog) { }
    public record DependencyHealth(String status, Map<String, Object> details) { }
}
