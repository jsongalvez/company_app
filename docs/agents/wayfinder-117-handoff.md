# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 22

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 22 resolved the AFK task **#117** ("Add finance read-back endpoints: compensation list + branch-day users + expense edit (graduated from #101)") — `wayfinder:task`, the last AFK ticket on the map. Full flow: claimed #117 → falsification against live backend (1 premise corrected: **expense table had no version column** → V11 migration added, V6 precedent) → build (18 files, +958) → `:backend:detekt :backend:ktlintCheck :backend:test` green + shared compile + test-DB cleanliness → /code-review (parallel Standards + Spec, both PASS after 2 fixes) → commit `6d6009e` on `ralph/company-app-full-build` (**not pushed**, #111/#112/#113/#114/#115/#116 precedent) → resolution comment (https://github.com/jsongalvez/company_app/issues/117#issuecomment-5199352099) → ticket closed → map #89 updated (child-table row 117 → ✅ closed, Decisions-so-far + #117 entry, closing paragraph rewritten).

**Next session pick:** the frontier is now **4 HITL prototypes, all unblocked, all unassigned, zero AFK**:

| # | Title | Type |
|---|-------|------|
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype — HITL |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype — HITL |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype — HITL |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype — HITL |

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #117 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/home/jayson/.config/opencode/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#117 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/117#issuecomment-5199352099**
- #101 resolution comment (the finance decisions #117 served): https://github.com/jsongalvez/company_app/issues/101#issuecomment-5199115973
- Prior handoffs: `docs/agents/wayfinder-101-handoff.md` (session 21), `docs/agents/wayfinder-116-handoff.md` (session 20)

## Session outcome

**#117 — resolved + closed.** Three endpoints shipped on commit `6d6009e`:
1. `GET /api/compensations?branchDayId=` — per-day rows joined with `app_user.display_name`; `CompensationResponse` gained additive nullable `userName`; filter = payingBranchDayId (per-user-per-day identity per `idx_compensation_unique`); `assignedAt DESC, id DESC` order; ASSIGN_COMPENSATION branch-scoped gate (allowance precedent).
2. `GET /api/branch-days/{branchDayId}/users` — assign-modal picker source: distinct attendance users for the day (relief included naturally — relief clock-ins are attendance rows), displayNames embedded, displayName ASC; ASSIGN_COMPENSATION gate; 404 on missing day.
3. `PATCH /api/expenses/{expenseId}` — amount/category/notes + expectedVersion, optimistic bump in one atomic update (0 rows → 409), same validation as POST (positive amount, 9-value enum), rides existing `/api/expenses/{expenseId}` before-filter (EDIT_BRANCH_DATA via record), day-state gate + audit UPDATE row (isFlagged on REMITTED).

## Key falsification finding

**F1 — expense has no version column**: ticket premised `expectedVersion` on expense, but the table never got one (compensation did, via V6). Added **`V11__add_expense_version.sql`** (`ALTER TABLE expense ADD COLUMN version INT NOT NULL DEFAULT 1;`). V1 untouched — Flyway checksums make editing applied migrations unsafe; the V6-pattern migration chain is the correct path. `ExpenseResponse` also gained `version` (route `toResponse` updated).

## Key decisions (recorded in full on the resolution comment)

1. **Comp list filter = payingBranchDayId** — the per-user-per-day identity; work≡paying per #101 F6 so either column works, but the unique index keys the day.
2. **PATCH = full-replace semantics** — omitted `notes` clears it, mirroring `CompensationRepository.update` exactly. The frontend edit modal (#101 D6) always sends the complete row payload, so no wipe-in-practice. Documented for the Finance build.
3. **PATCH on soft-deleted expense → 400 ValidationException** — can't edit history; delete+re-log is the flow.
4. **k6 deferred** — no frontend consumer yet (#98/#115/#116 precedent).
5. **No pagination** — YAGNI (#115 precedent).

## Fog note (surfaced this session, for the Finance build)

**#101 F4's "expense GET includes soft-deleted rows" is wrong in current code** — `ExpenseRepository.findByBranchDayId` filters `deleted_at IS NULL` (verified live). #101 D6 wanted deleted rows shown dimmed + reason, but the payload can't carry them. The future Finance build ticket must decide: extend GET to include deleted rows (or drop the dimmed-row requirement).

## Tests

- `CompensationServicePostgresTest` +4: list w/ names, cross-day exclusion, empty day, 404 missing day.
- `ExpenseServicePostgresTest` +6: success+bump, notes-clear, wrong-version 409, missing 404, deleted 400, audit row.
- `FinanceReadBackAuthzTest` (new, 15): allowed w/ payload (names + version bump), gate split (EDIT-only gets 403 on ASSIGN-gated reads; ASSIGN-only gets 403 on expense PATCH), cross-branch 403s, missing-day 404 with grant, 409 version mismatch, 400 invalid amount/category, per-day isolation.
- New `DatabaseTestHelper.insertTestAttendance` (insertIgnore + explicit clockIn — the documented defaultExpression caveat).

## Patterns + learnings (cumulative)

- **Version-column check before trusting expectedVersion premises**: #117's only falsification miss was assuming expense had a version column like compensation. The V6/V11 pattern (migration + model + repo optimistic update + 409) is now a two-instance precedent — any future "edit this record" ticket should verify the version column exists in `V1__full_schema.sql` first.
- **New route objects don't need Main.kt changes** when the route lives in an existing registered object (BranchDayRoutes/CompensationRoutes/ExpenseRoutes already registered).
- **Exposed 1.3.1 has no `ColumnSet.slice` top-level** — use `Slice(join, listOf(...))` constructor directly (compile-error discovery; `withDistinct` on the joined FieldSet).
- **Test-DB stale-data cascade**: a single leaked row (e.g. untracked child rows → teardown FK violation) makes every subsequent test in the class fail with dup-key on the shared branch name — track **all** rows a test inserts (the untracked-`secondUser`-comp leak cost a full failure cascade; `bash scripts/clean-test-db.sh` fixes the DB, but the leak itself was a test bug).
- **Cross-branch-day assertions in route tests**: a different branch's day gives 403 (no grant) — for "returns only rows for day X" assertions, create a second day on the **same branch** (`createBranchDayForDate(branchId, yesterday)`).
- Prior-session patterns unchanged: falsification-before-build, route-literal consistency, per-route before-filters (the #114 F1 hole), `-F` vs `-f` sub-issue wiring, unpushed commits on `ralph/company-app-full-build`.

## Current frontier (verified live post-session)

Per `gh api "repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100"` — 4 open children, all unassigned, all unblocked:

| # | Title | Type | Notes |
|---|-------|------|-------|
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling — **falsify at grill time**: remittance endpoints are draft/lines/day-breakdowns/submit + monthly summary |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling — owns monthly/all-time exports (#101 D8 deferred them here) |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero AFK tickets.** The next AFK work (Finance screen build, graduated from #101) can only be created by a future prototype session or by graduating it as the Finance build ticket — it was left ungraduated this session per one-ticket-per-session doctrine; the #101 resolution already declares it blocked-on-#117, now resolved.

**Standalone (NOT children of #89):** #110 ("Fix hardcoded-month dates...") still open + unassigned — Aug rollover mechanically fixed in `3a56fde`; #110 may be stale.

## Recommended next pick

- **#103 (Remittance)** — first in frontier order. **Falsify at grill time** (the #101-lesson): the ticket premises are "drafts, submit, detail" but the live remittance surface is draft/lines/day-breakdowns/submit + monthly summary (`MonthlyRemittanceSummaryRoutes` + `RemittanceRoutes` + `RemittanceService`). Check actual endpoint shapes, gates (SUBMIT_REMITTANCE), and the financial-snapshot immutability trigger before grilling.
- Any of #104/#105/#106 equally sharp-cuttable in parallel per wayfinder doctrine.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. **HITL prototype** per map Notes: /prototype + /grilling + /domain-modeling; falsification-before-claim against live backend (the map Notes + prior resolutions carry the backend-authoritative pattern); plain-language framing for multi-option questions (the #101 Q8/Q9 lesson: concrete scenarios beat abstract labels).
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`; also keep the child-tickets table + closing paragraph current).
4. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by` using numeric DB ids; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<numeric .id>` — **use `-F` not `-f` (integer vs string), verify new tickets appear in the sub-issues query**).
5. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling.

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: row #117 → ✅ closed.
- Decisions-so-far: new entry for #117 (F1 version-column falsification; 3 endpoints; 5 build decisions; tests; /code-review PASS; fog note on expense-GET-soft-deleted divergence from #101 F4/D6).
- Closing paragraph rewritten: frontier = 4 HITL prototypes, zero AFK; #117 gist; Finance build unblocked (its graduation deferred).
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, LocalNavHostController + poller-VM patterns, NotificationState.clear() maintenance point, pushed-route TopAppBar pattern, per-user commission-split drill-down, cross-day comp history.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #103–#106 (feature screen prototypes; falsification-first per screen).
- **`/research`** — for any Ktor / Compose / Material3 API surface the prototype needs to verify.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
