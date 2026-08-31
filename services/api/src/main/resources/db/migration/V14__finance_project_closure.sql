CREATE TABLE flowora_finance_setting (
    organization_id VARCHAR(36) NOT NULL PRIMARY KEY,
    base_currency_code VARCHAR(3) NOT NULL,
    fiscal_year_start_month INT NOT NULL DEFAULT 1,
    match_quantity_tolerance DECIMAL(19,4) NOT NULL DEFAULT 0,
    match_price_tolerance_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    match_tax_tolerance DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_finance_setting_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT ck_finance_setting_month CHECK (fiscal_year_start_month BETWEEN 1 AND 12)
);

CREATE TABLE flowora_posting_mapping (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    semantic_code VARCHAR(48) NOT NULL,
    account_code VARCHAR(48) NOT NULL,
    rule_version INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_posting_mapping_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT uq_posting_mapping UNIQUE (organization_id, semantic_code, rule_version)
);

CREATE TABLE flowora_finance_invoice (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(40) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    party_type VARCHAR(16) NOT NULL,
    party_id VARCHAR(36) NOT NULL,
    original_invoice_id VARCHAR(36) NULL,
    project_id VARCHAR(36) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    settlement_status VARCHAR(24) NOT NULL DEFAULT 'UNPAID',
    credit_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    business_date DATE NOT NULL,
    accounting_date DATE NOT NULL,
    due_date DATE NOT NULL,
    exchange_rate_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    base_currency_code VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(19,8) NOT NULL,
    net_amount DECIMAL(19,4) NOT NULL,
    tax_amount DECIMAL(19,4) NOT NULL,
    total_amount DECIMAL(19,4) NOT NULL,
    base_total_amount DECIMAL(19,4) NOT NULL,
    allocated_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    credited_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    match_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    match_exception_approved_by VARCHAR(64) NULL,
    match_exception_reason VARCHAR(500) NULL,
    posted_at TIMESTAMP NULL,
    posted_by VARCHAR(64) NULL,
    created_by VARCHAR(64) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_finance_invoice_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_finance_invoice_original FOREIGN KEY (original_invoice_id) REFERENCES flowora_finance_invoice(id),
    CONSTRAINT fk_finance_invoice_project FOREIGN KEY (project_id) REFERENCES flowora_project(id),
    CONSTRAINT uq_finance_invoice_number UNIQUE (organization_id, number),
    CONSTRAINT uq_finance_invoice_request UNIQUE (organization_id, request_id),
    CONSTRAINT ck_finance_invoice_rate CHECK (exchange_rate > 0),
    CONSTRAINT ck_finance_invoice_amount CHECK (net_amount >= 0 AND tax_amount >= 0 AND total_amount > 0),
    CONSTRAINT ck_finance_invoice_allocated CHECK (allocated_amount >= 0 AND credited_amount >= 0 AND allocated_amount + credited_amount <= total_amount)
);
CREATE INDEX idx_finance_invoice_search ON flowora_finance_invoice(organization_id, document_type, status, accounting_date);
CREATE INDEX idx_finance_invoice_party ON flowora_finance_invoice(organization_id, party_type, party_id, settlement_status, due_date);

CREATE TABLE flowora_finance_invoice_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    invoice_id VARCHAR(36) NOT NULL,
    line_no INT NOT NULL,
    item_id VARCHAR(36) NULL,
    description VARCHAR(240) NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    discount_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    net_amount DECIMAL(19,4) NOT NULL,
    tax_amount DECIMAL(19,4) NOT NULL,
    total_amount DECIMAL(19,4) NOT NULL,
    base_total_amount DECIMAL(19,4) NOT NULL,
    revenue_expense_account_code VARCHAR(48) NULL,
    project_id VARCHAR(36) NULL,
    credited_quantity DECIMAL(19,4) NOT NULL DEFAULT 0,
    match_quantity_variance DECIMAL(19,4) NOT NULL DEFAULT 0,
    match_price_variance_rate DECIMAL(9,4) NOT NULL DEFAULT 0,
    match_tax_variance DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_invoice_line_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_invoice_line_header FOREIGN KEY (invoice_id) REFERENCES flowora_finance_invoice(id),
    CONSTRAINT fk_invoice_line_project FOREIGN KEY (project_id) REFERENCES flowora_project(id),
    CONSTRAINT uq_invoice_line_no UNIQUE (invoice_id, line_no),
    CONSTRAINT ck_invoice_line_quantity CHECK (quantity > 0 AND credited_quantity >= 0 AND credited_quantity <= quantity),
    CONSTRAINT ck_invoice_line_amount CHECK (unit_price >= 0 AND net_amount >= 0 AND tax_amount >= 0 AND total_amount > 0)
);

CREATE TABLE flowora_finance_invoice_source (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    invoice_line_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id VARCHAR(36) NOT NULL,
    source_line_id VARCHAR(36) NULL,
    quantity DECIMAL(19,4) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_source_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_invoice_source_line FOREIGN KEY (invoice_line_id) REFERENCES flowora_finance_invoice_line(id),
    CONSTRAINT uq_invoice_source UNIQUE (organization_id, invoice_line_id, source_type, source_line_id),
    CONSTRAINT ck_invoice_source_values CHECK (quantity > 0 AND amount >= 0)
);
CREATE INDEX idx_invoice_source_origin ON flowora_finance_invoice_source(organization_id, source_type, source_id, source_line_id);

CREATE TABLE flowora_payment_v2 (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(40) NOT NULL,
    payment_type VARCHAR(24) NOT NULL,
    party_type VARCHAR(16) NOT NULL,
    party_id VARCHAR(36) NOT NULL,
    bank_account_id VARCHAR(36) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    allocation_status VARCHAR(16) NOT NULL DEFAULT 'UNALLOCATED',
    business_date DATE NOT NULL,
    accounting_date DATE NOT NULL,
    exchange_rate_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    base_currency_code VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(19,8) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    base_amount DECIMAL(19,4) NOT NULL,
    allocated_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    reference VARCHAR(160) NULL,
    reversal_of_id VARCHAR(36) NULL,
    posted_at TIMESTAMP NULL,
    posted_by VARCHAR(64) NULL,
    created_by VARCHAR(64) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_v2_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_payment_v2_reversal FOREIGN KEY (reversal_of_id) REFERENCES flowora_payment_v2(id),
    CONSTRAINT uq_payment_v2_number UNIQUE (organization_id, number),
    CONSTRAINT uq_payment_v2_request UNIQUE (organization_id, request_id),
    CONSTRAINT ck_payment_v2_amount CHECK (amount > 0 AND exchange_rate > 0 AND allocated_amount >= 0 AND allocated_amount <= amount)
);
CREATE INDEX idx_payment_v2_party ON flowora_payment_v2(organization_id, party_type, party_id, status);

CREATE TABLE flowora_payment_allocation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    payment_id VARCHAR(36) NOT NULL,
    invoice_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    base_amount DECIMAL(19,4) NOT NULL,
    realized_exchange_difference DECIMAL(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    allocated_by VARCHAR(64) NOT NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at TIMESTAMP NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_allocation_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_payment_allocation_payment FOREIGN KEY (payment_id) REFERENCES flowora_payment_v2(id),
    CONSTRAINT fk_payment_allocation_invoice FOREIGN KEY (invoice_id) REFERENCES flowora_finance_invoice(id),
    CONSTRAINT ck_payment_allocation_amount CHECK (amount > 0)
);
CREATE INDEX idx_payment_allocation_payment ON flowora_payment_allocation(organization_id, payment_id, status);
CREATE INDEX idx_payment_allocation_invoice ON flowora_payment_allocation(organization_id, invoice_id, status);

CREATE TABLE flowora_allocation_reversal (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    allocation_id VARCHAR(36) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    reversed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_id VARCHAR(120) NOT NULL,
    CONSTRAINT fk_allocation_reversal_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_allocation_reversal_allocation FOREIGN KEY (allocation_id) REFERENCES flowora_payment_allocation(id),
    CONSTRAINT uq_allocation_reversal UNIQUE (organization_id, allocation_id),
    CONSTRAINT uq_allocation_reversal_request UNIQUE (organization_id, request_id)
);

ALTER TABLE flowora_journal_entry
    ADD COLUMN business_date DATE NULL AFTER entry_date,
    ADD COLUMN accounting_date DATE NULL AFTER business_date,
    ADD COLUMN exchange_rate_date DATE NULL AFTER accounting_date,
    ADD COLUMN base_currency_code VARCHAR(3) NULL AFTER currency_code,
    ADD COLUMN exchange_rate DECIMAL(19,8) NOT NULL DEFAULT 1 AFTER base_currency_code,
    ADD COLUMN rule_version INT NOT NULL DEFAULT 1 AFTER exchange_rate,
    ADD COLUMN reversal_of_id VARCHAR(36) NULL AFTER source_id,
    ADD COLUMN posted_by VARCHAR(64) NULL AFTER status,
    ADD COLUMN posted_at TIMESTAMP NULL AFTER posted_by,
    ADD COLUMN request_id VARCHAR(120) NULL AFTER posted_at,
    ADD CONSTRAINT fk_journal_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES flowora_journal_entry(id),
    ADD CONSTRAINT uq_journal_request UNIQUE (organization_id, request_id);

UPDATE flowora_journal_entry SET business_date=entry_date, accounting_date=entry_date,
    exchange_rate_date=entry_date, base_currency_code=currency_code, posted_at=created_at
WHERE business_date IS NULL;

ALTER TABLE flowora_journal_line
    ADD COLUMN base_debit DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER credit,
    ADD COLUMN base_credit DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER base_debit,
    ADD COLUMN project_id VARCHAR(36) NULL AFTER currency_code,
    ADD COLUMN party_type VARCHAR(16) NULL AFTER project_id,
    ADD COLUMN party_id VARCHAR(36) NULL AFTER party_type,
    ADD COLUMN source_line_id VARCHAR(36) NULL AFTER party_id,
    ADD CONSTRAINT fk_journal_line_project FOREIGN KEY (project_id) REFERENCES flowora_project(id),
    ADD CONSTRAINT ck_journal_line_side CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0));

UPDATE flowora_journal_line SET base_debit=debit, base_credit=credit;

CREATE TABLE flowora_period_close_check (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    period_id VARCHAR(36) NOT NULL,
    check_code VARCHAR(48) NOT NULL,
    result_status VARCHAR(16) NOT NULL,
    result_count INT NOT NULL,
    details_json JSON NOT NULL,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_by VARCHAR(64) NOT NULL,
    CONSTRAINT fk_period_check_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_period_check_period FOREIGN KEY (period_id) REFERENCES flowora_accounting_period(id)
);

CREATE TABLE flowora_period_status_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    period_id VARCHAR(36) NOT NULL,
    from_status VARCHAR(16) NOT NULL,
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_id VARCHAR(120) NOT NULL,
    CONSTRAINT fk_period_event_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_period_event_period FOREIGN KEY (period_id) REFERENCES flowora_accounting_period(id),
    CONSTRAINT uq_period_event_request UNIQUE (organization_id, request_id)
);

ALTER TABLE flowora_bank_account
    ADD COLUMN ledger_account_code VARCHAR(48) NOT NULL DEFAULT '1000' AFTER currency_code;

ALTER TABLE flowora_payment_v2
    ADD CONSTRAINT fk_payment_v2_bank FOREIGN KEY (bank_account_id) REFERENCES flowora_bank_account(id);

CREATE TABLE flowora_bank_statement_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    bank_account_id VARCHAR(36) NOT NULL,
    transaction_date DATE NOT NULL,
    value_date DATE NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    external_reference VARCHAR(160) NOT NULL,
    counterparty VARCHAR(200) NULL,
    description VARCHAR(500) NULL,
    reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'UNMATCHED',
    import_batch_id VARCHAR(36) NOT NULL,
    imported_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_statement_line_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_statement_line_bank FOREIGN KEY (bank_account_id) REFERENCES flowora_bank_account(id),
    CONSTRAINT uq_statement_line_duplicate UNIQUE (organization_id, bank_account_id, transaction_date, amount, external_reference)
);

CREATE TABLE flowora_bank_reconciliation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(40) NOT NULL,
    bank_account_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    total_statement_amount DECIMAL(19,4) NOT NULL,
    total_payment_amount DECIMAL(19,4) NOT NULL,
    difference_amount DECIMAL(19,4) NOT NULL,
    confirmed_by VARCHAR(64) NOT NULL,
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_by VARCHAR(64) NULL,
    reversed_at TIMESTAMP NULL,
    reversal_reason VARCHAR(500) NULL,
    request_id VARCHAR(120) NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reconciliation_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_reconciliation_bank FOREIGN KEY (bank_account_id) REFERENCES flowora_bank_account(id),
    CONSTRAINT uq_reconciliation_number UNIQUE (organization_id, number),
    CONSTRAINT uq_reconciliation_request UNIQUE (organization_id, request_id)
);

CREATE TABLE flowora_bank_reconciliation_link (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    reconciliation_id VARCHAR(36) NOT NULL,
    statement_line_id VARCHAR(36) NOT NULL,
    payment_id VARCHAR(36) NOT NULL,
    matched_amount DECIMAL(19,4) NOT NULL,
    CONSTRAINT fk_reconciliation_link_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_reconciliation_link_header FOREIGN KEY (reconciliation_id) REFERENCES flowora_bank_reconciliation(id),
    CONSTRAINT uq_reconciliation_link_statement UNIQUE (reconciliation_id, statement_line_id),
    CONSTRAINT fk_reconciliation_link_statement FOREIGN KEY (statement_line_id) REFERENCES flowora_bank_statement_line(id),
    CONSTRAINT fk_reconciliation_link_payment FOREIGN KEY (payment_id) REFERENCES flowora_payment_v2(id),
    CONSTRAINT ck_reconciliation_link_amount CHECK (matched_amount > 0)
);

CREATE TABLE flowora_budget_version (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    fiscal_year INT NOT NULL,
    version_no_value INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    control_policy VARCHAR(16) NOT NULL DEFAULT 'WARN',
    approved_by VARCHAR(64) NULL,
    approved_at TIMESTAMP NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_budget_version_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT uq_budget_version UNIQUE (organization_id, fiscal_year, version_no_value)
);

CREATE TABLE flowora_budget_line_v2 (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    budget_version_id VARCHAR(36) NOT NULL,
    month INT NOT NULL,
    account_code VARCHAR(48) NOT NULL,
    department_id VARCHAR(36) NULL,
    project_id VARCHAR(36) NULL,
    amount DECIMAL(19,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budget_line_v2_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_budget_line_v2_version FOREIGN KEY (budget_version_id) REFERENCES flowora_budget_version(id),
    CONSTRAINT fk_budget_line_v2_project FOREIGN KEY (project_id) REFERENCES flowora_project(id),
    CONSTRAINT ck_budget_line_v2 CHECK (month BETWEEN 1 AND 12 AND amount >= 0)
);

CREATE TABLE flowora_currency_revaluation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(40) NOT NULL,
    accounting_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    rate DECIMAL(19,8) NOT NULL,
    total_gain DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_loss DECIMAL(19,4) NOT NULL DEFAULT 0,
    journal_entry_id VARCHAR(36) NULL,
    reversal_date DATE NULL,
    created_by VARCHAR(64) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_revaluation_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_revaluation_journal FOREIGN KEY (journal_entry_id) REFERENCES flowora_journal_entry(id),
    CONSTRAINT uq_revaluation_number UNIQUE (organization_id, number),
    CONSTRAINT uq_revaluation_request UNIQUE (organization_id, request_id),
    CONSTRAINT ck_revaluation_rate CHECK (rate > 0)
);

CREATE TABLE flowora_currency_revaluation_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    revaluation_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_id VARCHAR(36) NOT NULL,
    original_base_balance DECIMAL(19,4) NOT NULL,
    revalued_base_balance DECIMAL(19,4) NOT NULL,
    difference_amount DECIMAL(19,4) NOT NULL,
    project_id VARCHAR(36) NULL,
    CONSTRAINT fk_revaluation_line_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_revaluation_line_header FOREIGN KEY (revaluation_id) REFERENCES flowora_currency_revaluation(id),
    CONSTRAINT fk_revaluation_line_project FOREIGN KEY (project_id) REFERENCES flowora_project(id)
);

CREATE TABLE flowora_project_member (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    project_role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_member_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES flowora_project(id),
    CONSTRAINT uq_project_member UNIQUE (organization_id, project_id, user_id)
);

CREATE TABLE flowora_project_billing_basis (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    basis_type VARCHAR(24) NOT NULL,
    source_id VARCHAR(36) NOT NULL,
    description VARCHAR(240) NOT NULL,
    available_amount DECIMAL(19,4) NOT NULL,
    billed_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    approved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_billing_basis_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_project_billing_basis_project FOREIGN KEY (project_id) REFERENCES flowora_project(id),
    CONSTRAINT uq_project_billing_source UNIQUE (organization_id, basis_type, source_id),
    CONSTRAINT ck_project_billing_amount CHECK (available_amount > 0 AND billed_amount >= 0 AND billed_amount <= available_amount)
);

ALTER TABLE flowora_project
    ADD COLUMN department_id VARCHAR(36) NULL AFTER manager_user_id,
    ADD COLUMN billing_mode VARCHAR(24) NOT NULL DEFAULT 'TIME_MATERIAL' AFTER currency_code,
    ADD COLUMN contract_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER billing_mode,
    ADD COLUMN billing_status VARCHAR(16) NOT NULL DEFAULT 'UNBILLED' AFTER contract_amount;

ALTER TABLE flowora_project_milestone
    ADD COLUMN billable BOOLEAN NOT NULL DEFAULT FALSE AFTER status,
    ADD COLUMN billing_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER billable,
    ADD COLUMN approval_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED' AFTER billing_amount,
    ADD COLUMN billed_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER approval_status;

ALTER TABLE flowora_timesheet
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER note,
    ADD COLUMN approval_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER lifecycle_status,
    ADD COLUMN billing_status VARCHAR(16) NOT NULL DEFAULT 'UNBILLED' AFTER approval_status,
    ADD COLUMN billed_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER billing_status,
    ADD COLUMN approved_by VARCHAR(64) NULL AFTER billed_amount,
    ADD COLUMN approved_at TIMESTAMP NULL AFTER approved_by;

ALTER TABLE flowora_project_expense
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER description,
    ADD COLUMN approval_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER lifecycle_status,
    ADD COLUMN billing_status VARCHAR(16) NOT NULL DEFAULT 'UNBILLED' AFTER approval_status,
    ADD COLUMN reimbursement_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED' AFTER billing_status,
    ADD COLUMN billed_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER reimbursement_status,
    ADD COLUMN approved_by VARCHAR(64) NULL AFTER billed_amount,
    ADD COLUMN approved_at TIMESTAMP NULL AFTER approved_by;

INSERT INTO flowora_finance_setting(organization_id, base_currency_code)
SELECT id, base_currency_code FROM flowora_organization
ON DUPLICATE KEY UPDATE base_currency_code=VALUES(base_currency_code);

INSERT INTO flowora_posting_mapping(id,organization_id,semantic_code,account_code,rule_version)
SELECT UUID(), id, semantic_code, account_code, 1
FROM flowora_organization
JOIN (
    SELECT 'CASH' semantic_code, '1000' account_code UNION ALL
    SELECT 'RECEIVABLE', '1100' UNION ALL
    SELECT 'INVENTORY', '1400' UNION ALL
    SELECT 'TAX_RECEIVABLE', '1500' UNION ALL
    SELECT 'PAYABLE', '2000' UNION ALL
    SELECT 'ACCRUED_PAYABLE', '2100' UNION ALL
    SELECT 'TAX_PAYABLE', '2200' UNION ALL
    SELECT 'REVENUE', '4000' UNION ALL
    SELECT 'FX_GAIN', '4100' UNION ALL
    SELECT 'EXPENSE', '5000' UNION ALL
    SELECT 'FX_LOSS', '5100' UNION ALL
    SELECT 'PRICE_VARIANCE', '5200'
) defaults
WHERE TRUE
ON DUPLICATE KEY UPDATE account_code=VALUES(account_code);

INSERT INTO flowora_account(id,organization_id,code,name,account_type,parent_code,posting_allowed,active)
SELECT CONCAT('account-', LEFT(flowora_organization.id, 20), '-', defaults.code), flowora_organization.id, defaults.code, defaults.name, defaults.account_type, NULL, TRUE, TRUE
FROM flowora_organization
JOIN (
    SELECT '1500' code, 'Input tax' name, 'ASSET' account_type UNION ALL
    SELECT '2100', 'Accrued payable', 'LIABILITY' UNION ALL
    SELECT '2200', 'Tax payable', 'LIABILITY' UNION ALL
    SELECT '4100', 'Foreign exchange gain', 'REVENUE' UNION ALL
    SELECT '5100', 'Foreign exchange loss', 'EXPENSE' UNION ALL
    SELECT '5200', 'Purchase price variance', 'EXPENSE'
) defaults
WHERE NOT EXISTS (SELECT 1 FROM flowora_account a WHERE a.organization_id=flowora_organization.id AND a.code=defaults.code);

INSERT IGNORE INTO flowora_permission(code,resource_code,action_code,description,`sensitive`) VALUES
('finance:invoice','finance','invoice','Create and edit invoices',TRUE),
('finance:match-exception','finance','match-exception','Approve three-way match exceptions',TRUE),
('finance:allocate','finance','allocate','Allocate and unallocate settlements',TRUE),
('finance:period-reopen','finance','period-reopen','Reopen accounting periods',TRUE),
('finance:bank','finance','bank','Manage bank reconciliation',TRUE),
('finance:budget','finance','budget','Manage budgets and revaluation',TRUE),
('project:billing','project','billing','Manage project billing and profitability',TRUE);

INSERT IGNORE INTO flowora_role_permission(role_id,permission_code)
SELECT r.id,p.code FROM flowora_role r JOIN flowora_permission p ON p.code IN
('finance:invoice','finance:match-exception','finance:allocate','finance:period-reopen','finance:bank','finance:budget','project:billing')
WHERE r.code IN ('ADMIN','FINANCE');

INSERT IGNORE INTO flowora_role_permission(role_id,permission_code)
SELECT r.id,p.code FROM flowora_role r JOIN flowora_permission p ON p.code='project:billing'
WHERE r.code='PROJECT_MANAGER';
