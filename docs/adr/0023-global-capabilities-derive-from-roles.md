# ADR-0023: GLOBAL capabilities derive from roles via the capability view

**Status:** Accepted
**Date:** 2026-08-09

## Context

Every GLOBAL-scoped capability (`MANAGE_USERS`, `ASSIGN_DELEGATE`, GLOBAL `VIEW_BRANCH_DATA`, GLOBAL `ASSIGN_COMPENSATION`) is **granted nowhere in production**. The `role_capability` seeds (V2: SUPERUSER/OWNER/MANAGER hold `MANAGE_USERS`) are documentation-only per the V2 header comment; runtime authorization checks only the `active_user_capabilities` view, which reads `user_capability` rows; no production code inserts GLOBAL rows — only the test-only `DevSeeder`. The `user_role` table has no production rows at all.

Consequences (surfaced by #104 F2, #105 F1, #106 F2): every GLOBAL-gated endpoint 403s for every real user — User Management's deactivate/slots surface, the audit-log's original gate, the Finance drawer item, Accountant all-branch views. The defect class is the same at the root: **no production path assigns roles or capabilities**. The app is pre-launch (all `app_user`/`user_role` rows live in test seeding), so the grant mechanism is an open foundational decision, not a fix to retrofit.

## Decision

`active_user_capabilities` becomes a **union**: (a) direct `user_capability` rows, as today (relief grants, medical-mission delegate grants), and (b) **role-derived grants** — `user_role` → `role_capability` → `capability`. Business logic continues to check **only the view**; the V2 rule "never check `user_role` directly" is preserved, with the view as the single place that computes the union.

- **The migration pair (V15 + V16)** ships the derivation mechanism (view union + `ROLE` source type — V15 adds the enum value, V16 rewrites the view; split because PG rejects referencing a same-transaction-added enum value from a view); there are no production `app_user`/`user_role` rows at migration time (pre-launch), so **role assignment rides the user-creation path** — the dev/k6 flow already assigns `OWNER` via `DevSeeder`, and the production user-create flow (which must assign a role) is the tracked #106 fog. One `user_role` row yields the whole role bundle, GLOBAL capabilities included.
- **GLOBAL-scoped codes derive from roles** (concrete whitelist, V16): `MANAGE_USERS`, `ASSIGN_DELEGATE`, `ASSIGN_COMPENSATION` for SUPERUSER/OWNER/MANAGER (management class — COORDINATOR's `ASSIGN_COMPENSATION` is "assigned branches only" per V2 and deliberately does NOT derive, to avoid over-grant); plus `VIEW_BRANCH_DATA` for SUPERUSER/ACCOUNTANT (SUPERUSER "full access", ACCOUNTANT "read-only across all branches" — the #105 F1 intent, coordinated with #131's AuditLogReadScope-style window semantics: GLOBAL `VIEW_BRANCH_DATA` = all branches). BRANCH-scoped capabilities cannot derive from `role_capability` (it has no context column — a role cannot express *which* branch), so BRANCH grants continue to flow through direct `user_capability` inserts (relief, delegate).
- The V2 header comment ("GLOBAL-scoped capabilities… enforced at the service layer", "documentation only") is **amended at the view** — V2 itself is untouched (Flyway checksum validation); the V16 migration header carries the amended scope documentation, and the derivation is documented at the view definition.

Chosen over:
- **Materialization on login/startup** — production code expands `user_role` into `user_capability` rows. Adds a write path and idempotency concerns for no benefit; the view union achieves the same with zero writes.
- **Surgical seeding** — a migration inserting GLOBAL rows for specific users. Fixes one screen, leaves the defect class (ASSIGN_COMPENSATION, VIEW_BRANCH_DATA GLOBAL) dead.
- **Deferral** — the User Management screen would remain unreachable in production.

## Consequences

- Runtime is unchanged for direct grants; derived rows behave identically (INACTIVE users excluded by the view's status filter — deactivation still revokes everything).
- Any future user-create flow must assign a role at creation (#106 fog, tracked).
- `role_capability`'s lack of a context column is now an explicit boundary: roles express *scope class* (GLOBAL), never branch membership.
- Derived GLOBAL `ASSIGN_COMPENSATION` is consumed only by GLOBAL-scoped endpoints (e.g. `/api/commission-inclusions`); the BRANCH-scoped compensation surfaces (CompensationRoutes/AllowanceRoutes) check `ASSIGN_COMPENSATION` on BRANCH, where role-derived GLOBAL rows are inert by the same boundary — a role cannot express which branch. Known limitation, deliberately not bridged.
