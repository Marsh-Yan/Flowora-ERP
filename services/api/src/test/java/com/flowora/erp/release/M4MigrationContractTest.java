package com.flowora.erp.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M4MigrationContractTest {
    private final Path migration = Path.of("src/main/resources/db/migration/V14__finance_project_closure.sql");

    @Test
    void definesInvoicesPostingSettlementsAndPeriodControls() throws Exception {
        String sql = Files.readString(migration);

        assertThat(sql).contains(
                "flowora_finance_invoice",
                "flowora_finance_invoice_line",
                "flowora_finance_invoice_source",
                "flowora_posting_mapping",
                "flowora_payment_v2",
                "flowora_payment_allocation",
                "flowora_allocation_reversal",
                "flowora_period_close_check",
                "flowora_period_status_event",
                "base_debit",
                "base_credit",
                "ck_journal_line_side",
                "UNIQUE (organization_id, request_id)"
        );
    }

    @Test
    void definesBankBudgetRevaluationAndProjectBilling() throws Exception {
        String sql = Files.readString(migration);

        assertThat(sql).contains(
                "flowora_bank_statement_line",
                "uq_reconciliation_link_statement",
                "flowora_bank_reconciliation",
                "flowora_bank_reconciliation_link",
                "flowora_budget_version",
                "flowora_budget_line_v2",
                "flowora_currency_revaluation",
                "flowora_project_member",
                "flowora_project_billing_basis",
                "billing_mode",
                "approval_status",
                "'finance:period-reopen'",
                "'project:billing'"
        );
    }
}
