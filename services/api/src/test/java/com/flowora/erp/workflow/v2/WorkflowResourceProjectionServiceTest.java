package com.flowora.erp.workflow.v2;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WorkflowResourceProjectionServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final WorkflowResourceProjectionService service =
            new WorkflowResourceProjectionService(jdbcTemplate);

    @Test
    void projectsApprovedPurchaseRequestOnlyThroughOrganizationScopedUpdate() {
        service.project("org-1", "PURCHASE_REQUEST", "pr-1", "APPROVED");

        verify(jdbcTemplate).update(
                any(String.class), eq("APPROVED"), eq("pr-1"), eq("org-1"));
    }

    @Test
    void projectsApprovedSalesQuoteWithApprovalTimestamp() {
        service.project("org-1", "SALES_QUOTE", "quote-1", "APPROVED");

        verify(jdbcTemplate).update(
                any(String.class),
                eq("APPROVED"),
                any(java.sql.Timestamp.class),
                eq("quote-1"),
                eq("org-1")
        );
    }

    @Test
    void ignoresResourcesThatDoNotHaveAProjectionAdapter() {
        service.project("org-1", "PROJECT", "project-1", "APPROVED");

        verify(jdbcTemplate, never()).update(
                any(String.class), any(), any(), any());
    }
}
