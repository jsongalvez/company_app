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

## Deviation (2026-08-09, #134)

The UserBranchAssignment surface enforces at the **service layer**, not the route layer:
`create`, `remove`, and `findActiveByBranch` gate on GLOBAL `MANAGE_USERS` inside the
service; `swapSlots` requires GLOBAL `MANAGE_USERS` OR the caller is one of the two
swapped users — a participant-exception rule no path filter can express; `updateSlot`
requires GLOBAL `MANAGE_USERS` OR the caller is the target user (self-service slot
change). The 4-segment `before("/api/branches/{branchId}/slots")` and `/assignments`
filters never matched the 5-segment `/slots/swap` and assignment-DELETE paths (the #114
exact-path lesson, third occurrence) and were removed as misleading; the service-level
checks are now the only authorization surface for that route group. All other surfaces
keep route-filter enforcement. See #134's resolution for the leak-falsification record.

## Amendment (2026-08-14, #157) — day-scoped (BRANCH_DAY) gates

The day-scoped write surface accepts a **BRANCH_DAY grant for the specific branch day** in
addition to the BRANCH grant (see `CapabilityFilter.requireBranchOrBranchDayCapability`).
Relief grants are written as `(EDIT_BRANCH_DATA, BRANCH_DAY, branchDayId)` with a
`validFrom`/`validTo` window but were never checked — the exact-triple
`hasCapability` could never match them, so day-scoped relief editing 403'd end-to-end
(the #155 falsification). The gate is: `BRANCH at the day's branch OR BRANCH_DAY for the
day` — the day-scoped grant satisfies the gate for that day only. **GLOBAL never
satisfies these gates** (the #131 strictness: the OR adds only the narrower day-scoped
form, never a relaxation). Covered surface: expenses (all verbs + the read), product-sale
create, session create (resolves today's day **find-only** — filters never create rows)
+ session mutations. Not covered (decided): inventory movements (the movement's day comes
from the body while the route is branch-scoped via the path — a day-grant check there
would authorize a write against a different branch, the parent-child scoping trap), the
branch-day status read (`GET /api/branches/{branchId}/today` — branch-gated, zero
consumers), and surfaces gated on other codes (the relief grant carries only
`EDIT_BRANCH_DATA`).

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
