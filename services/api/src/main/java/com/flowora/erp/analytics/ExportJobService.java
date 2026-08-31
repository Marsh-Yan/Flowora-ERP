package com.flowora.erp.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.identity.FloworaPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.flowora.erp.analytics.ExportDtos.*;

@Service
public class ExportJobService {
    private static final Map<String, String> PERMISSIONS = Map.of(
            "SALES", "sales:view", "PURCHASES", "procurement:view", "INVENTORY", "inventory:view",
            "FINANCE", "finance:view", "PROJECTS", "project:view"
    );
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ExportJobProcessor processor;

    public ExportJobService(JdbcTemplate jdbc, ObjectMapper objectMapper, ExportJobProcessor processor) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    public ExportJobView create(FloworaPrincipal actor, ExportCreate command) {
        if (!actor.permissions().contains("analytics:export")) throw new org.springframework.security.access.AccessDeniedException("Export permission required");
        String resource = command.resourceType().toUpperCase();
        String permission = PERMISSIONS.get(resource);
        if (permission == null || !actor.permissions().contains(permission)) {
            throw new org.springframework.security.access.AccessDeniedException("Resource export permission required");
        }
        String id = UUID.randomUUID().toString();
        String filters = json(command.filters() == null ? Map.of() : command.filters());
        String locale = command.locale() == null || command.locale().isBlank() ? "en-US" : command.locale();
        jdbc.update("""
                INSERT INTO flowora_export_job(id,organization_id,requested_by,resource_type,format,filters_json,locale,status)
                VALUES(?,?,?,?, 'CSV', ?, ?, 'PENDING')
                """, id, actor.organizationId(), actor.userId(), resource, filters, locale);
        processor.process(id);
        return find(actor, id);
    }

    @Transactional(readOnly = true)
    public List<ExportJobView> list(FloworaPrincipal actor) {
        return jdbc.query("""
                SELECT id,resource_type,format,status,row_count,result_filename,error_code,expires_at,created_at,completed_at
                FROM flowora_export_job WHERE organization_id=? AND requested_by=? ORDER BY created_at DESC LIMIT 100
                """, (rs, row) -> view(rs), actor.organizationId(), actor.userId());
    }

    @Transactional(readOnly = true)
    public ExportJobView find(FloworaPrincipal actor, String id) {
        List<ExportJobView> values = jdbc.query("""
                SELECT id,resource_type,format,status,row_count,result_filename,error_code,expires_at,created_at,completed_at
                FROM flowora_export_job WHERE id=? AND organization_id=? AND requested_by=?
                """, (rs, row) -> view(rs), id, actor.organizationId(), actor.userId());
        if (values.isEmpty()) throw new IllegalArgumentException("Export job not found");
        return values.getFirst();
    }

    @Transactional(readOnly = true)
    public ExportDownload download(FloworaPrincipal actor, String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT result_filename,storage_key,sha256_hex,status,expires_at FROM flowora_export_job
                WHERE id=? AND organization_id=? AND requested_by=?
                """, id, actor.organizationId(), actor.userId());
        if (rows.isEmpty()) throw new IllegalArgumentException("Export job not found");
        Map<String, Object> row = rows.getFirst();
        if (!"COMPLETED".equals(row.get("status")) || row.get("storage_key") == null) throw new IllegalStateException("Export is not ready");
        Timestamp expires = (Timestamp) row.get("expires_at");
        if (expires == null || expires.toInstant().isBefore(Instant.now())) throw new IllegalStateException("Export has expired");
        Path path = processor.resolve(row.get("storage_key").toString());
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Export result is unavailable");
        return new ExportDownload(path, row.get("result_filename").toString(), row.get("sha256_hex").toString());
    }

    private ExportJobView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        Timestamp completed = rs.getTimestamp("completed_at");
        return new ExportJobView(rs.getString("id"), rs.getString("resource_type"), rs.getString("format"),
                rs.getString("status"), rs.getLong("row_count"), rs.getString("result_filename"),
                rs.getString("error_code"), expires == null ? null : expires.toInstant(),
                rs.getTimestamp("created_at").toInstant(), completed == null ? null : completed.toInstant());
    }

    private String json(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid export filters", exception); }
    }
}
