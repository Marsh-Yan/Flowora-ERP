package com.flowora.erp.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M1MigrationContractTest {
    private final Path migrationRoot = Path.of("src/main/resources/db/migration");

    @Test
    void v10DefinesPersistentIdentityOrganizationAndAclFoundation() throws IOException {
        String sql = Files.readString(migrationRoot.resolve("V10__identity_organization_acl.sql"));

        assertThat(sql).contains(
                "flowora_organization_membership",
                "flowora_department",
                "flowora_permission",
                "flowora_role_permission",
                "flowora_membership_role",
                "flowora_password_history",
                "flowora_user_mfa",
                "flowora_security_event",
                "uq_flowora_user_username"
        );
    }

    @Test
    void v11DefinesTrackingLocationsAndCompositeTaxes() throws IOException {
        String sql = Files.readString(migrationRoot.resolve("V11__master_data_platform.sql"));

        assertThat(sql).contains(
                "tracking_method",
                "flowora_stock_location",
                "flowora_tax_rule",
                "flowora_tax_component",
                "flowora_bank_account",
                "flowora_document_sequence"
        );
    }
}
