CREATE TABLE flowora_instance_setting (
    setting_key VARCHAR(96) NOT NULL PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    classification VARCHAR(24) NOT NULL DEFAULT 'PUBLIC',
    description VARCHAR(255) NOT NULL,
    updated_by VARCHAR(64) NOT NULL DEFAULT 'MIGRATION',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_instance_setting_classification CHECK (classification IN ('PUBLIC','INTERNAL'))
);

INSERT INTO flowora_instance_setting(setting_key, setting_value, classification, description) VALUES
('deployment.mode','production','PUBLIC','Current deployment mode'),
('export.retention.hours','24','INTERNAL','Completed export retention window'),
('backup.rpo.hours','24','INTERNAL','Target database and attachment recovery point'),
('backup.rto.hours','4','INTERNAL','Target service recovery time');

CREATE TABLE flowora_saved_view (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    owner_user_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    definition_json JSON NOT NULL,
    shared BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_saved_view_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_saved_view_owner FOREIGN KEY (owner_user_id) REFERENCES flowora_user_account(id),
    CONSTRAINT uq_saved_view_owner_name UNIQUE (organization_id, owner_user_id, resource_type, name)
);
CREATE INDEX idx_saved_view_resource ON flowora_saved_view(organization_id, resource_type, active, updated_at);

CREATE TABLE flowora_export_job (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    requested_by VARCHAR(36) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    format VARCHAR(16) NOT NULL DEFAULT 'CSV',
    filters_json JSON NOT NULL,
    locale VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    row_count BIGINT NOT NULL DEFAULT 0,
    result_filename VARCHAR(255) NULL,
    storage_key VARCHAR(255) NULL,
    sha256_hex VARCHAR(64) NULL,
    error_code VARCHAR(96) NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_export_job_org FOREIGN KEY (organization_id) REFERENCES flowora_organization(id),
    CONSTRAINT fk_export_job_user FOREIGN KEY (requested_by) REFERENCES flowora_user_account(id),
    CONSTRAINT ck_export_job_format CHECK (format = 'CSV'),
    CONSTRAINT ck_export_job_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','EXPIRED'))
);
CREATE INDEX idx_export_job_owner ON flowora_export_job(organization_id, requested_by, created_at);
CREATE INDEX idx_export_job_cleanup ON flowora_export_job(status, expires_at);

ALTER TABLE flowora_finance_invoice
    ADD COLUMN legacy_source_type VARCHAR(32) NULL AFTER request_id,
    ADD COLUMN legacy_source_id VARCHAR(36) NULL AFTER legacy_source_type,
    ADD COLUMN read_only BOOLEAN NOT NULL DEFAULT FALSE AFTER legacy_source_id,
    ADD CONSTRAINT uq_finance_invoice_legacy UNIQUE (organization_id, legacy_source_type, legacy_source_id);

INSERT INTO flowora_finance_invoice(
    id,organization_id,number,document_type,party_type,party_id,status,settlement_status,credit_status,
    business_date,accounting_date,due_date,exchange_rate_date,currency_code,base_currency_code,exchange_rate,
    net_amount,tax_amount,total_amount,base_total_amount,allocated_amount,credited_amount,match_status,
    posted_at,posted_by,created_by,request_id,legacy_source_type,legacy_source_id,read_only,created_at,updated_at
)
SELECT CONCAT('lr-',LEFT(SHA2(CONCAT('receivable:',r.id),256),33)),r.organization_id,
       CONCAT('LEG-R-',LEFT(r.number,34)),'SALES_INVOICE','CUSTOMER',r.customer_id,'POSTED',
       CASE WHEN r.paid_amount >= r.total_amount THEN 'PAID' WHEN r.paid_amount > 0 THEN 'PARTIALLY_PAID' ELSE 'UNPAID' END,
       'NONE',DATE(r.created_at),DATE(r.created_at),r.due_date,DATE(r.created_at),r.currency_code,s.base_currency_code,
       CASE WHEN r.currency_code=s.base_currency_code THEN 1 ELSE COALESCE((SELECT er.rate FROM flowora_exchange_rate er
           WHERE er.organization_id=r.organization_id AND er.base_currency_code=s.base_currency_code
             AND er.quote_currency_code=r.currency_code AND er.effective_date<=DATE(r.created_at) AND er.active=TRUE
           ORDER BY er.effective_date DESC LIMIT 1),1) END,
       r.total_amount,0,r.total_amount,
       ROUND(r.total_amount * CASE WHEN r.currency_code=s.base_currency_code THEN 1 ELSE COALESCE((SELECT er.rate FROM flowora_exchange_rate er
           WHERE er.organization_id=r.organization_id AND er.base_currency_code=s.base_currency_code
             AND er.quote_currency_code=r.currency_code AND er.effective_date<=DATE(r.created_at) AND er.active=TRUE
           ORDER BY er.effective_date DESC LIMIT 1),1) END,4),
       LEAST(r.paid_amount,r.total_amount),0,'NOT_REQUIRED',r.created_at,'MIGRATION','MIGRATION',
       CONCAT('legacy:receivable:',r.id),'MIGRATED_LEGACY',r.id,TRUE,r.created_at,r.updated_at
FROM flowora_receivable_document r
JOIN flowora_finance_setting s ON s.organization_id=r.organization_id
WHERE NOT EXISTS (SELECT 1 FROM flowora_finance_invoice f WHERE f.organization_id=r.organization_id
                  AND f.legacy_source_type='MIGRATED_LEGACY' AND f.legacy_source_id=r.id);

INSERT INTO flowora_finance_invoice_line(
    id,organization_id,invoice_id,line_no,description,quantity,unit_price,discount_rate,tax_rate,
    net_amount,tax_amount,total_amount,base_total_amount
)
SELECT CONCAT('lrl-',LEFT(SHA2(CONCAT('receivable-line:',r.id),256),32)),r.organization_id,
       CONCAT('lr-',LEFT(SHA2(CONCAT('receivable:',r.id),256),33)),1,'Migrated legacy receivable',1,
       r.total_amount,0,0,r.total_amount,0,r.total_amount,f.base_total_amount
FROM flowora_receivable_document r
JOIN flowora_finance_invoice f ON f.organization_id=r.organization_id AND f.legacy_source_type='MIGRATED_LEGACY'
    AND f.legacy_source_id=r.id
WHERE NOT EXISTS (SELECT 1 FROM flowora_finance_invoice_line l WHERE l.invoice_id=f.id);

INSERT INTO flowora_finance_invoice_source(id,organization_id,invoice_line_id,source_type,source_id,quantity,amount)
SELECT CONCAT('lrs-',LEFT(SHA2(CONCAT('receivable-source:',r.id),256),32)),r.organization_id,
       CONCAT('lrl-',LEFT(SHA2(CONCAT('receivable-line:',r.id),256),32)),'MIGRATED_LEGACY',r.id,1,r.total_amount
FROM flowora_receivable_document r;

INSERT INTO flowora_finance_invoice(
    id,organization_id,number,document_type,party_type,party_id,status,settlement_status,credit_status,
    business_date,accounting_date,due_date,exchange_rate_date,currency_code,base_currency_code,exchange_rate,
    net_amount,tax_amount,total_amount,base_total_amount,allocated_amount,credited_amount,match_status,
    posted_at,posted_by,created_by,request_id,legacy_source_type,legacy_source_id,read_only,created_at,updated_at
)
SELECT CONCAT('lp-',LEFT(SHA2(CONCAT('payable:',p.id),256),33)),p.organization_id,
       CONCAT('LEG-P-',LEFT(p.number,34)),'SUPPLIER_INVOICE','SUPPLIER',p.supplier_id,'POSTED',
       CASE WHEN p.paid_amount >= p.total_amount THEN 'PAID' WHEN p.paid_amount > 0 THEN 'PARTIALLY_PAID' ELSE 'UNPAID' END,
       'NONE',DATE(p.created_at),DATE(p.created_at),p.due_date,DATE(p.created_at),p.currency_code,s.base_currency_code,
       CASE WHEN p.currency_code=s.base_currency_code THEN 1 ELSE COALESCE((SELECT er.rate FROM flowora_exchange_rate er
           WHERE er.organization_id=p.organization_id AND er.base_currency_code=s.base_currency_code
             AND er.quote_currency_code=p.currency_code AND er.effective_date<=DATE(p.created_at) AND er.active=TRUE
           ORDER BY er.effective_date DESC LIMIT 1),1) END,
       p.total_amount,0,p.total_amount,
       ROUND(p.total_amount * CASE WHEN p.currency_code=s.base_currency_code THEN 1 ELSE COALESCE((SELECT er.rate FROM flowora_exchange_rate er
           WHERE er.organization_id=p.organization_id AND er.base_currency_code=s.base_currency_code
             AND er.quote_currency_code=p.currency_code AND er.effective_date<=DATE(p.created_at) AND er.active=TRUE
           ORDER BY er.effective_date DESC LIMIT 1),1) END,4),
       LEAST(p.paid_amount,p.total_amount),0,'NOT_REQUIRED',p.created_at,'MIGRATION','MIGRATION',
       CONCAT('legacy:payable:',p.id),'MIGRATED_LEGACY',p.id,TRUE,p.created_at,p.updated_at
FROM flowora_payable_document p
JOIN flowora_finance_setting s ON s.organization_id=p.organization_id
WHERE NOT EXISTS (SELECT 1 FROM flowora_finance_invoice f WHERE f.organization_id=p.organization_id
                  AND f.legacy_source_type='MIGRATED_LEGACY' AND f.legacy_source_id=p.id);

INSERT INTO flowora_finance_invoice_line(
    id,organization_id,invoice_id,line_no,description,quantity,unit_price,discount_rate,tax_rate,
    net_amount,tax_amount,total_amount,base_total_amount
)
SELECT CONCAT('lpl-',LEFT(SHA2(CONCAT('payable-line:',p.id),256),32)),p.organization_id,
       CONCAT('lp-',LEFT(SHA2(CONCAT('payable:',p.id),256),33)),1,'Migrated legacy payable',1,
       p.total_amount,0,0,p.total_amount,0,p.total_amount,f.base_total_amount
FROM flowora_payable_document p
JOIN flowora_finance_invoice f ON f.organization_id=p.organization_id AND f.legacy_source_type='MIGRATED_LEGACY'
    AND f.legacy_source_id=p.id
WHERE NOT EXISTS (SELECT 1 FROM flowora_finance_invoice_line l WHERE l.invoice_id=f.id);

INSERT INTO flowora_finance_invoice_source(id,organization_id,invoice_line_id,source_type,source_id,quantity,amount)
SELECT CONCAT('lps-',LEFT(SHA2(CONCAT('payable-source:',p.id),256),32)),p.organization_id,
       CONCAT('lpl-',LEFT(SHA2(CONCAT('payable-line:',p.id),256),32)),'MIGRATED_LEGACY',p.id,1,p.total_amount
FROM flowora_payable_document p;

INSERT INTO flowora_warehouse(id,organization_id,code,name,address,active)
SELECT CONCAT('legacy-',LEFT(SHA2(CONCAT('warehouse:',o.id),256),29)),o.id,'LEGACY-STOCK','Legacy stock review',
       'Migration fallback location; review before operational use',TRUE
FROM flowora_organization o
WHERE NOT EXISTS (SELECT 1 FROM flowora_warehouse w WHERE w.organization_id=o.id AND w.code='LEGACY-STOCK');

INSERT INTO flowora_permission(code,resource_code,action_code,description,`sensitive`) VALUES
('analytics:view','analytics','view','View role dashboards and analytics',FALSE),
('analytics:export','analytics','export','Create and download governed exports',TRUE),
('analytics:cross-org','analytics','cross-org','View authorized cross-organization summaries',TRUE),
('admin:diagnostics','admin','diagnostics','View deployment diagnostics and support bundle',TRUE);

INSERT IGNORE INTO flowora_role_permission(role_id,permission_code)
SELECT r.id,p.code FROM flowora_role r JOIN flowora_permission p ON p.code='analytics:view';

INSERT IGNORE INTO flowora_role_permission(role_id,permission_code)
SELECT r.id,p.code FROM flowora_role r JOIN flowora_permission p ON p.code IN ('analytics:export','analytics:cross-org')
WHERE r.code IN ('ADMIN','MANAGEMENT','FINANCE');

INSERT IGNORE INTO flowora_role_permission(role_id,permission_code)
SELECT r.id,'admin:diagnostics' FROM flowora_role r WHERE r.code='ADMIN';
