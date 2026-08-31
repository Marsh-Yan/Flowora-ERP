package com.flowora.erp.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExportJobProcessor {
    private static final Map<String, ExportDefinition> DEFINITIONS = definitions();
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Path root;
    private final int retentionHours;
    private final int maximumRows;

    public ExportJobProcessor(JdbcTemplate jdbc, ObjectMapper objectMapper,
                              @Value("${flowora.export.root:.flowora/exports}") String root,
                              @Value("${flowora.export.retention-hours:24}") int retentionHours,
                              @Value("${flowora.export.maximum-rows:100000}") int maximumRows) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.retentionHours = Math.max(1, retentionHours);
        this.maximumRows = Math.max(1, maximumRows);
    }

    @Async
    public void process(String id) {
        try {
            Map<String, Object> job = jdbc.queryForMap("SELECT organization_id,resource_type,filters_json FROM flowora_export_job WHERE id=? AND status='PENDING'", id);
            jdbc.update("UPDATE flowora_export_job SET status='RUNNING',started_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", id);
            ExportDefinition definition = DEFINITIONS.get(job.get("resource_type").toString());
            if (definition == null) throw new IllegalArgumentException("Unsupported export resource");
            Map<String, Object> filters = objectMapper.readValue(job.get("filters_json").toString(), new TypeReference<>() { });
            String query = "SELECT * FROM (" + definition.sql + ") exported WHERE 1=1";
            List<Object> arguments = new ArrayList<>();
            arguments.add(job.get("organization_id"));
            if (definition.dateColumn != null && filters.get("from") instanceof String from && filters.get("to") instanceof String to) {
                LocalDate fromDate = LocalDate.parse(from);
                LocalDate toDate = LocalDate.parse(to);
                if (toDate.isBefore(fromDate)) throw new IllegalArgumentException("Invalid export date range");
                query += " AND exported." + definition.dateColumn + " BETWEEN ? AND ?";
                arguments.add(fromDate);
                arguments.add(toDate);
            }
            List<Map<String, Object>> rows = jdbc.queryForList(query + " LIMIT " + (maximumRows + 1), arguments.toArray());
            if (rows.size() > maximumRows) throw new IllegalStateException("EXPORT_ROW_LIMIT_EXCEEDED");
            byte[] bytes = csv(definition.headers, rows).getBytes(StandardCharsets.UTF_8);
            String storageKey = job.get("organization_id") + "/" + id + ".csv";
            Path path = resolve(storageKey);
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
            String filename = job.get("resource_type").toString().toLowerCase() + "-" + id.substring(0, 8) + ".csv";
            jdbc.update("""
                    UPDATE flowora_export_job SET status='COMPLETED',row_count=?,result_filename=?,storage_key=?,sha256_hex=?,
                    completed_at=CURRENT_TIMESTAMP,expires_at=? WHERE id=?
                    """, rows.size(), filename, storageKey, sha256(bytes),
                    java.sql.Timestamp.from(Instant.now().plus(retentionHours, ChronoUnit.HOURS)), id);
        } catch (Exception exception) {
            jdbc.update("UPDATE flowora_export_job SET status='FAILED',error_code=?,completed_at=CURRENT_TIMESTAMP WHERE id=?",
                    errorCode(exception), id);
        }
    }

    public Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Invalid export storage key");
        return resolved;
    }

    @Scheduled(fixedDelayString = "${flowora.export.cleanup-delay-ms:3600000}")
    public void expire() {
        List<Map<String, Object>> expired = jdbc.queryForList("""
                SELECT id,storage_key FROM flowora_export_job WHERE status='COMPLETED' AND expires_at<CURRENT_TIMESTAMP LIMIT 200
                """);
        for (Map<String, Object> row : expired) {
            try { if (row.get("storage_key") != null) Files.deleteIfExists(resolve(row.get("storage_key").toString())); }
            catch (IOException ignored) { }
            jdbc.update("UPDATE flowora_export_job SET status='EXPIRED',storage_key=NULL WHERE id=?", row.get("id"));
        }
    }

    private String csv(List<String> headers, List<Map<String, Object>> rows) {
        StringBuilder output = new StringBuilder("\uFEFF");
        output.append(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            for (int index = 0; index < headers.size(); index++) {
                if (index > 0) output.append(',');
                output.append(escape(row.get(headers.get(index))));
            }
            output.append('\n');
        }
        return output.toString();
    }

    private String escape(Object raw) {
        String value = raw == null ? "" : raw.toString();
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return '"' + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private String errorCode(Exception exception) {
        return exception.getMessage() != null && exception.getMessage().contains("ROW_LIMIT")
                ? "EXPORT_ROW_LIMIT_EXCEEDED" : "EXPORT_PROCESSING_FAILED";
    }

    private static Map<String, ExportDefinition> definitions() {
        Map<String, ExportDefinition> values = new LinkedHashMap<>();
        values.put("SALES", new ExportDefinition(List.of("number","status","currency_code","total_amount","order_date"),
                "SELECT number,status,currency_code,total_amount,order_date FROM flowora_sales_order WHERE organization_id=? ORDER BY order_date DESC", "order_date"));
        values.put("PURCHASES", new ExportDefinition(List.of("number","status","currency_code","total_amount","order_date"),
                "SELECT number,status,currency_code,total_amount,order_date FROM flowora_purchase_order WHERE organization_id=? ORDER BY order_date DESC", "order_date"));
        values.put("INVENTORY", new ExportDefinition(List.of("warehouse_code","item_code","quantity","average_cost"),
                "SELECT w.code warehouse_code,i.code item_code,b.quantity,b.average_cost FROM flowora_stock_balance b JOIN flowora_warehouse w ON w.id=b.warehouse_id JOIN flowora_item i ON i.id=b.item_id WHERE b.organization_id=? ORDER BY w.code,i.code", null));
        values.put("FINANCE", new ExportDefinition(List.of("number","document_type","party_type","status","settlement_status","currency_code","total_amount","accounting_date"),
                "SELECT number,document_type,party_type,status,settlement_status,currency_code,total_amount,accounting_date FROM flowora_finance_invoice WHERE organization_id=? ORDER BY accounting_date DESC", "accounting_date"));
        values.put("PROJECTS", new ExportDefinition(List.of("number","name","status","currency_code","budget_revenue","budget_cost","target_date"),
                "SELECT number,name,status,currency_code,budget_revenue,budget_cost,target_date FROM flowora_project WHERE organization_id=? ORDER BY number", "target_date"));
        return Map.copyOf(values);
    }

    private record ExportDefinition(List<String> headers, String sql, String dateColumn) { }
}
