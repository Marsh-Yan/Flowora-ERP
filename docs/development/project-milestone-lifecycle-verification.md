# Project milestone delivery, approval and billing lifecycle

## Resulting behavior

Milestones move PLANNED → IN_PROGRESS → COMPLETED using their current version. Completion requires every linked task to be DONE. A completed milestone cannot receive new linked tasks. All unfinished milestones, including nonbillable milestones, block both project completion and archive. Existing terminal project write guards remain in effect.

Delivery completion and billing approval are separate actions. New milestones remain nonbillable by default. A billable milestone requires MILESTONE billing mode and an explicitly entered positive net amount in the project currency, with at most four fractional and fifteen integer digits. The amount control starts at zero and cannot save billable terms until the user enters a positive amount. Nonbillable terms must have zero amount. This uses existing database columns; no migration or historical rewrite is introduced.

Only a completed billable milestone can be approved or rejected. Review requires workflow:approve, a current nonnegative version and a nonblank reason of at most 500 characters. Successful review increments the version and records actor, request, milestone and reason in the audit event. Approval creates exactly one MILESTONE basis with the original project currency and amount. Approved milestones cannot be approved again or directly rejected. Rejected milestones can be reviewed again with their latest version. A rejection resolves the approval requirement without creating billing capacity.

MILESTONE mode cannot mix fixed-contract or operating billing bases. Existing billable milestones/bases constrain mode changes. In MILESTONE mode time and expenses are operating cost records: creation rejects billable=true and approval cannot generate operating billing capacity. Nonbillable operating costs remain registrable and approvable. Fixed-price contract behavior remains separate, avoiding duplicate milestone billing on a fixed contract.

Invoice draft creation and posting recheck the current milestone delivery/approval evidence and linked tasks, in addition to existing basis version, capacity, currency and reservation checks. Cancellation and financial reversal retain their existing correction paths even for inconsistent historical approval evidence; they do not rewrite it. Draft reservation of the full basis is not settlement and still blocks project closure. Existing organization finance locks serialize delivery, review, task creation and billing; close checks use the same readiness policy.

## API and page behavior

- POST /api/v2/compat/projects/{id}/milestones accepts billable and billingAmount alongside existing milestone terms. Responses include billable, billingAmount, approvalStatus, currencyCode and version.
- POST /api/v2/compat/projects/milestones/{milestoneId}/status accepts status and version; existing ADMIN/PROJECT_MANAGER roles apply.
- POST /api/v2/projects/milestones/{id}/approval accepts approved, reason and version under workflow:approve. Approved review returns its billing basis; rejection returns no basis.
- Profitability includes openMilestones and OPEN_MILESTONES in closeBlockers. Both completion and archive enforce it.

The page shows separate delivery and review controls, exact net amount with currency, and approval state. It limits available actions by role/permission, project state, billing mode and version, blocks duplicate pending submissions, and ignores late responses after a project/organization change. Review and delivery changes refresh readiness and capacity. Completed milestones are omitted from new-task targets. Milestone-mode time/expense forms explain operating cost treatment and disable billable selection. Amounts are not converted or summed across currencies.

## Verification on 2026-10-22

- Local isolated real MySQL FinanceMySqlTest: 125 passed, zero failures/errors/skips (113 retained plus twelve lifecycle cases). Covers linked-task and unfinished-milestone blockers, exact source/currency, separate review, reject/review with stale versions, duplicate approval protection and audit count, fixed-contract mode conflict, operating cost approval, actual invoice posting and reversal, inconsistent legacy evidence at posting/cancellation, organization/terminal boundaries, concurrent approve/reject and concurrent task creation/completion. Existing multiple-basis billing test now uses actual delivered and approved milestone evidence.
- Project tests: MilestoneTermsTest 4, ProjectMutationGuardTest 6, ProjectEntityTest 1 and ProjectOperationalCurrencyTest 8 passed. Combined backend invocation: 144 passed, zero failures/errors/skips; the same invocation packaged the acceptance JAR.
- Web: 235 tests in 23 files passed, lint zero warnings, type checking and production build passed. Eight new milestone action tests cover versioned delivery/review, required reason, permission/state/mode restrictions, rejection review, duplicate pending requests, stale responses and conflict refresh; the creation regression covers explicit positive amount entry. Existing chunk-size/PURE notices remain.
- The initial complete CI run 39789924954 failed its dependency audit on two newly reported @vitest/mocker advisories: [GHSA-qxg6-89mg-rqv8](https://github.com/advisories/GHSA-qxg6-89mg-rqv8) and [GHSA-xp26-3454-mjgp](https://github.com/advisories/GHSA-xp26-3454-mjgp). Their published 4.x fix is 4.1.12. Vitest and its matching internal packages were updated from 4.1.11 to 4.1.12; application runtime dependencies are unchanged. Official-registry audit reports no known vulnerabilities, and the full local web checks were repeated on that patch. The complete existing workflow runs again on the final PR head with all gates retained; final run/results are recorded in the PR. No deployment, migration or workflow changes belong to this batch.

## Ordinary browser acceptance

An independent R6S synthetic organization/account started with master data and an open period only. Normal product operations created project PRJ-494E1C40 in CNY with MILESTONE billing, milestone R6S billable delivery for 100.1234 CNY and a linked task. Completion while the task was unfinished was rejected. After the task reached DONE, delivery completed; capacity was absent before approval and appeared only after a separate reasoned approval. A same-amount draft reserved the capacity while readiness stayed false because the draft remained unsettled. No journal was created.

A second project PRJ-0B38941D completed its nonbillable milestone and archived successfully. Full refresh retained both project and milestone states. After chat interruption, the existing isolated services were restarted without recreating fixtures; the same final packaged JAR, normal sign-in and full refresh reconfirmed the approved amount and draft blocker. Read-only SQL reverified two projects, two milestones, one DONE task, exactly one basis/draft/source for 100.1234 CNY, four successful milestone delivery events, one reasoned approval and two project status events, with no time/expense/journal records.

Screenshots, SQL evidence and logs remain private. Browser acceptance proves the stated delivery/approval/draft and nonbillable archive paths; actual posting/reversal, history and concurrency are proved by the real MySQL tests. Cleanup signs out normally, disables only the synthetic account and archives only its organization while retaining business history, then stops the identity-checked isolated services.

## Remaining scope

Explicit reopening and amendment governance, historical billing/rate/journal repair, cross-currency budget valuation and wider release/performance acceptance remain separate work. This batch does not automatically repair historical data or certify full financial settlement from draft capacity consumption.
