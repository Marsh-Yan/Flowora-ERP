CREATE TABLE flowora_pending_mfa_enrollment (
    user_id VARCHAR(36) NOT NULL PRIMARY KEY,
    secret_ciphertext VARCHAR(512) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flowora_pending_mfa_user FOREIGN KEY (user_id)
        REFERENCES flowora_user_account (id)
);

ALTER TABLE flowora_export_job
    ADD COLUMN requested_scope VARCHAR(24) NULL,
    ADD COLUMN requested_department_id VARCHAR(36) NULL;
