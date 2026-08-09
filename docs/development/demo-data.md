# Demo data and quality hardening

## Demo profile

The default `standalone` profile is intentionally database-free and does not expose demo reset APIs. Use the `demo` profile with MySQL and Redis:

```powershell
$env:DB_USERNAME = 'your-local-user'
$env:DB_PASSWORD = 'your-local-password'
$env:FLOWORA_DEMO_SEED_ON_START = 'true'
java -jar services/api/target/flowora-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

The `demo` profile activates the existing `local` database profile and seeds `org-demo` after Flyway has completed. The seed is deterministic: restarting with `FLOWORA_DEMO_SEED_ON_START=true` replaces the demo dataset with the same known state. Set it to `false` when the database should be preserved across restarts.

## Seeded business scope

The seed contains six role accounts documented in [demo-accounts.md](./demo-accounts.md), eight customers, six suppliers, twenty goods/services, three warehouses, multiple document statuses, and three end-to-end examples:

- procurement request → purchase order → receipt → stock ledger → payable → supplier payment;
- sales quote → sales order → partial delivery → stock ledger → receivable → customer payment;
- sales order → project → milestone/tasks → billable timesheet and expense → project budget.

The database stores demo-profile user rows only to satisfy audit relationships. Authentication remains the in-memory demo account store; `password_hash` values in the seed are placeholders and are not production credentials.

## Reset and status API

When `flowora.demo.enabled=true`, administrators can call:

```text
GET  /api/v1/demo/status
POST /api/v1/demo/reset
```

The web Administration page exposes the same reset action to `ADMIN` users and requires confirmation. Every reset records a `DEMO_DATA_RESET` audit event. A row lock on `flowora_demo_control` serializes concurrent resets. The reset script deletes only `org-demo` records and never deletes another organization.

## Idempotency and error behavior

Inventory ledger entries are unique by organization, source document and movement type. Journal entries, payables and their source documents use the same source uniqueness rule. Write endpoints for purchase receipts and sales deliveries also accept an `Idempotency-Key`; reusing the key returns HTTP 409 without creating another document, stock movement or journal entry. Replaying an already-posted automatic posting is a no-op, and the service tests cover the no-op path.

State transition errors are returned as HTTP 409 with a stable `STATE_CONFLICT` code and the request ID. Validation remains HTTP 400, authorization remains HTTP 403, and all API errors retain `X-Request-Id` correlation.
