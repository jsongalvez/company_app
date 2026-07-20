# ADR-0007: Route-level capability gates via Javalin before filters

**Status:** Accepted
**Date:** 2026-07-16

## Context

Every service method that gates on a capability repeats a 6-line
`CapabilityService.requireCapability(capabilityCode, contextType, contextId)` block. In
`ExpenseService` alone, 3 methods (`create`, `softDelete`, `findByBranchDayId`) copy the same
`EDIT_BRANCH_DATA` on `BRANCH` check. Across 23 service files, `requireCapability` is called
54 times with identical parameter patterns per service group.

This boilerplate:
- Obscures the service's business logic with authorization plumbing.
- Makes it easy to forget the check when adding a new method (no compiler enforcement).
- Duplicates the capability→method mapping; if the capability required changes, every
  call site in the service must be updated.

## Decision

Move capability enforcement from the service layer to the Javalin HTTP layer via
`config.routes.before` filters. A `CapabilityFilter` utility object in `api/middleware` provides
helper functions (`requireBranchCapability`, `requireBranchCapabilityForExpense`) that
extract the relevant branch ID from the request context (query params, body, or path) and
delegate to `CapabilityService`.

The service layer retains day-state assertions (`BranchDayService.assertEditable`) — it no
longer performs capability checks.

**Pilot scope (completed):** ExpenseService routes only (`POST/GET /api/expenses`,
`DELETE /api/expenses/{expenseId}`). Proves the pattern before wider rollout (see
[#2](../../.scratch/issues/0002-collapse-capability-boilerplate/ISSUE.md)).

**Full rollout (completed, [#7](../../.scratch/issues/0007-group-b-branch-scope/ISSUE.md)):**
All Group B services converted from GLOBAL to BRANCH scope:

- **SessionRoutes** (7 of 8 filters): `POST /api/sessions`, `PATCH /api/sessions/{sessionId}/status`,
  `POST …/void`, `POST …/unvoid`, `POST …/practitioners`, `GET|POST|DELETE /api/sessions/{sessionId}/concerns`,
  `POST …/promote-concern` — all resolve branch via `requireBranchCapabilityForSession`.
  `GET /api/concerns` stays GLOBAL (no branch context).
- **BranchInventoryRoutes**: `MANAGE_PRODUCTS` on BRANCH via `requireBranchCapabilityForBranchId`.
- **CompensationRoutes**: `ASSIGN_COMPENSATION` on BRANCH via `requireBranchCapability` (resolves branch from `branchDayId` in body).
- **ProductSaleRoutes**: `EDIT_BRANCH_DATA` on BRANCH via `requireBranchCapability` (resolves branch from `branchDayId` in body).
- **AllowanceRoutes**: `ASSIGN_COMPENSATION` on BRANCH via `requireBranchCapability` (resolves branch from `branchDayId` in body).
- **ExportRoutes** (per-branch): `VIEW_BRANCH_DATA` via `requireBranchCapabilityForBranchId`
  for `{branchId}/export/*`; branch-type exports (`/api/branches/export/*`) stay GLOBAL.
- **DailySalesSummaryRoutes**: `VIEW_BRANCH_DATA` on BRANCH via `requireBranchCapabilityForBranchId`.
- **MonthlyRemittanceSummaryRoutes**: `VIEW_BRANCH_DATA` on BRANCH via `requireBranchCapabilityForBranchId`.

**Filter registration pattern:**

```kotlin
// In ExpenseRoutes.register():
config.routes.before("/api/expenses") { context ->
    val branchDayId = when (context.method()) {
        HandlerType.POST -> parseFromBody(context)
        HandlerType.GET  -> parseFromQuery(context)
        else -> throw BadRequestResponse(...)
    }
    CapabilityFilter.requireBranchCapability(context, branchDayId)
}

config.routes.before("/api/expenses/{expenseId}") { context ->
    val expenseId = context.pathParamAsUuid("expenseId")
    CapabilityFilter.requireBranchCapabilityForExpense(context, expenseId)
}
```

## Consequences

**Positive:**
- ExpenseService methods are 6 lines shorter each; capability requirements are declared
  once per route path rather than per service method.
- Adding a new expense route requires an explicit decision about capability gating
  (the before filter makes the requirement visible).
- The `CapabilityFilter` utility provides a growing family of helpers:
  `requireBranchCapability` (from branchDayId), `requireBranchCapabilityForExpense`,
  `requireBranchCapabilityForRemittance`, `requireBranchCapabilityForBranchId` (from direct branch UUID),
  `requireBranchCapabilityForSession` (from sessionId), and `requireGlobalCapability` for system-wide checks.

**Negative:**
- The DELETE filter looks up the expense and branch day to resolve the branch ID,
  duplicating the DB calls that the service handler already makes. This is acceptable
  for the pilot; optimization (e.g. context attribute passthrough) can follow.
- Service-level tests that asserted `ForbiddenResponse` for missing capabilities no
  longer apply — capability enforcement moved to the HTTP layer. Two existing
  `ExpenseServicePostgresTest` tests were updated to verify the service succeeds
  without capability (proving the check moved). HTTP-level filter tests require
  Javalin test infrastructure not yet present in the project.
- There is a risk that a future service method is added without a corresponding filter
  registration. A follow-up ADR or tooling (custom annotation + compile-time check)
  could address this.

## Alternatives considered

**Route metadata API (`addHttpHandler` directly):** Javalin 7's `config.routes.before`
method maps to `addHttpHandler(HandlerType.BEFORE, ...)` internally, but Kotlin 2.3.10
SAM conversion issues prevented passing a `Handler` as the second argument. Using
the lambda form of `before(String) { }` avoids the SAM ambiguity.

**Keeping capability checks in the service layer:** Rejected — the boilerplate is
already at 54 call sites and growing, and the pattern does not compose well for
service-to-service calls (there are none; every call originates from an HTTP route).
