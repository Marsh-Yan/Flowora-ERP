package com.flowora.erp.release;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseArtifactTest {

    @Test
    void demoSeedContainsEveryReleaseLoop() throws IOException {
        String seed = readResource("db/demo/seed.sql");

        assertThat(seed)
                .contains("INSERT INTO flowora_purchase_request")
                .contains("INSERT INTO flowora_purchase_order")
                .contains("INSERT INTO flowora_purchase_receipt")
                .contains("INSERT INTO flowora_payable_document")
                .contains("INSERT INTO flowora_supplier_payment")
                .contains("INSERT INTO flowora_sales_quote")
                .contains("INSERT INTO flowora_sales_order")
                .contains("INSERT INTO flowora_sales_delivery")
                .contains("INSERT INTO flowora_receivable_document")
                .contains("INSERT INTO flowora_payment")
                .contains("INSERT INTO flowora_project")
                .contains("INSERT INTO flowora_timesheet")
                .contains("INSERT INTO flowora_project_expense")
                .contains("INSERT INTO flowora_project_budget");
    }

    @Test
    void demoSeedIsOrganizationScopedAndResettable() throws IOException {
        String seed = readResource("db/demo/seed.sql");

        assertThat(seed)
                .contains("DELETE FROM flowora_idempotency_record WHERE organization_id = 'org-demo'")
                .contains("DELETE FROM flowora_audit_event WHERE organization_id = 'org-demo'")
                .contains("organization_id = 'org-demo'")
                .contains("'org-demo'");
    }

    private String readResource(String path) throws IOException {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
