# CR-011: SUBMIT_REMITTANCE and EDIT_BRANCH_DATA use GLOBAL context instead of BRANCH

**Source:** Critical B — found in chunk 2c (spec: "SUBMIT_REMITTANCE uses GLOBAL context", "EDIT_BRANCH_DATA uses GLOBAL")

**What:**
- `RemittanceService` (all 6 methods: `submit`, `createDraft`, `addLine`, `removeLine`, `addDayBreakdown`, `getRemittance`) checks `SUBMIT_REMITTANCE` with `contextType = CapabilityContextType.GLOBAL` — any submitter can act on any branch
- `ExpenseService` (all 3 methods: `create`, `softDelete`, `findByBranchDayId`) checks `EDIT_BRANCH_DATA` with `contextType = CapabilityContextType.GLOBAL`
- `EDIT_BRANCH_DATA` is inherently branch-scoped (per `docs/architecture_implementation_plan.md` A2 capability table: scope = "BRANCH / BRANCH_DAY")
- `DatabaseTestHelper.grantSubmitRemittance` also grants with GLOBAL context

**Spec reference:** `docs/architecture_implementation_plan.md` §A2 — `SUBMIT_REMITTANCE` scope is BRANCH, `EDIT_BRANCH_DATA` scope is BRANCH/BRANCH_DAY

**Files:**
- `backend/src/main/kotlin/com/companyb/companyapp/service/RemittanceService.kt` — lines ~37-43, ~95-101, ~140-146, ~205-211, ~247-253, ~287-293
- `backend/src/main/kotlin/com/companyb/companyapp/service/ExpenseService.kt` — lines ~91-97 (and similar in `create`, `softDelete`)
- `backend/src/test/kotlin/com/companyb/companyapp/DatabaseTestHelper.kt` — `grantSubmitRemittance` method

**Fix:**
1. Change all `SUBMIT_REMITTANCE` capability checks to use `CapabilityContextType.BRANCH` with the actual `branchId` as `contextId`
2. Change all `EDIT_BRANCH_DATA` capability checks in `ExpenseService` to use `CapabilityContextType.BRANCH` with the expense's `branchDay.branchId`
3. Update `DatabaseTestHelper.grantSubmitRemittance` to accept and use a `branchId` parameter
4. Update `RemittanceRoutes` to pass `branchId` through (may need to resolve from remittance → day_breakdown → branch_day)
5. Verify seed data: V2 migration grants `SUBMIT_REMITTANCE` with BRANCH context — ensure existing grants are scoped to specific branches

**Priority:** critical
**Story alignment:** US-020 (Remittance), US-022 (Expenses)
