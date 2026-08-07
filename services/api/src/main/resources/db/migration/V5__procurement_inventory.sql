CREATE TABLE IF NOT EXISTS flowora_purchase_request (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    supplier_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    requester_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    note VARCHAR(500) NULL,
    submitted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_purchase_request_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);
CREATE INDEX idx_flowora_purchase_request_search ON flowora_purchase_request (organization_id, status, created_at);

CREATE TABLE IF NOT EXISTS flowora_purchase_request_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    purchase_request_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    estimated_unit_cost DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_purchase_request_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_purchase_request_line_request FOREIGN KEY (purchase_request_id)
        REFERENCES flowora_purchase_request (id)
);

CREATE TABLE IF NOT EXISTS flowora_purchase_order (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    purchase_request_id VARCHAR(36) NULL,
    supplier_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    buyer_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    order_date DATE NOT NULL,
    expected_date DATE NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_purchase_order_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);
CREATE INDEX idx_flowora_purchase_order_search ON flowora_purchase_order (organization_id, status, created_at);

CREATE TABLE IF NOT EXISTS flowora_purchase_order_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    purchase_order_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    ordered_quantity DECIMAL(19, 4) NOT NULL,
    received_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0,
    unit_price DECIMAL(19, 4) NOT NULL,
    tax_rate DECIMAL(9, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_purchase_order_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_purchase_order_line_order FOREIGN KEY (purchase_order_id)
        REFERENCES flowora_purchase_order (id)
);

CREATE TABLE IF NOT EXISTS flowora_purchase_receipt (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    purchase_order_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    received_by VARCHAR(64) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_purchase_receipt_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE TABLE IF NOT EXISTS flowora_purchase_receipt_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    purchase_receipt_id VARCHAR(36) NOT NULL,
    purchase_order_line_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_purchase_receipt_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_purchase_receipt_line_receipt FOREIGN KEY (purchase_receipt_id)
        REFERENCES flowora_purchase_receipt (id)
);

CREATE TABLE IF NOT EXISTS flowora_stock_balance (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL DEFAULT 0,
    average_cost DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_stock_balance_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_stock_balance_item UNIQUE (organization_id, warehouse_id, item_id)
);
CREATE INDEX idx_flowora_stock_balance_warehouse ON flowora_stock_balance (organization_id, warehouse_id, updated_at);

CREATE TABLE IF NOT EXISTS flowora_stock_ledger_entry (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(24) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    quantity_delta DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    value_delta DECIMAL(19, 4) NOT NULL,
    balance_quantity DECIMAL(19, 4) NOT NULL,
    balance_value DECIMAL(19, 4) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_stock_ledger_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);
CREATE INDEX idx_flowora_stock_ledger_scope ON flowora_stock_ledger_entry (organization_id, warehouse_id, item_id, created_at);
CREATE UNIQUE INDEX uq_flowora_stock_ledger_document ON flowora_stock_ledger_entry (organization_id, document_type, document_id, movement_type);

CREATE TABLE IF NOT EXISTS flowora_stock_transfer (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    source_warehouse_id VARCHAR(36) NOT NULL,
    target_warehouse_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_stock_transfer_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE TABLE IF NOT EXISTS flowora_stock_transfer_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    stock_transfer_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_stock_transfer_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_stock_transfer_line_transfer FOREIGN KEY (stock_transfer_id)
        REFERENCES flowora_stock_transfer (id)
);

CREATE TABLE IF NOT EXISTS flowora_stock_count (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    warehouse_id VARCHAR(36) NOT NULL,
    counted_by VARCHAR(64) NOT NULL,
    counted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_stock_count_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE TABLE IF NOT EXISTS flowora_stock_count_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    stock_count_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    expected_quantity DECIMAL(19, 4) NOT NULL,
    counted_quantity DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_stock_count_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_stock_count_line_count FOREIGN KEY (stock_count_id)
        REFERENCES flowora_stock_count (id)
);

CREATE TABLE IF NOT EXISTS flowora_stock_adjustment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    warehouse_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity_delta DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL,
    workflow_task_id VARCHAR(36) NULL,
    posted_at TIMESTAMP NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_stock_adjustment_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);
