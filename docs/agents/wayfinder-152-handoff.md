# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 49

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 49 picked the #151 handoff's top pick — the **notifications→SessionDetail build graduation** — as **#152 ("Build — Session detail GET + notifications-path fetch + desktop SessionDetail route (graduated from #151)")**: `wayfinder:task` AFK build ticket, created + wired as a child of #89 + claimed (assigned @me), built in 4 commits (`1e14b29` feat, `78d0bd3` pass-1 fixes, `51a26c0` pass-2 fixes, `f176be5` pass-3 test rename) on `ralph/company-app-full-build` (not pushed), phased review loop ran 3 passes, **exit at pass 3: 0 HARD across all four phases**, resolution comment posted, ticket closed. Resolution: https://github.com/jsongalvez/company_app/issues/152#issuecomment-5282114495. Map #89 updated (Decisions-so-far #152 entry + child-tickets table row + frontier paragraph rewritten).

**Next-session state:** **0 unblocked tickets** — unchanged. The notifications→SessionDetail fog line is now **CLOSED end-to-end** (decision #151 + build #152). The natural next move is the **#117 expense-GET-soft-deleted HITL gate** (→ merged Finance & Reports build) — the only remaining open decision on the map.

## Session outcome

**#152 (Build — Session detail GET + notifications-path fetch + desktop SessionDetail route) — created + resolved + closed (AFK).**

- **The #151 resolution's six locked decisions shipped verbatim** (P1 code-verified each): Q1 bearer-only gate (`NotificationRepository.existsForSessionAndUser`, any read state, WHERE shape served by `idx_notification_unique`, no migration); Q2 no day-state gate; Q3 `DashboardSessionResponse` reuse via the shared `mapDashboardSession` (now takes pre-grouped maps — dashboard list path groups once); Q4/Q5 `GET /api/sessions/{sessionId}` with 404 for both non-bearer and missing; Q6 **desktop scoped #91-lock revision landed** (`composable<Route.SessionDetail>` registered, notification entry point only, dashboard inline pane untouched, ADR-0020 updated); Q7 one-shot fetch only when `row == null` (dashboard path seeds Success from the nav-arg row — zero requests, test-pinned).
- **Backend**: `DashboardService.getSessionDetail` + the route in `SessionRoutes`; no capability filters — backend/AGENTS.md documents the deliberate exception to the capability-gated-reads rule; the missing-session 404 half is defensive-only (the `session_id` FK keeps dangling notifications out — the service-level test for it is unseedable, covered at route level).
- **Frontend**: `SessionDetailViewModel` (entry-scoped #112, `fetchStarted` double-fire guard, `@Volatile inFlight` retry guard), `SessionDetailScreen` (Loading / Error+Retry / content; logWarn on Error per convention), both NavHost actuals updated.
- **Tests**: backend +9 (suite 806→815), desktop +6 (suite 249→**255**). Every commit passed the pre-commit gate; desktop+Android compile green.
- **k6 deferred with a logged reason** (the #149 precedent): no HTTP channel seeds notifications — `NextAppointmentScheduler` is the only production writer; DevSeeder's GLOBAL grants can't pass the branch-scoped session-create gate. Land when a test-DB notification-seeding channel exists (recorded on the map).

## Patterns + learnings (cumulative across sessions)

- **Session-49 additions**:
  - **The Exposed v1 insert-lambda table-receiver trap, third occurrence** — the lambda receiver of `Table.insert { }` is the TABLE, so an unqualified test-FIELD name in the insert body resolves to the COLUMN and renders `notification.branch_id` as a literal into VALUES (Postgres: "invalid reference to FROM-clause entry at character 92"). #118 documented it as a test-data trap; #152 hit it again in a seed helper. Fix shape: helper parameters named like the columns (locals shadow the receiver) — `DatabaseTestHelper.insertTestNotification` now carries the trap warning. Candidate for the loop register.
  - **Debugging unrenderable SQL**: `ExposedSQLException` shows "Failed on expanding args" when the error-path SQL rendering itself fails — set `ALTER DATABASE <test-db> SET log_min_error_statement = error` and read the postgres container logs for the exact STATEMENT. Reset afterwards.
  - **Kotlin constructor params without val/var ARE invisible in member functions** (the `initialRow` compile error in pass 0) — capture to a private val when a member-function gate needs the param.
  - **Ktor's `HttpClient.get` is an extension** (`io.ktor.client.request.get`) and `.body()` needs `io.ktor.client.call.body` — missing imports cascade into bizarre "MatchGroup?" type-inference errors.
  - **FK integrity can make a defensive branch untestable** — the "notification for missing session → 404" service branch can't be seeded (FK rejects the dangling row); keep the branch (defensive, documented), cover the path at the route level.
- Prior-session patterns unchanged: AFK flow = create → claim → implement → phased loop (`git diff <last-pass-commit>` per pass, batch-fix commits) → resolution comment → close → map Decisions-so-far + child-tickets row + frontier paragraph → handoff. One ticket per session. No k6, branch not pushed.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD; pre-push gate excludes iOS intentionally (`.githooks/pre-push:23-30`).
- **`testDebugUnitTest` has a pre-existing 1-failure flake** (SessionBootstrapViewModelTest teardown) — not gated, not mine, recorded.
- **Flyway migration numbering**: V1–V18 taken; next is V19. #152 needed NO migration (idx exists).
- **Root AGENTS.md pre-push-iOS line still stale** (says iOS compiled; the hook compiles desktop+android only) — truth-class record, doc-fix candidate. Unchanged this session.

## Current frontier (verified live post-session)

Per `gh issue list --state open`: only #89 (map), #110 (standalone hardcoded-month test fix, NOT a child), #139 (standalone OpenAPI map) open. **0 open children of #89** — #152 created+closed this session. The notifications→SessionDetail fog line is CLOSED end-to-end; the #117 expense-GET HITL gate + the standing fog lines stay open (the k6-for-session-detail note now joins the k6-for-PATCH-endpoints note as "waits on a seeding channel").

## Recommended next picks

- **The #117 expense-GET-includes-soft-deleted question** — the only remaining open decision gate on the map (HITL grilling; decides the merged Finance & Reports build per #101 D6 dimmed-deleted rows). The natural next session when the user is available.
- **Remaining Not-yet-specified candidates** — unchanged: keep-last-results port, user-create flow, non-admin self-slot-edit, F7, desktop token-storage, pushed-route topbar (now MORE load-bearing — desktop has a second pushed route), audit branch-name, detekt gate strategy, #110, route-path constants, Clients search-field pop-back reset, hardcoded-month test dates, the #106 grant-UI fog, the root-AGENTS.md stale pre-push-iOS line.

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-50 has NO unblocked ticket — pick from "Recommended next picks". The **#117 gate** is the recommended move (HITL grilling, /grilling + /domain-modeling).
3. Claim BEFORE work: `gh issue edit <n> --add-assignee @me` — verify no concurrent sessions.
4. HITL flow: create → claim → resolve live with the user (never answer the user's side) → resolution comment → close → map update.
5. AFK flow (if a build graduates instead): /implement per module AGENTS.md, the phased loop with `git diff <last-pass-commit>` per pass, batch-fix commits, exit at one full pass with zero HARD. Register classes to watch: count-0, ownership-in-WHERE (#141 class), comment-truth (READ the hook files/AGENTS.md lines a phase cites), fix-that-didn't-land, **the Exposed insert-lambda table-receiver trap** (seed helpers: name params like the columns).
6. Post the answer as a **resolution comment**, then `gh issue close <n>`, then append a context pointer to map #89's Decisions-so-far + update the child-tickets table row + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
7. Graduate fog (create-then-wire): `gh issue create --label wayfinder:<type>` → child via `gh issue edit <n> --parent 89` (verify via `gh issue view <n> --json parent`).
8. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: #152 row added (✅ closed).
- Decisions-so-far: new #152 entry (full build record: six decisions shipped, tests, k6 deferral reason, 3-pass loop, register candidate, resolution link), inserted before Not-yet-specified.
- Frontier paragraph: rewritten — #152 outcome first (the #151 build graduation, decision + build CLOSED end-to-end), 0 unblocked held, what remains chartable listed (the #117 gate, the standing fog lines incl. the k6-waits-on-seeding notes).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket — 0 unblocked).
- **`/grilling` + `/domain-modeling`** — for the #117 expense-GET-soft-deleted HITL gate (the recommended pick).
- **`/implement` + `docs/agents/code-review-loop.md`** — if a build graduates instead (e.g. from the #117 answer); the register now effectively carries the Exposed table-receiver trap from #118/#152.
