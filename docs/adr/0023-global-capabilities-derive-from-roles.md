# ADR-0023: GLOBAL capabilities derive from roles via the capability view

**Status:** Accepted
**Date:** 2026-08-09

## Context

Every GLOBAL-scoped capability (`MANAGE_USERS`, `ASSIGN_DELEGATE`, GLOBAL `VIEW_BRANCH_DATA`, GLOBAL `ASSIGN_COMPENSATION`) is **granted nowhere in production**. The `role_capability` seeds (V2: SUPERUSER/OWNER/MANAGER hold `MANAGE_USERS`) are documentation-only per the V2 header comment; runtime authorization checks only the `active_user_capabilities` view, which reads `user_capability` rows; no production code inserts GLOBAL rows — only the test-only `DevSeeder`. The `user_role` table has no production rows at all.

Consequences (surfaced by #104 F2, #105 F1, #106 F2): every GLOBAL-gated endpoint 403s for every real user — User Management's deactivate/slots surface, the audit-log's original gate, the Finance drawer item, Accountant all-branch views. The defect class is the same at the root: **no production path assigns roles or capabilities**. The app is pre-launch (all `app_user`/`user_role` rows live in test seeding), so the grant mechanism is an open foundational decision, not a fix to retrofit.

## Decision

`active_user_capabilities` becomes a **union**: (a) direct `user_capability` rows, as today (relief grants, medical-mission delegate grants), and (b) **role-derived grants** — `user_role` → `role_capability` → `capability`. Business logic continues to check **only the view**; the V2 rule "never check `user_role` directly" is preserved, with the view as the single place that computes the union.

- **Seed V15** assigns roles to ops users (e.g. `OWNER` role to the owner account). One `user_role` row yields the whole role bundle, GLOBAL capabilities included.
- **GLOBAL-scoped codes derive from roles** (`MANAGE_USERS`, `ASSIGN_DELEGATE` per the V2 scope comments). BRANCH-scoped capabilities cannot derive from `role_capability` (it has no context column — a role cannot express *which* branch), so BRANCH grants continue to flow through direct `user_capability` inserts (relief, delegate). The ACCOUNTANT "read-only across all branches" intent coordinates with #131's AuditLogReadScope-style window semantics (GLOBAL `VIEW_BRANCH_DATA` = all branches).
- The V2 header comment ("GLOBAL-scoped capabilities… enforced at the service layer", "documentation only") is amended to match; the derivation is documented at the view.

Chosen over:
- **Materialization on login/startup** — production code expands `user_role` into `user_capability` rows. Adds a write path and idempotency concerns for no benefit; the view union achieves the same with zero writes.
- **Surgical seeding** — V15 inserting GLOBAL rows for specific users. Fixes one screen, leaves the defect class (ASSIGN_COMPENSATION, VIEW_BRANCH_DATA GLOBAL) dead.
- **Deferral** — the User Management screen would remain unreachable in production.

## Consequences

- Runtime is unchanged for direct grants; derived rows behave identically (INACTIVE users excluded by the view's status filter — deactivation still revokes everything).
- Any future user-create flow must assign a role at creation (#106 fog, tracked).
- `role_capability`'s lack of a context column is now an explicit boundary: roles express *scope class* (GLOBAL), never branch membership.
