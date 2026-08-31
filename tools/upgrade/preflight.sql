SELECT 'schema_version' AS check_name, COALESCE(MAX(CAST(version AS UNSIGNED)),0) AS check_value
FROM flyway_schema_history WHERE success=TRUE;
SELECT 'legacy_receivables' AS check_name, COUNT(*) AS check_value FROM flowora_receivable_document;
SELECT 'legacy_payables' AS check_name, COUNT(*) AS check_value FROM flowora_payable_document;
SELECT 'journal_entries' AS check_name, COUNT(*) AS check_value FROM flowora_journal_entry;
SELECT 'orphan_receivables' AS check_name, COUNT(*) AS check_value FROM flowora_receivable_document r
LEFT JOIN flowora_organization o ON o.id=r.organization_id WHERE o.id IS NULL;
SELECT 'orphan_payables' AS check_name, COUNT(*) AS check_value FROM flowora_payable_document p
LEFT JOIN flowora_organization o ON o.id=p.organization_id WHERE o.id IS NULL;
