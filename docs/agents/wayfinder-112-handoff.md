# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 14

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 14 resolved frontier ticket **#112** ("Build — Notifications screen (graduated from #102)") — an AFK `wayfinder:task`, driven with the /implement flow (pattern #109). Implementation committed (`659bdaf` on `ralph/company-app-full-build`, 8 files +705, **not pushed**), /code-review passed (parallel Standards + Spec — 1 Spec FAIL + several findings addressed), resolution comment posted, ticket closed, map #89 updated (frontier table + Decisions-so-far entry + closing paragraph). **No new tickets graduated. The notifications chain (#102 → #111 → #112) is complete — the map has ZERO AFK tickets left: frontier is 8 unblocked HITL prototypes (#99–#106).**

**The next session has no AFK pick.** All 8 remaining tickets are HITL prototypes requiring live grilling with the user: #99 (Clients), #100 (Inventory), #101 (Finance), #103 (Remittance), #104 (Audit Log), #105 (Reports), #106 (User Management). Alternative: a fog-graduation charting session (orphan-code-cleanup is sharp + ready; detekt-gate strategy is cross-foggy).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. the new #112 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- Frontend conventions: `composeApp/AGENTS.md` (load-bearing for any future build; ApiCallHandler, ViewModel patterns, test patterns)
- **#112 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/112#issuecomment-5192175477**
- #102 resolution (the UX lock #112 executed): https://github.com/jsongalvez/company_app/issues/102#issuecomment-5187756539
- #111 resolution (the endpoint #112 consumes): https://github.com/jsongalvez/company_app/issues/111#issuecomment-5191707505
- Prototype asset: `docs/prototypes/0102-notifications-outline.md` on branch `prototype/0102-notifications` (wireframe + all decisions)
- Prior handoff: `docs/agents/wayfinder-111-handoff.md`

## Session outcome

**#112 (Notifications screen build) — resolved + closed.** AFK flow: claimed ticket → read ticket body + prototype outline + #102/#111 resolutions + live NotificationViewModel/State/BadgeVM/DTOs/routes/TestHelpers → implemented → quality gate → /code-review (parallel Standards + Spec) → fixed findings (incl. 1 Spec FAIL) → committed. 13 new tests (8 VM + 5 timestamp).

## Key decisions (recorded in full on the resolution comment)

1. **Entry-scoped `viewModel()` — first use in the app** (was the Spec FAIL). Plain `remember { NotificationViewModel(apiClient) }` dies when the mobile push to SessionDetail disposes the destination composition → D3 "appears in Read (dimmed) on return" broke. `viewModel { NotificationViewModel(apiClient) }` scopes to the NavBackStackEntry's ViewModelStore (JetBrains navigation port's `NavBackStackEntry` implements `ViewModelStoreOwner` on all platforms) — survives push/pop, fresh entry = fresh VM = D1 self-clean. **Future per-route VMs should follow this; `remember` loses state across push/pop.**
2. **Mark-all + concurrent arrival → reload iff `unreadCount > 0`** — with no in-screen polling (D5), the reload is the only way an arrival (included in #111's authoritative count) surfaces on screen; zero count skips the round-trip.
3. **Double-tap guard** — move + badge decrement only if row still in unread list; no dup rows, no double decrement.
4. **kotlinx-datetime 0.7.1 pinned** — material3 1.10.0-alpha05 already forces 0.7.1 transitively (declared 0.6.2 got overridden → metadata/jar mismatch NoClassDefFoundError at test runtime). Uses stable `kotlin.time.Instant/Clock` (0.7.x kotlinx typealiases are deprecated). Absolute date "Aug 4" renders in Asia/Manila (backend domain zone).
5. Read rows non-interactive; "now" <1m floor; error card = Surface container (D5 literal "card").

## Graduated tickets

- **None.** #112 closed the notifications chain; nothing new surfaced.

## Patterns + learnings (cumulative across sessions 7-14)

- **AFK frontend build flow works** (#112, pattern #109): composeApp/AGENTS.md → ticket body + prototype outline + locked resolutions + live code + test patterns → implement → ktlintFormat + `compileKotlinDesktop` + `compileDebugKotlinAndroid` + `desktopTest`/`testDebugUnitTest` → /code-review (parallel) → fix → commit. Pre-commit hook: ktlint scoped-to-staged + `:backend:detekt` + `:backend:test` + test-data cleanliness + `:shared:compileKotlinJvm` + Postgres connectivity.
- **`viewModel()` entry-scoping is the per-route VM pattern now** (decision 1) — plain `remember` loses state across push/pop; entry-scoped VM survives push, self-cleans on fresh entry. First use in app; pattern-doc'd in resolution.
- **kotlinx-datetime version trap**: never declare a version below what material3/compose forces transitively — metadata (klib) vs jar mismatch compiles but throws NoClassDefFoundError at test runtime. Check `./gradlew :composeApp:dependencies --configuration desktopTestRuntimeClasspath | grep datetime` first.
- **kotlinx-datetime 0.7.x format DSL**: `LocalDateTime.Format { monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); dayOfMonth(Padding.NONE) }` — `monthName`/`dayOfMonth` are builder members (no import), `char` is a top-level ext in `kotlinx.datetime.format`, `LocalDateTime.format(...)` ext lives in package `kotlinx.datetime` (import `kotlinx.datetime.format` — the function). `LocalDateTimeFormat` class is internal.
- **HttpRequestRetry retries 5xx** — a 500-response failure test leaves state Loading (retry delays pending); use 401 for deterministic failure tests.
- **MockEngine handler routing**: method + `request.url.encodedPath`; stateful handlers via closure vars (GET count → serve different bodies per request). `jsonRespond` helper needs `MockRequestHandleScope` receiver. Don't shadow the imported `respond` with a same-named private fun (recursive type-check failure).
- Prior-session patterns unchanged: handler-based MockEngine + Unconfined (`TestHelpers.kt:54-67`), `runTest(testScheduler)` + `runCurrent`/`advanceTimeBy` drain + `vm.dispose()` where poll loops exist, pre-push ~4 min, detekt baselines unchanged.
- **Falsification-before-claim** still the HITL rule for #99–#106 (check the backend endpoint's actual payload before grilling list-layout questions).
- **MonthlyRemittanceSummaryServicePostgresTest month-rollover**: on `2026, 8` — breaks again Sep 1 (fog entry on map; #110 is a standalone open ticket for it, NOT a wayfinder child).

## Current frontier (verified live post-session)

**8 unblocked tickets** (per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — #112 closed):

| # | Title | Type | Suggested session shape |
|---|-------|------|------------------------|
| 99 | Prototype the Clients screen UX (search, detail, anonymization) | prototype | HITL grilling |
| 100 | Prototype the Inventory screen UX (stock levels, product sales, drill-down) | prototype | HITL grilling |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked. Zero AFK tickets.** (Note: #110 "Fix hardcoded-month dates in MonthlyRemittanceSummaryServicePostgresTest" is open + unassigned but NOT a child of map #89 — standalone issue, not on this map's frontier.)

## Recommended next pick

- **Any of #99/#100/#101/#103/#104/#105/#106** — the only takeable tickets; all HITL prototypes, all independent (may run in parallel per wayfinder doctrine). Same grilling pattern as #102: falsification-before-claim (check backend reality — e.g. does the Clients endpoint return `anonymized` flags? does Inventory have an endpoint at all, or is that a backend gap like #98 was?), facts-vs-taste split visible, option-framing with named costs. User preferences cumulative: prior-locks-as-load-bearing; falsification-before-claim; cost-of-sidestep-over-cost-of-accept; accepted-costs-named-not-hidden; fog-as-actionable-not-vague; honest failure-mode; least-restrictive-signature-as-API-principle. Check ktlint "no comment between args" rule placement BEFORE locking inline-comment placement.
- **Fog-graduation option (charting session, no ticket needed):** (a) orphan-code-cleanup (HomeScreen/ClientSearchScreen/SessionCreateScreen) — still sharp, still ready (absorb into #94-grad Build — Login + capabilities fetch + BranchSelect surface — the coherent option; #94-grad is also the first consumer of shipped `GET /api/me/branches` + the k6-baseline add-on); (b) detekt-gate strategy fork (a/b/c) — still cross-foggy. Also note: the map has no AFK path until a prototype graduates a build ticket (like #102 did) — HITL prototype sessions are the map's only forward motion right now.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST, before any work. Verify no concurrent sessions on the same frontier (sub-issues assignees empty).
2. If one of #99–#106 (prototype): /prototype + /grilling + /domain-modeling per map Notes (same pattern as #96/#97/#102). Falsification-before-claim per screen (backend payload reality).
3. If a future AFK build graduates: /implement flow — composeApp/AGENTS.md + locked prototype outline + live VM/state code; use entry-scoped `viewModel()` for per-route VMs; end with /code-review (parallel Standards + Spec).
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** section (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking edges with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` — `gh api repos/<owner>/<repo>/issues/<n> --jq .id` for db-ids; clearing each graduated patch from **Not yet specified**). If the answer reveals a ticket sits past the destination, close it and add a line to **Out of scope**.
6. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling. ADR-0023 discriminator: when decisions trace back to existing ADRs through extensions of their axes, no new ADR (#112 didn't).

**One-ticket-per-session limit** (research is the only exception). When the chosen ticket is done, stop and hand off again. Write the next handoff doc to `docs/agents/wayfinder-<N>-handoff.md` where N is the resolved ticket number — follow the prior-session handoff format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #112 → ✅ closed (was 🔓 unblocked at session-13 end). 9 frontier → 8 unblocked, 0 blocked. **Zero AFK tickets remain — all 8 are HITL prototypes.**
- Decisions-so-far: new entry for #112 (screen build — D1–D5 execution + 6 build decisions incl. entry-scoped viewModel() + arrival-reload + double-tap guard + kotlinx-datetime 0.7.1 pin; cites resolution comment URL + commit).
- Not-yet-specified: unchanged (read-history fog stands; k6 deferral entries stand — notifications read-all k6 baseline still deferred until... it now HAS a consumer, the screen. The k6 entry says "add when consumers land" — notifications read-all landed with #112; the baseline add-on is now a candidate, same status as /api/me/branches's).
- Closing paragraph: 8 unblocked (#99–#106), notifications chain complete, AFK route exhausted, prior fog items re-listed + two new pattern notes (viewModel() entry-scoping; kotlinx-datetime 0.7.1 pin).
- Prior fog preserved: k6 baseline coverage for /api/me/branches + notifications read-all, backend route-path literals, hardcoded-month tests, Desktop token storage hardening, PrimaryHover token, pre-BranchSelect drawer empty-state, orphan-code-cleanup (sharp, ready to graduate), LocalNavHostController pattern, shell-scoped poller VM lifecycle, logout/clock-out must append NotificationState.clear(), detekt-gate strategy fog.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #99–#106 (feature screen prototypes). Per map Notes; same pattern as #96/#97/#102. Falsification-first per screen (backend payload reality), facts-vs-taste split visible.
- **`/implement`** — only if a prototype graduates a build ticket (pattern #109/#112; entry-scoped `viewModel()` now the per-route VM pattern).
- **`/code-review`** — at end of any /implement flow: parallel Standards + Spec subagents. Run always.
- **`/research`** — for any Ktor / Compose / Material3 API surface the build or prototype needs to verify (sources-jar unzip pattern from session-10).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md` for the next session to pick up.
