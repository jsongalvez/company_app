# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 13

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 13 resolved frontier ticket **#111** ("Add backend mark-all-read endpoint for notifications") — an AFK `wayfinder:task`, driven with the /implement flow (pattern #98). Implementation committed (`e96ca5c` on `ralph/company-app-full-build`, 6 files +172, **not pushed**), /code-review passed (Standards + Spec findings addressed), resolution comment posted, ticket closed, map #89 updated (frontier table + Decisions-so-far entry + closing paragraph). **No new tickets graduated — but #112 (Notifications screen build) is now UNBLOCKED.**

**The next session should pick the next frontier ticket — 9 unblocked remain** (#99–#106, all HITL prototypes, + #112, frontend build, AFK-capable). **#112 is the AFK pick** — the frontend build consuming this session's endpoint; drivable with the same /implement flow as #109.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. the new #111 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- Frontend conventions: `composeApp/AGENTS.md` (load-bearing for #112 — ApiCallHandler, ViewModel patterns, test patterns)
- Backend conventions: `backend/AGENTS.md` (for the new endpoint's shape, or future backend work)
- Spec: `docs/specs/0001-frontend-rebuild.md` — US-24 line 73; notifications have no other spec lines (queue model was a #102 decision)
- Domain glossary: `CONTEXT.md` (Notification term, line 71); architecture: `docs/architecture.md`
- **#111 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/111#issuecomment-5191707505**
- **#102 resolution comment (the UX lock this endpoint serves): https://github.com/jsongalvez/company_app/issues/102#issuecomment-5187756539**
- Prototype asset: `docs/prototypes/0102-notifications-outline.md` on branch `prototype/0102-notifications` (D3 Mark all button wireframe)
- Wayfinder-102 handoff (session 12): `docs/agents/wayfinder-102-handoff.md`

## Session outcome

**#111 (mark-all-read endpoint) — resolved + closed.** AFK flow: claimed ticket → read backend/AGENTS.md + ticket body + #102 resolution + live NotificationRoutes/Service/Repository + NotificationServicePostgresTest + RouteValidationTest patterns → implemented → quality gate → /code-review (parallel Standards + Spec) → fixed findings → commit. Two "pick one, say why" obligations in the ticket body resolved in the resolution comment: count semantics + verb/path.

## Key decisions (recorded in full on the resolution comment)

1. **Count semantics: return resulting unread count** (`NotificationMarkAllReadResponse.unreadCount`, new `@Serializable` DTO in shared). Frontend assigns badge directly from response — no `old − marked` client arithmetic, which is the only corruption path for concurrent new notifications (an arrival committed before the count statement is INCLUDED; after the count → caught by next 60s poll). Count-now-marked rejected: arrival between fetch and response invisible to client arithmetic → badge undercounts. Re-call stable (0→0) → idempotent by construction. ADR-0022 authoritative axis.
2. **Verb/path: `POST /api/notifications/read-all` (not PATCH).** Param route is `PATCH /api/notifications/{notificationId}/read` (4 segments) vs literal (3) — different method AND segment count, shadowing structurally impossible regardless of registration order. Literal still registered BEFORE param route (belt-and-suspenders). Route test proves param route still matches.
3. **Atomicity**: single `transaction {}` — update `isRead=true, readAt=CurrentTimestampWithTimeZone` where `userId = caller AND isRead = false`, then count remaining for caller.
4. **Ownership**: WHERE-clause scoped to caller; no 404 case (no resource id); zero-unread = valid no-op → 0.
5. **No audit row** — consistent with ALL existing notification mutations (insert + per-item markRead skip audit; notifications are ephemeral push records). Code-review flagged AGENTS.md audit rule; kept for consistency — pre-existing deviation across the notification table.
6. **No capability gate** (notifications ungated); masked UUID logging (`maskUUID`, AuthService.kt:78 pattern).
7. **k6 baseline deferred** — no frontend consumer yet (the consumer IS #112); same deferral logic as #98.
8. **No JMH** — user-scoped single-row update+count, not a hot path.
9. **No new ADR** (ADR-0023 discriminator): count-authoritative = ADR-0022 axis; no-audit/ungated = pre-existing pattern; verb choice = routing convention.

## Graduated tickets

- **None this session.** #112 was already created (by #102) and its blocker (#111) is now closed → **#112 is unblocked, unassigned, takeable**.

## Patterns + learnings (cumulative across sessions 7-13)

- **AFK backend task flow works again** (#111, pattern #98): backend/AGENTS.md → read ticket body + resolution comment + live code + tests → implement → `:backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm` → /code-review (parallel) → fix → commit. Pre-commit hook adds test-data cleanliness check — **failed runs leak rows into the test DB** (aborted @AfterEach cleanup); `bash scripts/clean-test-db.sh` fixes; re-commit after.
- **`idx_notification_unique(session_id, user_id)` gotcha**: can't insert 2 notifications for the same (session, user) — test data needs one-session-per-notification (helper `insertNotificationForNewSession` in NotificationServicePostgresTest).
- **Javalin testtools**: `client.post(path)` / `client.patch(path)` take no body (json param default null); response `.body` is `ResponseBody` with `.string()` (not String). KotlinxSerializationMapper emits compact JSON — `contains("\"unreadCount\":0")` works.
- **Route-level tests live in RouteValidationTest.kt** (companion `createApp()` + `registerAllRoutes`); NotificationRoutes was NOT registered there before this session — now is (additive).
- Prior-session patterns unchanged and still load-bearing: MockEngine handler harness + Unconfined dispatcher (`TestHelpers.kt:54-67`), `runTest(testScheduler)` + `runCurrent/advanceTimeBy` drain + `vm.dispose()` pattern (`NotificationBadgeViewModelTest.kt`), pre-push ~4 min (iOS compile dropped — commit `f2ca89c`), `detektMetadataCommonMain` baseline 53, `detektMetadataCommonTest` task nonexistent.
- **Falsification-before-claim** still the HITL rule for #99–#106 (check the backend endpoint's actual payload before grilling list-layout questions).
- **MonthlyRemittanceSummaryServicePostgresTest month-rollover**: now on `2026, 8` — breaks again Sep 1 (fog entry on map; also #110 is a standalone open ticket for it, NOT a wayfinder child — see note below).

## Current frontier (verified live post-session)

**9 unblocked tickets** (per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — #111 closed, #112's blocked_by edge auto-resolved):

| # | Title | Type | Suggested session shape |
|---|-------|------|------------------------|
| 99 | Prototype the Clients screen UX (search, detail, anonymization) | prototype | HITL grilling |
| 100 | Prototype the Inventory screen UX (stock levels, product sales, drill-down) | prototype | HITL grilling |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |
| 112 | Build — Notifications screen (graduated from #102) | task | **AFK /implement (like #109)** |

**Zero blocked.** One AFK frontend build + seven HITL prototypes. (Note: #110 "Fix hardcoded-month dates in MonthlyRemittanceSummaryServicePostgresTest" is open + unassigned but is NOT a child of map #89 — it's a standalone issue, not on this map's frontier.)

## Recommended next pick

- **#112 (Notifications screen build)** — the AFK pick, now unblocked. /implement flow per #109: composeApp/AGENTS.md → prototype outline (`docs/prototypes/0102-notifications-outline.md` on branch `prototype/0102-notifications`) → live `NotificationViewModel`/`NotificationState`/`NotificationBadgeViewModel`/`NotificationResponse` + this session's `NotificationMarkAllReadResponse` + `POST /api/notifications/read-all` → build per D1–D5 → ViewModel tests (MockEngine harness) → /code-review (parallel) → commit. D3 wire: mark-all button calls new endpoint, badge = response.unreadCount (authoritative, no arithmetic). Read section is in-memory (D1). No in-screen polling (D5).
- **Any of #99/#100/#101/#103/#104/#105/#106** — equally-sharp-cuttable HITL prototypes, all independent (may run in parallel per wayfinder doctrine). Same grilling pattern as #102: falsification-before-claim (check backend reality — e.g. does the Clients endpoint return `anonymized` flags? does Inventory have an endpoint at all?), facts-vs-taste split visible, option-framing with named costs.
- **Fog-graduation option (charting session, no ticket needed):** (a) orphan-code-cleanup (HomeScreen/ClientSearchScreen/SessionCreateScreen) — still sharp, still ready (absorb into #94-grad Build — Login + capabilities fetch + BranchSelect surface — the coherent option; #94-grad is also the first consumer of shipped `GET /api/me/branches` + the k6-baseline add-on for it); (b) detekt-gate strategy fork (a/b/c) — still cross-foggy.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST, before any work. Verify no concurrent sessions on the same frontier (sub-issues assignees empty).
2. If #112 (frontend build): /implement flow per #109 — composeApp/AGENTS.md + prototype outline + live VM/state code; end with /code-review (parallel Standards + Spec).
3. If one of #99–#106 (prototype): /prototype + /grilling + /domain-modeling per map Notes (same pattern as #96/#97/#102). User preferences cumulative: prior-locks-as-load-bearing; falsification-before-claim; cost-of-sidestep-over-cost-of-accept; accepted-costs-named-not-hidden; fog-as-actionable-not-vague; honest failure-mode; least-restrictive-signature-as-API-principle; facts-vs-taste split visible. Check ktlint "no comment between args" rule placement BEFORE locking inline-comment placement. **Falsification checklist per screen**: verify the backend endpoint's actual payload + ordering BEFORE grilling list-layout questions (the #102 mailbox-vs-queue lesson) — e.g. does `GET /api/clients?search=` return anonymized flags? does Inventory even have an endpoint, or is that a backend gap like #98 was?
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** section (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking edges with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` — `gh api repos/<owner>/<repo>/issues/<n> --jq .id` for db-ids; clearing each graduated patch from **Not yet specified**). If the answer reveals a ticket sits past the destination, close it and add a line to **Out of scope**.
6. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling. ADR-0023 discriminator: when decisions trace back to existing ADRs through extensions of their axes, no new ADR (#111 didn't).

**One-ticket-per-session limit** (research is the only exception). When the chosen ticket is done, stop and hand off again. Write the next handoff doc to `docs/agents/wayfinder-<N>-handoff.md` where N is the resolved ticket number — follow the prior-session handoff format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #111 → ✅ closed (was 🔓 unblocked at session-12 end). #112 → 🔓 unblocked (was 🔒 blocked by #111; blocking edge auto-resolved on close). 9 frontier → 9 unblocked, 0 blocked.
- Decisions-so-far: new entry for #111 (count-semantics decision + POST-vs-PATCH decision + atomicity/ownership/no-audit/k6-deferral notes + test summary; cites resolution comment URL + commit).
- Not-yet-specified: unchanged (read-history fog entry stands — still notes future read-history endpoint should keep count-semantics in mind; k6 deferral entries stand).
- Closing paragraph: 9 unblocked (#99–#106 + #112); #111 closed; AFK route now carried by #112 (frontend build); next backend work fog-gated; prior fog items re-listed.
- Prior fog preserved: k6 baseline coverage for /api/me/branches + notifications read-all (deferred until consumers land), backend route-path literals, hardcoded-month tests, Desktop token storage hardening, PrimaryHover token, pre-BranchSelect drawer empty-state, orphan-code-cleanup (sharp, ready to graduate), LocalNavHostController pattern, shell-scoped poller VM lifecycle, logout/clock-out must append NotificationState.clear(), detekt-gate strategy fog.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for #112 (frontend build, like #109).
- **`/code-review`** — at end of `/implement` flow: parallel Standards + Spec subagents. Run always.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #99–#106 (feature screen prototypes). Per map Notes; same pattern as #96/#97/#102. Falsification-first per screen (backend payload reality), facts-vs-taste split visible.
- **`/research`** — for any Ktor / Compose / Material3 API surface the build or prototype needs to verify (sources-jar unzip pattern from session-10).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md` for the next session to pick up.
