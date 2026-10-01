# Flowora ERP Demo Accounts

These accounts are local-only demonstration identities. They are not production credentials and must not be reused outside the demo environment.

| Username | Password | Role |
| --- | --- | --- |
| `admin@demo.flowora` | `Demo123!` | Administrator |
| `operator@demo.flowora` | `Demo123!` | Business |
| `warehouse@demo.flowora` | `Demo123!` | Warehouse |
| `finance@demo.flowora` | `Demo123!` | Finance |
| `project@demo.flowora` | `Demo123!` | Project manager |
| `manager@demo.flowora` | `Demo123!` | Management |

The standalone profile uses in-memory demonstration identities and cannot verify persistent identity, MFA, session revocation or database business operations. The database-backed `local`/`demo` profiles authenticate persisted, encoded user accounts. Demo seed/reset must be explicitly enabled only in a disposable database; `production` does not seed these users.

These are initial fixture passwords, not a promise that an existing database still accepts them. Password changes, resets and MFA configuration change the fixture state. Never reset a business database to recover a demo login. See [demo data](demo-data.md), [security](security.md) and [current verification](release-verification.md).
