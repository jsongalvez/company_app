# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 32

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 32 resolved the AFK task **#129** ("Add date-range export endpoint (graduated from #105)") — `wayfinder:task`, driven by the agent alone (the map is 100% AFK). Resolution comment: https://github.com/jsongalvez/company_app/issues/129#issuecomment-5235460528. Ticket closed; map #89 updated (Decisions-so-far #129 entry, merged-build fog "2 remaining → last remaining backend task (#130)", frontier paragraph 3→2 unblocked + new #129 close sentence). Commit `6e63e51` on `ralph/company-app-full-build` (6 files, +314/−0, not pushed — k6 deferred per #98/#115 precedent; pre-commit gate passed: ktlint/detekt/729 tests/cleanliness/shared-compile).

**Next session pick:** any of the **2 unblocked tickets** (both AFK):

- **#130** — paged daily-summaries feed (keyset cursor per #122 precedent; window = `BranchReadScope.windowBranchIds` — the report window is the shared helper). The only remaining #105-grad backend task.
- **#135 Build — User Management screen** — unblocked (`blocked_by: 0`); spec locked (#106 D2–D5, wire existing unused `UserViewModel`; drawer visibility waits on #94-grad caps wiring). Backend surface complete + authz-tested.
- **Merged Finance & Reports build ticket** — ungraduated; opens when **#130** lands + the #117 expense-GET-includes-soft-deleted question is decided.
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #129 entry, Not-yet-specified, Out-of-scope, frontier paragraph)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#129 resolution comment: https://github.com/jsongalvez/company_app/issues/129#issuecomment-5235460528**
- Prior handoff: `docs/agents/wayfinder-128-handoff.md` (this session's parent)

## Session outcome

**#129 (Date-range export endpoint — ONE rollup row for a whole range) — resolved + closed (AFK).**

- **Falsification — the gate was already wired**: the ticket's "same gate as the sibling export routes" turned out to be **zero new gate code**. The #131 wildcard `before("/api/branches/{branchId}/export/*")` (branch-or-global `VIEW_BRANCH_DATA` via `requireBranchOrGlobalCapabilityForBranchId`) already fires on the new 5-segment `/export/range` route — a trailing `*` is a true prefix wildcard, so **any new sibling route under the wildcard is automatically gated**. Proven red-first: the range path was added to `ReportsReadScopeAuthzTest`'s matrices before the route existed — no-grant callers went 404 (route missing), then 403 (filter fires) — the matrix locks the wildcard-fire (403 never 404-gate-pass).
- **Shipped**: `GET /api/branches/{branchId}/export/range?from=&to=&format=csv|pdf` — **ONE rollup row** (sums of grossIncome/totalCompensation/totalExpenses/netIncome/totalProductSales/totalCommission across the window's `daily_sales_summary` rows, shared `dailyHeaders()` six columns); `ExportRoutes.handleRangeExport` + extracted `parseRequiredDate` (missing from/to 400, invalid date 400, `from > to` 400, format required 400 — the documented null-check-then-throw pattern); `ExportService.exportRange` (findBranch → window fetch → empty → 404 "No data for this branch and date range"; zero-activity days inside the window count as zero rows since the view emits one row per existing `branch_day`); `DailySalesSummaryRepository.findRangeByBranch` (inclusive `greaterEq`/`lessEq` window, date ASC).
- **Rollup semantics**: netIncome = Σgross − Σcomp − Σexp (the view has no net column — consistent with the repo's per-row mapper); commission keeps scale 4 (`200.0000`); boundaries inclusive.
- **Tests**: +10 (suite 729, was 719). Service (5): exact CSV rollup row asserted **verbatim** (`4000.00,800.00,300.00,2900.00,400.00,200.0000`) + header-and-one-row count, PDF magic bytes, boundary exclusion + exact zero-row `0.00,0.00,0.00,0.00,0.00,0.0000` for an in-window zero-activity day, 404 empty range, 404 unknown branch. Route 400s (5): missing from/to, invalid date, from>to, missing + invalid format. Authz matrix: range added to both path lists — zero-grant 403, branch-scoped 404 gate-pass on granted / 403 on other branch, GLOBAL holder 404 on a zero-grant branch.
- **Key decisions**: (1) rollup computed in the service via `fold` (sibling pattern; window bounded by the branch's day count — no arbitrary range cap invented, not in spec); (2) **`seedDayFinancials` test helper tracks rows AS it inserts** (see new learning below — mid-seed throw safety); (3) filename `range-export-{branch}-{from}-{to}` (descriptive, sibling `<kind>-{branch}-…` convention).
- Review: /code-review rounds 1+2 PASS (r1: 0 hard violations both axes; 3 soft fixes — exact-zero-row assertion replacing weak `contains("0.00")`, `assertFalse` over `assertTrue(!…)`, `format=invalid` parity test; r2: fix delta clean — though ktlint still caught a wrapped-line nit at the gate, fixed before commit).

## Patterns + learnings (cumulative across sessions)

- **NEW — bundled test-seeding helpers MUST trackOwned as they insert, not at the end.** The red phase exposed the failure mode: `seedDayFinancials` inserted everything then tracked at the end; a mid-seed constraint violation (expense `amount > 0` check, `expense_amount_check`) aborted the helper **before any tracking ran** — the already-inserted rows (user/client/category/product) became permanent DB litter that silently poisoned every later test class in the same JVM (symptom: `UserManagementAuthzTest`/`UserServicePostgresTest` saw 5 users instead of 3; `ReportsReadScopeAuthzTest`'s accessible-branches test saw a 3rd branch). Fix: track each row immediately after its insert. When a failing red-phase test leaks rows, the wipe recipe is `docker exec <postgres> psql -U company_user -d company_app_test -c "TRUNCATE <data tables> CASCADE"` — keep `role`/`role_capability`/`capability` (V2 seeds, NOT test data; TRUNCATE does not touch flyway_schema_history). Also: psql is NOT on the host — use `docker exec $(docker ps -q --filter name=postgres | head -1) psql …`.
- **NEW — `String.lines()` includes a trailing empty line** for a trailing newline (`"a\nb\n".lines()` = `[a, b, ""]`): CSV row-count assertions must filter `isNotBlank()` or count data rows by content.
- **NEW — the wildcard gate generalizes to NEW siblings**: the #131 wildcard `/api/branches/{branchId}/export/*` means future export routes need NO new filter — the authz matrix (403 for zero-grant) is the proof. The #114 lesson's exact-segment rule applies to *literal* filters; wildcard `*` suffixes are true prefix wildcards (JWT filter `before("/api/*")` matches all depths).
- Prior-session patterns unchanged: AFK flow = /implement per backend/AGENTS.md, red-first regression tests, quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred (#98/#115 precedent), resolution comment → close → map Decisions-so-far + fog + frontier paragraph, handoff.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V17 taken; **next is V18** (`ls backend/src/main/resources/db/migration/` before picking).
- **PG enum same-transaction rule** (from #132): `ALTER TYPE ... ADD VALUE` + use in one migration = 55P04; split into two migrations.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100`:

| # | Title | Type | Status |
|---|-------|------|--------|
| 130 | Add paged daily-summaries endpoint for the Reports feed (graduated from #105) | task | 🔓 unblocked |
| 135 | Build — User Management screen (graduated from #106) | task | 🔓 unblocked (verified `blocked_by: 0`) |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #129 closed this session — the whole #105-graduated set is down to **#130**.)

## Recommended next pick

- **#130** — the last #105-grad backend task; meatiest remaining backend work. `BranchReadScope.windowBranchIds` is the ready-made window; keyset cursor precedent = #122 (`(changed_at, id)` base64url opaque cursor); daily-summary view = `daily_sales_summary` (same view #129 rolled up); JSON twin precedent = `DailySalesSummaryRoutes` (the gate + route shape from #131).
- **#135 Build — User Management** — spec locked (#106 D2–D5), backend surface complete and authz-tested; `UserViewModel` already exists unused. Frontend build conventions per `composeApp/AGENTS.md` + /code-review rounds 1+2.
- **Merged Finance & Reports build** — ungraduated; opens after #130 + #117's expense-GET-soft-deleted decision.
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK flow (#130): /implement per `backend/AGENTS.md` (falsification-first against the ticket's body), quality gate, /code-review rounds 1+2, k6 deferral precedent (#98/#115). For #135 (build): /implement per `composeApp/AGENTS.md`.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND the merged-build fog line as counts change).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #129 entry (falsification — gate already wired via wildcard, rollup semantics, test matrix + exact-row assertions, track-as-you-go seeding decision, review outcome, resolution link).
- Not-yet-specified: merged-build fog updated ("2 remaining backend tasks (#129–#130)" → "last remaining backend task (#130)").
- Frontier paragraph: 3 → 2 unblocked (#130, #135); #129 close sentence (commit + resolution link + one-line gist).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for the AFK backend task #130 (parallel Standards + Spec; round 2 on the fix delta) or the #135 frontend build.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
