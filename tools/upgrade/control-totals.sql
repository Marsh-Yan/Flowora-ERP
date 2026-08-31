SELECT 'schema_version' AS check_name, COALESCE(MAX(CAST(version AS UNSIGNED)),0) AS check_value
FROM flyway_schema_history WHERE success=TRUE;
SELECT 'legacy_receivables' AS check_name, COUNT(*) AS check_value FROM flowora_receivable_document;
SELECT 'shadow_receivables' AS check_name, COUNT(*) AS check_value FROM flowora_finance_invoice
WHERE legacy_source_type='MIGRATED_LEGACY' AND document_type='SALES_INVOICE' AND read_only=TRUE;
SELECT 'legacy_payables' AS check_name, COUNT(*) AS check_value FROM flowora_payable_document;
SELECT 'shadow_payables' AS check_name, COUNT(*) AS check_value FROM flowora_finance_invoice
WHERE legacy_source_type='MIGRATED_LEGACY' AND document_type='SUPPLIER_INVOICE' AND read_only=TRUE;
SELECT 'shadow_journal_entries' AS check_name, COUNT(*) AS check_value FROM flowora_journal_entry
WHERE source_type='MIGRATED_LEGACY';
SELECT 'missing_legacy_warehouse' AS check_name, COUNT(*) AS check_value FROM flowora_organization o
LEFT JOIN flowora_warehouse w ON w.organization_id=o.id AND w.code='LEGACY-STOCK' WHERE w.id IS NULL;
