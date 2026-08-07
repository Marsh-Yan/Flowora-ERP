CREATE TABLE IF NOT EXISTS flowora_workflow_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    amount DECIMAL(19, 4) NOT NULL DEFAULT 0,
    requester_user_id VARCHAR(64) NOT NULL,
    assignee_user_id VARCHAR(64) NULL,
    assignee_role VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    due_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_workflow_task_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE INDEX idx_flowora_workflow_task_inbox
    ON flowora_workflow_task (organization_id, status, assignee_user_id, assignee_role, created_at);
CREATE INDEX idx_flowora_workflow_task_resource
    ON flowora_workflow_task (organization_id, resource_type, resource_id);

CREATE TABLE IF NOT EXISTS flowora_notification (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    recipient_user_id VARCHAR(80) NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_notification_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE INDEX idx_flowora_notification_inbox
    ON flowora_notification (organization_id, recipient_user_id, read_at, created_at);

CREATE TABLE IF NOT EXISTS flowora_comment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    author_user_id VARCHAR(64) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_comment_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE INDEX idx_flowora_comment_resource
    ON flowora_comment (organization_id, resource_type, resource_id, created_at);

CREATE TABLE IF NOT EXISTS flowora_activity_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    details_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_activity_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE INDEX idx_flowora_activity_resource
    ON flowora_activity_event (organization_id, resource_type, resource_id, created_at);
