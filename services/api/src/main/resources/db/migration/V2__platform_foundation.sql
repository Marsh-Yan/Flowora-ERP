CREATE TABLE IF NOT EXISTS flowora_organization (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    base_currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    approval_threshold DECIMAL(19, 4) NOT NULL DEFAULT 10000.0000,
    default_tax_rate DECIMAL(9, 4) NOT NULL DEFAULT 0.0000,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS flowora_user_account (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    username VARCHAR(190) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_user_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_user_organization_username UNIQUE (organization_id, username)
);

CREATE TABLE IF NOT EXISTS flowora_role (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_role_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_role_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS flowora_user_role (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_flowora_user_role_user FOREIGN KEY (user_id)
        REFERENCES flowora_user_account (id),
    CONSTRAINT fk_flowora_user_role_role FOREIGN KEY (role_id)
        REFERENCES flowora_role (id)
);

CREATE TABLE IF NOT EXISTS flowora_audit_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(36) NULL,
    action_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(96) NOT NULL,
    resource_id VARCHAR(36) NULL,
    request_id VARCHAR(96) NOT NULL,
    details_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_audit_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES flowora_user_account (id)
);

CREATE INDEX idx_flowora_audit_organization_created
    ON flowora_audit_event (organization_id, created_at);
