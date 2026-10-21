# Project operational currency verification

Project summaries previously added timesheet and expense amounts without separating currencies, then labelled the sum with the project currency. The project-list card combined those already mixed amounts across projects, and CSV omitted the amount currency. This change preserves each recorded currency through list/summary responses, display and export.

## API and business semantics

- Project list and detail share `operationalAmounts`: rows of `currencyCode`, `actualCost` and `billableAmount`, grouped with BigDecimal and sorted by recorded currency. Registered cost includes timesheet cost plus expense amount; registered billable amount includes billable records only. Hours remain a separate nonmonetary total.
- These are operational registrations, including pending approval; they are not posted ledger costs, approved invoice capacity or a converted project-currency valuation. Existing source amounts, currencies and workflow records are not rewritten.
- Legacy `actualCost` and `billableAmount` remain populated only when the corresponding nonzero amounts are comparable with the declared project currency; otherwise that scalar is null. An empty project retains zero scalars with no invented source currency rows. Malformed historical currency codes fail explicitly rather than being relabelled as USD.
- The summary card groups currencies across the currently loaded project list, not all organization rows beyond that page. Detail cards and print output show each source currency explicitly. Missing grouped evidence renders unavailable rather than falling back to an untrustworthy mixed scalar.
- Timesheet, expense and budget forms expose active currency selection. Selecting a project updates those defaults immediately. Saving time/expense reloads list totals and detail together. Delayed detail responses cannot restore an earlier selected project.
- CSV now has project currency and amount currency. It contains one row per project/source currency, cost and billable amount with four decimals, and project hours only on the first currency row. Empty projects export a zero row in their own currency; missing grouped evidence is marked unavailable.
- Approving a billable timesheet/expense in another currency must not create an unusable billing basis. The existing transactional billing-basis writer now rejects a currency different from the project currency and rolls back approval, counters and basis writes. Nonbillable foreign operating costs can still be approved without generating quota. No implicit exchange rate or valuation policy is introduced; inconsistent existing bases require separate review.

## Verification

Final local checks on 2026-10-21:

- Project service projection tests: 8 passed, failures/errors/skipped 0. Covers same/mixed/single-foreign/empty projects, per-field scalar comparability, source precision/no rewriting, organization-qualified list/detail consistency and malformed history.
- Real isolated MySQL `FinanceMySqlTest`: 101 passed, failures/errors/skipped 0 (99 retained plus two new cases). Billable foreign time and expense approvals reject atomically without changing source fields or creating quota; nonbillable foreign records approve in their original currency without adding a basis or journal. Combined local backend invocation: 109 passed.
- Web: 220 tests in 22 files passed (210 retained plus ten). Covers grouped totals and rendering, fractional values, unavailable evidence, CSV currencies/one-time hours, old-selection isolation and refreshing totals/detail after new expense registration. Lint, type checking and production build passed.
- Full existing GitHub CI must run unchanged on the final PR head, including Web, API, Windows Maven paths, isolated real MySQL/HTTP scenarios and production Compose gates. Final results are recorded in the PR. Local packaging with tests skipped was only to start the browser acceptance JAR and is not test evidence.

## Ordinary browser and actual CSV acceptance

Used an independent synthetic organization with CNY base currency and a temporary PROJECT_MANAGER user. Only master data and an open period were preloaded. All project, time and expense rows were created through the normal UI.

1. Created USD project PRJ-9D6F0B57. Recorded 2 hours at USD cost rate 10 and billing rate 20, then a billable expense of 5 USD and another of 100 CNY. Its detail shows cost 25 USD / 100 CNY, billable registrations 45 USD / 100 CNY and 2 hours.
2. Created CNY project PRJ-7A9AE477 and recorded a billable expense of 20 CNY. The loaded-list card shows 45 USD and 120 CNY separately.
3. Fully refreshed the page, selected the USD project again and confirmed the same figures. The actual Export CSV action produced three monetary rows with explicit project/source currencies; hours sum to 2, not 4 across the two USD-project currency rows.
4. Read-only SQL matched the one timesheet and three expenses against the actual CSV, confirmed original currencies/amounts and unapproved workflow state, and confirmed no approved billing basis, invoice or journal was created by this operational acceptance.

The actual CSV, read-only snapshot and final screenshot are retained locally outside public source. The synthetic user was normally signed out and disabled, its organization archived and all four isolated services stopped after identity checks; source history was retained.

## Remaining scope

Cross-currency valuation and budget comparisons need an explicit policy and recorded rate evidence; this batch does not invent one or repair inconsistent historical bases. Posted financial profitability remains governed by the explicit base-currency contract from the previous batch. Project close eligibility and approval/financial dependency consistency need a separate review, alongside historical journal issues, credit rounding, physical return costs and broader release validation.
