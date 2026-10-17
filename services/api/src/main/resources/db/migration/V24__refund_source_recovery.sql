-- Source recovery is explicit, one-time metadata reconciliation; never infer old sources.
CREATE TABLE flowora_refund_source_recovery (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    refund_id VARCHAR(36) NOT NULL,
    original_payment_id VARCHAR(36) NOT NULL,
    refund_journal_entry_id VARCHAR(36) NOT NULL,
    original_journal_entry_id VARCHAR(36) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    refund_version BIGINT NOT NULL,
    original_version BIGINT NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refund_recovery_payment UNIQUE (organization_id,refund_id),
    CONSTRAINT uq_refund_recovery_request UNIQUE (organization_id,request_id),
    CONSTRAINT fk_refund_recovery_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_refund_recovery_payment FOREIGN KEY (refund_id) REFERENCES flowora_payment_v2(id),
    CONSTRAINT fk_refund_recovery_original FOREIGN KEY (original_payment_id) REFERENCES flowora_payment_v2(id),
    CONSTRAINT fk_refund_recovery_journal FOREIGN KEY (refund_journal_entry_id) REFERENCES flowora_journal_entry(id),
    CONSTRAINT fk_refund_recovery_original_journal FOREIGN KEY (original_journal_entry_id) REFERENCES flowora_journal_entry(id)
);
