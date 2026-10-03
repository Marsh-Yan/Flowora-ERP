-- Preserve immutable create fingerprints. Historical orders remain untouched.
ALTER TABLE flowora_sales_order ADD COLUMN request_fingerprint CHAR(64) NULL;
ALTER TABLE flowora_purchase_order ADD COLUMN request_fingerprint CHAR(64) NULL;
