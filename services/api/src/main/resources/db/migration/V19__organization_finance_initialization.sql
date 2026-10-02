-- Only provision organizations whose finance settings were never created.
-- Existing finance configuration and historical timestamps remain unchanged.
INSERT INTO flowora_finance_setting (organization_id, base_currency_code, fiscal_year_start_month)
SELECT org.id, org.base_currency_code, org.fiscal_year_start_month
FROM flowora_organization org
WHERE NOT EXISTS (
    SELECT 1 FROM flowora_finance_setting settings WHERE settings.organization_id = org.id
);
