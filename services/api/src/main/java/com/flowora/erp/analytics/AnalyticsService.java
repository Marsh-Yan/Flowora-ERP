package com.flowora.erp.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.identity.DataScope;
import com.flowora.erp.identity.FloworaPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.flowora.erp.analytics.AnalyticsDtos.*;

@Service
public class AnalyticsService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AnalyticsService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WorkspaceSnapshot workspace(FloworaPrincipal actor) {
        String org = actor.organizationId();
        List<WorkspaceCard> cards = new ArrayList<>();
        cards.add(card("MY_APPROVALS", approvalCount(actor), null, "/workflow", "workflow:view", "INFO", actor));
        cards.add(card("OPEN_SALES", scopedCount("flowora_sales_order", "sales_user_id", actor,
                "status IN ('DRAFT','CONFIRMED','PARTIALLY_FULFILLED')"), null,
                "/sales", "sales:view", "INFO", actor));
        cards.add(card("OPEN_PURCHASES", scopedCount("flowora_purchase_order", "buyer_user_id", actor,
                "status NOT IN ('RECEIVED','CANCELLED')"), null,
                "/procurement", "procurement:view", "INFO", actor));
        cards.add(card("STOCK_EXCEPTIONS", decimal("""
                SELECT COUNT(*) FROM flowora_inventory_summary_v2 WHERE organization_id=? AND quantity<=0
                """, org), null, "/inventory", "inventory:view", "WARNING", actor));
        cards.add(card("RECEIVABLES", decimal("""
                SELECT COALESCE(SUM((total_amount-allocated_amount-credited_amount)*exchange_rate),0)
                FROM flowora_finance_invoice WHERE organization_id=? AND party_type='CUSTOMER'
                  AND document_type='SALES_INVOICE' AND status='POSTED'
                """, org), baseCurrency(org), "/finance", "finance:view", "WARNING", actor));
        cards.add(card("PAYABLES", decimal("""
                SELECT COALESCE(SUM((total_amount-allocated_amount-credited_amount)*exchange_rate),0)
                FROM flowora_finance_invoice WHERE organization_id=? AND party_type='SUPPLIER'
                  AND document_type='SUPPLIER_INVOICE' AND status='POSTED'
                """, org), baseCurrency(org), "/finance", "finance:view", "INFO", actor));
        cards.add(card("AT_RISK_PROJECTS", scopedCount("flowora_project", "manager_user_id", actor,
                "status='AT_RISK'"), null, "/projects", "project:view", "DANGER", actor));
        List<String> risks = new ArrayList<>();
        if (actor.permissions().contains("workflow:admin") && decimal("SELECT COUNT(*) FROM flowora_outbox_event WHERE organization_id=? AND status='DEAD'", org).signum() > 0) {
            risks.add("OUTBOX_FAILURES");
        }
        if (actor.permissions().contains("finance:view") && decimal("SELECT COUNT(*) FROM flowora_bank_statement_line WHERE organization_id=? AND reconciliation_status='UNMATCHED'", org).signum() > 0) {
            risks.add("UNMATCHED_BANK_LINES");
        }
        return new WorkspaceSnapshot(org, actor.roles(), Instant.now(), cards.stream().filter(c -> c != null).toList(), risks);
    }

    private BigDecimal approvalCount(FloworaPrincipal actor) {
        if (!actor.permissions().contains("workflow:view")) return BigDecimal.ZERO;
        BigDecimal nativeCount = decimal("SELECT COUNT(*) FROM flowora_workflow_approval_task WHERE organization_id=? AND assignee_user_id=? AND status='OPEN'", actor.organizationId(), actor.userId());
        // The workspace links to the v2 MINE inbox. Legacy tasks remain on the compatibility API.
        return nativeCount;
    }

    private WorkspaceCard card(String code, BigDecimal value, String currency, String route,
                               String permission, String severity, FloworaPrincipal actor) {
        return actor.permissions().contains(permission)
                ? new WorkspaceCard(code, value, currency, route, permission, severity) : null;
    }

    private BigDecimal scopedCount(String table, String ownerColumn, FloworaPrincipal actor, String condition) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table)
                .append(" WHERE organization_id=? AND ").append(condition);
        List<Object> args = new ArrayList<>();
        args.add(actor.organizationId());
        if (actor.dataScope() == DataScope.SELF) {
            sql.append(" AND ").append(ownerColumn).append("=?");
            args.add(actor.userId());
        } else if (actor.dataScope() == DataScope.DEPARTMENT && actor.departmentId() != null) {
            sql.append(" AND ").append(ownerColumn).append(" IN (SELECT user_id FROM flowora_organization_membership WHERE organization_id=? AND department_id=?)");
            args.add(actor.organizationId());
            args.add(actor.departmentId());
        } else if (actor.dataScope() == DataScope.ASSIGNED) {
            sql.append(" AND 1=0");
        }
        return decimal(sql.toString(), args.toArray());
    }

    @Transactional(readOnly = true)
    public AnalyticsSnapshot analytics(FloworaPrincipal actor, LocalDate from, LocalDate to) {
        validateRange(from, to);
        String org = actor.organizationId();
        Map<String, TrendAccumulator> periods = new LinkedHashMap<>();
        jdbc.query("""
                SELECT DATE_FORMAT(order_date,'%Y-%m') period,COALESCE(SUM(total_amount),0) amount
                FROM flowora_sales_order WHERE organization_id=? AND order_date BETWEEN ? AND ?
                GROUP BY DATE_FORMAT(order_date,'%Y-%m') ORDER BY period
                """, rs -> {
                    periods.computeIfAbsent(rs.getString("period"), ignored -> new TrendAccumulator()).sales = rs.getBigDecimal("amount");
                }, org, from, to);
        jdbc.query("""
                SELECT DATE_FORMAT(order_date,'%Y-%m') period,COALESCE(SUM(total_amount),0) amount
                FROM flowora_purchase_order WHERE organization_id=? AND order_date BETWEEN ? AND ?
                GROUP BY DATE_FORMAT(order_date,'%Y-%m') ORDER BY period
                """, rs -> {
                    periods.computeIfAbsent(rs.getString("period"), ignored -> new TrendAccumulator()).purchases = rs.getBigDecimal("amount");
                }, org, from, to);
        jdbc.query("""
                SELECT DATE_FORMAT(accounting_date,'%Y-%m') period,
                       COALESCE(SUM(CASE WHEN document_type='SALES_INVOICE' THEN base_total_amount ELSE 0 END),0) revenue,
                       COALESCE(SUM(CASE WHEN document_type='SUPPLIER_INVOICE' THEN base_total_amount ELSE 0 END),0) expense
                FROM flowora_finance_invoice WHERE organization_id=? AND status='POSTED'
                  AND accounting_date BETWEEN ? AND ? GROUP BY DATE_FORMAT(accounting_date,'%Y-%m') ORDER BY period
                """, rs -> {
                    TrendAccumulator row = periods.computeIfAbsent(rs.getString("period"), ignored -> new TrendAccumulator());
                    row.revenue = rs.getBigDecimal("revenue");
                    row.expense = rs.getBigDecimal("expense");
                }, org, from, to);
        List<TrendPoint> trends = periods.entrySet().stream().map(entry -> entry.getValue().view(entry.getKey())).toList();
        return new AnalyticsSnapshot(from, to, baseCurrency(org), trends, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<OrganizationSummary> crossOrganization(FloworaPrincipal actor, List<String> requested,
                                                        String reportCurrency) {
        if (!actor.permissions().contains("analytics:cross-org")) throw new org.springframework.security.access.AccessDeniedException("Cross organization analytics permission required");
        List<Map<String, Object>> organizations = jdbc.queryForList("""
                SELECT o.id,o.name,s.base_currency_code FROM flowora_organization o
                JOIN flowora_organization_membership m ON m.organization_id=o.id AND m.user_id=? AND m.status='ACTIVE'
                JOIN flowora_finance_setting s ON s.organization_id=o.id
                ORDER BY o.name
                """, actor.userId());
        return organizations.stream().filter(row -> requested == null || requested.isEmpty() || requested.contains(row.get("id").toString()))
                .map(row -> organizationSummary(row, reportCurrency)).toList();
    }

    private OrganizationSummary organizationSummary(Map<String, Object> row, String requestedCurrency) {
        String org = row.get("id").toString();
        String source = row.get("base_currency_code").toString();
        String report = requestedCurrency == null || requestedCurrency.isBlank() ? source : requestedCurrency.toUpperCase();
        Rate rate = rate(org, source, report);
        BigDecimal sales = decimal("SELECT COALESCE(SUM(total_amount),0) FROM flowora_sales_order WHERE organization_id=?", org);
        BigDecimal receivables = decimal("""
                SELECT COALESCE(SUM((total_amount-allocated_amount-credited_amount)*exchange_rate),0)
                FROM flowora_finance_invoice WHERE organization_id=? AND party_type='CUSTOMER' AND document_type='SALES_INVOICE' AND status='POSTED'
                """, org);
        BigDecimal payables = decimal("""
                SELECT COALESCE(SUM((total_amount-allocated_amount-credited_amount)*exchange_rate),0)
                FROM flowora_finance_invoice WHERE organization_id=? AND party_type='SUPPLIER' AND document_type='SUPPLIER_INVOICE' AND status='POSTED'
                """, org);
        BigDecimal cash = decimal("""
                SELECT COALESCE(SUM(CASE WHEN payment_type IN ('RECEIPT','SUPPLIER_REFUND') THEN base_amount ELSE -base_amount END),0)
                FROM flowora_payment_v2 WHERE organization_id=? AND status='POSTED'
                """, org);
        return new OrganizationSummary(org, row.get("name").toString(), source, report, rate.date, rate.missing,
                convert(sales, rate.value), convert(receivables, rate.value), convert(payables, rate.value),
                convert(cash, rate.value), false);
    }

    @Transactional
    public SavedView saveView(FloworaPrincipal actor, SavedViewCreate command) {
        if (command.shared() && !actor.permissions().contains("organization:configure")) {
            throw new org.springframework.security.access.AccessDeniedException("Shared views require organization configuration permission");
        }
        String id = UUID.randomUUID().toString();
        String json = json(command.definition() == null ? Map.of() : command.definition());
        jdbc.update("""
                INSERT INTO flowora_saved_view(id,organization_id,owner_user_id,resource_type,name,definition_json,shared)
                VALUES(?,?,?,?,?,?,?)
                """, id, actor.organizationId(), actor.userId(), command.resourceType().toUpperCase(), command.name(), json, command.shared());
        return new SavedView(id, command.resourceType().toUpperCase(), command.name(), parse(json), command.shared(), actor.userId(), Instant.now(), 0);
    }

    @Transactional(readOnly = true)
    public List<SavedView> views(FloworaPrincipal actor, String resourceType) {
        return jdbc.query("""
                SELECT id,resource_type,name,definition_json,shared,owner_user_id,updated_at,version_no
                FROM flowora_saved_view WHERE organization_id=? AND resource_type=? AND active=TRUE
                  AND (owner_user_id=? OR shared=TRUE) ORDER BY shared DESC,updated_at DESC
                """, (rs, row) -> new SavedView(rs.getString("id"), rs.getString("resource_type"), rs.getString("name"),
                parse(rs.getString("definition_json")), rs.getBoolean("shared"), rs.getString("owner_user_id"),
                rs.getTimestamp("updated_at").toInstant(), rs.getLong("version_no")),
                actor.organizationId(), resourceType.toUpperCase(), actor.userId());
    }

    @Transactional
    public void deleteView(FloworaPrincipal actor, String id) {
        int updated = jdbc.update("""
                UPDATE flowora_saved_view SET active=FALSE,version_no=version_no+1
                WHERE id=? AND organization_id=? AND owner_user_id=? AND active=TRUE
                """, id, actor.organizationId(), actor.userId());
        if (updated == 0) throw new IllegalArgumentException("Saved view not found or not owned by current user");
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw new IllegalArgumentException("Analytics date range must be between 0 and 366 days");
        }
    }

    private String baseCurrency(String organizationId) {
        return jdbc.queryForObject("SELECT base_currency_code FROM flowora_finance_setting WHERE organization_id=?", String.class, organizationId);
    }

    private Rate rate(String organizationId, String source, String report) {
        if (source.equals(report)) return new Rate(BigDecimal.ONE, LocalDate.now(), false);
        List<Rate> values = jdbc.query("""
                SELECT rate,effective_date FROM flowora_exchange_rate WHERE organization_id=?
                  AND base_currency_code=? AND quote_currency_code=? AND active=TRUE
                ORDER BY effective_date DESC LIMIT 1
                """, (rs, row) -> new Rate(rs.getBigDecimal("rate"), rs.getDate("effective_date").toLocalDate(), false),
                organizationId, source, report);
        return values.isEmpty() ? new Rate(BigDecimal.ONE, null, true) : values.getFirst();
    }

    private BigDecimal convert(BigDecimal value, BigDecimal rate) {
        return value.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String json(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid saved view definition", exception); }
    }

    private Map<String, Object> parse(String value) {
        try { return objectMapper.readValue(value, MAP_TYPE); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored saved view is invalid", exception); }
    }

    private static final class TrendAccumulator {
        private BigDecimal sales = BigDecimal.ZERO;
        private BigDecimal purchases = BigDecimal.ZERO;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
        private TrendPoint view(String period) { return new TrendPoint(period, sales, purchases, revenue, expense, revenue.subtract(expense)); }
    }

    private record Rate(BigDecimal value, LocalDate date, boolean missing) { }
}
