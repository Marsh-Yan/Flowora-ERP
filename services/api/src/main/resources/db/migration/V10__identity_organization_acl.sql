ALTER TABLE flowora_organization
    ADD COLUMN parent_id VARCHAR(36) NULL AFTER id,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER name,
    ADD COLUMN fiscal_year_start_month INT NOT NULL DEFAULT 1 AFTER timezone,
    ADD COLUMN amount_scale INT NOT NULL DEFAULT 2 AFTER fiscal_year_start_month,
    ADD COLUMN price_scale INT NOT NULL DEFAULT 4 AFTER amount_scale,
    ADD COLUMN quantity_scale INT NOT NULL DEFAULT 4 AFTER price_scale,
    ADD COLUMN tax_rounding_mode VARCHAR(24) NOT NULL DEFAULT 'HALF_UP' AFTER quantity_scale,
    ADD COLUMN reservation_ttl_minutes INT NOT NULL DEFAULT 1440 AFTER default_tax_rate,
    ADD COLUMN expiry_warning_days INT NOT NULL DEFAULT 30 AFTER reservation_ttl_minutes,
    ADD COLUMN default_approval_policy VARCHAR(24) NOT NULL DEFAULT 'BLOCK' AFTER expiry_warning_days,
    ADD CONSTRAINT fk_flowora_organization_parent FOREIGN KEY (parent_id)
        REFERENCES flowora_organization (id);

UPDATE flowora_organization
SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'ARCHIVED' END;

ALTER TABLE flowora_user_account
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER password_hash,
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN locked_until TIMESTAMP NULL AFTER failed_login_count,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER locked_until,
    ADD COLUMN password_changed_at TIMESTAMP NULL AFTER must_change_password,
    ADD COLUMN last_failed_login_at TIMESTAMP NULL AFTER last_login_at,
    ADD CONSTRAINT uq_flowora_user_username UNIQUE (username);

UPDATE flowora_user_account
SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'DISABLED' END;

ALTER TABLE flowora_role
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN data_scope VARCHAR(24) NOT NULL DEFAULT 'ALL' AFTER description,
    ADD COLUMN system_role BOOLEAN NOT NULL DEFAULT FALSE AFTER data_scope,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER system_role,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

CREATE TABLE flowora_department (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36) NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    manager_membership_id VARCHAR(36) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_department_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_department_parent FOREIGN KEY (parent_id)
        REFERENCES flowora_department (id),
    CONSTRAINT uq_flowora_department_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE flowora_organization_membership (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    department_id VARCHAR(36) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    default_organization BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_membership_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_membership_user FOREIGN KEY (user_id)
        REFERENCES flowora_user_account (id),
    CONSTRAINT fk_flowora_membership_department FOREIGN KEY (department_id)
        REFERENCES flowora_department (id),
    CONSTRAINT uq_flowora_membership_organization_user UNIQUE (organization_id, user_id)
);

ALTER TABLE flowora_department
    ADD CONSTRAINT fk_flowora_department_manager FOREIGN KEY (manager_membership_id)
        REFERENCES flowora_organization_membership (id);

CREATE TABLE flowora_permission (
    code VARCHAR(96) NOT NULL PRIMARY KEY,
    resource_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    description VARCHAR(255) NOT NULL,
    `sensitive` BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_flowora_permission_resource_action UNIQUE (resource_code, action_code)
);

CREATE TABLE flowora_role_permission (
    role_id VARCHAR(36) NOT NULL,
    permission_code VARCHAR(96) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_flowora_role_permission_role FOREIGN KEY (role_id)
        REFERENCES flowora_role (id),
    CONSTRAINT fk_flowora_role_permission_permission FOREIGN KEY (permission_code)
        REFERENCES flowora_permission (code)
);

CREATE TABLE flowora_membership_role (
    membership_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (membership_id, role_id),
    CONSTRAINT fk_flowora_membership_role_membership FOREIGN KEY (membership_id)
        REFERENCES flowora_organization_membership (id),
    CONSTRAINT fk_flowora_membership_role_role FOREIGN KEY (role_id)
        REFERENCES flowora_role (id)
);

CREATE TABLE flowora_password_history (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_password_history_user FOREIGN KEY (user_id)
        REFERENCES flowora_user_account (id)
);

CREATE INDEX idx_flowora_password_history_user_created
    ON flowora_password_history (user_id, created_at);

CREATE TABLE flowora_user_mfa (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    factor_type VARCHAR(16) NOT NULL DEFAULT 'TOTP',
    secret_ciphertext VARCHAR(512) NOT NULL,
    recovery_codes_json JSON NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_user_mfa_user FOREIGN KEY (user_id)
        REFERENCES flowora_user_account (id),
    CONSTRAINT uq_flowora_user_mfa_factor UNIQUE (user_id, factor_type)
);

CREATE TABLE flowora_password_reset_token (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token_digest VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_password_reset_user FOREIGN KEY (user_id)
        REFERENCES flowora_user_account (id),
    CONSTRAINT uq_flowora_password_reset_digest UNIQUE (token_digest)
);

CREATE TABLE flowora_security_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NULL,
    organization_id VARCHAR(36) NULL,
    event_code VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    request_id VARCHAR(96) NOT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    details_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_security_event_user FOREIGN KEY (user_id)
        REFERENCES flowora_user_account (id),
    CONSTRAINT fk_flowora_security_event_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE INDEX idx_flowora_security_event_user_created
    ON flowora_security_event (user_id, created_at);
CREATE INDEX idx_flowora_security_event_organization_created
    ON flowora_security_event (organization_id, created_at);

INSERT INTO flowora_organization_membership (
    id, organization_id, user_id, status, default_organization
)
SELECT UUID(), organization_id, id,
       CASE WHEN active THEN 'ACTIVE' ELSE 'DISABLED' END, TRUE
FROM flowora_user_account;

INSERT INTO flowora_membership_role (membership_id, role_id)
SELECT membership.id, user_role.role_id
FROM flowora_user_role user_role
JOIN flowora_organization_membership membership ON membership.user_id = user_role.user_id;

INSERT INTO flowora_permission (code, resource_code, action_code, description, `sensitive`) VALUES
    ('organization:view', 'organization', 'view', 'View organizations and settings', FALSE),
    ('organization:configure', 'organization', 'configure', 'Configure organizations and departments', TRUE),
    ('user:view', 'user', 'view', 'View users and memberships', FALSE),
    ('user:configure', 'user', 'configure', 'Create and administer users', TRUE),
    ('role:view', 'role', 'view', 'View roles and permissions', FALSE),
    ('role:configure', 'role', 'configure', 'Configure roles and permission grants', TRUE),
    ('audit:view', 'audit', 'view', 'View security and configuration audit', TRUE),
    ('master:view', 'master', 'view', 'View master data', FALSE),
    ('master:create', 'master', 'create', 'Create master data', FALSE),
    ('master:edit', 'master', 'edit', 'Edit master data', FALSE),
    ('master:export', 'master', 'export', 'Export master data', TRUE),
    ('master:configure', 'master', 'configure', 'Configure master-data policy', TRUE),
    ('sales:view', 'sales', 'view', 'View sales data', FALSE),
    ('sales:create', 'sales', 'create', 'Create sales documents', FALSE),
    ('sales:submit', 'sales', 'submit', 'Submit sales documents', FALSE),
    ('procurement:view', 'procurement', 'view', 'View procurement data', FALSE),
    ('procurement:create', 'procurement', 'create', 'Create procurement documents', FALSE),
    ('procurement:submit', 'procurement', 'submit', 'Submit procurement documents', FALSE),
    ('inventory:view', 'inventory', 'view', 'View inventory data', FALSE),
    ('inventory:create', 'inventory', 'create', 'Create inventory operations', FALSE),
    ('inventory:post', 'inventory', 'post', 'Post inventory operations', TRUE),
    ('finance:view', 'finance', 'view', 'View finance data', FALSE),
    ('finance:create', 'finance', 'create', 'Create finance documents', FALSE),
    ('finance:post', 'finance', 'post', 'Post finance documents', TRUE),
    ('workflow:view', 'workflow', 'view', 'View workflow tasks', FALSE),
    ('workflow:approve', 'workflow', 'approve', 'Approve workflow tasks', TRUE),
    ('project:view', 'project', 'view', 'View project data', FALSE),
    ('project:create', 'project', 'create', 'Create and edit project data', FALSE);

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code
FROM flowora_role role
CROSS JOIN flowora_permission permission
WHERE role.code = 'ADMIN';

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code
FROM flowora_role role
JOIN flowora_permission permission ON permission.code IN (
    'master:view', 'master:create', 'master:edit', 'master:export',
    'sales:view', 'sales:create', 'sales:submit',
    'procurement:view', 'procurement:create', 'procurement:submit',
    'workflow:view'
)
WHERE role.code = 'BUSINESS';

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code
FROM flowora_role role
JOIN flowora_permission permission ON permission.code IN (
    'master:view', 'inventory:view', 'inventory:create', 'inventory:post', 'workflow:view'
)
WHERE role.code = 'WAREHOUSE';

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code
FROM flowora_role role
JOIN flowora_permission permission ON permission.code IN (
    'master:view', 'finance:view', 'finance:create', 'finance:post', 'workflow:view'
)
WHERE role.code = 'FINANCE';

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code
FROM flowora_role role
JOIN flowora_permission permission ON permission.code IN (
    'master:view', 'project:view', 'project:create', 'workflow:view'
)
WHERE role.code = 'PROJECT_MANAGER';

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code
FROM flowora_role role
JOIN flowora_permission permission ON permission.action_code IN ('view', 'approve')
WHERE role.code = 'MANAGEMENT';
