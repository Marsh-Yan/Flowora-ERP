CREATE TABLE IF NOT EXISTS flowora_project (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NULL,
    customer_id VARCHAR(36) NULL,
    sales_order_id VARCHAR(36) NULL,
    manager_user_id VARCHAR(64) NOT NULL,
    target_date DATE NOT NULL,
    budget_revenue DECIMAL(19, 4) NOT NULL DEFAULT 0,
    budget_cost DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_project_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_project_customer FOREIGN KEY (customer_id) REFERENCES flowora_customer (id),
    CONSTRAINT fk_flowora_project_sales_order FOREIGN KEY (sales_order_id) REFERENCES flowora_sales_order (id)
);
CREATE INDEX idx_flowora_project_search ON flowora_project (organization_id, status, target_date);

CREATE TABLE IF NOT EXISTS flowora_project_milestone (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    name VARCHAR(160) NOT NULL,
    sequence_no INT NOT NULL,
    target_date DATE NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_project_milestone_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_project_milestone_project FOREIGN KEY (project_id) REFERENCES flowora_project (id)
);
CREATE INDEX idx_flowora_project_milestone_project ON flowora_project_milestone (organization_id, project_id, sequence_no);

CREATE TABLE IF NOT EXISTS flowora_project_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    milestone_id VARCHAR(36) NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    assignee_user_id VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    due_date DATE NULL,
    status VARCHAR(24) NOT NULL,
    estimated_hours DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_project_task_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_project_task_project FOREIGN KEY (project_id) REFERENCES flowora_project (id),
    CONSTRAINT fk_flowora_project_task_milestone FOREIGN KEY (milestone_id) REFERENCES flowora_project_milestone (id)
);
CREATE INDEX idx_flowora_project_task_project ON flowora_project_task (organization_id, project_id, status, due_date);

CREATE TABLE IF NOT EXISTS flowora_timesheet (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36) NULL,
    user_id VARCHAR(64) NOT NULL,
    work_date DATE NOT NULL,
    hours DECIMAL(19, 4) NOT NULL,
    cost_rate DECIMAL(19, 4) NOT NULL,
    billing_rate DECIMAL(19, 4) NOT NULL,
    cost_amount DECIMAL(19, 4) NOT NULL,
    billable_amount DECIMAL(19, 4) NOT NULL,
    billable BOOLEAN NOT NULL DEFAULT FALSE,
    currency_code VARCHAR(3) NOT NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_timesheet_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_timesheet_project FOREIGN KEY (project_id) REFERENCES flowora_project (id),
    CONSTRAINT fk_flowora_timesheet_task FOREIGN KEY (task_id) REFERENCES flowora_project_task (id)
);
CREATE INDEX idx_flowora_timesheet_project ON flowora_timesheet (organization_id, project_id, work_date);

CREATE TABLE IF NOT EXISTS flowora_project_expense (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36) NULL,
    user_id VARCHAR(64) NOT NULL,
    expense_date DATE NOT NULL,
    category VARCHAR(64) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    billable_amount DECIMAL(19, 4) NOT NULL,
    billable BOOLEAN NOT NULL DEFAULT FALSE,
    currency_code VARCHAR(3) NOT NULL,
    description VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_project_expense_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_project_expense_project FOREIGN KEY (project_id) REFERENCES flowora_project (id),
    CONSTRAINT fk_flowora_project_expense_task FOREIGN KEY (task_id) REFERENCES flowora_project_task (id)
);
CREATE INDEX idx_flowora_project_expense_project ON flowora_project_expense (organization_id, project_id, expense_date);

CREATE TABLE IF NOT EXISTS flowora_project_budget (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    category VARCHAR(64) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flowora_project_budget_organization FOREIGN KEY (organization_id) REFERENCES flowora_organization (id),
    CONSTRAINT fk_flowora_project_budget_project FOREIGN KEY (project_id) REFERENCES flowora_project (id)
);
CREATE INDEX idx_flowora_project_budget_project ON flowora_project_budget (organization_id, project_id, category);
