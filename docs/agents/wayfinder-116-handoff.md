# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 20

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 20 resolved the AFK backend task **#116** ("Add today's-branch-day endpoint") — `wayfinder:task`, graduated from #100, the **last of the three backend tickets (#114/#115/#116)**. Full flow: claimed #116 → falsification against live backend (premise VERIFIED: `evaluateStatus` exists + pure, no branch-day read endpoint anywhere, clock-in already creates today's day via `resolveOrCreate`) → build (`GET /api/branches/{branchId}/today` + per-route EDIT_BRANCH_DATA filter + new shared `BranchDayTodayResponse` DTO) → `:backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm` green + test-DB cleanliness (one leak incident mid-way: endpoint-created branch_day rows untracked → teardown FK break; fixed via upfront `trackOwned` by branchId + `bash scripts/clean-test-db.sh`) → /code-review (parallel Standards + Spec; both PASS after 3 fixes: route-literal consistency, dedup trackOwned, evaluateStatus-in-handler; +1 added test REMITTED-through-HTTP) → committed `0f89748` on `ralph/company-app-full-build` (**not pushed**, #111/#112/#113/#114/#115 precedent) → resolution comment (https://github.com/jsongalvez/company_app/issues/116#issuecomment-5198809567) → ticket closed → map #89 updated (child-table row 116 → ✅ closed, Decisions-so-far + #116 entry, closing paragraph rewritten — frontier now 5 HITL prototypes only).

**Next session pick:** one of the **5 unblocked HITL prototypes** — #101 Finance, #103 Remittance, #104 Audit Log, #105 Reports, #106 User Management (frontier order; all unassigned, zero blocked). No AFK tickets remain on the map.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #116 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/home/jayson/.config/opencode/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#116 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/116#issuecomment-5198809567**
- New backend state: `BranchDayRoutes.kt` (GET /api/branches/{branchId}/today + per-route filter), `BranchDayService.getToday(branchId)` (resolve-or-create, branch-exists 404), `BranchDayTodayResponse` in `shared/.../dto/BranchDayDto.kt`
- Prior handoffs: `docs/agents/wayfinder-115-handoff.md` (session 19), `docs/agents/wayfinder-114-handoff.md` (session 18)

## Session outcome

**#116 (today's-branch-day endpoint) — resolved + closed.** `GET /api/branches/{branchId}/today` → `BranchDayTodayResponse {branchDayId, status}` (new shared DTO; status = enum name string OPEN/PAST/REMITTED). Own per-route branch-scoped `EDIT_BRANCH_DATA` before-filter (4-segment route — the #114 F1 no-cascade rule applied again; premise verified correct). **Resolve-or-create semantics** — the endpoint's purpose is sourcing `branchDayId` before the first mutation of the day (restock/movement/sale require it in the body); a find-only read would deadlock the frontend. Branch-exists = service-layer 404 (parent-child convention). Response status = **effective status** through `evaluateStatus(status, date, today Manila)` (identity for today, laziness enforced at contract). 11 new tests: 4 service + 7 route authz.

## Key falsification findings (verified against live backend code)

1. **F1-verified — premise CORRECT**: `BranchDayService.evaluateStatus(status, date, today)` exists (BranchDayService.kt:94, pure). But `getEffectiveStatus(branchDayId)` is per-branchDayId — no branchId→today lookup existed; new service method required. Daily-summary is VIEW_BRANCH_DATA + date-param'd (confirmed not a branchDayId source); clock-in response covers only the clocked-in branch — ticket's "no clean endpoint exists" holds.
2. **F2-verified**: clock-in already creates today's branch day (`AttendanceService.kt:66` `resolveOrCreate`) — so post-clock-in (the #94-gated state where inventory screen is reachable) the day normally exists; the create path is a fallback, and `resolveOrCreate` is idempotent (`insertIgnore` + select).
3. **F3-verified**: no shared branch-day DTO existed; `DayStatus` enum is backend-only (`repository/model/BranchDay.kt`) — status serialized as name string, same shape as `InventoryMovementReason`→String in #115.

## Key decisions (recorded in full on the resolution comment)

1. **GET resolves-or-creates (no 404-on-missing-day)** — deadlock rationale above; idempotent insert; branch-exists still 404 at service layer.
2. **Effective status via `evaluateStatus`** — spec-named mechanism used, laziness at the contract.
3. **Per-route `EDIT_BRANCH_DATA` before-filter in new `BranchDayRoutes.kt`** — matches #114/#115 pattern; also serves Finance/Expenses/Allowances branchDayId gap.
4. **`DayStatus` stays backend-only, status as String in DTO** — move to shared/domain later per shared/AGENTS.md (fog).
5. **No pagination, no date param, k6 deferred** — YAGNI (#98/#115 precedent).

## Patterns + learnings (cumulative across sessions)

- **Endpoint-created rows leak teardown**: any test whose code-under-test CREATES rows (e.g. resolve-or-create on GET) must `trackOwn` those rows upfront by a known key (branchId) — tracking after the fact leaks on assertion failure (teardown FK break). Cleaned with `bash scripts/clean-test-db.sh`; the #115 handoff's leak class (mid-way test failure) is the sibling variant.
- **Same-package imports tolerated** — `import com.companyb.companyapp.api.routes.pathParamAsUuid` in routes files in the same package passes ktlint (precedent BranchRoutes/BranchInventoryRoutes).
- **Route authz harness needs a NotFoundException handler too** — the `createApp()` copy registers ForbiddenException; a 404 test requires adding the NotFoundException mapping (from Main.kt `registerExceptionHandlers`).
- **Javalin route literal style**: `before` filters must interpolate the same `{$BRANCH_ID_PARAM}` constant as the handler path (consistency finding from /code-review).
- Prior-session patterns unchanged: falsification-before-claim (HITL prototypes), facts-vs-taste split, code-only gates, backend-authoritative (ADR-0007), `#93` handler-based MockEngine, sub-issues wiring via numeric DB id, JMH-noise caution, unpushed commits on `ralph/company-app-full-build`.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — 5 open children, all HITL prototypes:

| # | Title | Type | Notes |
|---|-------|------|-------|
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling — next in frontier order |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked. Zero assigned.** All three backend tickets (#114 gates+payload, #115 movements, #116 today) are closed — the Inventory screen's backend gaps are all filled; remaining inventory work is the frontend build (graduation from #100). #110 ("Fix hardcoded-month dates...") remains open + unassigned but NOT a child of map #89 — standalone (map fog says the Aug rollover was mechanically fixed in `3a56fde`; #110 may be stale).

## Recommended next pick

- **#101 (Finance)** — next HITL prototype in frontier order; same grilling pattern as #96/#97/#99/#100/#102. **Note per screen: falsify at grill time** — #114's gate fix + #115's movements + #116's today endpoint landed while Finance grills; check current gates/endpoints rather than pre-fix state. Finance/Expenses/Allowances also need branchDayId → #116 is now the sourcing endpoint for that screen's own F5-style gap.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes (pattern #96/#97/#99/#100/#102). Falsification-before-claim per screen. AFK (if any future backend ticket graduates): /implement-style per #98/#111/#114/#115/#116 pattern (falsification → build → `:backend:detekt :backend:ktlintCheck :backend:test` → test-DB cleanliness → /code-review parallel axes → commit on `ralph/company-app-full-build`, not pushed).
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`; also keep the child-tickets table + closing paragraph current).
4. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by`; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<numeric .id>` — **verify new tickets appear in the sub-issues query**).
5. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling.

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: row #116 → ✅ closed.
- Decisions-so-far: new entry for #116 (premise verified; resolve-or-create rationale; effective-status via evaluateStatus; per-route gate; DayStatus-as-String fog note; 11 tests; /code-review PASS; k6 deferred).
- Closing paragraph rewritten: frontier = 5 HITL prototypes; no AFK tickets remain; #116 gist; Inventory screen backend gaps all closed (frontend build is the remaining inventory work); fog additions = DayStatus-not-shared.
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, LocalNavHostController pattern, poller-VM lifecycle pattern, NotificationState.clear() maintenance point, pushed-route TopAppBar pattern.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #101–#106 (feature screen prototypes; falsification-first per screen; plain-language framing for multi-option questions).
- **`/research`** — for any Ktor / Compose / Material3 API surface the prototype needs to verify.
- **`/implement` + `/code-review`** — when a prototype graduates to a build ticket (AFK backend or frontend build; hand untracked/stale-staged files to the sub-agents explicitly if any).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
