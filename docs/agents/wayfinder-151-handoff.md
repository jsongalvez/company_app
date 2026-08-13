# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 48

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 48 picked the #150 handoff's top pick — the **notifications→SessionDetail data path grilling** — as **#151 ("Grilling — Notifications→SessionDetail data path: session-detail GET + read gate (graduated from #147 fog)")**: `wayfinder:grilling` HITL decision ticket, created + wired as a child of #89 + claimed (assigned @me), resolved live with the user in 3 grilling rounds, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/151#issuecomment-5280937357. Map #89 updated (Decisions-so-far #151 entry + child-tickets table row + frontier paragraph rewritten with the #151 outcome + "what remains chartable" narrowed).

**Next-session state:** **0 unblocked tickets** — unchanged. The notifications→SessionDetail **decision is MADE**; the natural next move is its **build graduation** (backend GET + frontend fetch-on-null + desktop route registration — a `wayfinder:task` sized for one session, spec = the #151 resolution). The other HITL gate (#117 expense-GET-soft-deleted → merged Finance & Reports build) stays open.

## Session outcome

**#151 (Grilling — Notifications→SessionDetail data path) — graduated + created + resolved + closed (HITL).**

- **The #147/#149 "notifications path renders a limited state (row=null)" line is now a decided build.** The design tree, all decided live (user picked A/A/A in round 1 — explicitly delegating ("no idea what's going on"), so decisions were re-explained in plain terms before locking; Q4/Q6/Q7 in round 3 after plain-term explanations):
  - **Q1 gate — bearer-only**: caller may fetch iff a notification row exists for `(sessionId, caller)`, any read state. The notification IS the authorization (the #141 markRead ownership shape; scheduler dedupes to ≤1 row per (session, user); covered by the existing UNIQUE `idx_notification_unique (session_id, user_id)` — **no migration**). Rejected: RECEIVE_NEXT_APPOINTMENT_ALERTS-at-branch (broader), VIEW_BRANCH_DATA (AGENTS.md read-gate letter, but recipients may lack it), attendance-scoped (breaks the +2-day use case).
  - **Q2 — NO day-state gate**: the #138 `checkBranchDayReadable` rule does NOT apply — the bearer check replaces it. Grounded fact: notified sessions are COMPLETED with `nextAppointmentDate = today+2` (NextAppointmentScheduler) → their branch_day is today-or-past → PAST/REMITTED `EDIT_PAST_DAY` would 403 the primary case. #138 governs browsing surfaces, not bearer-authorized pushes.
  - **Q3 — response = reuse `DashboardSessionResponse`**: no new DTO; byte-identical rendering with the dashboard path.
  - **Q4 — `GET /api/sessions/{sessionId}`** (session-keyed, reusable; the bearer check = one indexed lookup). Rejected: notification-keyed route (authz structural but notification-only).
  - **Q5 — 404 for BOTH non-bearer and missing** (the #141 foreign-row precedent; one UI fallback).
  - **Q6 — desktop: scoped #91-lock REVISION** (user: "desktop is the main platform"): desktop notification tap = markRead + push `Route.SessionDetail` (desktop composable currently unregistered — AppNavHost.desktop.kt:278), same commonMain `SessionDetailScreen` via the GET, content-level Back (ClientDetail precedent; pushed-route topbar pattern stays fog). Dashboard keeps its inline master-detail pane — the pushed route exists ONLY for the notification entry point.
  - **Q7 — one-shot fetch, only when `row == null`**: dashboard path keeps passing the enriched row (zero extra requests — the user's perf concern answered: per-tap = 1 GET + 1 PATCH, human-rate; no batch-fetch pattern in-app; bearer query already indexed; k6 metric + 3-run baseline + threshold at build time per the #140 precedent).
- **Perf question answered with facts** (user: "bajillion fetches?") — schema-verified: `idx_notification_unique` (V1:577) serves the bearer query; the app's only recurring fetch is the 30s dashboard poll, already k6-baselined.
- **No code changes** (decision ticket only); no tests, no commits.

## Patterns + learnings (cumulative across sessions)

- **Session-48 additions**:
  - **When the user answers "no idea, A" — the grilling isn't done, the framing is wrong.** Round-1 answers were blind delegation; re-presenting the locked decisions in plain user-visible terms (who-can-see-what, "old-day rule skipped because the notification is the permission", "screen renders exactly like the dashboard") converted the blind picks into informed ones and unblocked the real decisions (Q6 desktop revision, the back-navigation question) that a pure acceptance loop would have buried.
  - **The #91-lock revision pattern**: a user statement about the main platform ("desktop is the main platform") overrides a documented lock; the fix is a *scoped* exception (pushed route exists only for the notification entry point; dashboard UX untouched), recorded in the ticket as a deliberate revision, not a silent build deviation.
  - **Fact-first grilling**: every decision was grounded in schema/service facts verified before asking (scheduler eligibility query, notification table indexes, day-state of notified sessions, existing route registration gaps) — the user was never asked a question that a grep could answer.
- Prior-session patterns unchanged: HITL flow = create → claim → resolve live → resolution comment → close → map Decisions-so-far + child-tickets row + frontier paragraph → handoff. One ticket per session. No k6, no commits, no branches touched.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (missing actuals incl. `SessionList`; pre-push gate excludes iOS intentionally — `.githooks/pre-push:23-30`).
- **`testDebugUnitTest` has a pre-existing 1-failure flake** (SessionBootstrapViewModelTest teardown) — not gated, not mine, recorded.
- **Flyway migration numbering**: V1–V18 taken; next is **V19**. The #151 build ticket needs NO migration (idx exists); a future one may.
- **Root AGENTS.md pre-push-iOS line still stale** (says iOS compiled; the hook compiles desktop+android only) — truth-class record from #150, doc-fix candidate for a docs session. Unchanged this session.

## Current frontier (verified live post-session)

Per `gh issue list --state open`: only #89 (map), #110 (standalone hardcoded-month test fix, NOT a child), #139 (standalone OpenAPI map) open. **0 open children of #89** — #151 created+closed this session. The notifications→SessionDetail decision is RESOLVED (build graduates from it); the #117 expense-GET HITL gate + the standing fog lines stay open; the k6-for-the-PATCH-endpoints note is recorded against the #106 grant-UI fog.

## Recommended next picks

- **The #151 build graduation — "Build — Session detail GET + notifications-path fetch + desktop SessionDetail route (graduated from #151)"**: the natural next move — the decision is fully made (6 locked decisions in the resolution), sized for one AFK/light-HITL session. Backend: `GET /api/sessions/{sessionId}` + bearer check (notification row exists for (sessionId, caller), any read state) + 404-for-both + reuse the dashboard enrichment join + tests (service: bearer-pass/fail, missing session, cross-user; route authz: 200, 404-no-notification, 404-missing, 401) + k6 metric/3-run baseline/threshold (#140 precedent). Frontend: fetch-on-null (only when `row == null`), entry-scoped VM, limited-state fallback + Retry, **desktop `composable<Route.SessionDetail>` registration** (currently absent — the scoped #91 revision) + content-level Back; tap stays markRead + navigate. **Watch items for the phased loop**: the bearer check must live in the repository WHERE clause (the #141 ownership class); the desktop route registration must not disturb the dashboard's inline pane; row==null fetch must not double-fire (entry-scoped VM, #112 pattern).
- **The #117 expense-GET-includes-soft-deleted question** — the other HITL gate (ungraduated); decides the merged Finance & Reports build.
- **Remaining Not-yet-specified candidates** — unchanged from the #150 handoff's predecessors (keep-last-results port, user-create flow, non-admin self-slot-edit, F7, desktop token-storage, pushed-route topbar — now *more* load-bearing since #151 adds a desktop pushed route, audit branch-name, detekt gate strategy, #110, route-path constants, Clients search-field pop-back reset, hardcoded-month test dates, the #106 grant-UI fog, the root-AGENTS.md stale pre-push-iOS line).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-49 has NO unblocked ticket — pick from "Recommended next picks". The **#151 build graduation** is the recommended move (decisions fully locked; AFK-doable); the #117 gate is the HITL alternative.
3. Claim BEFORE work: `gh issue edit <n> --add-assignee @me` — verify no concurrent sessions.
4. AFK flow: red-first falsification, /implement per module AGENTS.md, the phased loop with `git diff <last-pass-commit>` per pass, batch-fix commits, exit at one full pass with zero HARD. The register classes to watch: count-0 (version locks), ownership-in-WHERE (#141 class — the bearer check), comment-truth (READ the hook files/AGENTS.md lines a phase cites), fix-that-didn't-land.
5. Post the answer as a **resolution comment**, then `gh issue close <n>`, then append a context pointer to map #89's Decisions-so-far (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND the child-tickets table row).
6. Graduate fog (create-then-wire): `gh issue create --label wayfinder:task` → child via `gh issue edit <n> --parent 89` (verify via the child's `parent` field — `gh issue view <n> --json parent`).
7. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: #151 row added (✅ closed).
- Decisions-so-far: new #151 entry (full decision record: the 6 locked decisions + rejections + perf verification + next-graduation note + resolution link), inserted before Not-yet-specified.
- Frontier paragraph: rewritten — #151 outcome first (EIGHTH fog-graduation, HITL grilling, decision MADE / build remains), 0 unblocked held, what remains chartable listed (the #151 build graduation, the #117 gate, the standing fog lines).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket — 0 unblocked).
- **`/implement`** — for the #151 build graduation (backend GET + frontend fetch + desktop route) per the module AGENTS.md files.
- **`docs/agents/code-review-loop.md`** — the register now carries seven classes; the #151 resolution is the spec the build's loop verifies against (P1 axis = the six locked decisions).
- **`/grilling` + `/domain-modeling`** — if the user picks the #117 gate instead.
