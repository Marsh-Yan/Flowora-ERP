CREATE TABLE flowora_invoice_correction (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    invoice_id VARCHAR(36) NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    accounting_date DATE NOT NULL,
    journal_entry_id VARCHAR(36) NULL,
    source_version BIGINT NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invoice_correction_invoice UNIQUE (organization_id,invoice_id),
    CONSTRAINT uq_invoice_correction_request UNIQUE (organization_id,request_id),
    CONSTRAINT fk_invoice_correction_invoice FOREIGN KEY (invoice_id) REFERENCES flowora_finance_invoice(id),
    CONSTRAINT fk_invoice_correction_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_invoice_correction_journal FOREIGN KEY (journal_entry_id) REFERENCES flowora_journal_entry(id),
    CONSTRAINT ck_invoice_correction_action CHECK (action IN ('CANCEL','REVERSE'))
);
