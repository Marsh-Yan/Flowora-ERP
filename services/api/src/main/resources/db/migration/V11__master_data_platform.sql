ALTER TABLE flowora_customer
    ADD COLUMN credit_limit DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER payment_terms_days,
    ADD COLUMN credit_policy VARCHAR(16) NOT NULL DEFAULT 'WARN' AFTER credit_limit;

ALTER TABLE flowora_item
    ADD COLUMN tracking_method VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER inventory_managed;

CREATE TABLE flowora_stock_location (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36) NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    location_type VARCHAR(16) NOT NULL DEFAULT 'STORAGE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_location_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_location_warehouse FOREIGN KEY (warehouse_id)
        REFERENCES flowora_warehouse (id),
    CONSTRAINT fk_flowora_location_parent FOREIGN KEY (parent_id)
        REFERENCES flowora_stock_location (id),
    CONSTRAINT uq_flowora_location_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE flowora_tax_rule (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_tax_rule_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_tax_rule_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE flowora_tax_component (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    tax_rule_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(120) NOT NULL,
    rate DECIMAL(9, 4) NOT NULL,
    sequence_no INT NOT NULL DEFAULT 1,
    compound BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_tax_component_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_tax_component_rule FOREIGN KEY (tax_rule_id)
        REFERENCES flowora_tax_rule (id),
    CONSTRAINT uq_flowora_tax_component_rule_code UNIQUE (tax_rule_id, code)
);

CREATE TABLE flowora_bank_account (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    bank_name VARCHAR(160) NOT NULL,
    account_number_masked VARCHAR(64) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_bank_account_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_bank_account_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE flowora_document_sequence (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    document_type VARCHAR(48) NOT NULL,
    period_pattern VARCHAR(24) NOT NULL DEFAULT 'YEAR',
    prefix VARCHAR(24) NOT NULL,
    next_value BIGINT NOT NULL DEFAULT 1,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_document_sequence_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_document_sequence_type UNIQUE (organization_id, document_type)
);
