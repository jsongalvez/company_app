# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 12

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 12 resolved frontier ticket **#102** ("Prototype the Notifications screen UX (list, unread badge, mark-as-read)") — a HITL prototype, resolved by grilling (one question at a time) with the user. Resolution comment posted, ticket closed, prototype asset on branch `prototype/0102-notifications`, map #89 updated (Decisions-so-far + frontier table + 1 fog entry + closing paragraph), and **two build tickets graduated: #111 (backend mark-all-read endpoint, unblocked) + #112 (Notifications screen build, blocked by #111)**.

**The next session should pick the next frontier ticket — 9 unblocked remain** (#99–#106, all HITL prototypes, + #111, backend AFK-capable). **#111 restores the AFK route** — the first backend ticket since #98 closed; it's drivable with the same /implement flow as #98.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. the new #102 row, Not-yet-specified incl. the new read-history fog entry, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- Backend conventions: `backend/AGENTS.md` (load-bearing for #111 — Exposed DSL, NotificationRoutes/NotificationService/NotificationRepository pattern, *PostgresTest.kt)
- Frontend conventions: `composeApp/AGENTS.md` (for #112 or future prototype sessions)
- Spec: `docs/specs/0001-frontend-rebuild.md` — US-24 line 73; notifications have no other spec lines (queue model was a #102 decision)
- Domain glossary: `CONTEXT.md` (Notification term, line 71); architecture: `docs/architecture.md`
- **#102 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/102#issuecomment-5187756539**
- **Prototype asset: `docs/prototypes/0102-notifications-outline.md` on branch `prototype/0102-notifications` (commit `4abb724`, pushed)**
- Wayfinder-98 handoff (session 11): `docs/agents/wayfinder-98-handoff.md` — the session whose frontier this session inherited.

## Session outcome

**#102 (Notifications screen UX prototype) — resolved + closed.** Session drove the HITL grilling flow: loaded map + ticket body + prior-art (NotificationViewModel/NotificationState/NotificationBadgeViewModel live code, NotificationRoutes/NotificationService backend, NotificationResponse DTO, Route.kt, AppNavHost desktop/android actuals, CONTEXT.md term) → opened grilling with a **falsification finding** (ticket Q1 presupposed a mailbox; backend returns unread only) → four ticket Qs + two follow-ups resolved one at a time → prototype asset (outline, no rendered UI — all Qs were interaction-model/layout, per #96's "no UI prototype this time" precedent) → resolution comment → close → graduated tickets → map update.

## Key decisions (recorded in full on the resolution comment + outline)

1. **Model B (unread queue)** over A (expand backend to mailbox) / C (skip extras). Backend truth that drove it: `GET /api/notifications` returns **unread only** (`NotificationRepository.findUnreadByUserId`, `createdAt DESC`); no read-history, no mark-all, no un-read endpoints.
2. **D1 two-section screen**: Unread (full emphasis, live count in section label) + Read (rows marked-read-this-session, dimmed `ink-muted`; **in-memory only** — backend never returns read rows, self-cleans on re-entry). No unread markers anywhere (everything in list is unread).
3. **D2 row**: message (primary) + relative ts <24h / absolute past 24h (secondary); whole row tappable.
4. **D3 tap**: mobile → markRead + navigate `Route.SessionDetail(sessionId)`; desktop → markRead only (no desktop SessionDetail route — #91 lock; upcoming appointment may be future-dated so dashboard preseed could show nothing). **Mark all header button** → new backend mark-all endpoint (→ #111); badge set from authoritative response.
5. **D4 empty state**: "all caught up"; "view past notifications" button fog-gated (needs read-history endpoint).
6. **D5**: load on screen entry, **no in-screen polling** (user explicitly chose — drawer badge's 60s poll keeps count live); cold-start spinner; in-place error card + retry; 401 → existing #92 path.
7. **No new frontend infrastructure needed** — everything live: `NotificationViewModel` (`loadUnreadNotifications`, `markRead`), `NotificationState.decrementUnread`, badge poll, `NotificationResponse` DTO, mobile `Route.SessionDetail`.

## Graduated tickets (created this session, wired)

- **#111 "Add backend mark-all-read endpoint for notifications"** — `wayfinder:task`, unblocked, unassigned. Body carries: no capability gate (notifications ungated), atomic mark-all-unread for caller, **authoritative count in response** (count-now-marked vs resulting-unread — decide + say why; concurrent-new-notifications must not corrupt the badge), ownership-guarded 404s consistent with per-item read, Exposed DSL + `*PostgresTest` coverage (success / zero-unread / idempotent re-call), **Javalin route-shadowing pitfall** (`{notificationId}` param route can swallow a literal `read-all` path — register literal first or pick non-shadowing path). Out of scope: read-history + un-read endpoints (fog).
- **#112 "Build — Notifications screen (graduated from #102)"** — `wayfinder:task`, **blocked by #111** (native dependency wired). Body carries locked D1–D5 + live-infra list + conventions (ApiCallHandler, Linear tokens, ADR-0020 split at tap behavior only, #93 test patterns).

## Patterns + learnings (cumulative across sessions 7-12)

- **Falsification-before-claim paid off again** (#102 Q1 premise dead — mailbox vs queue). Same move as #96's stale-framing guard: check the backend reality before grilling the UX question. The user's mailbox instinct surfaced TWO backend gaps (read-history, un-read) + one decision (mark-all endpoint) — the grilling surfaced scope the ticket author didn't see.
- **User-side clarification mid-grilling**: user asked "is this architectural or just design? what do you need from me?" — answer: split locked-architectural facts from taste-calls, present the 4 ticket Qs as taste, got traction. Keep the "facts vs your call" split visible for future HITL sessions.
- **Option framing (A/B/C) with named costs** worked — user picked B with zero friction once costs were concrete (tickets + delay vs ship-now + fog).
- **Backend task tickets restore the AFK route** — #98 (session 11) was the last backend ticket; #111 is now the only non-HITL frontier item. Future sessions wanting AFK work: #111, or graduate orphan-code-cleanup / #94-grad Build (still fog).
- **Prototype asset precedent**: #97 used branch `prototype/0097-session-dashboard` + `docs/prototypes/0097-session-dashboard-outline.md`. #102 followed: branch `prototype/0102-notifications` + `docs/prototypes/0102-notifications-outline.md`. Works; pre-commit passed on a docs-only commit (skips ktlintFormat, runs backend gate + cleanliness + shared compile + Postgres check), push clean.
- Prior-session patterns unchanged and still load-bearing: MockEngine handler harness + Unconfined dispatcher (`TestHelpers.kt:54-67`), `runTest(testScheduler)` + `runCurrent/advanceTimeBy` drain + `vm.dispose()` pattern (`NotificationBadgeViewModelTest.kt`), pre-push ~4 min (iOS compile issue since fixed — commit `f2ca89c` dropped iOS compile from pre-push), `detektMetadataCommonMain` baseline 53, `detektMetadataCommonTest` task nonexistent.
- **ADR-0023 discriminator re-applied**: #102 did NOT graduate an ADR — every choice traced to existing axes (#91 desktop-persona, ADR-0020 split, ADR-0022 authoritative, #92 session-termination, #97 states).
- **MonthlyRemittanceSummaryServicePostgresTest month-rollover** (session-11 fix, 2026,7 → 2026,8): will break again Sep 1 — fog entry on map; future fix = relative-month dates.

## Current frontier (verified live post-session)

**9 unblocked tickets** (per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — confirmed #102 closed, #112 blocked_by=1):

| # | Title | Type | Suggested session shape |
|---|-------|------|------------------------|
| 99 | Prototype the Clients screen UX (search, detail, anonymization) | prototype | HITL grilling |
| 100 | Prototype the Inventory screen UX (stock levels, product sales, drill-down) | prototype | HITL grilling |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |
| 111 | Add backend mark-all-read endpoint for notifications | task | **AFK /implement (like #98)** |
| 112 | Build — Notifications screen (graduated from #102) | task | blocked by #111 (unblocked once #111 closes) |

**One blocked** (#112 ← #111). Eight HITL prototypes + one AFK backend task.

## Recommended next pick

- **#111 (backend mark-all-read endpoint)** — the AFK pick, restores the build-route. /implement flow identical to #98: read backend/AGENTS.md → NotificationRoutes/NotificationService/NotificationRepository + existing *PostgresTest patterns (e.g. `NotificationServicePostgresTest`-style) → implement → `:backend:detekt :backend:ktlintCheck :backend:test` + `:shared:compileKotlinJvm` → /code-review (parallel Standards + Spec) → commit. Watch: route-shadowing pitfall + authoritative-count semantics (already in ticket body).
- **#112** — once #111 closes, the Notifications screen build (dense prior-art: all infra live, prototype outline has the wireframe). Until then it's blocked — don't claim unless you want to wait.
- **Any of #99/#100/#101/#103/#104/#105/#106** — equally-sharp-cuttable HITL prototypes, all independent (may run in parallel per wayfinder doctrine). Same grilling pattern as #102: falsification-before-claim (check backend reality — e.g. does the Clients endpoint return `anonymized` flags? does Inventory have an endpoint at all?), facts-vs-taste split visible, option-framing with named costs.
- **Fog-graduation option (charting session, no ticket needed):** (a) orphan-code-cleanup (HomeScreen/ClientSearchScreen/SessionCreateScreen) — still sharp, still ready (absorb into #94-grad Build — Login + capabilities fetch + BranchSelect surface — the coherent option; #94-grad is also the first consumer of shipped `GET /api/me/branches` + the k6-baseline add-on for it); (b) detekt-gate strategy fork (a/b/c) — still cross-foggy.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST, before any work. Verify no concurrent sessions on the same frontier (sub-issues assignees empty).
2. If #111 (backend task): /implement flow per #98 — backend/AGENTS.md first, /code-review (parallel Standards + Spec) both-PASS before commit. Ticket body has requirements + pitfalls.
3. If #112 (frontend build, post-#111): /implement flow per #109 — composeApp/AGENTS.md + prototype outline + live VM/state code; end with /code-review.
4. If one of #99–#106 (prototype): /prototype + /grilling + /domain-modeling per map Notes (same pattern as #96/#97/#102). User preferences cumulative: prior-locks-as-load-bearing; falsification-before-claim; cost-of-sidestep-over-cost-of-accept; accepted-costs-named-not-hidden; fog-as-actionable-not-vague; honest failure-mode; least-restrictive-signature-as-API-principle; **facts-vs-taste split visible** (NEW this session — user asked "what do you need from me?", responded to the split). Check ktlint "no comment between args" rule placement BEFORE locking inline-comment placement. **Falsification checklist per screen**: verify the backend endpoint's actual payload + ordering BEFORE grilling list-layout questions (the #102 mailbox-vs-queue lesson) — e.g. does `GET /api/clients?search=` return anonymized flags? does Inventory even have an endpoint, or is that a backend gap like #98 was?
5. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** section (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
6. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking edges with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` — `gh api repos/<owner>/<repo>/issues/<n> --jq .id` for db-ids; clearing each graduated patch from **Not yet specified**). If the answer reveals a ticket sits past the destination, close it and add a line to **Out of scope**.
7. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling. ADR-0023 discriminator: when decisions trace back to existing ADRs through extensions of their axes, no new ADR (#102 didn't).

**One-ticket-per-session limit** (research is the only exception). When the chosen ticket is done, stop and hand off again. Write the next handoff doc to `docs/agents/wayfinder-<N>-handoff.md` where N is the resolved ticket number — follow the prior-session handoff format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #102 → ✅ closed (was 🔓 unblocked at session-11 end). Added #111 (🔓 unblocked) + #112 (🔒 blocked by #111). 8 frontier → 9 unblocked + 1 blocked.
- Decisions-so-far: new entry for #102 (falsification finding + model B + D1–D5 + graduations + ADR-0023 note; cites resolution comment URL + prototype branch).
- Not-yet-specified: **1 new fog entry**: notification read-history + un-read endpoints (Option A mailbox expansion, deferred by #102; also notes #111's count-semantics decision should keep the future read-history endpoint in mind).
- Closing paragraph: 9 unblocked (#99–#106 + #111) + #112 blocked; freshness-glow spent (graduated into #111/#112); AFK route restored via #111; prior fog items re-listed.
- Prior fog preserved: k6 baseline coverage for /api/me/branches (deferred until #94-grad Build), backend route-path literals, hardcoded-month tests, Desktop token storage hardening, PrimaryHover token, pre-BranchSelect drawer empty-state, orphan-code-cleanup (sharp, ready to graduate), LocalNavHostController pattern, shell-scoped poller VM lifecycle, logout/clock-out must append NotificationState.clear(), detekt-gate strategy fog.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for #111 (backend, like #98) or #112 (frontend, like #109).
- **`/code-review`** — at end of `/implement` flow: parallel Standards + Spec subagents. Run always.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #99–#106 (feature screen prototypes). Per map Notes; same pattern as #96/#97/#102. Falsification-first per screen (backend payload reality), facts-vs-taste split visible.
- **`/research`** — for any Ktor / Compose / Material3 / Exposed / Javalin API surface the build or prototype needs to verify (sources-jar unzip pattern from session-10).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md` for the next session to pick up.
