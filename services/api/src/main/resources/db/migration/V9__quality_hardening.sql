CREATE TABLE IF NOT EXISTS flowora_demo_control (
    organization_id VARCHAR(36) NOT NULL PRIMARY KEY,
    last_reset_at TIMESTAMP NULL,
    reset_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_demo_control_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

INSERT INTO flowora_demo_control (organization_id, reset_count)
SELECT 'org-demo', 0
WHERE NOT EXISTS (SELECT 1 FROM flowora_demo_control WHERE organization_id = 'org-demo');

CREATE TABLE IF NOT EXISTS flowora_idempotency_record (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_idempotency_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_idempotency_operation UNIQUE (organization_id, operation, operation_key)
);
