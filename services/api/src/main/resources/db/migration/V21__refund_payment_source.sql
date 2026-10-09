-- Existing refunds remain historical; do not guess their source or delete them.
ALTER TABLE flowora_payment_v2
    ADD COLUMN original_payment_id VARCHAR(36) NULL,
    ADD INDEX idx_payment_refund_source (organization_id, original_payment_id, status);
