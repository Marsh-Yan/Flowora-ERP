# Security and Privacy Guidelines

This repository is intended for public GitHub hosting. Never commit real personal or sensitive information.

## Never commit

- Real names, email addresses, phone numbers, addresses, or personal identifiers.
- Passwords, tokens, API keys, private keys, cookies, or reusable credentials.
- Internal domains, private IP addresses, customer records, supplier records, or company secrets.
- Real database connection strings or production configuration.
- Screenshots, logs, exports, or test snapshots containing any of the above.

## Safe defaults

- Use Demo Organization, Demo User, and example.com values.
- Keep secrets in local environment files or GitHub Actions Secrets.
- Commit only empty or clearly unusable environment variable examples.
- Inspect git diff before every commit.
- Rotate credentials immediately if a leak is suspected.
## Phase 09 hardening

- Session cookies use `HttpOnly` and `SameSite=Lax`; set `FLOWORA_COOKIE_SECURE=true` whenever the API is served over HTTPS.
- CORS remains allow-list based through `flowora.cors.allowed-origins`.
- Database demo reset requires explicit `flowora.demo.enabled=true` and the `ADMIN` role. Use it only with a disposable `local`/`demo` database; production must leave it disabled.
- Organization-scoped queries and the demo reset script preserve the organization boundary.
- Error responses expose a stable code and request ID, but no stack trace or credential material.

## Identity and data-scope contract

- Both `/api/v1/auth/login` and `/api/v2/session/login` require an MFA code when the account has an enabled factor. No business session is created before MFA succeeds.
- MFA replacement requires the current factor. A new factor remains pending for ten minutes; cancellation or failed confirmation leaves the current factor active.
- Login reservations are released only after the indexed session store confirms the completed login; ID rotation and logout do not rely on the reservation TTL. In-flight logins still occupy capacity; see [session reservations](session-reservations.md).
- Local and production profiles use indexed Redis sessions. A fourth concurrent login is rejected; password changes, resets, and account disablement revoke existing sessions. Session-store failures prevent password changes from reporting a successful revocation.
- Accounts marked `mustChangePassword` may use only session identity, password change, CSRF, and logout endpoints until they change the password and sign in again.
- Organization administrators may create a child organization of their current organization and receive an administrator membership there. Organization lists show only active memberships, and archiving requires the current organization's administrator role.
- Compatibility reads and global search enforce module permissions. Sales, purchasing, and project lists apply data scope in the database query and page count; corresponding details also check scope. `ASSIGNED` order reads return no rows until an assignment policy exists.
- Async exports recheck the requester's permissions and scope at execution and download. Non-`ALL` exports currently support sales and purchasing; other resources are denied until a row-level scope policy is defined. Existing exports lacking a recorded scope cannot be downloaded.

## Executable verification and remaining gates

The isolated CI job runs [security-smoke.ps1](../../tools/verification/security-smoke.ps1) against real MySQL/Redis sessions. SEC-01–SEC-12 cover forced password change, active sessions and limits, password history and revocation, both MFA login paths, pending replacement cancellation, single-use recovery, account disable, module permissions, organization membership, attachment bytes/type/scope, administrator reset, CORS and SELF exports with permission revocation. It uses fresh synthetic accounts and disables them on exit.

The isolated CI job also runs [session-reservation-smoke.ps1](../../tools/verification/session-reservation-smoke.ps1): SESSION-01–08 cover immediate rotation/logout, real organization switches, v1 logout, actual three-session capacity, eight simultaneous logins, reauthentication, failed credentials and password-change revocation.

These checks do not replace a complete role/data-scope/concurrency matrix or release dependency/image scans. The [2026-10-01 dependency audit](dependency-audit-2026-10-01.md) currently fails; see [coverage and remaining gates](audit-verification.md). Production profile smoke is not an approval to publish or mutate a business database.
