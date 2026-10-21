# Project invoice currency and profitability verification

Project billing previously sent exchange rate 1 without asking the user. Project profitability summed base-currency journal amounts but labelled them with the project currency. This change requires explicit foreign-rate confirmation and exposes separate contract and financial reporting currencies.

## Resulting behavior

- New projects require an explicit selection from active organization currencies. Currency lookup reads all pages; no implicit USD selection is submitted by the new-project UI.
- A project billing basis now opens a draft form with net amount, tax rate, accounting date and exchange-rate date. The direction is explicit: 1 project-currency unit equals the entered number of base-currency units. Foreign rates start at zero and block saving until supplied. Same-currency rates are fixed at 1.
- The user confirms the amount, currencies, rate and dates before saving; edits reset confirmation. An uncertain save can be explicitly retried with the same key and unchanged body. Changed details receive a different key after reconfirmation. Duplicate pending saves/close are blocked, and old-context responses are ignored. Posting remains a separate Finance action.
- Project-source creation, posting and correction validate positive rates with at most eight decimals, same-currency rate 1, the current finance base currency and a rate date no later than accounting date or today. Existing organization locking, source/quota checks and atomic correction behavior remain.
- Profitability `currencyCode` continues to identify project contract/quota amounts. New `baseCurrencyCode` identifies `postedRevenue`, `postedCost` and `grossProfit`, which use actual stored base journal amounts, including original and reversal entries. No current market rate is substituted for historical posting rates.
- `billedAmount` now represents reserved billing-basis net quota, excluding invoice tax. `budgetRevenueVariance` and `budgetCostVariance` are only calculated when project and base currencies match; otherwise they are null rather than subtracting incompatible amounts.
- New `managementLaborCosts` groups approved timesheet costs by recorded currency. The legacy `managementLaborCost` scalar is null when any group differs from project currency. Management costs are not silently added into posted ledger cost. Historical mixed-currency billing bases or base-ledger currencies fail explicitly rather than being summed as one currency.

## Automated verification

Final local checks on 2026-10-21:

- Real isolated MySQL `FinanceMySqlTest`: 99 tests, failures/errors/skipped 0, including seven new cases.
- Web: 210 tests in 20 files, including six new invoice-form cases and one report-rendering case. Lint, type check and production build passed.
- New database coverage: taxed foreign invoice net quota and actual base revenue through credit/reversal; split invoices at different historical rates; comparable same-currency budgets and posted expense; grouped management costs; invalid rate precision/dates and same-currency rate rejection without reservation; stored exchange terms rechecked at posting; explicit refusal to combine incompatible historical currencies.
- New UI coverage: explicit rate/source payload, missing rate/base/permission, same-currency 1, amount/precision/date guards, confirmation reset, pending actions, uncertain retry key, changed-details key and old-project response isolation. The report test asserts base USD revenue is not rendered as EUR while the contract remains EUR.
- Full existing GitHub CI must run unchanged. The final exact-head results are recorded in the PR; local JAR packaging with tests skipped is only for browser acceptance, not test evidence.

## Ordinary browser acceptance

Used an independent synthetic CNY-base organization with only master data, a temporary PROJECT_MANAGER user and an open accounting period. No project or invoice transaction rows were preseeded.

1. Created PRJ-DACF5961 through the ordinary new-project form, explicitly choosing USD. Configured a fixed contract of 100 USD.
2. Opened the new project invoice form. Foreign rate was initially zero and Save was disabled. Entered rate 7.2, tax 10 percent, dates 2026-10-21 and explicit confirmation; preview showed 792 CNY.
3. Saved draft SI-1792556371743, then posted it through Finance. Read-only SQL confirmed net 100 USD, tax 10 USD, total 110 USD, base total 792 CNY and one three-line journal: receivable debit 792, revenue credit 720 and tax credit 72 CNY. The source points to the actual project/basis; the project reserves only 100 USD once.
4. Fully reloaded the project page and reopened the project. Contract remained 100 USD, remaining quota 0 USD, posted revenue and gross profit were 720 CNY, and the currency explanation distinguished USD quota from CNY financial figures.

Screenshots and the read-only SQL snapshot are retained locally outside the public source tree. After acceptance the user was signed out and disabled, its synthetic organization archived and all four isolated services stopped after identity checks, retaining financial history.

## Boundaries and remaining work

Rates are entered explicitly; this change does not fetch market rates or repair earlier invoices saved at an incorrect rate. Historical corrections require the existing audited workflow and valid original evidence. Cross-currency budget comparisons require an explicit valuation policy and are not estimated here. The older operational project summary still aggregates raw timesheet/expense values and needs a separate currency-aware change; the financial panel and profitability contract described above are the scope of this batch. Historical inconsistent data, credit rounding, physical return costs and broader release validation remain separate tasks.

## CI dependency patch

The first PR run stopped at the unchanged official-registry audit, before Web type checking/tests: five findings (three high, two moderate) on follow-redirects 1.16.0 and Vite 7.3.6. The project dependency range was raised to Vite ^7.3.7; a narrow same-major override selects follow-redirects 1.16.1. The lockfile is regenerated against the official npm registry; no audit ignore or threshold change is used. These patches address the existing gate findings and do not change project financial logic.

Official patched-version evidence: [follow-redirects](https://github.com/advisories/GHSA-q3jh-wrc6-jgq8), [Vite](https://github.com/advisories/GHSA-587h-7wmj-h5pm). The existing CI gate and full front-end verification must pass again on the updated dependency graph. Browser evidence above was recorded before the dependency-only patch; financial production code is unchanged afterward.
