# Project invoice correction and billing quota verification

Project invoice drafts and posted normal invoices reserve the approved billing basis net amount. Cancelling a draft or reversing a posted invoice releases that invoice's net reservation and recalculates basis and project billing statuses in the same transaction. Posting does not reserve the amount twice. Tax and base-currency conversions do not alter the project-currency net quota.

## Behavior and boundaries

- The project endpoint and direct finance invoice creation share one reservation implementation and organization finance lock. Project configuration and approval writers acquire the same lock before project/basis rows.
- Before creation, posting or release, current locked active invoice/source rows must reconcile with each basis counter. Metadata, customer, project, currency, quantities and net amounts must match. Inconsistent historical reservations block the operation without automatic repair.
- Split invoices release only their own reservation. Basis statuses are AVAILABLE/PARTIAL/BILLED; project billing statuses are UNBILLED/PARTIAL/BILLED. Increasing a contract basis preserves existing reservations and refreshes statuses.
- Same-content request replay returns the original invoice, including after cancellation/reversal, without reserving again. Reusing a request key with different project, dates, rate or lines fails.
- Posted reversal retains original journal lines and posts an equal opposite journal using the actual original accounts/rate. Closed periods, active allocations, credits or revaluations and inconsistent journal/source data prevent the whole correction, including quota changes.
- Credits and credit reversals retain the original project reservation. Resolve active credits before explicitly correcting the whole original invoice to release quota. Contract values, approvals, project operational fields and source references remain intact; payments, refunds, stock and costs are not automatically reversed.
- The project panel opens correction history scoped to the selected project, reloads quota after success and rejects stale responses when project/organization context changes. Failed loads disable financial writes while allowing reload. Saving billing configuration requires an explicit mode selection.

## Automated verification

Final local verification on 2026-10-20:

- Real isolated MySQL `FinanceMySqlTest`: 92 tests, failures/errors/skipped 0 (15 new project cases).
- Web: 203 tests across 19 files; lint and type/build passed. Six new cases cover project filtering, delayed responses, correction refresh, permissions and failed-load handling.
- MySQL covers draft cancellation, taxed/foreign-currency posted reversal, split and multiple bases, direct finance creation, bound replay, active credit policy, inconsistent stored counters/metadata, closed-period rollback, active allocations/revaluation, contract increase, concurrent reservations/release and current reads after an earlier snapshot.
- Existing GitHub CI must run unchanged, including Web, API, Windows Maven paths, isolated MySQL HTTP regression and production Compose smoke gates. Final run evidence is recorded in the pull request.

## Ordinary browser acceptance

Used an independent synthetic organization with USD base currency and one project created through the normal UI. No project or invoice rows were preseeded. The fixed-price contract was 100 USD.

1. Generated draft SI-1792508645401: 100 net held, 0 remaining, basis BILLED.
2. Cancelled that draft through the project correction dialog: 0 held, 100 remaining, basis AVAILABLE, project UNBILLED; no journal created.
3. Generated SI-1792508711347 using the released 100, then posted it through Finance. Reservation remained 100 and one 100 USD journal was created.
4. Reversed the posted invoice through the project correction dialog. Full page reload showed 100 remaining and 0 posted revenue. Read-only database comparison confirmed 0 held, basis AVAILABLE, project UNBILLED, retained contract/approval/source records and unchanged original journal lines. The new two-line journal exactly opposed the original; combined ledger net was zero.

The two invoices ended CANCELLED and REVERSED with two correction audit records. The synthetic user was signed out and disabled, its organization archived, and all four isolated test services stopped after identity checks. Evidence is retained locally, outside the public source tree.

## Remaining work

Project invoice UI currently supplies exchange rate 1 and project creation defaults to USD without a currency selector. Foreign-rate selection and consistent profitability report currency presentation require a separate change. The browser acceptance above uses USD/USD; explicit foreign rates are covered by backend tests. Historical inconsistent data repair, physical return cost handling and credit rounding allocation remain separate work.
