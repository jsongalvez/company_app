# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 11

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 11 picked frontier ticket **#98** ("Add GET /api/me/branches endpoint (assigned branches + clock-in status)") — the only backend + only AFK-capable ticket on the frontier (per session-10 handoff recommendation), resolved it, posted the resolution comment, closed it, and updated map #89's Decisions-so-far + frontier table + closing paragraph + added three fog entries per the wayfinder "Work through the map" doctrine.

**The next session should pick the next frontier ticket — 8 unblocked remain** (one less than session-10's 9, after #98's close). **The frontier now has NO backend / NO AFK-build tickets** — all 8 remaining are HITL prototypes (#99–#106).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. the new #98 row appended this session, Not-yet-specified incl. 3 new fog entries, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- Backend conventions: `backend/AGENTS.md` (load-bearing for this session's work — Exposed DSL, MeRoutes/MeService pattern, *PostgresTest.kt)
- Frontend conventions: `composeApp/AGENTS.md` (for future prototype sessions)
- Spec: `docs/specs/0001-frontend-rebuild.md` — BranchSelect/clock-in status lines 185-190; backend test decisions lines 141-160
- Domain glossary: `CONTEXT.md`; architecture: `docs/architecture.md`
- **#98 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/98#issuecomment-5186251079**
- **Commit (ticket shipped):** `3a56fde` on `ralph/company-app-full-build` — `feat(#98): add GET /api/me/branches endpoint`. 7 files: 2 new (`shared/.../domain/BranchClockInStatus.kt`, `backend/src/test/kotlin/com/companyb/companyapp/service/MeServiceBranchesPostgresTest.kt`), 5 modified (`backend/.../service/MeService.kt`, `backend/.../api/routes/MeRoutes.kt`, `backend/.../Main.kt`, `shared/.../dto/MeDto.kt`, `backend/src/test/.../MonthlyRemittanceSummaryServicePostgresTest.kt` — the last is the unrelated date-rollover fix). Commit not yet pushed (pre-push hook takes ~4 min and runs full JMH + baselines + composeApp multi-target compile + k6 load-test; pre-push has an iOS-compile-failure tripping it per session-9 handoff line 49 — known out-of-scope per spec line 171). Best run `git push` with `600000ms+` timeout if the next session is a push session. Note: branch is now 13 commits ahead of origin.
- Wayfinder-93 handoff (session 10): `docs/agents/wayfinder-93-handoff.md` — the session whose session-end frontier table this session inherited.

## Session outcome

**#98 (backend `GET /api/me/branches` build ticket) — resolved + closed + committed `3a56fde` (not pushed).** Session drove the /implement flow: read #94 resolution (the ticket's origin — "Backend gap exposed" finding) + ticket body + backend/AGENTS.md + MeRoutes/MeService/AttendanceRepository/UserBranchAssignmentRepository/BranchDayService + schema (monthly_remittance_summary view etc.) + test helpers (BasePostgresTest, DatabaseTestHelper, MeServicePostgresTest, AttendanceServicePostgresTest, RouteValidationTest) → implemented (shared enum + DTO, MeService.getBranches, MeRoutes.getBranches, Main wiring, 8-test Postgres test class) → full quality gate (`:backend:detekt :backend:ktlintCheck :backend:test` + `:shared:ktlintCheck :shared:detekt :shared:compileKotlinJvm` + composeApp desktop/Android compile) → /code-review (parallel Standards + Spec subagents; both PASS with judgement calls) → commit (pre-commit hook green through every phase).

**Environment fix during session (flag for future sessions):** local Postgres at 5432 was squatted by another project's container (`osu-pp-reworks-postgres` — different credentials, `role "company_user" does not exist`). Fixed by `docker stop osu-pp-reworks-postgres` + wiping the stale company-postgres volume (`docker compose -f docker/docker-compose.yml down -v` + `up -d` — AGENTS.md-documented teardown; dev-only data). If tests fail with "password authentication failed for user company_user" or "role company_user does not exist", check which container owns 5432 first.

## Key design decisions (recorded in full on the resolution comment)

1. **Response = union of (a) active assignments and (b) branches with an active clock-in today.** The ticket's `isRelief` ("true if the user has no `user_branch_assignment` at this branch") can only ever be `true` if a non-assigned branch can appear — assigned-only would be a dead field. #94 resolution + spec line 185 show "Relief duty" as a display state on BranchSelect; a user with an active relief clock-in lands on BranchSelect at launch (per #94 Q1 flow).
2. **"today" = `LocalDate.now(Asia/Manila)`** — matches `AttendanceService.clockIn`'s day resolution.
3. **Stale active clock-ins (`branch_day.date ≠ today`) excluded** from list + statuses. `idx_one_active_clock_in` is per `(user_id, branch_day_id)` — a stale active clock-in does NOT block a fresh clock-in today (verified against `V1__full_schema.sql:131`).
4. **clockInStatus**: branch ∈ clocked-in-today → `HERE`; any clock-in today → `ELSEWHERE` for all other listed branches; none → `NOT_CLOCKED_IN` everywhere. (At most one active clock-in today per the unique index.)
5. **isRelief = branch ∉ assignedBranchIds** — spec-literal, derived from *current* assignments. Known low-severity divergence: `branch_day_assignment.is_relief` was fixed at clock-in time; if assignments change mid-shift the two could disagree. Accepted (ticket wording unambiguous).
6. **Ordering**: branch name ASC, id ASC (matches `BranchRepository.findAll`).
7. **Layering**: queries inline in `MeService` within one transaction — follows `MeService.getMe`'s direct-table-access precedent. The attendance query shape (across-all-branches scoped to date, returning branchIds) differs from `AttendanceRepository.hasActiveClockIn` (single branchDay, Boolean) — not a reuse candidate.
8. **Shared module**: `BranchClockInStatus` enum (domain) + `MeBranchResponse` DTO (`@Serializable`, `branchId/branchName/branchType/clockInStatus/isRelief`) — frontend BranchSelect consumes directly per shared/AGENTS.md.

## /code-review two-pass summary

- **Standards PASS** with 2 judgement calls left open:
  1. Route path `/api/me/branches` is a raw literal while shared/AGENTS.md says route constants live in `shared` — **pre-existing pattern** (every backend route is a literal; `ApiRoutes` has only auth paths). Fixing properly = constants for all routes + backend migration → new fog.
  2. New endpoint not added to `tests/k6/baseline.js` per backend/AGENTS.md "Adding a new endpoint to the baseline" — **deferred**: no frontend consumer exists yet (BranchSelect unbuilt) → new fog.
  3. (Third, mild) Feature Envy on branch-status derivation — judged tolerable at this size.
- **Spec PASS**: all ticket requirements present verbatim (route protected no-capability-gate, response fields, enum values, DSL-only, MeRoutes/MeService pattern, *PostgresTest trio: success / inactive / zero-assignment). Minor notes: `getMe` refactor behavior-preserving; union reading justified by spec line 185; 4 extra tests benign.

## Patterns + learnings (cumulative across sessions 7-11)

- **`MonthlyRemittanceSummaryServicePostgresTest` breaks on month rollover** (NEW this session): tests hardcode `2026, 7` but `RemittanceService.createDraft` sets `submittedDate = today` (RemittanceService.kt:90), so the `monthly_remittance_summary` VIEW (V1__full_schema.sql:623) lands rows in the current month → `getMonthlySummary(branchId, 2026, 7)` throws NotFound every month change. Verified pre-existing (failed with #98 changes stashed). Fixed mechanically 2026,7 → 2026,8 (15 occurrences). **Will break again Aug 1→Sep 1** — fog entry added (relative-month dates / date-relative helper as the future fix).
- **Port-squatter check for backend tests** (NEW): "password authentication failed for user company_user" or "role company_user does not exist" → check `docker ps` — another project's postgres may own 5432 (this session: `osu-pp-reworks-postgres`). Fix: stop squatter, then `down -v` + `up` the company stack (stale volume role mismatch).
- **k6 baseline coverage is per-endpoint and deferred-to-consumer**: /api/me/branches skipped until BranchSelect lands (see fog).
- Prior-session patterns unchanged and still load-bearing: MockEngine handler harness + Unconfined dispatcher (`TestHelpers.kt:54-67`), `runTest(testScheduler)` + `runCurrent/advanceTimeBy` drain + `vm.dispose()` pattern (`NotificationBadgeViewModelTest.kt`), pre-push ~4 min + iOS compile failure (known out-of-scope), `detektMetadataCommonMain` baseline 53, `detektMetadataCommonTest` task nonexistent.
- **ADR-0023 discriminator re-applied**: #98 did NOT graduate an ADR — every non-obvious choice (union semantics, today-scoping) traced back to spec line 185 / #94's resolution / clock-in service behavior, i.e. applications of existing axes, not new architecture.

## Current frontier (verified live post-session)

**8 unblocked tickets** (per `gh api repos/jsongalvez/company_app/issues/89/sub_issues --jq '.[] | select(.state=="open") | .number'` — confirmed: #98 closed):

| # | Title | Type | Suggested session shape |
|---|-------|------|------------------------|
| 99 | Prototype the Clients screen UX (search, detail, anonymization) | prototype | HITL grilling |
| 100 | Prototype the Inventory screen UX (stock levels, product sales, drill-down) | prototype | HITL grilling |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 102 | Prototype the Notifications screen UX (list, unread badge, mark-as-read) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked. Zero backend tickets. Zero AFK-capable tickets.** All 8 remaining are HITL prototypes — every future session until the map graduates more build tickets is a `/prototype` + `/grilling` + `/domain-modeling` session (or a fog-graduation session producing new tickets).

## Recommended next pick

- **#102 (Prototype the Notifications screen UX)** — the freshness-glow pick carried from session-9 (handoff line 80) and sharpened in session-10: `NotificationState.unreadCount` + `NotificationBadgeViewModel` poll/dispose + `NotificationViewModel.markRead` are ALL live in code per #109, AND now test-covered per #93 (`NotificationBadgeViewModelTest` — prior-art pattern for poll-and-dispose VMs). The prototype's HITL grilling can reference live code + live test patterns directly, avoiding the #96 Q8 avoid-stale-framing failure mode. #98 additionally shipped nothing Notifications-related, so this glow is undiminished.
- **Any of #99/#100/#101/#103/#104/#105/#106** — equally-sharp-cuttable, all independent (wayfinder doctrine: may run unblocked tickets in parallel; expect concurrent sessions editing the tracker). Each is HITL prototype per map Notes.
- **Fog-graduation option (no ticket needed — charting session):** the map now has several sharp-but-un-ticketed items: (a) orphan-code-cleanup (HomeScreen/ClientSearchScreen/SessionCreateScreen) — sharp + ready-to-ticket per session-9/10 handoff, graduate as dedicated refactor OR absorb into the "#94-grad Build — Login + capabilities fetch flow + BranchSelect surface" ticket (the coherent option — #94-grad owns BranchSelect UX + capabilities-refresh plumbing + will be the FIRST consumer of the now-shipped `GET /api/me/branches`); (b) the detekt-gate strategy fork (a/b/c) — still cross-foggy with LinearTheme token migration, sub-graduation-ready question already phrased in map fog. A session could graduate (a) into build tickets to restore an AFK route for future sessions — without it, every session is HITL.
- **Sharper fog — "k6 baseline coverage for `GET /api/me/branches`"** — deferred by #98's resolution until BranchSelect (the #94-grad Build) lands; becomes a 10-minute add-on inside that build ticket rather than its own ticket.

Picks: **#102** for the next HITL session (freshest prior-art glow). If the human wants an AFK-capable route restored, **graduate the #94-grad Build ticket** (absorbs orphan-code cleanup + BranchSelect + first `/api/me/branches` consumer + k6 baseline add-on).

## How to drive the next session (wayfinder "Work through the map")

(Reproduced from prior handoffs — same doctrine; frontier + commit refs refreshed.)

1. `gh issue edit <N> --add-assignee @me` — claim FIRST, before any work (N = chosen ticket number). Verify no concurrent sessions on the same frontier (sub-issues assignees empty).
2. If one of #99–#106 (prototype): use `/prototype` + `/grilling` + `/domain-modeling` per map Notes (same pattern as #96 + #97). Look up facts in the repo rather than asking. User preferences cumulative: prior-locks-as-load-bearing (treat prior ADRs as constraints whose answer is implied); falsification-before-claim; cost-of-sidestep-over-cost-of-accept; accepted-costs-named-not-hidden; fog-as-actionable-not-vague; honest failure-mode; least-restrictive-signature-as-API-principle. Check ktlint "no comment between args" rule placement BEFORE locking inline-comment placement. For #102 specifically: badge plumbing live per #109 + test-covered per #93 (`NotificationBadgeViewModelTest` is the prior-art pattern for future poll-and-dispose VMs; `advanceTimeBy(60_000); runCurrent()` drives exactly one poll iteration).
3. If a future build ticket graduates (e.g. #94-grad): same `/implement` flow as #107/#108/#109/#93/#98; backend work reads `backend/AGENTS.md` first per top-level AGENTS.md rule; end with /code-review (parallel Standards + Spec subagents) — both passes PASS before commit.
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** section (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate any fog the answer makes specifiable (clearing each graduated patch from **Not yet specified** so it lives only as its new ticket). If the answer reveals a ticket sits past the destination, close it and add a line to **Out of scope**.
6. If the decision meets the ADR bar (hard to reverse + surprising without context + real trade-off), offer an ADR per `/domain-modeling`. Apply the explicit discriminator carried across sessions: when a ticket's decisions all trace back to existing ADRs through extensions of their axes (rather than establishing a new axis), the ticket does NOT graduate a new ADR. #98 did not (union semantics traced to spec line 185 + #94 + clock-in service behavior).

**One-ticket-per-session limit** (research is the only exception). When the chosen ticket is done, stop and hand off again. Write the next handoff doc to `docs/agents/wayfinder-<N>-handoff.md` where N is the resolved ticket number — follow the prior-session handoff format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #98 → ✅ closed (was 🔓 unblocked at session-10 end). 9 frontier tickets → 8.
- Decisions-so-far: new entry for #98 (cites commit `3a56fde` + resolution comment URL; union semantics + today-scoping + isRelief divergence recorded so low-res map readers can judge relevance).
- Not-yet-specified: **3 new fog entries**: (a) k6 baseline coverage for /api/me/branches deferred until the #94-grad Build consumer lands; (b) backend route paths as raw literals vs shared/AGENTS.md route-constants rule (pre-existing pattern gap, maintenance ticket); (c) hardcoded-month integration tests break on month rollover (the MonthlyRemittanceSummary fix pattern + date-relative-helper future fix).
- Closing paragraph: 8 unblocked + #98 closed + frontier has NO backend/AFK tickets + #102 freshness-glow retained.
- Prior fog preserved: Desktop token storage hardening, PrimaryHover token, pre-BranchSelect drawer empty-state, orphan-code-cleanup (sharp, ready to graduate — see Recommended next pick), LocalNavHostController pattern, shell-scoped poller VM lifecycle, logout/clock-out must append NotificationState.clear(), detekt-gate strategy fog (sharpened).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #99–#106 (feature screen prototypes). Per map Notes; same pattern as #96 + #97. Check the ticket body first — some prototypes are HITL ("What should this look like?") and some are logic ("Does this state model feel right?"); pick the prototype branch accordingly.
- **`/implement`** — only if a fog-graduation session creates a build ticket (e.g. #94-grad Build — Login + capabilities fetch flow + BranchSelect surface: the first consumer of the shipped `GET /api/me/branches`; absorbs orphan-code cleanup + k6 baseline add-on).
- **`/code-review`** — at end of `/implement` flow: parallel Standards + Spec subagents. Both axes caught real things this session and in #93's — run always.
- **`/research`** — for any Ktor / Compose / Material3 / Exposed / Javalin API surface the build or prototype needs to verify (sources-jar unzip pattern from session-10).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md` for the next session to pick up.
