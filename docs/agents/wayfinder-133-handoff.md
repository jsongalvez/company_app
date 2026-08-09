# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 28

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 28 resolved the AFK task **#133** ("User management backend: user list + reactivate + deactivated_at + self-guard") — `wayfinder:task`, driven by the agent alone (the map is 100% AFK). Resolution comment: https://github.com/jsongalvez/company_app/issues/133#issuecomment-5231584901. Ticket closed; map #89 updated (Decisions-so-far #133 entry, table row #133 → ✅ closed, closing paragraph — frontier 5 unblocked + #135 blocker text → #134). Commit `b3c21fc` on `ralph/company-app-full-build` (10 files, +613/−23, not pushed — k6 deferred per #98/#115 precedent; pre-commit gate passed: ktlint/detekt/687 tests/cleanliness/shared-compile).

**Next session pick:** any of the **5 unblocked AFK backend tasks** (all independent, all per `backend/AGENTS.md` + /code-review rounds 1+2):

- **#134 Gate hardening** — service-level MANAGE_USERS checks on swapSlots + assignment DELETE (the F3 security hole; authz regression tests proving the leak first). The #114 exact-path lesson, 3rd occurrence — route filters must be verified per-sub-path; service-level checks are the reliable location. **#135 (Build — User Management screen) is now blocked on #134 alone** (native deps auto-dropped #132/#133 — verified `blocked_by: 1`).
- **#128/#129/#130/#131** — the #105-graduated Reports/Finance backend set. **#131 coordinates with #132**: GLOBAL `VIEW_BRANCH_DATA` now has a real producer (SUPERUSER/ACCOUNTANT roles derive it); the accessible-branches endpoint should treat derived GLOBAL rows like direct ones (same view, no distinction needed).
- **#135 Build — User Management screen** — only when #134 closes; spec = #106 outline D2–D5 (wire the existing unused `UserViewModel`; drawer visibility waits on #94-grad caps wiring). `UserSummaryResponse` DTO is already in `shared/` (id/displayName/username/status/deactivatedAt/assignments[{branchId, branchName, slot}]) — the build ticket's payload is ready.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #133 entry, Not-yet-specified, Out-of-scope, frontier table)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#133 resolution comment: https://github.com/jsongalvez/company_app/issues/133#issuecomment-5231584901**
- Prior handoff: `docs/agents/wayfinder-132-handoff.md` (this session's parent)

## Session outcome

**#133 (User management backend) — resolved + closed (AFK).**

- **GET /api/users** — full list, no pagination: `UserSummaryResponse {id, displayName, username, status, deactivatedAt?, assignments:[{branchId, branchName, slot}]}` (active-only, `ended_at IS NULL`, slot ASC), sorted `displayName ASC, username ASC`. Exact-path GLOBAL `MANAGE_USERS` before-filter (the #114 lesson — each sub-path carries its own filter; authz-tested per sub-path).
- **PATCH /api/users/{userId}/reactivate** — INACTIVE→ACTIVE, `deactivated_at` cleared, audit row, 204. DenyList untouched — old tokens stay dead (in-memory, auto-evicts; test-asserted).
- **V17__add_user_deactivated_at.sql** — `deactivated_at TIMESTAMPTZ` nullable (V15/V16 taken; V14 never existed; **V18 is free**).
- **Self-deactivate guard** — service-level `ValidationException("cannot deactivate yourself")` → 400 through HTTP (test-proven); the GLOBAL gate alone couldn't stop a holder locking themselves out.
- **Idempotent deactivate/reactivate pair** (round-1 Spec finding): re-deactivating an already-INACTIVE user previously reset `deactivated_at` + wrote a spurious audit row (corrupting the "deactivated X ago" display). Both ops now short-circuit on no-op (no update/audit/timestamp reset); DenyList refresh still fires on deactivate retries (harmless).
- **Exposed 1.3.1 receiver trap — NEW LEARNING, documented for the repo**: `insert {}`'s lambda receiver is the **TABLE** (`T.(InsertStatement<Number>) -> Unit`), so an unqualified name colliding with a table column resolves to the COLUMN, not the enclosing scope: `it[UserBranchAssignmentTable.branchId] = branchId` silently emits `VALUES (..., user_branch_assignment.branch_id, ...)` → PG error "invalid reference to FROM-clause entry" (42P01-ish). Function params and locals WIN the resolution; object/class properties LOSE. Production code is immune (repos use `params.*`); the trap is documented in `DatabaseTestHelper.insertTestAssignment`'s KDoc. **Any future test that hand-writes `Table.insert { }` with class-field values is at risk.**
- Tests: 20 new (10 service + 6 route-authz in new `UserManagementAuthzTest` + helper); suite 687 green. Direct grants via `DatabaseTestHelper.grantManageUsers` throughout (ADR-0023 — no role-derivation assumption; no production seed exists).

## Key decisions (recorded in full on the resolution comment + map #89)

1. **Idempotency pair** — deactivate and reactivate both no-op on already-in-the-target-state; no audit row, no timestamp reset. Symmetric, retry-safe (backend convention).
2. **`UserSummaryResponse.id` included** beyond the spec's field list — the reactivate target; #117 additive-nullable precedent. `status`/`deactivatedAt` as String (no shared UserStatus enum; MeService precedent).
3. **Assignment-with-branchName join in `UserRepository.findActiveAssignmentsWithBranch`** — UserBranchAssignmentRepository was at its 11-function detekt ceiling (`TooManyFunctions`); the query serves the user list.
4. **reactivate doesn't clear DenyList** — old tokens stay dead by design (spec note); a reactivated user logs in fresh.
5. **Test-DB pollution recovery**: the crashed-run cascade (untracked assignment row → teardown FK failure → leftover rows → duplicate-branch failures in every later test) is fixed by tracking every inserted row; if it recurs, `bash scripts/clean-test-db.sh` is the recovery.

## Patterns + learnings (cumulative across sessions)

- **Exposed `insert {}` receiver = Table** (see above) — the single most dangerous silent-footgun in this codebase's test land. Always pass `params.*`/locals; never bare class fields whose names match columns.
- **Javalin testtools `client.patch(path, body)`** — there is NO `patch(path, Consumer<Request.Builder>)` overload; the consumer lands in the BODY slot and KotlinxSerializationMapper tries to serialize the lambda ("Serializer for class X$$Lambda is not found"). Use `patch(path, null, consumer)`. (get/post/put/delete DO have consumer overloads.)
- **Review-round traps on behavior**: round-1 Spec caught a real spec bug (deactivate timestamp reset) that only the "deactivated X ago" display semantics expose — the fix is a pair of idempotency tests (audit count + timestamp preserved). Round 2 confirmed clean.
- Prior-session patterns unchanged: AFK flow = /implement per backend/AGENTS.md, falsification-first (this ticket's premises — reactivate missing, deactivated_at missing, self-guard missing — were verified against UserRoutes.kt/UserRepository.kt before building), quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred (#98/#115 precedent), resolution comment → close → map Decisions-so-far + table + closing paragraph.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: check `ls backend/src/main/resources/db/migration/` before picking a number. V17 taken by #133; **next is V18**.
- **PG enum same-transaction rule** (from #132): `ALTER TYPE ... ADD VALUE` + use in one migration = 55P04; split into two migrations.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100` (`issue_dependencies_summary` — `blocked_by` = open blockers only):

| # | Title | Type | Status |
|---|-------|------|--------|
| 128 | Public branch-type exports: drop VIEW_BRANCH_DATA gate on provincial/medical-mission (graduated from #105) | task | 🔓 unblocked |
| 129 | Add date-range export endpoint (graduated from #105) | task | 🔓 unblocked |
| 130 | Add paged daily-summaries endpoint for the Reports feed (graduated from #105) | task | 🔓 unblocked |
| 131 | Fix Reports access scope: GLOBAL VIEW_BRANCH_DATA = all branches + accessible-branches endpoint (graduated from #105) | task | 🔓 unblocked |
| 134 | Fix ungated slot-swap + assignment-delete (graduated from #106) | task | 🔓 unblocked |
| 135 | Build — User Management screen (graduated from #106) | task | 🔒 blocked by #134 |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #132 + #133 closed this and the prior session. **The map is 100% AFK — all HITL prototypes closed.**)

## Recommended next pick

- **#134** — the F3 security hole, closes the last blocker on #135; service-level MANAGE_USERS on swapSlots + assignment DELETE with leak-first authz regression tests.
- **#128–#131** — the Reports/Finance backend set; #131 recommended first (unblocks the whole Reports read surface; coordinate-ready with #132's derived GLOBAL VIEW_BRANCH_DATA), #128 smallest.
- **#135 (Build — User Management)** — only when #134 closes; spec = outline D2–D5; payload DTO already shipped.
- **Merged Finance & Reports build ticket** — ungraduated; opens when #128–#131 land + the #117 expense-GET-includes-soft-deleted question is decided. Its spec = #105's outline (D1–D7) + #101's outline (edit-mode D1–D8) + #117 read-backs.
- **Candidate backend fog (small AFK hardening tickets)**: the cross-draft line-session raw 500 (#120 fog — `idx_remittance_line_session` insertIgnore→`.single()` NoSuchElement) and the `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK flow (#128–#131, #134): /implement per `backend/AGENTS.md` (falsification-first against the ticket's body — #134 encodes a service-level-enforcement mandate), quality gate, /code-review rounds 1+2, k6 deferral precedent (#98/#115).
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update BOTH the table row AND the closing paragraph; the frontier counts + blocker lists change as tickets close).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Table: #133 → ✅ closed; #135's blocker text → #134 (native deps auto-dropped #132/#133 — verified `blocked_by: 1`).
- Decisions-so-far: new #133 entry (endpoints, V17, idempotency pair, Exposed receiver trap, test counts, review outcome, #135-notice, V18 note, resolution link).
- Closing paragraph: frontier = 5 unblocked (#128–#131, #134) + #135 blocked on #134; #133 closed with resolution link; merged Finance & Reports build still ungraduated.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for the AFK backend tasks #128–#131, #134 (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
