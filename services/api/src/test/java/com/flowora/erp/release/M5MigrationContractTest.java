package com.flowora.erp.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5MigrationContractTest {
    private final Path migration = Path.of("src/main/resources/db/migration/V15__analytics_upgrade_release.sql");

    @Test
    void definesAnalyticsExportsDiagnosticsAndPermissions() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains(
                "flowora_instance_setting", "flowora_saved_view", "flowora_export_job",
                "'analytics:view'", "'analytics:export'", "'analytics:cross-org'", "'admin:diagnostics'"
        );
    }

    @Test
    void preservesLegacyFinanceWithoutGeneratingJournals() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains("MIGRATED_LEGACY", "read_only", "LEGACY-STOCK");
        assertThat(sql).doesNotContain("INSERT INTO flowora_journal_entry", "INSERT INTO flowora_journal_line");
    }
}
