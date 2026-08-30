package com.flowora.erp.workflow.v2;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
@Profile("local")
public class WorkflowResourceProjectionService {
    private final JdbcTemplate jdbcTemplate;

    public WorkflowResourceProjectionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void project(String organizationId, String resourceType, String resourceId, String workflowStatus) {
        switch (resourceType) {
            case "PURCHASE_REQUEST" -> projectPurchaseRequest(
                    organizationId, resourceId, purchaseStatus(workflowStatus));
            case "SALES_QUOTE" -> projectSalesQuote(
                    organizationId, resourceId, salesStatus(workflowStatus), workflowStatus);
            default -> {
            }
        }
    }

    private void projectPurchaseRequest(String organizationId, String resourceId, String status) {
        if (status == null) return;
        jdbcTemplate.update("""
                UPDATE flowora_purchase_request SET status = ?, version_no = version_no + 1
                WHERE id = ? AND organization_id = ? AND status = 'SUBMITTED'
                """, status, resourceId, organizationId);
    }

    private void projectSalesQuote(String organizationId, String resourceId, String status, String workflowStatus) {
        if (status == null) return;
        Timestamp approvedAt = "APPROVED".equals(workflowStatus) ? Timestamp.from(Instant.now()) : null;
        jdbcTemplate.update("""
                UPDATE flowora_sales_quote
                SET status = ?, approved_at = COALESCE(?, approved_at), version_no = version_no + 1
                WHERE id = ? AND organization_id = ? AND status = 'SUBMITTED'
                """, status, approvedAt, resourceId, organizationId);
    }

    private String purchaseStatus(String workflowStatus) {
        return switch (workflowStatus) {
            case "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "WITHDRAWN", "RETURNED" -> "DRAFT";
            case "TERMINATED" -> "CANCELLED";
            default -> null;
        };
    }

    private String salesStatus(String workflowStatus) {
        return switch (workflowStatus) {
            case "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "WITHDRAWN", "RETURNED" -> "DRAFT";
            case "TERMINATED" -> "REJECTED";
            default -> null;
        };
    }
}
