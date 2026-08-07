CREATE TABLE IF NOT EXISTS flowora_sales_quote (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    customer_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    valid_until DATE NOT NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    note VARCHAR(500) NULL,
    requester_user_id VARCHAR(64) NOT NULL,
    workflow_task_id VARCHAR(36) NULL,
    approved_at TIMESTAMP NULL,
    converted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_sales_quote_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);
CREATE INDEX idx_flowora_sales_quote_search ON flowora_sales_quote (organization_id, status, created_at);

CREATE TABLE IF NOT EXISTS flowora_sales_quote_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    quote_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    discount_rate DECIMAL(9, 4) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(9, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_sales_quote_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_sales_quote_line_quote FOREIGN KEY (quote_id)
        REFERENCES flowora_sales_quote (id)
);
CREATE INDEX idx_flowora_sales_quote_line_quote ON flowora_sales_quote_line (organization_id, quote_id);

CREATE TABLE IF NOT EXISTS flowora_sales_order (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    quote_id VARCHAR(36) NULL,
    customer_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    order_date DATE NOT NULL,
    due_date DATE NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    note VARCHAR(500) NULL,
    sales_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_sales_order_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_sales_order_quote FOREIGN KEY (quote_id)
        REFERENCES flowora_sales_quote (id)
);
CREATE INDEX idx_flowora_sales_order_search ON flowora_sales_order (organization_id, status, order_date);

CREATE TABLE IF NOT EXISTS flowora_sales_order_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    sales_order_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    ordered_quantity DECIMAL(19, 4) NOT NULL,
    fulfilled_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0,
    unit_price DECIMAL(19, 4) NOT NULL,
    discount_rate DECIMAL(9, 4) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(9, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_sales_order_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_sales_order_line_order FOREIGN KEY (sales_order_id)
        REFERENCES flowora_sales_order (id)
);
CREATE INDEX idx_flowora_sales_order_line_order ON flowora_sales_order_line (organization_id, sales_order_id);

CREATE TABLE IF NOT EXISTS flowora_sales_delivery (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    sales_order_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    posted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_sales_delivery_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_sales_delivery_order FOREIGN KEY (sales_order_id)
        REFERENCES flowora_sales_order (id)
);
CREATE INDEX idx_flowora_sales_delivery_search ON flowora_sales_delivery (organization_id, sales_order_id, posted_at);

CREATE TABLE IF NOT EXISTS flowora_sales_delivery_line (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    delivery_id VARCHAR(36) NOT NULL,
    sales_order_line_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_sales_delivery_line_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_sales_delivery_line_delivery FOREIGN KEY (delivery_id)
        REFERENCES flowora_sales_delivery (id),
    CONSTRAINT fk_flowora_sales_delivery_line_order_line FOREIGN KEY (sales_order_line_id)
        REFERENCES flowora_sales_order_line (id)
);
CREATE INDEX idx_flowora_sales_delivery_line_delivery ON flowora_sales_delivery_line (organization_id, delivery_id);

CREATE TABLE IF NOT EXISTS flowora_receivable_document (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    sales_order_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
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
    CONSTRAINT fk_flowora_receivable_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_receivable_order FOREIGN KEY (sales_order_id)
        REFERENCES flowora_sales_order (id)
);
CREATE INDEX idx_flowora_receivable_search ON flowora_receivable_document (organization_id, status, due_date);

CREATE TABLE IF NOT EXISTS flowora_payment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    receivable_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    payment_method VARCHAR(16) NOT NULL,
    payment_date DATE NOT NULL,
    reference VARCHAR(120) NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_payment_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_payment_receivable FOREIGN KEY (receivable_id)
        REFERENCES flowora_receivable_document (id)
);
CREATE INDEX idx_flowora_payment_receivable ON flowora_payment (organization_id, receivable_id, payment_date);
