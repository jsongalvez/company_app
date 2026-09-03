# ADR-0023: GLOBAL capabilities derive from roles via the capability view

**Status:** Accepted (amended 2026-08-28, #431 — OWNER all-branch read added; see the amendment sections at the end)
**Date:** 2026-08-09

## Context

Every GLOBAL-scoped capability (`MANAGE_USERS`, `ASSIGN_DELEGATE`, GLOBAL `VIEW_BRANCH_DATA`, GLOBAL `ASSIGN_COMPENSATION`) is **granted nowhere in production**. The `role_capability` seeds (V2: SUPERUSER/OWNER/MANAGER hold `MANAGE_USERS`) are documentation-only per the V2 header comment; runtime authorization checks only the `active_user_capabilities` view, which reads `user_capability` rows; no production code inserts GLOBAL rows — only the test-only `DevSeeder`. The `user_role` table has no production rows at all.

Consequences (surfaced by #104 F2, #105 F1, #106 F2): every GLOBAL-gated endpoint 403s for every real user — User Management's deactivate/slots surface, the audit-log's original gate, the Finance drawer item, Accountant all-branch views. The defect class is the same at the root: **no production path assigns roles or capabilities**. The app is pre-launch (all `app_user`/`user_role` rows live in test seeding), so the grant mechanism is an open foundational decision, not a fix to retrofit.

## Decision

`active_user_capabilities` becomes a **union**: (a) direct `user_capability` rows, as today (relief grants, medical-mission delegate grants), (b) **role-derived GLOBAL grants**, and (c) assignment-keyed **role-derived BRANCH grants**. Business logic continues to check **only the view**; the V2 rule "never check `user_role` directly" is preserved, with the view as the single place that computes the union.

- **The pre-squash migration pair (V15 + V16)** shipped the derivation mechanism (view union + `ROLE` source type — V15 added the enum value, V16 rewrote the view; split because PG rejects referencing a same-transaction-added enum value from a view); there were no production `app_user`/`user_role` rows at migration time (pre-launch), so **role assignment rides the user-creation path** — the dev/k6 flow already assigns `OWNER` via `DevSeeder`, and the production user-create flow (which must assign a role) is the tracked #106 fog. One `user_role` row yields the whole role bundle, GLOBAL capabilities included.
- **GLOBAL-scoped codes derive from roles** (concrete whitelist, V16, amended by #431): `MANAGE_USERS`, `ASSIGN_DELEGATE`, `ASSIGN_COMPENSATION` for SUPERUSER/OWNER/MANAGER (management class — COORDINATOR's `ASSIGN_COMPENSATION` is "assigned branches only" per V2 and deliberately does NOT derive, to avoid over-grant); plus `VIEW_BRANCH_DATA` for SUPERUSER/OWNER/ACCOUNTANT (SUPERUSER "full access", OWNER and ACCOUNTANT "read-only across all branches" — the #105 F1 intent, coordinated with #131's AuditLogReadScope-style window semantics: GLOBAL `VIEW_BRANCH_DATA` = all branches). BRANCH-scoped capabilities derive from `role_capability` through ACTIVE assignments (V21), where the assignment supplies branch context; relief and delegate grants remain direct `user_capability` inserts.
- The V2 header comment ("GLOBAL-scoped capabilities… enforced at the service layer", "documentation only") is **amended at the view** — V2 itself is untouched (Flyway checksum validation); the pre-squash V16 header and later V21/V25 migrations carry the derivation documentation, and the current view definition remains the authorization source of truth.

Chosen over:
- **Materialization on login/startup** — production code expands `user_role` into `user_capability` rows. Adds a write path and idempotency concerns for no benefit; the view union achieves the same with zero writes.
- **Surgical seeding** — a migration inserting GLOBAL rows for specific users. Fixes one screen, leaves the defect class (ASSIGN_COMPENSATION, VIEW_BRANCH_DATA GLOBAL) dead.
- **Deferral** — the User Management screen would remain unreachable in production.

## Consequences

- Runtime is unchanged for direct grants; derived rows behave identically (INACTIVE users excluded by the view's status filter — deactivation still revokes everything).
- Any future user-create flow must assign a role at creation (#106 fog, tracked).
- `role_capability`'s lack of a branch context remains an explicit boundary: roles express scope class, while ACTIVE assignments supply branch membership for role-derived BRANCH rows.
- Derived GLOBAL `ASSIGN_COMPENSATION` is consumed only by GLOBAL-scoped endpoints (e.g. `/api/commission-inclusions`); the BRANCH-scoped compensation surfaces (CompensationRoutes/AllowanceRoutes) check `ASSIGN_COMPENSATION` on BRANCH, where assignment-keyed role-derived rows authorize assigned branches and GLOBAL rows remain inert.

## Amendment (2026-08-25, #417) — branch-scoped role derivation

The Context paragraph above ("no production path assigns roles or capabilities", "the `user_role` table has no production rows at all") is historical: #344 shipped the production create-user + role-replace path and #344-era home-branch assignments write `user_branch_assignment`. With roles assignable but BRANCH-scoped bundles still flowing "through direct inserts only", every ops/finance surface gated on a BRANCH-context code 403'd for freshly created staff — the relief/delegate grant paths were the only production `user_capability` writers.

**What changed:** union leg (c) widens from the single Coordinator alerts code to **each assigned role's non-management bundle, at every branch holding an ACTIVE assignment** (`ended_at IS NULL` window unchanged). A role still cannot express *which* branch — the assignment now does. Data-driven from `role_capability`: no per-role whitelist, ONBOARDING's empty bundle derives nothing, only-Coordinators-hold `RECEIVE_NEXT_APPOINTMENT_ALERTS` falls out of V2/V5 data.

- **Management codes never derive BRANCH-scoped:** `MANAGE_USERS` (map #384 ruling — company-wide authority) and `ASSIGN_DELEGATE` (GLOBAL-enforced everywhere) stay out of leg (c). COORDINATOR's `ASSIGN_COMPENSATION` lands BRANCH-scoped exactly as V2 documented; OWNER/MANAGER keep the GLOBAL derivation for GLOBAL-gated surfaces.
- **The leg-(b) whitelist and every priority stand:** explicit grants outrank derived rows (5 < 10/20/100); INACTIVE exclusion, the time-window filter, and the #131 strictness are untouched.
- **Consequence update:** the "known limitation, deliberately not bridged" note above is bridged for assignment holders — BRANCH-checking surfaces now see role-derived rows keyed to their ACTIVE assignments (zero writes; materialization stays rejected).
- Shipped in `V21__derive_branch_scoped_role_capabilities.sql`; derivation matrix pinned by `StaffRoleBranchDerivationPostgresTest`, route-level reachability by `StaffRoleReachabilityAuthzTest`.

## Amendment (2026-08-28, #431) — OWNER all-branch read

The business rule for OWNER is view-only access across all branches. The role-derived GLOBAL `VIEW_BRANCH_DATA` whitelist now includes OWNER alongside SUPERUSER and ACCOUNTANT. The capability remains read-only: OWNER's branch-scoped operational grants still come from ACTIVE assignments, and GLOBAL `VIEW_BRANCH_DATA` does not satisfy branch or branch-day write gates.

Shipped in `V25__derive_owner_global_view_capability.sql`; the no-assignment derivation is pinned by `CapabilityGrantPathPostgresTest`, and the unassigned-second-branch HTTP read window by `ReportsReadScopeAuthzTest`.

## Amendment (2026-09-03, #436) — GLOBAL catalog authority

The shared product catalog leaves BRANCH-scoped `MANAGE_PRODUCTS` (map #422 decision #436, Option 3). `MANAGE_CATALOG` is the distinct GLOBAL-scoped catalog authority: product/category collection and detail routes gate GLOBAL `MANAGE_CATALOG`, derived GLOBALly for SUPERUSER/OWNER/MANAGER/COORDINATOR and excluded from the BRANCH-derived leg beside the other management codes, so no branch-qualified catalog grant ever derives. BRANCH `MANAGE_PRODUCTS` keeps governing branch inventory and #418 base rates; GLOBAL `MANAGE_PRODUCTS` still derives for nobody.

Shipped in `V26__derive_global_catalog_capability.sql`; derivation pinned by `CapabilityGrantPathPostgresTest` plus the leg-(c) exclusion pin in `StaffRoleBranchDerivationPostgresTest`, route gates by `CatalogAuthzTest`.
