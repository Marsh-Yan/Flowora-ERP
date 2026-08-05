CREATE TABLE IF NOT EXISTS flowora_customer (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    contact_name VARCHAR(120) NULL,
    email VARCHAR(190) NULL,
    phone VARCHAR(48) NULL,
    address VARCHAR(500) NULL,
    currency_code VARCHAR(3) NOT NULL,
    payment_terms_days INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_customer_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_customer_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS flowora_supplier (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    contact_name VARCHAR(120) NULL,
    email VARCHAR(190) NULL,
    phone VARCHAR(48) NULL,
    address VARCHAR(500) NULL,
    currency_code VARCHAR(3) NOT NULL,
    payment_terms_days INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_supplier_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_supplier_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS flowora_item (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    item_type VARCHAR(16) NOT NULL,
    unit VARCHAR(24) NOT NULL,
    sales_price DECIMAL(19, 4) NOT NULL DEFAULT 0,
    purchase_price DECIMAL(19, 4) NOT NULL DEFAULT 0,
    average_cost DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(9, 4) NOT NULL DEFAULT 0,
    inventory_managed BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_item_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_item_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS flowora_warehouse (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_warehouse_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_warehouse_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS flowora_currency (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(3) NOT NULL,
    name VARCHAR(80) NOT NULL,
    symbol VARCHAR(8) NOT NULL,
    decimal_places INT NOT NULL DEFAULT 2,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_currency_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_currency_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS flowora_exchange_rate (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    base_currency_code VARCHAR(3) NOT NULL,
    quote_currency_code VARCHAR(3) NOT NULL,
    rate DECIMAL(19, 8) NOT NULL,
    effective_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_exchange_rate_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_exchange_rate_effective UNIQUE (organization_id, base_currency_code, quote_currency_code, effective_date)
);

CREATE TABLE IF NOT EXISTS flowora_tax_rate (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(120) NOT NULL,
    rate DECIMAL(9, 4) NOT NULL DEFAULT 0,
    exempt BOOLEAN NOT NULL DEFAULT FALSE,
    effective_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_tax_rate_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_tax_rate_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS flowora_account (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    parent_code VARCHAR(48) NULL,
    posting_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_account_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_account_organization_code UNIQUE (organization_id, code)
);

INSERT INTO flowora_organization (id, name, base_currency_code, timezone, approval_threshold, default_tax_rate, active)
SELECT 'org-demo', 'Demo Organization', 'USD', 'UTC', 10000.0000, 0.0000, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_organization WHERE id = 'org-demo');

INSERT INTO flowora_currency (id, organization_id, code, name, symbol, decimal_places, active)
SELECT 'currency-demo-usd', 'org-demo', 'USD', 'US Dollar', '$', 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_currency WHERE organization_id = 'org-demo' AND code = 'USD');

INSERT INTO flowora_currency (id, organization_id, code, name, symbol, decimal_places, active)
SELECT 'currency-demo-cny', 'org-demo', 'CNY', 'Chinese Yuan', '¥', 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_currency WHERE organization_id = 'org-demo' AND code = 'CNY');

INSERT INTO flowora_currency (id, organization_id, code, name, symbol, decimal_places, active)
SELECT 'currency-demo-eur', 'org-demo', 'EUR', 'Euro', '€', 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_currency WHERE organization_id = 'org-demo' AND code = 'EUR');

INSERT INTO flowora_tax_rate (id, organization_id, code, name, rate, exempt, effective_date, active)
SELECT 'tax-demo-zero', 'org-demo', 'TAX-0', 'Zero rate / exempt', 0.0000, TRUE, CURRENT_DATE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_tax_rate WHERE organization_id = 'org-demo' AND code = 'TAX-0');

INSERT INTO flowora_account (id, organization_id, code, name, account_type, parent_code, posting_allowed, active)
SELECT 'account-demo-cash', 'org-demo', '1000', 'Cash and bank', 'ASSET', NULL, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_account WHERE organization_id = 'org-demo' AND code = '1000');

INSERT INTO flowora_account (id, organization_id, code, name, account_type, parent_code, posting_allowed, active)
SELECT 'account-demo-receivable', 'org-demo', '1100', 'Accounts receivable', 'ASSET', NULL, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_account WHERE organization_id = 'org-demo' AND code = '1100');

INSERT INTO flowora_account (id, organization_id, code, name, account_type, parent_code, posting_allowed, active)
SELECT 'account-demo-payable', 'org-demo', '2000', 'Accounts payable', 'LIABILITY', NULL, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_account WHERE organization_id = 'org-demo' AND code = '2000');

INSERT INTO flowora_account (id, organization_id, code, name, account_type, parent_code, posting_allowed, active)
SELECT 'account-demo-revenue', 'org-demo', '4000', 'Operating revenue', 'REVENUE', NULL, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_account WHERE organization_id = 'org-demo' AND code = '4000');

INSERT INTO flowora_account (id, organization_id, code, name, account_type, parent_code, posting_allowed, active)
SELECT 'account-demo-expense', 'org-demo', '5000', 'Operating expense', 'EXPENSE', NULL, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM flowora_account WHERE organization_id = 'org-demo' AND code = '5000');
