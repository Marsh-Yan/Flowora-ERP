# Flowora ERP 2.0 M2 workflow and collaboration

## Scope

M2 replaces fixed approval routing with an organization-scoped, versioned workflow runtime. The v1 workflow API remains readable for compatibility; v1 writes are blocked and compatibility writes use `/api/v2/compat/workflow`; new development uses `/api/v2/workflows` and `/api/v2/collaboration`.

## Compatibility workflow authorization (R5-E, 2026-10-02)

The compatibility task model remains separate from v2 approval instances. Creating a task requires `workflow:submit` and access to its linked resource under the current organization and data scope. GENERAL resources require `workflow:view`. A fixed ADMIN/BUSINESS/FINANCE/WAREHOUSE/PROJECT role name does not replace submit permission.

| Compatibility action | Required permission | Task relationship |
| --- | --- | --- |
| APPROVE / REJECT | `workflow:approve` | Current user or role assignee, or actual `workflow:admin` permission |
| TRANSFER | `workflow:delegate` | Current user or role assignee, or actual `workflow:admin` permission |
| COMPLETE | `workflow:submit` | Current user or role assignee, or actual `workflow:admin` permission |
| CANCEL | Requester with `workflow:submit`, or `workflow:admin` | Requester or actual administrator |

`workflow:admin` alone permits cancellation; it does not implicitly grant approval, delegation or completion. Existing status preconditions and organization-scoped task lookup still apply. Action permission checks are in the shared compatibility service, including its inventory and quote approval callers. Business module write permissions remain independent.

Compatibility HTTP calls reload the current identity; role changes apply to an existing session. Bundled action comments require `collaboration:comment` and linked resource access before task mutation. Default standalone exposes diagnostics only; local/production enforce database resource and assignee eligibility policies. Marking notifications read also requires `workflow:view` and recipient ownership.

Default demo roles retain their catalog permissions. Custom roles must explicitly receive the required permissions; this change never adds permissions or changes assignments automatically. The v2 engine has its own action model and is not changed by this compatibility patch.

Repeatable checks: `CompatibilityWorkflowAuthorizationTest` (real method-security proxies), `WorkflowServiceTest` (no mutation or persistence on denial), and [workflow-compat-smoke.ps1](../../tools/verification/workflow-compat-smoke.ps1) (ten isolated HTTP scenarios, including custom roles, assignment, live revocation, bundled comments and cross-organization task IDs). CI archives sanitized scenario results. R5-H additionally validates explicit creation and transfer targets against current membership, workflow capabilities and linked resource scope; see [assignee eligibility](workflow-assignees.md) and the eighteen-scenario [HTTP regression](../../tools/verification/workflow-transfer-smoke.ps1). Full browser coverage, concurrent task actions/revocation, default role-pool candidates, all v2 action/scope combinations, existing-task source scope changes, and legacy business-controller identity refresh remain separate verification work; these results do not close F27.

## Runtime model

- A template identifies a business resource type and match priority.
- Each draft version stores a condition group and an ordered step definition.
- Publishing retires the previous published version. Published definitions are immutable.
- An instance stores the selected version ID and a complete JSON snapshot. Later template changes cannot alter a running or historical instance.
- A step creates approval tasks resolved from user, role, department manager, document owner, or requester manager rules.
- Decisions append original approver, actual actor, action, comment and request ID.

Supported task actions are `APPROVE`, `REJECT`, `RETURN`, and `TRANSFER`. Instance actions are requester `WITHDRAW` before the first decision and administrator `TERMINATE`.

Every task mutation requires the current task `version` in the `If-Match` header. A stale value returns `OPTIMISTIC_LOCK_CONFLICT`.

## Conditions and approvers

Conditions are declarative; executable expressions are not accepted. Allowed fields are:

- `amount`, `baseAmount`, `currency`, `departmentId`
- `customerRisk`, `supplierRisk`, `discount`
- `resourceType`, `ownerUserId`, `documentStatus`

Operators are `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`, and `CONTAINS`. Step sequences must be unique and contiguous from one.

Delegations require an active delegate in the same organization, a bounded time window, and no cycle in an overlapping active chain. The task records the original approver separately from the delegated assignee and actual actor.

## SLA and reliable notifications

The scheduler scans open overdue tasks every `flowora.workflow.sla-scan-delay-ms`. Assignment, decision, mention and overdue events enter the transactional outbox. Delivery:

1. claims pending records using row locks;
2. creates the notification with a unique outbox event ID;
3. records every attempt;
4. retries with backoff;
5. moves the record to `DEAD` after five failures;
6. permits administrator replay without duplicate notification side effects.

The local dispatcher delay is configured by `flowora.workflow.outbox-delay-ms`.

## Collaboration and attachments

Comments, activities, mentions and attachments inherit both organization scope and the underlying business resource view permission. Mentions only target active members and do not grant resource access.

The local attachment store follows a staged-write-link flow:

1. validate non-empty size, configured maximum, MIME allowlist and dangerous extension denylist;
2. copy to a private staging path while calculating SHA-256;
3. insert a `STAGED` row;
4. move to its opaque organization/UUID storage key;
5. mark it `LINKED`;
6. remove staged/final files when the database transaction rolls back.

There is no public storage URL. Every download rechecks the current organization, attachment permission, underlying resource permission and resource existence. Responses use attachment disposition and `X-Content-Type-Options: nosniff`.

Configuration:

```yaml
flowora:
  attachment:
    root: .flowora/attachments
    max-bytes: 20971520
    allowed-types: application/pdf,image/png,image/jpeg,text/plain,text/csv
```

## Permissions

| Permission | Purpose |
| --- | --- |
| `workflow:view` | View templates, tasks, instances and decisions |
| `workflow:submit` | Start and withdraw own workflow |
| `workflow:approve` | Decide assigned approval tasks |
| `workflow:configure` | Create, version, publish and retire templates |
| `workflow:delegate` | Manage own delegation windows |
| `workflow:admin` | Terminate instances and replay dead events |
| `collaboration:comment` | Comment and mention on authorized resources |
| `attachment:view` | List and download authorized attachments |
| `attachment:upload` | Upload attachments to authorized resources |

## Integration sequence

For a business resource:

1. persist the resource and its revision;
2. call `POST /api/v2/workflows/instances` with resource type, ID, revision, amount, currency and match fields;
3. keep the returned instance ID as the approval reference;
4. use the instance status, not a client-maintained flag, as the approval authority;
5. render decisions and activities from their append-only APIs;
6. use collaboration endpoints with the same resource type and ID.

Purchase requests and sales quotes are the first supported integration resource types; the engine is resource-neutral and also validates the remaining registered ERP resource types.

## Verification

See [workspace and outbox operations](workspace-operations.md) for current task-count semantics, dispatch transactions, dead-letter metrics and mandatory regression checks.

- `mvn -pl services/api -am test`
- `npm --prefix apps/web run typecheck`
- `npm --prefix apps/web run build`
- run Flyway V10-V12 against an empty database and a backed-up v1 sample database
- exercise publish/version isolation, serial and parallel decisions, delegation cycle rejection, stale `If-Match`, overdue notification, dead-letter replay, mention revocation and protected attachment download
