package com.flowora.erp.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M2MigrationContractTest {
    private final Path migration = Path.of("src/main/resources/db/migration/V12__workflow_collaboration_platform.sql");

    @Test
    void definesVersionedWorkflowRuntimeAndImmutableDecisionTrail() throws Exception {
        String sql = Files.readString(migration);

        assertThat(sql).contains(
                "flowora_workflow_template",
                "flowora_workflow_version",
                "flowora_workflow_step_definition",
                "flowora_workflow_instance",
                "workflow_version_id",
                "snapshot_json",
                "flowora_workflow_approval_task",
                "original_approver_user_id",
                "actual_actor_user_id",
                "flowora_workflow_decision",
                "flowora_workflow_delegation"
        );
    }

    @Test
    void definesProtectedCollaborationAndReliableDeliveryStorage() throws Exception {
        String sql = Files.readString(migration);

        assertThat(sql).contains(
                "flowora_mention",
                "flowora_attachment",
                "sha256_hex",
                "storage_key",
                "flowora_outbox_event",
                "flowora_purchase_request",
                "flowora_sales_quote",
                "workflow_instance_id",
                "flowora_delivery_attempt",
                "uq_flowora_notification_outbox",
                "'workflow:configure'",
                "'workflow:admin'",
                "'attachment:upload'"
        );
    }
}
