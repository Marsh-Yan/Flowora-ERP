-- Keep historical refunds unchanged: they were restricted to the source rate.
ALTER TABLE flowora_payment_v2
    ADD COLUMN refund_source_base_amount DECIMAL(19,4) NULL,
    ADD COLUMN refund_exchange_journal_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_refund_exchange_journal FOREIGN KEY (refund_exchange_journal_id) REFERENCES flowora_journal_entry(id);
ALTER TABLE flowora_refund_correction
    ADD COLUMN exchange_journal_entry_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_refund_correction_exchange FOREIGN KEY (exchange_journal_entry_id) REFERENCES flowora_journal_entry(id);
