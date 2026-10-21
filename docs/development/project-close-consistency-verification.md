# Project completion and archive consistency

## Problem and resulting behavior

The project status endpoint previously accepted completion/archive without applying the readiness shown by profitability. Readiness also omitted draft invoices, invoices linked only on lines, and completed billable milestone approvals. A draft could reserve all billing capacity and make the project appear ready while no invoice had been posted or settled.

Completion and archiving now both require finished tasks, resolved time/expense and completed-billable-milestone approvals, no draft or unpaid posted invoices, and no remaining approved billing capacity. Rejected operational approvals are resolved. Cancelled/reversed invoices do not count as unsettled; their released capacity can still block closing. Header and line project links are included once per invoice.

`ProjectLifecycleService` supplies both profitability readiness and the transactional status guard. Status changes and operational/billing writers share the existing organization finance lock, then lock the project. Close checks use current locking reads for tasks, sources, invoices, bases and active invoice reservations, including when the transaction already has an older MySQL snapshot. Failed close attempts leave project state/version and status audit unchanged. Existing state transitions remain in force.

Readiness rejects inconsistent basis amounts, states, currencies, missing approval evidence and mismatches between reserved counters and actual active draft/posted sales-invoice sources. Approved positive billable deliveries and positive fixed contracts must have their expected basis; missing evidence is not interpreted as zero outstanding work. Each positive unused basis blocks independently; amounts are not netted across bases or currencies. Historical evidence is never repaired by this check.

Completed/archived projects reject new milestones, tasks, time, expenses, budgets, membership/billing configuration, operational approvals and new ordinary project invoices, including direct finance header/line entry and posting of legacy drafts. Existing idempotent invoice replay remains readable. Existing financial correction, credit and settlement correction rules remain available; a later correction can make current readiness false without silently changing the historical project status. Repeating completion or archiving rechecks current conditions. This batch adds no reopen transition, automatic financial correction or data migration.

## Page behavior

The project status selector offers the existing valid next states and prevents duplicate pending status requests. Responses for an earlier selected project cannot replace the current selection. New-entry and task-status controls are disabled on terminal projects, and submit handlers also guard that state. Billing setup/new invoicing are disabled, while the existing finance correction entry remains available subject to its existing permissions.

The finance panel explains the close conditions and lists actual blockers. Readiness unavailable is shown as unavailable, not an affirmative result. New operational registrations and task/status changes refresh readiness; saving the status always invokes the backend check again.

## Verification on 2026-10-21

- Local isolated real MySQL `FinanceMySqlTest`: 113 passed, failures/errors/skipped zero (101 retained plus twelve). Covers task and approval blockers, completed billable milestone approval, full-capacity drafts, cancellation/released capacity, line-only supplier invoices through real posting/payment/allocation, actual settlement then completion and later allocation/invoice reversal, malformed or missing historical evidence without repair, terminal mutation/correction/replay behavior, organization scoping, an older snapshot and concurrent close versus registration.
- Project tests: 8 existing currency projection + 1 entity transition + 3 new operational write guards passed, failures/errors/skipped zero. Combined local backend invocation: 125 passed. Tests ran before packaging the actual acceptance JAR; no database skip is counted as proof.
- Web: 226 tests in 22 files passed (220 retained plus six close-state cases), lint zero warnings, type checking and production build passed. The initial new test wrapper typing failed type checking; typing was corrected and the full checks rerun. Existing chunk-size/PURE build notices remain.
- Full existing GitHub CI runs against the final PR head without skipped or weakened jobs/gates; final results are recorded in the PR.

## Ordinary browser acceptance

An independent synthetic organization/user was provisioned with master data and an open period only. The normal UI created CNY project PRJ-99B3C9AE and task `R6R delivery acceptance`. No project, task, operating or financial transactions were preloaded.

The UI moved the project to active, displayed the open-task blocker, and a normal attempt to complete it was rejected with the project still active. The task moved TODO → IN_PROGRESS → DONE; readiness automatically changed to yes. The UI then completed and archived the project. A full page refresh retained archived status and 100% progress. New operating/billing controls were disabled, and the existing financial correction entry remained available. Final read-only verification was repeated using the final packaged backend after the additional missing-evidence guard; no transactions were altered for that refresh.

Read-only SQL evidence and screenshots are retained outside public source. SQL verifies one archived project, one done task, exactly three successful project status audit events, and no operating records, bases, invoices or journals in this acceptance organization. The UI scenario proves the task/completion/archive path; invoice/approval/history/concurrency scenarios are proved by the real MySQL tests. Normal sign-out, disabling the synthetic user, archiving the organization and stopping the four identity-checked isolated services complete cleanup, retaining source history.

## Remaining scope

This check covers the existing task/readiness and billing dependencies. Planned/in-progress milestone lifecycle, explicit reopening/correction governance, historical rate/basis/journal repair, cross-currency budget valuation and broader release/performance acceptance remain separate work. It does not certify that all historical financial records are correct or rewrite closed project history.
