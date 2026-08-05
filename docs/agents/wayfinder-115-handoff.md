# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 19

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 19 resolved the AFK backend task **#115** ("Add movement history endpoint for branch inventory") — `wayfinder:task`, graduated from #100, the second of the three backend tickets (#114/#115/#116). Full flow: claimed #115 → falsification against live backend (ticket premise VERIFIED this time — gate assumption correct post-#114) → build (`GET /api/branches/{branchId}/inventory/movements?date=` + per-route EDIT_BRANCH_DATA filter + branch-day-date filter + id-DESC tiebreak) → `:backend:detekt :backend:ktlintCheck :backend:test` green + test-DB cleanliness + shared compile → /code-review (parallel Standards + Spec; both PASS after 4 fixes: id-DESC tiebreak, `createBranchDayForToday`→`createBranchDayForDate` delegation, manilaZone consistency in service tests, date-route payload assert) → committed `ef9d16d` on `ralph/company-app-full-build` (**not pushed**, #111/#112/#113/#114 precedent) → resolution comment (https://github.com/jsongalvez/company_app/issues/115#issuecomment-5198606850) → ticket closed → map #89 updated (child-table rows 114/115/116 added, Decisions-so-far + #115 entry, closing paragraph rewritten).

**Next session pick:** either the **last unblocked AFK backend ticket (#116 today's-branch-day endpoint — independent, parallel-friendly)** or one of the **5 unblocked HITL prototypes** (#101 Finance, #103 Remittance, #104 Audit Log, #105 Reports, #106 User Management).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #115 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md` — NOTE: `.agents/` skill dir was deleted in working tree (pre-existing, unstaged deletions of an old skill copy — **do not commit**; live skills live in `skill/` and `~/.config/opencode/skills/`)
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#115 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/115#issuecomment-5198606850**
- New backend state: `BranchInventoryRepository.findMovements(branchId, date?)`, `InventoryService.getMovementHistory(branchId, date?)`, movements GET route + filter in `BranchInventoryRoutes.kt`, `createBranchDayForDate` in `DatabaseTestHelper.kt`
- Prior handoffs: `docs/agents/wayfinder-114-handoff.md` (session 18), `docs/agents/wayfinder-100-handoff.md` (session 17)

## Session outcome

**#115 (movement history endpoint) — resolved + closed.** `GET /api/branches/{branchId}/inventory/movements?date=` (optional ISO `yyyy-MM-dd`) → `InventoryMovementResponse[]` (existing shared DTO, unchanged). Own per-route branch-scoped `EDIT_BRANCH_DATA` before-filter (5-segment path — the #114 F1 hole would otherwise leave it ungated; ticket premise verified correct). Date filter = **branch-day date** (innerJoin `branch_day.date`), NOT `movedAt` — movements belong to a business day, domain "day" keys on `branch_day.date`, movedAt would split late days across dates. Default = **all** (whole-branch log per #100 D9); frontend passes `date=today` when it wants the current day. Ordering `movedAt DESC` + **`id DESC` tiebreak** (`movedAt` = `CURRENT_TIMESTAMP` = transaction start; same-tx movements share it). No pagination (YAGNI, no consumer yet). DTO untouched (no productName — frontend joins via the inventory list it already loads). Service keeps branch-exists 404 check (parent-child scoping). 13 new tests: 6 service + 7 route authz.

## Key falsification findings (verified against live backend code)

1. **F1-verified — gate premise CORRECT this time**: post-#114 `BranchInventoryRoutes.kt` registers per-route filters for `/inventory`, `/low-stock`, `/{productId}/restock`, `/{productId}/movement` only. A new `/inventory/movements` (5 segments) route needed its own before-filter or it would be completely ungated (the #114 F1 lesson — Javalin exact-pattern matching, no cascade). Added per-route `EDIT_BRANCH_DATA` branch-scoped, matching the ticket's spec.
2. **F2-verified**: `InventoryMovementResponse` exists in `shared/InventoryDto.kt` with exactly the 9 specced fields; `InventoryMovement.toResponse()` mapping already existed in the routes file — reused.
3. **F3-verified**: movements were write-only (no read path); `InventoryMovementTable` has all needed columns; `BranchDayTable.date` the clean filter source. Past-day movement recording is blocked at service layer by day-state gate (`StockValidator` → `checkBranchDayEditable`; OPEN-with-past-date = PAST) unless `EDIT_PAST_DAY` granted — that's how historical rows exist in prod, and how the date-filter test seeds them.

## Key decisions (recorded in full on the resolution comment)

1. **Date filter = branch-day date, not movedAt date** — business-day semantics, consistent with daily-summary/remittances/#116; movedAt is server clock (late-running days would split).
2. **Default = all** (branch-wide log per #100 D9), optional `?date=` narrows; frontend can pass `date=today`.
3. **`movedAt DESC, id DESC` tiebreak** — CURRENT_TIMESTAMP is transaction-start time; code-review Spec finding.
4. **No pagination, DTO unchanged** — YAGNI; productName join left to frontend via its loaded inventory list.
5. **404 for missing branch** in service (parent-child scoping convention) — /code-review Spec flagged as "not asked for"; benign, repo convention, kept.
6. **ADJUSTMENT rows visible to EDIT_BRANCH_DATA readers** — read-gate asymmetry flagged by /code-review; kept (spec mandates EDIT_BRANCH_DATA gate; ADJUSTMENT-adjusted stock already visible via GET /inventory — not a leak).

## Patterns + learnings (cumulative across sessions)

- **Gate check now has a positive + negative case**: #114 taught that new sub-routes are silently ungated; #115 confirmed the per-route-filter pattern is the fix. For any future backend route addition under `/api/branches/{branchId}/inventory/...`, register a before-filter in the same commit or it ships ungated.
- **`movedAt`-style timestamp ordering needs a deterministic tiebreak** — `CURRENT_TIMESTAMP` is transaction-start; two writes in one transaction tie. Add `id DESC` (or similar) as secondary sort whenever ordering on a DB-clock column.
- **Past-date test seeding**: service-layer writes to past branch days are day-state-gated (`EDIT_PAST_DAY`); grant it in tests to record "historical" movements through the real service path (mirrors prod), or bypass via repository.
- **ktlint chain rule**: a chain continuing after a multi-line lambda must ride the closing `}` line — `ktlintFormat` auto-fixes; don't fight it.
- **Test-data leak class bug still live**: a failing test mid-way skips `trackOwned` → teardown FK break + rows persist. If the DB ever shows leaks: `bash scripts/clean-test-db.sh` then `./gradlew :backend:test --rerun-tasks` (an UP-TO-DATE test task skips teardown and hides the leak).
- **Working tree hygiene**: `.agents/skills/` deletions are pre-staged by an earlier session — `git restore --staged .agents/` before committing; commit only your files (prior sessions left them unstaged for the same reason).
- Prior-session patterns unchanged: falsification-before-claim (HITL prototypes), facts-vs-taste split, code-only gates, backend-authoritative (ADR-0007), `#93` handler-based MockEngine, sub-issues wiring via numeric DB id, JMH-noise caution, unpushed commits on `ralph/company-app-full-build`.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — 6 open children:

| # | Title | Type | Notes |
|---|-------|------|-------|
| 116 | Add today's-branch-day endpoint (graduated from #100) | task (AFK) | Backend: e.g. `GET /api/branches/{branchId}/today` → `{branchDayId, status}` (OPEN/PAST/REMITTED via `BranchDayService.evaluateStatus`); gate per #114/#115 pattern: EDIT_BRANCH_DATA per-route before-filter; serves Finance/Expenses later; route + service tests, k6 deferred (#98 precedent) |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked. Zero assigned.** #110 ("Fix hardcoded-month dates in MonthlyRemittanceSummaryServicePostgresTest") remains open + unassigned but NOT a child of map #89 — standalone (note: map fog says the Aug rollover was mechanically fixed in `3a56fde`; #110 may be stale).

## Recommended next pick

- **#116 (today's-branch-day endpoint)** — #115's sibling; the per-route-gate + service/repo + authz-test patterns from #114/#115 apply directly. It's the last backend gap for the Inventory screen (branchDayId sourcing, #100 F5) and unblocks Finance/Expenses later. Independent — parallel-friendly.
- Otherwise **#101 (Finance)** — next HITL prototype in frontier order; same grilling pattern. **Note per screen: #114's gate fix + #115's movements endpoint landed while Finance grills — falsify gates/endpoints at grill time, don't trust pre-fix state.**

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend ticket flow: /implement-style per #98/#111/#114/#115 pattern (falsification → build → `:backend:detekt :backend:ktlintCheck :backend:test` → test-DB cleanliness → /code-review parallel axes → commit on `ralph/company-app-full-build`, not pushed). HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes (pattern #96/#97/#99/#100/#102). Falsification-before-claim per screen.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`; also keep the child-tickets table + closing paragraph current).
4. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by`; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<numeric .id>` — **verify new tickets appear in the sub-issues query**).
5. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling.

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: rows added for #114 (✅ closed), #115 (✅ closed), #116 (🔓 unblocked).
- Decisions-so-far: new entry for #115 (premise verified; branch-day-date semantics; default=all; id-DESC tiebreak; 13 tests; /code-review PASS; k6 deferred).
- Closing paragraph rewritten: frontier = #116 + 5 HITL prototypes; #115 gist; no new fog additions (branch-day-date filter semantics + DTO-without-productName documented in #115's resolution).
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals (#115 added), route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, LocalNavHostController pattern, poller-VM lifecycle pattern, NotificationState.clear() maintenance point, pushed-route TopAppBar pattern.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for #116 (backend build, /implement flow per #98/#111/#114/#115).
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; hand untracked/stale-staged files to the sub-agents explicitly — `git status` shows `.agents/` deletions; scope them out).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #101–#106 (feature screen prototypes; falsification-first per screen; plain-language framing for multi-option questions).
- **`/research`** — for any Ktor / Compose / Material3 API surface the prototype needs to verify.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
