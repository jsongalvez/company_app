# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 31

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 31 resolved the AFK task **#128** ("Public branch-type exports: drop VIEW_BRANCH_DATA gate on provincial/medical-mission") — `wayfinder:task`, driven by the agent alone (the map is 100% AFK). Resolution comment: https://github.com/jsongalvez/company_app/issues/128#issuecomment-5235182646. Ticket closed; map #89 updated (Decisions-so-far #128 entry, table row #128 → ✅ closed, #128–#130 counts → #129–#130 in Not-yet-specified + closing paragraph, stale "#131-scoped-out" claims cleaned). Commit `c5a7d2c` on `ralph/company-app-full-build` (2 files, +190/−11, not pushed — k6 deferred per #98/#115 precedent; pre-commit gate passed: ktlint/detekt/719 tests/cleanliness/shared-compile).

**Next session pick:** any of the **3 unblocked tickets** (all AFK):

- **#129** — date-range export endpoint (whole-range rollup, csv/pdf, window semantics live: `BranchReadScope` + `requireBranchOrGlobalCapabilityForBranchId`; #128 closed the last of the export-gate work).
- **#130** — paged daily-summaries feed (keyset cursor per #122 precedent; window = `BranchReadScope.windowBranchIds` — the report window is the shared helper).
- **#135 Build — User Management screen** — unblocked (`blocked_by: 0`); spec locked (#106 D2–D5, wire existing unused `UserViewModel`; drawer visibility waits on #94-grad caps wiring). Backend surface complete + authz-tested.
- **Merged Finance & Reports build ticket** — ungraduated; opens when #129–#130 land + the #117 expense-GET-includes-soft-deleted question is decided.
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #128 entry, Not-yet-specified, Out-of-scope, frontier table)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#128 resolution comment: https://github.com/jsongalvez/company_app/issues/128#issuecomment-5235182646**
- Prior handoff: `docs/agents/wayfinder-131-handoff.md` (this session's parent)

## Session outcome

**#128 (Public branch-type exports — drop VIEW_BRANCH_DATA gate on provincial/medical-mission) — resolved + closed (AFK).**

- **Falsification — the premise was already half-done** (matching #131's context comment): the 3-segment `before("/api/branches/export")` filter never fired on the 4-segment provincial/medical-mission routes (the #114 exact-segment lesson, **5th occurrence**), so those exports were already JWT-only — exactly the #105 D2 target state ("any logged-in user can download"). Proven by writing the new tests FIRST: all 4 ran green against the unfixed code (zero-grant 404/200, never 403). The ticket's "RouteValidationTest export authz cases need the flip" context line is **stale** — verified by grep: RouteValidationTest holds only 400-format-validation export tests; export authz lives in `ReportsReadScopeAuthzTest` (from #131).
- **Shipped**: dead filter removed from `ExportRoutes.register` (sibling comment now documents falsification + locked state); branch-scoped exports untouched (wildcard `/api/branches/{branchId}/export/*` VIEW_BRANCH_DATA gate stays). **Regression tests** (`ReportsReadScopeAuthzTest`, +4): (1) zero-grant user reaches both routes on all 4 path variants (incl. year+month filter) — 404 gate-pass never 403; (2) **200-with-data** for zero-grant user on both routes — seeds 2 minimal submitted remittances + financial snapshots, proves the public download path returns the CSV with correct rows; (3) **unauthenticated still 401** via new `createAppWithJwt()` factory mirroring Main.kt's real `before("/api/*")` JWT filter; (4) valid JWT → 404 gate-pass.
- **Key decisions**: (1) JWT test-app factory mirrors Main.kt rather than reusing the prod app — the X-Test-User bypass app pattern (this file + RouteValidationTest) can't exercise JWT; drift risk accepted + documented in a comment; (2) `seedSubmittedRemittance` bundles money fields in a private `FinancialSnapshot` data class (detekt LongParameterList threshold 6); (3) class KDoc amended — matrix stays deterministic except the one data-seeding test; (4) 200 assertions check body content (branch names + net income values), not just status.
- Tests: suite 719 green (was 715). ReportsReadScopeAuthzTest now 16 route cases; branch-scoped matrix untouched + green.
- **New learning — `headers()` is arg-less in javalin-testtools**: `response.headers()` returns `HttpHeaders` with `get(name): List<String>?` (case-insensitive); no `contentType` property on `Response`.

## Patterns + learnings (cumulative across sessions)

- **Javalin 7 before-filters match SEGMENT COUNT EXACTLY, not prefix** — now proven 6× (the #114 lesson: `/slots` 4-seg vs `/slots/swap` 5-seg in #134; `/assignments` POST/GET vs DELETE in #134; `/api/branches` 2-seg vs `/api/branches/{branchId}` 3-seg; `/export` 4-seg vs 5-seg in #131; `/branches/export` 3-seg vs 4-seg in #131/#128; `/accessible` 3-seg vs MANAGE_USERS 2-seg). **Any gate on a literal path must count segments against every route it claims to cover — and a test must prove it fires.** Wildcard `/path/*` is the fix when routes are deeper than the shared prefix. NOTE: the JWT auth filter `before("/api/*")` DOES match all depths — a trailing `*` is a true prefix wildcard; the exact-match rule applies to literals without wildcards.
- **Falsification-first paid off again**: the new tests ran green against the unfixed code, proving the deadness before the removal — no behavior changed, the removal was provably dead-code deletion. The 404-vs-403 technique remains the deterministic no-seeding gate-pass proof; one 200-with-data test proves the download path end-to-end (minimal seed: branch + remittance SUBMITTED + financial snapshot — the `monthly_remittance_summary` view needs no session/line/breakdown rows).
- **Test-factory JWT mirror pattern**: to regression-test "still 401", the bypass-app test factories can't do it — register the real `before("${ApiRoutes.API_PREFIX}*")` filter (JWT verify + `UnauthorizedResponse`) in a second factory, after the `Database.connect` filter. First time the real JWT filter has been exercised at route level in this suite.
- Prior-session patterns unchanged: AFK flow = /implement per backend/AGENTS.md, red-first regression tests, quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred (#98/#115 precedent), resolution comment → close → map Decisions-so-far + table + closing paragraph, handoff.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V17 taken; **next is V18** (`ls backend/src/main/resources/db/migration/` before picking).
- **PG enum same-transaction rule** (from #132): `ALTER TYPE ... ADD VALUE` + use in one migration = 55P04; split into two migrations.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100`:

| # | Title | Type | Status |
|---|-------|------|--------|
| 129 | Add date-range export endpoint (graduated from #105) | task | 🔓 unblocked |
| 130 | Add paged daily-summaries endpoint for the Reports feed (graduated from #105) | task | 🔓 unblocked |
| 135 | Build — User Management screen (graduated from #106) | task | 🔓 unblocked (verified `blocked_by: 0`) |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #128 closed this session — the whole #105-graduated set is down to #129–#130.)

## Recommended next pick

- **#129** — date-range export endpoint: gates trivially expressible via `requireBranchOrGlobalCapabilityForBranchId`; `BranchReadScope` + wildcard filter pattern from #131/#128 are the template; summary-view queries + ExportService pattern from #131's route test file.
- **#130** — meatiest remaining backend task; `BranchReadScope.windowBranchIds` is the ready-made window; keyset cursor precedent = #122 (`(changed_at, id)` base64url opaque cursor).
- **#135 Build — User Management** — spec locked (#106 D2–D5), backend surface complete and authz-tested; `UserViewModel` already exists unused. Frontend build conventions per `composeApp/AGENTS.md` + /code-review rounds 1+2.
- **Merged Finance & Reports build** — ungraduated; opens after #129–#130 + #117's expense-GET-soft-deleted decision.
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK flow (#129–#130): /implement per `backend/AGENTS.md` (falsification-first against the ticket's body), quality gate, /code-review rounds 1+2, k6 deferral precedent (#98/#115). For #135 (build): /implement per `composeApp/AGENTS.md`.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update BOTH the table row AND the closing paragraph; the frontier counts + blocker lists change as tickets close).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Table: #128 → ✅ closed (row now `| 128 | ... | task | ✅ closed | — |`).
- Decisions-so-far: new #128 entry (falsification finding, dead-filter removal, test matrix + JWT-mirror factory, 200-with-data seeding, scoped-out confirmation, review outcome, resolution link).
- Not-yet-specified: merged-build fog text updated ("2 remaining backend tasks (#129–#130)").
- Closing paragraph: frontier = 3 unblocked (#129–#130, #135); #128 closed with commit + resolution link; whole #105 set down to #129–#130.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for the AFK backend tasks #129–#130 (parallel Standards + Spec; round 2 on the fix delta) or the #135 frontend build.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
