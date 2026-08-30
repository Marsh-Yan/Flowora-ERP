CREATE TABLE flowora_workflow_template (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    current_version_id VARCHAR(36) NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_workflow_template_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_workflow_template_code UNIQUE (organization_id, code)
);

CREATE INDEX idx_flowora_workflow_template_match
    ON flowora_workflow_template (organization_id, resource_type, status, priority);

CREATE TABLE flowora_workflow_version (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    template_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    condition_json JSON NOT NULL,
    definition_json JSON NOT NULL,
    allow_self_approval BOOLEAN NOT NULL DEFAULT FALSE,
    published_by VARCHAR(36) NULL,
    published_at TIMESTAMP NULL,
    retired_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_workflow_version_template FOREIGN KEY (template_id)
        REFERENCES flowora_workflow_template (id),
    CONSTRAINT uq_flowora_workflow_version_number UNIQUE (template_id, version_number)
);

ALTER TABLE flowora_workflow_template
    ADD CONSTRAINT fk_flowora_workflow_template_current_version FOREIGN KEY (current_version_id)
        REFERENCES flowora_workflow_version (id);

CREATE TABLE flowora_workflow_step_definition (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    workflow_version_id VARCHAR(36) NOT NULL,
    step_key VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    sequence_no INT NOT NULL,
    completion_mode VARCHAR(24) NOT NULL DEFAULT 'SERIAL',
    approver_type VARCHAR(32) NOT NULL,
    approver_ref VARCHAR(96) NULL,
    due_hours INT NULL,
    allowed_actions_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_step_definition_version FOREIGN KEY (workflow_version_id)
        REFERENCES flowora_workflow_version (id),
    CONSTRAINT uq_flowora_step_definition_key UNIQUE (workflow_version_id, step_key),
    CONSTRAINT uq_flowora_step_definition_sequence UNIQUE (workflow_version_id, sequence_no)
);

CREATE TABLE flowora_workflow_transition_definition (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    workflow_version_id VARCHAR(36) NOT NULL,
    from_step_key VARCHAR(64) NULL,
    to_step_key VARCHAR(64) NULL,
    outcome VARCHAR(24) NOT NULL,
    condition_json JSON NOT NULL,
    sequence_no INT NOT NULL,
    CONSTRAINT fk_flowora_transition_definition_version FOREIGN KEY (workflow_version_id)
        REFERENCES flowora_workflow_version (id)
);

CREATE TABLE flowora_workflow_instance (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    template_id VARCHAR(36) NOT NULL,
    workflow_version_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    resource_revision INT NOT NULL DEFAULT 1,
    requester_user_id VARCHAR(36) NOT NULL,
    owner_user_id VARCHAR(36) NULL,
    status VARCHAR(24) NOT NULL,
    current_sequence_no INT NULL,
    snapshot_json JSON NOT NULL,
    first_action_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_workflow_instance_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_workflow_instance_template FOREIGN KEY (template_id)
        REFERENCES flowora_workflow_template (id),
    CONSTRAINT fk_flowora_workflow_instance_version FOREIGN KEY (workflow_version_id)
        REFERENCES flowora_workflow_version (id),
    CONSTRAINT uq_flowora_workflow_instance_revision UNIQUE (
        organization_id, resource_type, resource_id, resource_revision
    )
);

CREATE INDEX idx_flowora_workflow_instance_resource
    ON flowora_workflow_instance (organization_id, resource_type, resource_id, created_at);

CREATE TABLE flowora_workflow_step_instance (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    workflow_instance_id VARCHAR(36) NOT NULL,
    step_definition_id VARCHAR(36) NOT NULL,
    step_key VARCHAR(64) NOT NULL,
    sequence_no INT NOT NULL,
    completion_mode VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    due_at TIMESTAMP NULL,
    activated_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_step_instance_workflow FOREIGN KEY (workflow_instance_id)
        REFERENCES flowora_workflow_instance (id),
    CONSTRAINT fk_flowora_step_instance_definition FOREIGN KEY (step_definition_id)
        REFERENCES flowora_workflow_step_definition (id),
    CONSTRAINT uq_flowora_step_instance_sequence UNIQUE (workflow_instance_id, sequence_no)
);

CREATE TABLE flowora_workflow_approval_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    workflow_instance_id VARCHAR(36) NOT NULL,
    step_instance_id VARCHAR(36) NOT NULL,
    original_approver_user_id VARCHAR(36) NOT NULL,
    assignee_user_id VARCHAR(36) NOT NULL,
    actual_actor_user_id VARCHAR(36) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    decision_comment VARCHAR(1000) NULL,
    due_at TIMESTAMP NULL,
    sla_notified_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_approval_task_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_approval_task_workflow FOREIGN KEY (workflow_instance_id)
        REFERENCES flowora_workflow_instance (id),
    CONSTRAINT fk_flowora_approval_task_step FOREIGN KEY (step_instance_id)
        REFERENCES flowora_workflow_step_instance (id),
    CONSTRAINT uq_flowora_approval_task_approver UNIQUE (step_instance_id, original_approver_user_id)
);

CREATE INDEX idx_flowora_approval_task_inbox
    ON flowora_workflow_approval_task (organization_id, assignee_user_id, status, due_at);

CREATE TABLE flowora_workflow_decision (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    workflow_instance_id VARCHAR(36) NOT NULL,
    step_instance_id VARCHAR(36) NULL,
    task_id VARCHAR(36) NULL,
    action_code VARCHAR(24) NOT NULL,
    original_approver_user_id VARCHAR(36) NULL,
    actual_actor_user_id VARCHAR(36) NOT NULL,
    comment_text VARCHAR(1000) NOT NULL,
    request_id VARCHAR(96) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_workflow_decision_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_workflow_decision_instance FOREIGN KEY (workflow_instance_id)
        REFERENCES flowora_workflow_instance (id),
    CONSTRAINT fk_flowora_workflow_decision_step FOREIGN KEY (step_instance_id)
        REFERENCES flowora_workflow_step_instance (id),
    CONSTRAINT fk_flowora_workflow_decision_task FOREIGN KEY (task_id)
        REFERENCES flowora_workflow_approval_task (id)
);

CREATE TABLE flowora_workflow_delegation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    delegator_user_id VARCHAR(36) NOT NULL,
    delegate_user_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(64) NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_workflow_delegation_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_workflow_delegation_window UNIQUE (
        organization_id, delegator_user_id, delegate_user_id, resource_type, starts_at
    )
);

CREATE INDEX idx_flowora_workflow_delegation_active
    ON flowora_workflow_delegation (organization_id, delegator_user_id, active, starts_at, ends_at);

CREATE TABLE flowora_mention (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    comment_id VARCHAR(36) NOT NULL,
    mentioned_user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_mention_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_mention_comment FOREIGN KEY (comment_id)
        REFERENCES flowora_comment (id),
    CONSTRAINT uq_flowora_mention_user UNIQUE (comment_id, mentioned_user_id)
);

CREATE TABLE flowora_attachment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    uploader_user_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    linked_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_attachment_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id),
    CONSTRAINT uq_flowora_attachment_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_flowora_attachment_resource
    ON flowora_attachment (organization_id, resource_type, resource_id, status, created_at);

ALTER TABLE flowora_notification
    ADD COLUMN event_type VARCHAR(64) NULL AFTER type,
    ADD COLUMN resource_type VARCHAR(64) NULL AFTER message,
    ADD COLUMN resource_id VARCHAR(64) NULL AFTER resource_type,
    ADD COLUMN outbox_event_id VARCHAR(36) NULL AFTER resource_id;

ALTER TABLE flowora_notification
    ADD CONSTRAINT uq_flowora_notification_outbox UNIQUE (outbox_event_id);

CREATE TABLE flowora_outbox_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    recipient_user_id VARCHAR(36) NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    last_error VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_outbox_organization FOREIGN KEY (organization_id)
        REFERENCES flowora_organization (id)
);

CREATE INDEX idx_flowora_outbox_dispatch

    ON flowora_outbox_event (status, available_at, created_at);

CREATE TABLE flowora_delivery_attempt (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    outbox_event_id VARCHAR(36) NOT NULL,
    attempt_number INT NOT NULL,
    channel VARCHAR(24) NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_delivery_attempt_outbox FOREIGN KEY (outbox_event_id)
        REFERENCES flowora_outbox_event (id),
    CONSTRAINT uq_flowora_delivery_attempt UNIQUE (outbox_event_id, channel, attempt_number)
);

INSERT INTO flowora_permission (code, resource_code, action_code, description, `sensitive`) VALUES
    ('workflow:submit', 'workflow', 'submit', 'Submit resources to workflow', FALSE),
    ('workflow:configure', 'workflow', 'configure', 'Configure and publish workflow templates', TRUE),
    ('workflow:delegate', 'workflow', 'delegate', 'Configure approval delegation', TRUE),
    ('workflow:admin', 'workflow', 'admin', 'Terminate workflows and replay delivery events', TRUE),
    ('collaboration:comment', 'collaboration', 'comment', 'Comment on authorized resources', FALSE),
    ('attachment:view', 'attachment', 'view', 'View attachments on authorized resources', FALSE),
    ('attachment:upload', 'attachment', 'upload', 'Upload attachments to authorized resources', FALSE);

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code
FROM flowora_role role
CROSS JOIN flowora_permission permission
WHERE role.code = 'ADMIN'
  AND permission.code IN (
      'workflow:submit', 'workflow:configure', 'workflow:delegate', 'workflow:admin',
      'collaboration:comment', 'attachment:view', 'attachment:upload'
  );

INSERT INTO flowora_role_permission (role_id, permission_code)
SELECT role.id, permission.code
FROM flowora_role role
JOIN flowora_permission permission ON permission.code IN (
    'workflow:submit', 'workflow:delegate', 'collaboration:comment',
    'attachment:view', 'attachment:upload'
)
WHERE role.code IN ('BUSINESS', 'WAREHOUSE', 'FINANCE', 'PROJECT_MANAGER', 'MANAGEMENT');

ALTER TABLE flowora_purchase_request
    ADD COLUMN workflow_instance_id VARCHAR(36) NULL AFTER submitted_at,
    ADD CONSTRAINT fk_flowora_purchase_request_workflow FOREIGN KEY (workflow_instance_id)
        REFERENCES flowora_workflow_instance (id);

ALTER TABLE flowora_sales_quote
    ADD COLUMN workflow_instance_id VARCHAR(36) NULL AFTER workflow_task_id,
    ADD CONSTRAINT fk_flowora_sales_quote_workflow FOREIGN KEY (workflow_instance_id)
        REFERENCES flowora_workflow_instance (id);
