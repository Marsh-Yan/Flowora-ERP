package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.identity.DataScope;
import com.flowora.erp.project.ProjectReadScope;
import com.flowora.erp.trade.v2.OrderReadScope;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class WorkflowResourceAccessPolicy {
    private static final Map<String, ResourceRule> RULES = Map.ofEntries(
            Map.entry("PURCHASE_REQUEST", new ResourceRule("flowora_purchase_request", "procurement:view")),
            Map.entry("PURCHASE_ORDER", new ResourceRule("flowora_purchase_order", "procurement:view")),
            Map.entry("SALES_QUOTE", new ResourceRule("flowora_sales_quote", "sales:view")),
            Map.entry("SALES_ORDER", new ResourceRule("flowora_sales_order", "sales:view")),
            Map.entry("STOCK_TRANSFER", new ResourceRule("flowora_stock_transfer", "inventory:view")),
            Map.entry("STOCK_COUNT", new ResourceRule("flowora_stock_count", "inventory:view")),
            Map.entry("INVENTORY_ADJUSTMENT", new ResourceRule("flowora_stock_adjustment", "inventory:view")),
            Map.entry("PROJECT", new ResourceRule("flowora_project", "project:view")),
            Map.entry("TIMESHEET", new ResourceRule("flowora_timesheet", "project:view")),
            Map.entry("PROJECT_EXPENSE", new ResourceRule("flowora_project_expense", "project:view")),
            Map.entry("JOURNAL_ENTRY", new ResourceRule("flowora_journal_entry", "finance:view")),
            Map.entry("PAYABLE_DOCUMENT", new ResourceRule("flowora_payable_document", "finance:view")),
            Map.entry("RECEIVABLE_DOCUMENT", new ResourceRule("flowora_receivable_document", "finance:view")),
            Map.entry("WORKFLOW_INSTANCE", new ResourceRule("flowora_workflow_instance", "workflow:view"))
    );

    private final JdbcTemplate jdbcTemplate;
    private final OrderReadScope orderReadScope;
    private final ProjectReadScope projectReadScope;

    public WorkflowResourceAccessPolicy(JdbcTemplate jdbcTemplate, OrderReadScope orderReadScope,
                                        ProjectReadScope projectReadScope) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderReadScope = orderReadScope;
        this.projectReadScope = projectReadScope;
    }

    public String require(FloworaPrincipal principal, String resourceType, String resourceId, String capability) {
        String normalized = resourceType == null ? "" : resourceType.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || resourceId == null || resourceId.isBlank()) {
            badRequest();
        }
        String platformPermission = switch (capability) {
            case "upload" -> "attachment:upload";
            case "comment" -> "collaboration:comment";
            case "attachment-view" -> "attachment:view";
            default -> null;
        };
        if (platformPermission != null) requirePermission(principal, platformPermission);
        if ("GENERAL".equals(normalized)) {
            requirePermission(principal, "workflow:view");
            return normalized;
        }
        ResourceRule rule = RULES.get(normalized);
        if (rule == null) {
            badRequest();
        }
        requirePermission(principal, rule.permission());
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + rule.table() + " WHERE id = ? AND organization_id = ?",
                Integer.class, resourceId, principal.organizationId());
        if (count == null || count == 0) {
            throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound");
        }
        if (principal.dataScope() != DataScope.ALL) {
            switch (normalized) {
                case "SALES_ORDER" -> orderReadScope.requireSales(principal, resourceId);
                case "PURCHASE_ORDER" -> orderReadScope.requirePurchase(principal, resourceId);
                case "PROJECT" -> projectReadScope.require(principal, resourceId);
                default -> throw new PlatformApiException(HttpStatus.FORBIDDEN,
                        "PERMISSION_DENIED", "errors.authForbidden");
            }
        }
        return normalized;
    }

    private void requirePermission(FloworaPrincipal principal, String permission) {
        if (!principal.permissions().contains(permission)) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "errors.authForbidden");
        }
    }

    private void badRequest() {
        throw new PlatformApiException(HttpStatus.BAD_REQUEST, "RESOURCE_TYPE_INVALID", "errors.badRequest");
    }

    private record ResourceRule(String table, String permission) {
    }
}
