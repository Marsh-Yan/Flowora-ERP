CREATE TABLE IF NOT EXISTS flowora_accounting_period (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    year INT NOT NULL,
    month INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_accounting_period_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_accounting_period_month UNIQUE (organization_id, year, month)
);

CREATE TABLE IF NOT EXISTS flowora_journal_entry (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    period_id VARCHAR(36) NOT NULL,
    entry_date DATE NOT NULL,
    source_type VARCHAR(48) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    memo VARCHAR(500) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    total_debit DECIMAL(19, 4) NOT NULL,
    total_credit DECIMAL(19, 4) NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_journal_entry_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_journal_entry_period FOREIGN KEY (period_id)
        REFERENCES flowora_accounting_period (id),
    CONSTRAINT uq_flowora_journal_entry_source UNIQUE (organization_id, source_type, source_id)
);
CREATE INDEX idx_flowora_journal_entry_date ON flowora_journal_entry (organization_id, entry_date);

CREATE TABLE IF NOT EXISTS flowora_journal_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    journal_entry_id VARCHAR(36) NOT NULL,
    line_no INT NOT NULL,
    account_code VARCHAR(48) NOT NULL,
    description VARCHAR(200) NULL,
    debit DECIMAL(19, 4) NOT NULL,
    credit DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_journal_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_journal_line_entry FOREIGN KEY (journal_entry_id)
        REFERENCES flowora_journal_entry (id),
    CONSTRAINT uq_flowora_journal_line_no UNIQUE (journal_entry_id, line_no)
);
CREATE INDEX idx_flowora_journal_line_account ON flowora_journal_line (organization_id, account_code);

CREATE TABLE IF NOT EXISTS flowora_payable_document (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    purchase_receipt_id VARCHAR(36) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(36) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    paid_amount DECIMAL(19, 4) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    due_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_payable_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_payable_receipt FOREIGN KEY (purchase_receipt_id)
        REFERENCES flowora_purchase_receipt (id),
    CONSTRAINT uq_flowora_payable_source UNIQUE (organization_id, source_type, source_id)
);
CREATE INDEX idx_flowora_payable_search ON flowora_payable_document (organization_id, status, due_date);

CREATE TABLE IF NOT EXISTS flowora_supplier_payment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    payable_id VARCHAR(36) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    payment_method VARCHAR(16) NOT NULL,
    payment_date DATE NOT NULL,
    reference VARCHAR(120) NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_supplier_payment_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_supplier_payment_payable FOREIGN KEY (payable_id)
        REFERENCES flowora_payable_document (id)
);
CREATE INDEX idx_flowora_supplier_payment_payable ON flowora_supplier_payment (organization_id, payable_id, payment_date);

INSERT INTO flowora_account (id, organization_id, code, name, account_type, parent_code, posting_allowed, active)
SELECT 'account-demo-inventory', 'org-demo', '1400', 'Inventory assets', 'ASSET', NULL, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_account WHERE organization_id = 'org-demo' AND code = '1400');
