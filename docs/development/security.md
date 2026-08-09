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
- Demo reset is available only in the explicitly enabled `demo` profile and is protected by the `ADMIN` role.
- Organization-scoped queries and the demo reset script preserve the organization boundary.
- Error responses expose a stable code and request ID, but no stack trace or credential material.
