-- This script is intentionally deterministic. The demo profile runs it while holding
-- the flowora_demo_control row lock, so reset and startup seeding are repeatable.
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM flowora_idempotency_record WHERE organization_id = 'org-demo';
DELETE FROM flowora_audit_event WHERE organization_id = 'org-demo';
DELETE FROM flowora_activity_event WHERE organization_id = 'org-demo';
DELETE FROM flowora_comment WHERE organization_id = 'org-demo';
DELETE FROM flowora_notification WHERE organization_id = 'org-demo';
DELETE FROM flowora_workflow_task WHERE organization_id = 'org-demo';
DELETE FROM flowora_project_budget WHERE organization_id = 'org-demo';
DELETE FROM flowora_project_expense WHERE organization_id = 'org-demo';
DELETE FROM flowora_timesheet WHERE organization_id = 'org-demo';
DELETE FROM flowora_project_task WHERE organization_id = 'org-demo';
DELETE FROM flowora_project_milestone WHERE organization_id = 'org-demo';
DELETE FROM flowora_project WHERE organization_id = 'org-demo';
DELETE FROM flowora_supplier_payment WHERE organization_id = 'org-demo';
DELETE FROM flowora_payable_document WHERE organization_id = 'org-demo';
DELETE FROM flowora_journal_line WHERE organization_id = 'org-demo';
DELETE FROM flowora_journal_entry WHERE organization_id = 'org-demo';
DELETE FROM flowora_accounting_period WHERE organization_id = 'org-demo';
DELETE FROM flowora_payment WHERE organization_id = 'org-demo';
DELETE FROM flowora_receivable_document WHERE organization_id = 'org-demo';
DELETE FROM flowora_sales_delivery_line WHERE organization_id = 'org-demo';
DELETE FROM flowora_sales_delivery WHERE organization_id = 'org-demo';
DELETE FROM flowora_sales_order_line WHERE organization_id = 'org-demo';
DELETE FROM flowora_sales_order WHERE organization_id = 'org-demo';
DELETE FROM flowora_sales_quote_line WHERE organization_id = 'org-demo';
DELETE FROM flowora_sales_quote WHERE organization_id = 'org-demo';
DELETE FROM flowora_stock_ledger_entry WHERE organization_id = 'org-demo';
DELETE FROM flowora_stock_balance WHERE organization_id = 'org-demo';
DELETE FROM flowora_purchase_receipt_line WHERE organization_id = 'org-demo';
DELETE FROM flowora_purchase_receipt WHERE organization_id = 'org-demo';
DELETE FROM flowora_purchase_order_line WHERE organization_id = 'org-demo';
DELETE FROM flowora_purchase_order WHERE organization_id = 'org-demo';
DELETE FROM flowora_purchase_request_line WHERE organization_id = 'org-demo';
DELETE FROM flowora_purchase_request WHERE organization_id = 'org-demo';
DELETE FROM flowora_account WHERE organization_id = 'org-demo';
DELETE FROM flowora_tax_rate WHERE organization_id = 'org-demo';
DELETE FROM flowora_exchange_rate WHERE organization_id = 'org-demo';
DELETE FROM flowora_currency WHERE organization_id = 'org-demo';
DELETE FROM flowora_item WHERE organization_id = 'org-demo';
DELETE FROM flowora_warehouse WHERE organization_id = 'org-demo';
DELETE FROM flowora_customer WHERE organization_id = 'org-demo';
DELETE FROM flowora_supplier WHERE organization_id = 'org-demo';
DELETE FROM flowora_user_role WHERE user_id IN (SELECT id FROM flowora_user_account WHERE organization_id = 'org-demo');
DELETE FROM flowora_role WHERE organization_id = 'org-demo';
DELETE FROM flowora_user_account WHERE organization_id = 'org-demo';

UPDATE flowora_organization
SET name = 'Demo Organization', base_currency_code = 'USD', timezone = 'UTC',
    approval_threshold = 10000.0000, default_tax_rate = 0.0000, active = TRUE
WHERE id = 'org-demo';

INSERT INTO flowora_role (id, organization_id, code, name) VALUES
    ('role-demo-admin', 'org-demo', 'ADMIN', 'Administrator'),
    ('role-demo-business', 'org-demo', 'BUSINESS', 'Business operator'),
    ('role-demo-warehouse', 'org-demo', 'WAREHOUSE', 'Warehouse operator'),
    ('role-demo-finance', 'org-demo', 'FINANCE', 'Finance operator'),
    ('role-demo-project', 'org-demo', 'PROJECT_MANAGER', 'Project manager'),
    ('role-demo-management', 'org-demo', 'MANAGEMENT', 'Management approver');

INSERT INTO flowora_user_account (id, organization_id, username, display_name, password_hash, active) VALUES
    ('user-demo-admin', 'org-demo', 'admin@demo.flowora', 'Demo Administrator', 'demo-profile-only', TRUE),
    ('user-demo-operator', 'org-demo', 'operator@demo.flowora', 'Demo Operator', 'demo-profile-only', TRUE),
    ('user-demo-warehouse', 'org-demo', 'warehouse@demo.flowora', 'Demo Warehouse', 'demo-profile-only', TRUE),
    ('user-demo-finance', 'org-demo', 'finance@demo.flowora', 'Demo Finance', 'demo-profile-only', TRUE),
    ('user-demo-project', 'org-demo', 'project@demo.flowora', 'Demo Project Manager', 'demo-profile-only', TRUE),
    ('user-demo-manager', 'org-demo', 'manager@demo.flowora', 'Demo Manager', 'demo-profile-only', TRUE);

INSERT INTO flowora_user_role (user_id, role_id) VALUES
    ('user-demo-admin', 'role-demo-admin'),
    ('user-demo-operator', 'role-demo-business'),
    ('user-demo-warehouse', 'role-demo-warehouse'),
    ('user-demo-finance', 'role-demo-finance'),
    ('user-demo-project', 'role-demo-project'),
    ('user-demo-manager', 'role-demo-management');

INSERT INTO flowora_currency (id, organization_id, code, name, symbol, decimal_places, active) VALUES
    ('currency-demo-usd', 'org-demo', 'USD', 'US Dollar', '$', 2, TRUE),
    ('currency-demo-cny', 'org-demo', 'CNY', 'Chinese Yuan', '¥', 2, TRUE),
    ('currency-demo-eur', 'org-demo', 'EUR', 'Euro', '€', 2, TRUE);

INSERT INTO flowora_exchange_rate (id, organization_id, base_currency_code, quote_currency_code, rate, effective_date, active) VALUES
    ('rate-demo-usd-cny', 'org-demo', 'USD', 'CNY', 7.18000000, '2026-08-01', TRUE),
    ('rate-demo-usd-eur', 'org-demo', 'USD', 'EUR', 0.86000000, '2026-08-01', TRUE);

INSERT INTO flowora_tax_rate (id, organization_id, code, name, rate, exempt, effective_date, active) VALUES
    ('tax-demo-zero', 'org-demo', 'TAX-0', 'Zero rate / exempt', 0.0000, TRUE, '2026-08-01', TRUE),
    ('tax-demo-standard', 'org-demo', 'TAX-13', 'Standard VAT', 13.0000, FALSE, '2026-08-01', TRUE);

INSERT INTO flowora_account (id, organization_id, code, name, account_type, parent_code, posting_allowed, active) VALUES
    ('account-demo-cash', 'org-demo', '1000', 'Cash and bank', 'ASSET', NULL, TRUE, TRUE),
    ('account-demo-receivable', 'org-demo', '1100', 'Accounts receivable', 'ASSET', NULL, TRUE, TRUE),
    ('account-demo-inventory', 'org-demo', '1400', 'Inventory assets', 'ASSET', NULL, TRUE, TRUE),
    ('account-demo-payable', 'org-demo', '2000', 'Accounts payable', 'LIABILITY', NULL, TRUE, TRUE),
    ('account-demo-revenue', 'org-demo', '4000', 'Operating revenue', 'REVENUE', NULL, TRUE, TRUE),
    ('account-demo-expense', 'org-demo', '5000', 'Operating expense', 'EXPENSE', NULL, TRUE, TRUE);

INSERT INTO flowora_customer (id, organization_id, code, name, contact_name, email, phone, address, currency_code, payment_terms_days, active) VALUES
    ('customer-demo-001', 'org-demo', 'CUST-001', 'Acme Retail Group', 'Ava Chen', 'ava@acme.example', '+86-21-6000-1001', 'Shanghai', 'USD', 30, TRUE),
    ('customer-demo-002', 'org-demo', 'CUST-002', 'Northwind Labs', 'Noah Smith', 'noah@northwind.example', '+1-206-600-1002', 'Seattle', 'USD', 45, TRUE),
    ('customer-demo-003', 'org-demo', 'CUST-003', 'Blue Harbor Services', 'Mia Wang', 'mia@blueharbor.example', '+86-20-6000-1003', 'Guangzhou', 'CNY', 30, TRUE),
    ('customer-demo-004', 'org-demo', 'CUST-004', 'Orchid Foods', 'Leo Zhang', 'leo@orchid.example', '+86-10-6000-1004', 'Beijing', 'CNY', 15, TRUE),
    ('customer-demo-005', 'org-demo', 'CUST-005', 'Summit Mobility', 'Emma Brown', 'emma@summit.example', '+1-415-600-1005', 'San Francisco', 'USD', 30, TRUE),
    ('customer-demo-006', 'org-demo', 'CUST-006', 'Delta Education', 'Liam Wilson', 'liam@deltaedu.example', '+44-20-6000-1006', 'London', 'EUR', 30, TRUE),
    ('customer-demo-007', 'org-demo', 'CUST-007', 'Maple Creative', 'Olivia Lee', 'olivia@maple.example', '+1-416-600-1007', 'Toronto', 'USD', 20, TRUE),
    ('customer-demo-008', 'org-demo', 'CUST-008', 'Vertex Manufacturing', 'Ethan Miller', 'ethan@vertex.example', '+49-30-6000-1008', 'Berlin', 'EUR', 60, TRUE);

INSERT INTO flowora_supplier (id, organization_id, code, name, contact_name, email, phone, address, currency_code, payment_terms_days, active) VALUES
    ('supplier-demo-001', 'org-demo', 'SUP-001', 'Evergreen Components', 'Grace Liu', 'grace@evergreen.example', '+86-21-7000-2001', 'Shanghai', 'USD', 30, TRUE),
    ('supplier-demo-002', 'org-demo', 'SUP-002', 'Atlas Office Supply', 'James Taylor', 'james@atlas.example', '+1-312-700-2002', 'Chicago', 'USD', 30, TRUE),
    ('supplier-demo-003', 'org-demo', 'SUP-003', 'Cedar Cloud Hosting', 'Sophia Martin', 'sophia@cedar.example', '+44-20-7000-2003', 'London', 'EUR', 15, TRUE),
    ('supplier-demo-004', 'org-demo', 'SUP-004', 'Pacific Logistics', 'Lucas Wang', 'lucas@pacific.example', '+86-20-7000-2004', 'Guangzhou', 'CNY', 30, TRUE),
    ('supplier-demo-005', 'org-demo', 'SUP-005', 'Beacon Consulting', 'Amelia Davis', 'amelia@beacon.example', '+1-212-700-2005', 'New York', 'USD', 45, TRUE),
    ('supplier-demo-006', 'org-demo', 'SUP-006', 'EuroTech Tools', 'Henry Muller', 'henry@eurotech.example', '+49-30-7000-2006', 'Berlin', 'EUR', 60, TRUE);

INSERT INTO flowora_item (id, organization_id, code, name, item_type, unit, sales_price, purchase_price, average_cost, tax_rate, inventory_managed, active) VALUES
    ('item-demo-001', 'org-demo', 'SKU-001', 'Wireless keyboard', 'GOODS', 'pcs', 120.0000, 40.0000, 40.0000, 13.0000, TRUE, TRUE),
    ('item-demo-002', 'org-demo', 'SKU-002', 'Wireless mouse', 'GOODS', 'pcs', 60.0000, 20.0000, 20.0000, 13.0000, TRUE, TRUE),
    ('item-demo-003', 'org-demo', 'SKU-003', 'USB-C dock', 'GOODS', 'pcs', 180.0000, 70.0000, 70.0000, 13.0000, TRUE, TRUE),
    ('item-demo-004', 'org-demo', 'SKU-004', '27-inch monitor', 'GOODS', 'pcs', 360.0000, 180.0000, 180.0000, 13.0000, TRUE, TRUE),
    ('item-demo-005', 'org-demo', 'SKU-005', 'Laptop stand', 'GOODS', 'pcs', 85.0000, 30.0000, 30.0000, 13.0000, TRUE, TRUE),
    ('item-demo-006', 'org-demo', 'SKU-006', 'Webcam', 'GOODS', 'pcs', 95.0000, 38.0000, 38.0000, 13.0000, TRUE, TRUE),
    ('item-demo-007', 'org-demo', 'SKU-007', 'Headset', 'GOODS', 'pcs', 110.0000, 45.0000, 45.0000, 13.0000, TRUE, TRUE),
    ('item-demo-008', 'org-demo', 'SKU-008', 'Desk lamp', 'GOODS', 'pcs', 45.0000, 16.0000, 16.0000, 13.0000, TRUE, TRUE),
    ('item-demo-009', 'org-demo', 'SKU-009', 'Power strip', 'GOODS', 'pcs', 35.0000, 12.0000, 12.0000, 13.0000, TRUE, TRUE),
    ('item-demo-010', 'org-demo', 'SKU-010', 'HDMI cable', 'GOODS', 'pcs', 22.0000, 8.0000, 8.0000, 13.0000, TRUE, TRUE),
    ('item-demo-011', 'org-demo', 'SKU-011', 'Notebook', 'GOODS', 'pcs', 12.0000, 4.0000, 4.0000, 13.0000, TRUE, TRUE),
    ('item-demo-012', 'org-demo', 'SKU-012', 'Ergonomic chair', 'GOODS', 'pcs', 420.0000, 240.0000, 240.0000, 13.0000, TRUE, TRUE),
    ('item-demo-013', 'org-demo', 'SKU-013', 'Standing desk', 'GOODS', 'pcs', 680.0000, 380.0000, 380.0000, 13.0000, TRUE, TRUE),
    ('item-demo-014', 'org-demo', 'SKU-014', 'Conference speaker', 'GOODS', 'pcs', 260.0000, 120.0000, 120.0000, 13.0000, TRUE, TRUE),
    ('item-demo-015', 'org-demo', 'SKU-015', 'Barcode scanner', 'GOODS', 'pcs', 210.0000, 90.0000, 90.0000, 13.0000, TRUE, TRUE),
    ('item-demo-016', 'org-demo', 'SKU-016', 'Packing box', 'GOODS', 'pcs', 8.0000, 3.0000, 3.0000, 13.0000, TRUE, TRUE),
    ('item-demo-017', 'org-demo', 'SVC-001', 'Implementation service', 'SERVICE', 'hour', 150.0000, 0.0000, 0.0000, 0.0000, FALSE, TRUE),
    ('item-demo-018', 'org-demo', 'SVC-002', 'Training workshop', 'SERVICE', 'hour', 180.0000, 0.0000, 0.0000, 0.0000, FALSE, TRUE),
    ('item-demo-019', 'org-demo', 'SVC-003', 'Data migration service', 'SERVICE', 'hour', 200.0000, 0.0000, 0.0000, 0.0000, FALSE, TRUE),
    ('item-demo-020', 'org-demo', 'SVC-004', 'Support subscription', 'SERVICE', 'month', 500.0000, 0.0000, 0.0000, 0.0000, FALSE, TRUE);

INSERT INTO flowora_warehouse (id, organization_id, code, name, address, active) VALUES
    ('warehouse-demo-001', 'org-demo', 'WH-SH', 'Shanghai Main Warehouse', 'Shanghai / A-01', TRUE),
    ('warehouse-demo-002', 'org-demo', 'WH-SZ', 'Shenzhen Transit Warehouse', 'Shenzhen / B-02', TRUE),
    ('warehouse-demo-003', 'org-demo', 'WH-EU', 'European Demo Warehouse', 'Berlin / C-03', TRUE);

INSERT INTO flowora_purchase_request (id, organization_id, number, supplier_id, warehouse_id, requester_user_id, status, note, submitted_at) VALUES
    ('purchase-request-demo-1', 'org-demo', 'PR-DEMO-001', 'supplier-demo-001', 'warehouse-demo-001', 'user-demo-operator', 'APPROVED', 'Procurement loop seed request', '2026-08-01 09:00:00'),
    ('purchase-request-demo-2', 'org-demo', 'PR-DEMO-002', 'supplier-demo-002', 'warehouse-demo-002', 'user-demo-operator', 'SUBMITTED', 'Pending approval example', '2026-08-04 10:00:00');

INSERT INTO flowora_purchase_request_line (id, organization_id, purchase_request_id, item_id, quantity, estimated_unit_cost) VALUES
    ('purchase-request-line-demo-1', 'org-demo', 'purchase-request-demo-1', 'item-demo-001', 20.0000, 40.0000),
    ('purchase-request-line-demo-2', 'org-demo', 'purchase-request-demo-2', 'item-demo-002', 30.0000, 20.0000);

INSERT INTO flowora_purchase_order (id, organization_id, number, purchase_request_id, supplier_id, warehouse_id, buyer_user_id, status, order_date, expected_date, note) VALUES
    ('purchase-order-demo-1', 'org-demo', 'PO-DEMO-001', 'purchase-request-demo-1', 'supplier-demo-001', 'warehouse-demo-001', 'user-demo-operator', 'RECEIVED', '2026-08-01', '2026-08-05', 'Complete procurement loop'),
    ('purchase-order-demo-2', 'org-demo', 'PO-DEMO-002', NULL, 'supplier-demo-002', 'warehouse-demo-002', 'user-demo-operator', 'APPROVED', '2026-08-05', '2026-08-15', 'Approved but not received');

INSERT INTO flowora_purchase_order_line (id, organization_id, purchase_order_id, item_id, ordered_quantity, received_quantity, unit_price, tax_rate) VALUES
    ('purchase-order-line-demo-1', 'org-demo', 'purchase-order-demo-1', 'item-demo-001', 20.0000, 20.0000, 40.0000, 13.0000),
    ('purchase-order-line-demo-2', 'org-demo', 'purchase-order-demo-2', 'item-demo-002', 30.0000, 0.0000, 20.0000, 13.0000);

INSERT INTO flowora_purchase_receipt (id, organization_id, number, purchase_order_id, warehouse_id, received_by, received_at, status) VALUES
    ('purchase-receipt-demo-1', 'org-demo', 'GR-DEMO-001', 'purchase-order-demo-1', 'warehouse-demo-001', 'user-demo-warehouse', '2026-08-05 14:00:00', 'POSTED');

INSERT INTO flowora_purchase_receipt_line (id, organization_id, purchase_receipt_id, purchase_order_line_id, item_id, quantity, unit_cost) VALUES
    ('purchase-receipt-line-demo-1', 'org-demo', 'purchase-receipt-demo-1', 'purchase-order-line-demo-1', 'item-demo-001', 20.0000, 40.0000);

INSERT INTO flowora_stock_balance (id, organization_id, warehouse_id, item_id, quantity, average_cost) VALUES
    ('stock-balance-demo-1', 'org-demo', 'warehouse-demo-001', 'item-demo-001', 16.0000, 40.0000);

INSERT INTO flowora_stock_ledger_entry (id, organization_id, warehouse_id, item_id, movement_type, document_type, document_id, quantity_delta, unit_cost, value_delta, balance_quantity, balance_value, actor_user_id) VALUES
    ('stock-ledger-demo-receipt', 'org-demo', 'warehouse-demo-001', 'item-demo-001', 'RECEIPT', 'PURCHASE_RECEIPT', 'purchase-receipt-demo-1', 20.0000, 40.0000, 800.0000, 20.0000, 800.0000, 'user-demo-warehouse'),
    ('stock-ledger-demo-shipment', 'org-demo', 'warehouse-demo-001', 'item-demo-001', 'SHIPMENT', 'SALES_DELIVERY', 'sales-delivery-demo-1', -4.0000, 40.0000, -160.0000, 16.0000, 640.0000, 'user-demo-warehouse');

INSERT INTO flowora_sales_quote (id, organization_id, number, customer_id, status, currency_code, valid_until, total_amount, note, requester_user_id, approved_at, converted_at) VALUES
    ('sales-quote-demo-1', 'org-demo', 'QT-DEMO-001', 'customer-demo-001', 'CONVERTED', 'USD', '2026-08-31', 1200.0000, 'Complete sales loop', 'user-demo-operator', '2026-08-02 10:00:00', '2026-08-03 11:00:00'),
    ('sales-quote-demo-2', 'org-demo', 'QT-DEMO-002', 'customer-demo-002', 'APPROVED', 'USD', '2026-09-15', 900.0000, 'Approved quote example', 'user-demo-operator', '2026-08-06 10:00:00', NULL),
    ('sales-quote-demo-3', 'org-demo', 'QT-DEMO-003', 'customer-demo-003', 'DRAFT', 'CNY', '2026-09-30', 3000.0000, 'Draft quote example', 'user-demo-operator', NULL, NULL);

INSERT INTO flowora_sales_quote_line (id, organization_id, quote_id, item_id, quantity, unit_price, discount_rate, tax_rate) VALUES
    ('sales-quote-line-demo-1', 'org-demo', 'sales-quote-demo-1', 'item-demo-001', 10.0000, 120.0000, 0.0000, 13.0000),
    ('sales-quote-line-demo-2', 'org-demo', 'sales-quote-demo-2', 'item-demo-003', 5.0000, 180.0000, 0.0000, 13.0000),
    ('sales-quote-line-demo-3', 'org-demo', 'sales-quote-demo-3', 'item-demo-017', 10.0000, 300.0000, 0.0000, 0.0000);

INSERT INTO flowora_sales_order (id, organization_id, number, quote_id, customer_id, warehouse_id, status, currency_code, order_date, due_date, total_amount, note, sales_user_id) VALUES
    ('sales-order-demo-1', 'org-demo', 'SO-DEMO-001', 'sales-quote-demo-1', 'customer-demo-001', 'warehouse-demo-001', 'PARTIALLY_FULFILLED', 'USD', '2026-08-03', '2026-09-02', 1200.0000, 'Complete sales and project loop', 'user-demo-operator'),
    ('sales-order-demo-2', 'org-demo', 'SO-DEMO-002', 'sales-quote-demo-2', 'customer-demo-002', 'warehouse-demo-002', 'CONFIRMED', 'USD', '2026-08-06', '2026-09-20', 900.0000, 'Confirmed order example', 'user-demo-operator');

INSERT INTO flowora_sales_order_line (id, organization_id, sales_order_id, item_id, ordered_quantity, fulfilled_quantity, unit_price, discount_rate, tax_rate) VALUES
    ('sales-order-line-demo-1', 'org-demo', 'sales-order-demo-1', 'item-demo-001', 10.0000, 4.0000, 120.0000, 0.0000, 13.0000),
    ('sales-order-line-demo-2', 'org-demo', 'sales-order-demo-2', 'item-demo-003', 5.0000, 0.0000, 180.0000, 0.0000, 13.0000);

INSERT INTO flowora_sales_delivery (id, organization_id, number, sales_order_id, warehouse_id, status, actor_user_id, posted_at) VALUES
    ('sales-delivery-demo-1', 'org-demo', 'DO-DEMO-001', 'sales-order-demo-1', 'warehouse-demo-001', 'POSTED', 'user-demo-warehouse', '2026-08-07 15:00:00');

INSERT INTO flowora_sales_delivery_line (id, organization_id, delivery_id, sales_order_line_id, item_id, quantity, unit_cost) VALUES
    ('sales-delivery-line-demo-1', 'org-demo', 'sales-delivery-demo-1', 'sales-order-line-demo-1', 'item-demo-001', 4.0000, 40.0000);

INSERT INTO flowora_receivable_document (id, organization_id, number, sales_order_id, customer_id, source_type, source_id, currency_code, total_amount, paid_amount, status, due_date) VALUES
    ('receivable-demo-1', 'org-demo', 'AR-DEMO-001', 'sales-order-demo-1', 'customer-demo-001', 'SALES_ORDER', 'sales-order-demo-1', 'USD', 1200.0000, 400.0000, 'PARTIALLY_SETTLED', '2026-09-02');

INSERT INTO flowora_payment (id, organization_id, number, receivable_id, customer_id, amount, currency_code, payment_method, payment_date, reference, actor_user_id) VALUES
    ('payment-demo-1', 'org-demo', 'CP-DEMO-001', 'receivable-demo-1', 'customer-demo-001', 400.0000, 'USD', 'BANK', '2026-08-08', 'DEMO-RECEIPT-001', 'user-demo-finance');

INSERT INTO flowora_payable_document (id, organization_id, number, purchase_receipt_id, supplier_id, source_type, source_id, currency_code, total_amount, paid_amount, status, due_date) VALUES
    ('payable-demo-1', 'org-demo', 'AP-DEMO-001', 'purchase-receipt-demo-1', 'supplier-demo-001', 'PURCHASE_RECEIPT', 'purchase-receipt-demo-1', 'USD', 800.0000, 300.0000, 'PARTIALLY_SETTLED', '2026-09-04');

INSERT INTO flowora_supplier_payment (id, organization_id, number, payable_id, supplier_id, amount, currency_code, payment_method, payment_date, reference, actor_user_id) VALUES
    ('supplier-payment-demo-1', 'org-demo', 'SP-DEMO-001', 'payable-demo-1', 'supplier-demo-001', 300.0000, 'USD', 'BANK', '2026-08-08', 'DEMO-PAYMENT-001', 'user-demo-finance');

INSERT INTO flowora_accounting_period (id, organization_id, year, month, start_date, end_date, status) VALUES
    ('period-demo-2026-08', 'org-demo', 2026, 8, '2026-08-01', '2026-08-31', 'OPEN');

INSERT INTO flowora_journal_entry (id, organization_id, number, period_id, entry_date, source_type, source_id, memo, currency_code, total_debit, total_credit, status) VALUES
    ('journal-demo-purchase', 'org-demo', 'JE-DEMO-001', 'period-demo-2026-08', '2026-08-05', 'PURCHASE_RECEIPT', 'purchase-receipt-demo-1', 'Purchase receipt demo posting', 'USD', 800.0000, 800.0000, 'POSTED'),
    ('journal-demo-sales-order', 'org-demo', 'JE-DEMO-002', 'period-demo-2026-08', '2026-08-03', 'SALES_ORDER', 'sales-order-demo-1', 'Sales order demo posting', 'USD', 1200.0000, 1200.0000, 'POSTED'),
    ('journal-demo-delivery', 'org-demo', 'JE-DEMO-003', 'period-demo-2026-08', '2026-08-07', 'SALES_DELIVERY', 'sales-delivery-demo-1', 'Cost of sales demo posting', 'USD', 160.0000, 160.0000, 'POSTED'),
    ('journal-demo-customer-payment', 'org-demo', 'JE-DEMO-004', 'period-demo-2026-08', '2026-08-08', 'CUSTOMER_PAYMENT', 'payment-demo-1', 'Customer payment demo posting', 'USD', 400.0000, 400.0000, 'POSTED'),
    ('journal-demo-supplier-payment', 'org-demo', 'JE-DEMO-005', 'period-demo-2026-08', '2026-08-08', 'SUPPLIER_PAYMENT', 'supplier-payment-demo-1', 'Supplier payment demo posting', 'USD', 300.0000, 300.0000, 'POSTED');

INSERT INTO flowora_journal_line (id, organization_id, journal_entry_id, line_no, account_code, description, debit, credit, currency_code) VALUES
    ('journal-line-demo-001', 'org-demo', 'journal-demo-purchase', 1, '1400', 'Inventory received', 800.0000, 0.0000, 'USD'),
    ('journal-line-demo-002', 'org-demo', 'journal-demo-purchase', 2, '2000', 'Accrued payable', 0.0000, 800.0000, 'USD'),
    ('journal-line-demo-003', 'org-demo', 'journal-demo-sales-order', 1, '1100', 'Customer receivable', 1200.0000, 0.0000, 'USD'),
    ('journal-line-demo-004', 'org-demo', 'journal-demo-sales-order', 2, '4000', 'Sales revenue', 0.0000, 1200.0000, 'USD'),
    ('journal-line-demo-005', 'org-demo', 'journal-demo-delivery', 1, '5000', 'Cost of sales', 160.0000, 0.0000, 'USD'),
    ('journal-line-demo-006', 'org-demo', 'journal-demo-delivery', 2, '1400', 'Inventory issued', 0.0000, 160.0000, 'USD'),
    ('journal-line-demo-007', 'org-demo', 'journal-demo-customer-payment', 1, '1000', 'Cash received', 400.0000, 0.0000, 'USD'),
    ('journal-line-demo-008', 'org-demo', 'journal-demo-customer-payment', 2, '1100', 'Receivable settlement', 0.0000, 400.0000, 'USD'),
    ('journal-line-demo-009', 'org-demo', 'journal-demo-supplier-payment', 1, '2000', 'Payable settlement', 300.0000, 0.0000, 'USD'),
    ('journal-line-demo-010', 'org-demo', 'journal-demo-supplier-payment', 2, '1000', 'Cash paid', 0.0000, 300.0000, 'USD');

INSERT INTO flowora_project (id, organization_id, number, name, description, customer_id, sales_order_id, manager_user_id, target_date, budget_revenue, budget_cost, currency_code, status) VALUES
    ('project-demo-1', 'org-demo', 'PROJ-DEMO-001', 'Acme rollout project', 'Demo project linked to the sales order and billable delivery work.', 'customer-demo-001', 'sales-order-demo-1', 'user-demo-project', '2026-09-30', 1800.0000, 900.0000, 'USD', 'ACTIVE');

INSERT INTO flowora_project_milestone (id, organization_id, project_id, name, sequence_no, target_date, status) VALUES
    ('milestone-demo-1', 'org-demo', 'project-demo-1', 'Discovery and setup', 1, '2026-08-15', 'COMPLETED'),
    ('milestone-demo-2', 'org-demo', 'project-demo-1', 'User rollout', 2, '2026-09-15', 'IN_PROGRESS');

INSERT INTO flowora_project_task (id, organization_id, project_id, milestone_id, title, description, assignee_user_id, priority, due_date, status, estimated_hours) VALUES
    ('project-task-demo-1', 'org-demo', 'project-demo-1', 'milestone-demo-1', 'Confirm requirements', 'Requirements sign-off for the rollout.', 'user-demo-project', 'HIGH', '2026-08-10', 'DONE', 8.0000),
    ('project-task-demo-2', 'org-demo', 'project-demo-1', 'milestone-demo-2', 'Configure workspace', 'Configure the customer workspace and roles.', 'user-demo-project', 'MEDIUM', '2026-08-20', 'IN_PROGRESS', 24.0000),
    ('project-task-demo-3', 'org-demo', 'project-demo-1', 'milestone-demo-2', 'Deliver training', 'Run the first user training workshop.', 'user-demo-operator', 'MEDIUM', '2026-08-28', 'TODO', 16.0000);

INSERT INTO flowora_timesheet (id, organization_id, project_id, task_id, user_id, work_date, hours, cost_rate, billing_rate, cost_amount, billable_amount, billable, currency_code, note) VALUES
    ('timesheet-demo-1', 'org-demo', 'project-demo-1', 'project-task-demo-1', 'user-demo-project', '2026-08-06', 8.0000, 60.0000, 150.0000, 480.0000, 1200.0000, TRUE, 'USD', 'Discovery workshop');

INSERT INTO flowora_project_expense (id, organization_id, project_id, task_id, user_id, expense_date, category, amount, billable_amount, billable, currency_code, description) VALUES
    ('expense-demo-1', 'org-demo', 'project-demo-1', 'project-task-demo-2', 'user-demo-project', '2026-08-07', 'Travel', 120.0000, 120.0000, TRUE, 'USD', 'Customer site visit');

INSERT INTO flowora_project_budget (id, organization_id, project_id, category, amount, currency_code, note) VALUES
    ('budget-demo-1', 'org-demo', 'project-demo-1', 'Implementation', 900.0000, 'USD', 'Demo project delivery budget');

INSERT INTO flowora_workflow_task (id, organization_id, resource_type, resource_id, title, description, amount, requester_user_id, assignee_user_id, assignee_role, status, due_at) VALUES
    ('workflow-demo-1', 'org-demo', 'PROJECT', 'project-demo-1', 'Review rollout progress', 'Review project delivery progress before the next milestone.', 0.0000, 'user-demo-project', 'user-demo-manager', NULL, 'OPEN', '2026-08-18 09:00:00');

INSERT INTO flowora_activity_event (id, organization_id, resource_type, resource_id, actor_user_id, action_code, summary, details_json) VALUES
    ('activity-demo-1', 'org-demo', 'SALES_ORDER', 'sales-order-demo-1', 'user-demo-operator', 'DEMO_SEEDED', 'Sales order seeded for the demo loop', '{"source":"phase-09"}'),
    ('activity-demo-2', 'org-demo', 'PROJECT', 'project-demo-1', 'user-demo-project', 'DEMO_SEEDED', 'Project seeded for the demo loop', '{"source":"phase-09"}');

SET FOREIGN_KEY_CHECKS = 1;
