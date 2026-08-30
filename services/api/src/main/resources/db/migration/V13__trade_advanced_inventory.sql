CREATE TABLE flowora_inventory_lot (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    lot_code VARCHAR(96) NOT NULL,
    supplier_lot_code VARCHAR(96) NULL,
    manufactured_on DATE NULL,
    expires_on DATE NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_inventory_lot_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_inventory_lot_item FOREIGN KEY (item_id) REFERENCES flowora_item (id),
    CONSTRAINT uq_flowora_inventory_lot_code UNIQUE (organization_id, item_id, lot_code)
);

CREATE TABLE flowora_inventory_serial (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    serial_code VARCHAR(128) NOT NULL,
    lot_id VARCHAR(36) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    warehouse_id VARCHAR(36) NULL,
    location_id VARCHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_inventory_serial_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_inventory_serial_item FOREIGN KEY (item_id) REFERENCES flowora_item (id),
    CONSTRAINT fk_flowora_inventory_serial_lot FOREIGN KEY (lot_id) REFERENCES flowora_inventory_lot (id),
    CONSTRAINT uq_flowora_inventory_serial_code UNIQUE (organization_id, serial_code)
);

CREATE TABLE flowora_inventory_balance_v2 (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    location_id VARCHAR(36) NOT NULL DEFAULT '',
    item_id VARCHAR(36) NOT NULL,
    lot_id VARCHAR(36) NOT NULL DEFAULT '',
    serial_id VARCHAR(36) NOT NULL DEFAULT '',
    on_hand_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0,
    reserved_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0,
    average_cost DECIMAL(19, 4) NOT NULL DEFAULT 0,
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_inventory_balance_v2_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_inventory_balance_v2_dimension UNIQUE (organization_id, warehouse_id, location_id, item_id, lot_id, serial_id),
    CONSTRAINT ck_flowora_inventory_balance_v2_nonnegative CHECK (on_hand_quantity >= 0 AND reserved_quantity >= 0)
);

CREATE INDEX idx_flowora_inventory_balance_v2_available
    ON flowora_inventory_balance_v2 (organization_id, warehouse_id, item_id, frozen, on_hand_quantity, reserved_quantity);

CREATE TABLE flowora_stock_movement (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(40) NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    reversal_of_id VARCHAR(36) NULL,
    actor_user_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    posted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_stock_movement_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_stock_movement_reversal FOREIGN KEY (reversal_of_id) REFERENCES flowora_stock_movement (id),
    CONSTRAINT uq_flowora_stock_movement_number UNIQUE (organization_id, number),
    CONSTRAINT uq_flowora_stock_movement_request UNIQUE (organization_id, request_id)
);

CREATE INDEX idx_flowora_stock_movement_source
    ON flowora_stock_movement (organization_id, source_type, source_id, posted_at);

CREATE TABLE flowora_stock_movement_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    movement_id VARCHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    from_warehouse_id VARCHAR(36) NULL,
    from_location_id VARCHAR(36) NULL,
    to_warehouse_id VARCHAR(36) NULL,
    to_location_id VARCHAR(36) NULL,
    lot_id VARCHAR(36) NULL,
    serial_id VARCHAR(36) NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    value_amount DECIMAL(19, 4) NOT NULL,
    source_line_type VARCHAR(64) NULL,
    source_line_id VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_stock_movement_line_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_stock_movement_line_movement FOREIGN KEY (movement_id) REFERENCES flowora_stock_movement (id),
    CONSTRAINT fk_flowora_stock_movement_line_lot FOREIGN KEY (lot_id) REFERENCES flowora_inventory_lot (id),
    CONSTRAINT fk_flowora_stock_movement_line_serial FOREIGN KEY (serial_id) REFERENCES flowora_inventory_serial (id),
    CONSTRAINT uq_flowora_stock_movement_line_sequence UNIQUE (movement_id, sequence_no),
    CONSTRAINT ck_flowora_stock_movement_line_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_flowora_stock_movement_line_trace
    ON flowora_stock_movement_line (organization_id, item_id, lot_id, serial_id, created_at);

CREATE TABLE flowora_stock_reservation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    sales_order_id VARCHAR(36) NOT NULL,
    sales_order_line_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    location_id VARCHAR(36) NOT NULL DEFAULT '',
    item_id VARCHAR(36) NOT NULL,
    lot_id VARCHAR(36) NOT NULL DEFAULT '',
    serial_id VARCHAR(36) NOT NULL DEFAULT '',
    reserved_quantity DECIMAL(19, 4) NOT NULL,
    consumed_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_stock_reservation_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_stock_reservation_order FOREIGN KEY (sales_order_id) REFERENCES flowora_sales_order (id),
    CONSTRAINT fk_flowora_stock_reservation_order_line FOREIGN KEY (sales_order_line_id) REFERENCES flowora_sales_order_line (id),
    CONSTRAINT ck_flowora_stock_reservation_quantity CHECK (reserved_quantity > 0 AND consumed_quantity >= 0 AND consumed_quantity <= reserved_quantity)
);

CREATE INDEX idx_flowora_stock_reservation_active
    ON flowora_stock_reservation (organization_id, warehouse_id, item_id, status, expires_at);

CREATE TABLE flowora_stock_freeze (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    location_id VARCHAR(36) NOT NULL DEFAULT '',
    item_id VARCHAR(36) NULL,
    lot_id VARCHAR(36) NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(36) NOT NULL,
    released_by VARCHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP NULL,
    CONSTRAINT fk_flowora_stock_freeze_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id)
);

ALTER TABLE flowora_stock_count
    ADD COLUMN location_id VARCHAR(36) NOT NULL DEFAULT '' AFTER warehouse_id,
    ADD COLUMN actor_user_id VARCHAR(36) NULL AFTER counted_by,
    ADD COLUMN request_id VARCHAR(128) NULL AFTER actor_user_id,
    ADD CONSTRAINT uq_flowora_stock_count_request UNIQUE (organization_id, request_id);

ALTER TABLE flowora_stock_count_line
    ADD COLUMN lot_id VARCHAR(36) NOT NULL DEFAULT '' AFTER item_id,
    ADD COLUMN serial_id VARCHAR(36) NOT NULL DEFAULT '' AFTER lot_id,
    ADD COLUMN difference_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER counted_quantity,
    ADD CONSTRAINT uq_flowora_stock_count_dimension UNIQUE (stock_count_id, item_id, lot_id, serial_id),
    ADD CONSTRAINT ck_flowora_stock_count_nonnegative CHECK (expected_quantity >= 0 AND counted_quantity >= 0);

CREATE INDEX idx_flowora_stock_count_scope ON flowora_stock_count (organization_id, warehouse_id, counted_at);

CREATE TABLE flowora_trade_source_line_link (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    source_document_type VARCHAR(64) NOT NULL,
    source_document_id VARCHAR(64) NOT NULL,
    source_line_id VARCHAR(64) NOT NULL,
    target_document_type VARCHAR(64) NOT NULL,
    target_document_id VARCHAR(64) NOT NULL,
    target_line_id VARCHAR(64) NOT NULL,
    linked_quantity DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_trade_source_link_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_trade_source_link UNIQUE (organization_id, source_line_id, target_line_id)
);

CREATE TABLE flowora_sales_return (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(40) NOT NULL,
    sales_order_id VARCHAR(36) NOT NULL,
    delivery_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    disposition VARCHAR(24) NOT NULL,
    actor_user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_sales_return_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_sales_return_order FOREIGN KEY (sales_order_id) REFERENCES flowora_sales_order (id),
    CONSTRAINT fk_flowora_sales_return_delivery FOREIGN KEY (delivery_id) REFERENCES flowora_sales_delivery (id),
    CONSTRAINT uq_flowora_sales_return_number UNIQUE (organization_id, number)
);

CREATE TABLE flowora_sales_return_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    sales_return_id VARCHAR(36) NOT NULL,
    delivery_line_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_sales_return_line_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_sales_return_line_return FOREIGN KEY (sales_return_id) REFERENCES flowora_sales_return (id)
);

CREATE TABLE flowora_purchase_return (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(40) NOT NULL,
    purchase_order_id VARCHAR(36) NOT NULL,
    purchase_receipt_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    actor_user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_purchase_return_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_purchase_return_order FOREIGN KEY (purchase_order_id) REFERENCES flowora_purchase_order (id),
    CONSTRAINT fk_flowora_purchase_return_receipt FOREIGN KEY (purchase_receipt_id) REFERENCES flowora_purchase_receipt (id),
    CONSTRAINT uq_flowora_purchase_return_number UNIQUE (organization_id, number)
);

CREATE TABLE flowora_purchase_return_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    purchase_return_id VARCHAR(36) NOT NULL,
    purchase_receipt_line_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_purchase_return_line_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_purchase_return_line_return FOREIGN KEY (purchase_return_id) REFERENCES flowora_purchase_return (id)
);

CREATE TABLE flowora_financial_source_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_FINANCE',
    payload_json JSON NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_financial_source_event_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_financial_source_event UNIQUE (organization_id, event_type, source_type, source_id)
);

ALTER TABLE flowora_sales_order_line
    ADD COLUMN reserved_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER fulfilled_quantity,
    ADD COLUMN cancelled_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER reserved_quantity,
    ADD COLUMN returned_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER cancelled_quantity,
    ADD COLUMN net_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER tax_rate,
    ADD COLUMN tax_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER net_amount,
    ADD COLUMN gross_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER tax_amount;

ALTER TABLE flowora_sales_order
    ADD COLUMN request_id VARCHAR(128) NULL AFTER sales_user_id,
    ADD CONSTRAINT uq_flowora_sales_order_request UNIQUE (organization_id, request_id);

ALTER TABLE flowora_purchase_order
    ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'CNY' AFTER status,
    ADD COLUMN total_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER currency_code,
    ADD COLUMN request_id VARCHAR(128) NULL AFTER buyer_user_id,
    ADD CONSTRAINT uq_flowora_purchase_order_request UNIQUE (organization_id, request_id);

ALTER TABLE flowora_purchase_order_line
    ADD COLUMN rejected_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER received_quantity,
    ADD COLUMN returned_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER rejected_quantity,
    ADD COLUMN discount_rate DECIMAL(9, 4) NOT NULL DEFAULT 0 AFTER unit_price,
    ADD COLUMN net_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER tax_rate,
    ADD COLUMN tax_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER net_amount,
    ADD COLUMN gross_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER tax_amount;

ALTER TABLE flowora_purchase_receipt_line
    ADD COLUMN location_id VARCHAR(36) NULL AFTER item_id,
    ADD COLUMN lot_id VARCHAR(36) NULL AFTER location_id,
    ADD COLUMN serial_id VARCHAR(36) NULL AFTER lot_id,
    ADD COLUMN accepted_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER quantity,
    ADD COLUMN rejected_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER accepted_quantity,
    ADD COLUMN returned_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER rejected_quantity;

ALTER TABLE flowora_sales_delivery_line
    ADD COLUMN location_id VARCHAR(36) NULL AFTER item_id,
    ADD COLUMN lot_id VARCHAR(36) NULL AFTER location_id,
    ADD COLUMN serial_id VARCHAR(36) NULL AFTER lot_id,
    ADD COLUMN returned_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER quantity;

INSERT INTO flowora_permission (code, resource_code, action_code, description, `sensitive`) VALUES
    ('inventory:reserve', 'inventory', 'reserve', 'Reserve and allocate available stock', FALSE),
    ('inventory:trace', 'inventory', 'trace', 'Trace lots, serials and stock movements', FALSE),
    ('inventory:freeze', 'inventory', 'freeze', 'Freeze and release inventory dimensions', TRUE),
    ('inventory:return', 'inventory', 'return', 'Post sales and purchase inventory returns', TRUE),
    ('trade:financial-source', 'trade', 'financial-source', 'View pending finance source events', TRUE);

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code FROM flowora_role role
CROSS JOIN flowora_permission permission
WHERE role.code = 'ADMIN' AND permission.code IN (
    'inventory:reserve', 'inventory:trace', 'inventory:freeze', 'inventory:return', 'trade:financial-source'
);

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code FROM flowora_role role
JOIN flowora_permission permission ON permission.code IN ('inventory:reserve', 'inventory:trace', 'inventory:return')
WHERE role.code IN ('WAREHOUSE', 'MANAGEMENT');

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code FROM flowora_role role
JOIN flowora_permission permission ON permission.code = 'trade:financial-source'
WHERE role.code IN ('FINANCE', 'MANAGEMENT');
