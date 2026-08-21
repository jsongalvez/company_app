# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 21

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 21 resolved the HITL prototype **#101** ("Prototype the Finance screen UX") — `wayfinder:prototype`, HITL grilling (all 9 questions answered live; Q1/Q4/Q5/Q6 re-grilled after falsification, Q8/Q9 reframed from "what are we talking about here"). Full flow: claimed #101 (first frontier ticket in order) → falsification against live backend (7 premises corrected: endpoint name+gate, no comp read-back, no user-picker source, no expense PATCH, work/paying day coincidence, gate split vs role matrix, exports exist) → 2 grilling rounds → prototype outline `docs/prototypes/0101-finance-outline.md` on branch `prototype/0101-finance` (commit `2492c52`, doc-only, pre-commit passed) → **graduated #117** (finance read-back endpoints — created, sub-issue-linked to map, unblocked) → resolution comment (https://github.com/jsongalvez/company_app/issues/101#issuecomment-5199115973) → ticket closed → map #89 updated (child-table rows 101→✅ closed + 117 added, Decisions-so-far + #101 entry, closing paragraph rewritten — frontier now 4 HITL prototypes + #117 AFK task).

**Next session pick:** two candidates, frontier order:
1. **#117** (finance read-back endpoints) — AFK backend task, the only unblocked build ticket; pattern = #98/#111/#114/#115/#116 (falsification → build → `:backend:detekt :backend:ktlintCheck :backend:test` → test-DB cleanliness → /code-review parallel axes → commit on `ralph/company-app-full-build`, not pushed). **This is the first-pick: it's the only AFK ticket on the map, and the Finance build (future graduation) is blocked on it.**
2. **#103** (Remittance prototype) — next HITL prototype in frontier order.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #101 row + #117 child row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/home/jayson/.config/opencode/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#101 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/101#issuecomment-5199115973**
- **#117 body** (the graduated ticket — the next session's work order): https://github.com/jsongalvez/company_app/issues/117
- Prototype outline (locked D1–D8): branch `prototype/0101-finance`, `docs/prototypes/0101-finance-outline.md`
- Prior handoffs: `docs/agents/wayfinder-116-handoff.md` (session 20), `docs/agents/wayfinder-115-handoff.md` (session 19)

## Session outcome

**#101 (Finance screen UX) — resolved + closed.** All 9 questions settled via 2 grilling rounds (HITL). Route gate = **VIEW_BRANCH_DATA** (widest finance capability — not ASSIGN_COMPENSATION as the ticket premised; Accountant's view-only P&L role + Practitioner's BR:314 expense-logging would have been locked out). Per-persona: ACCOUNTANT = any-branch picker (not clocked in → no selectedBranchId) + P&L only + download; PRACTITIONER = selected branch + P&L + expense log/edit/delete; COORDINATOR/MANAGER = everything incl. past-day edits (EDIT_PAST_DAY); OWNER = everything except past-day writes (lacks EDIT_PAST_DAY, V2 seed). Day picker default today, past days read-only unless EDIT_PAST_DAY + day-state banner. No drill-down (rows are the whole story — every field already in row payloads). Assign-comp modal: both workBranchDayId + payingBranchDayId = viewed branch day (they always coincide — BR:118-120 relief-deducted-from-duty-branch; service validates only paying day-state + work-day existence). Allowances = third section "not in P&L". Commission = informational breakdown row. Download = daily CSV/PDF export, access-scoped ("if they have access, they can download" — relief auto view-only per BR:106; monthly/all-time stay #105's).

## Key falsification findings (verified against live backend code)

1. **F1 — P&L endpoint name + gate both wrong**: `GET /api/branches/{branchId}/daily-summary?date=` (not "daily-sales-summary"), gated **VIEW_BRANCH_DATA** BRANCH-scoped (DailySalesSummaryRoutes.kt:27-40), not ASSIGN_COMPENSATION. Response IS P&L-shaped: grossIncome/totalCompensation/totalExpenses/netIncome/totalProductSales/totalCommission (Strings). Excludes allowances (view SQL V1:585-618). 404 "No data for this branch and date" on empty days.
2. **F2 — No compensation read-back**: POST `/api/compensation` + PATCH (optimistic `expectedVersion`) only. No GET anywhere — "compensation list" (scratch spec) has no data source; daily-summary carries the aggregate only. Edit-after-reload impossible (version unknowable).
3. **F3 — No practitioner-picker source**: no endpoint returns branch-day users with names. Assignments GET = MANAGE_USERS-gated (Coordinator locked out) + `AssignmentResponse` is userId-only. Commission-splits = pool-only + userId-only. No attendance GET at all (AttendanceRoutes = clock-in/clock-out POSTs only).
4. **F4 — Expense shape**: POST + DELETE (soft, required reason) + GET by `?branchDayId=` (EDIT_BRANCH_DATA). GET **includes soft-deleted rows**; summary total excludes them (`deleted_at IS NULL`). No PATCH — edit = delete+re-log today (loses row identity, swaps createdBy, doubles audit entries).
5. **F6 — workBranchDayId ≡ payingBranchDayId in every flow**: BR:118-120 (relief comp deducted from duty branch; `unique(user_id, paying_branch_day_id)`); CompensationService validates paying day-state + work-day existence only, no cross-branch rule. Form defaults both to the viewed branch day.
6. **F7 — Gate split + role matrix** (V2__seed_roles_capabilities.sql): summary=VIEW_BRANCH_DATA, expense list=EDIT_BRANCH_DATA, comp/allowance=ASSIGN_COMPENSATION, past-day=EDIT_PAST_DAY (**Coordinator-only; OWNER lacks it**). PRACTITIONER = VIEW+EDIT (no ASSIGN); ACCOUNTANT = VIEW only, "read-only across all branches".
7. **F10 — Exports exist**: `GET /api/branches/{branchId}/export/{daily|monthly|all-time}?format=csv|pdf` (ExportRoutes.kt), VIEW_BRANCH_DATA branch-scoped. BR:106 — relief check-in grants automatic view-only access → download scope = access scope, enforced by route gate, no UI special-casing.

## Key decisions (recorded in full on the resolution comment + outline)

1. **Route gate = VIEW_BRANCH_DATA with per-persona sections** (D1) — matches BR:314 "anyone can log an expense" + Accountant role; comp/allowance sections hidden without ASSIGN_COMPENSATION; drawer item visible to VIEW holders (#108 gating).
2. **Day picker default today** (D3) — past days read-only unless EDIT_PAST_DAY + day-state banner; Coordinator's past-day editing is the explicit BR day-state flow (PAST/REMITTED = "Coordinator only").
3. **No drill-down route** (D8/Q8) — modals + self-contained rows are the whole UI (#91 lock applied).
4. **Comp edit = PATCH w/ expectedVersion** (D5/D6) — pessimistic per ADR-0022; expense PATCH **graduated** (doesn't exist); compensation no delete (none exists).
5. **work = paying = viewed branch day** (D4) — no pickers; relief handled by assigning at the duty branch.
6. **Commission = informational breakdown row** (D9/Q9) — in payload, zero cost; per-user split drill-down deferred (fog).
7. **Download = daily export, access-scoped** (D8/Q1) — monthly/all-time belong to #105; 403 backstop per #99 D7.
8. **Allowances = "not in P&L" third section** (D7) — BR:308-310.

## Graduation

**#117 — "Add finance read-back endpoints: compensation list + branch-day users + expense edit (graduated from #101)"** — `wayfinder:task`, child of #89, **unblocked, unassigned**. Three small pieces, session-sized:
1. `GET /api/compensations?branchDayId=` (or branch-day path per expense/allowance pattern) — rows embedded with user name; gate ASSIGN_COMPENSATION BRANCH-scoped (allowance precedent).
2. Branch-day users GET (attendance-based, names embedded) — the assign-modal picker source (F3); gate ASSIGN_COMPENSATION.
3. `PATCH /api/expenses/{expenseId}` — amount/category/notes + expectedVersion, same validation as POST; EDIT_BRANCH_DATA + day-state per record.

## Patterns + learnings (cumulative across sessions)

- **Practitioner-picker gap class**: any assign-to-a-user form needs a users-with-names read for a branch day; none exists (assignments = MANAGE_USERS + nameless; commission-splits = pool-only). #117's users-endpoint is the first.
- **work/paying day coincidence**: when a request has two branch-day ids, check whether the service enforces a relationship before adding pickers — BR:118-120 + service validation settled it (no relationship enforced → they coincide in every flow).
- **Role matrix as falsification input**: V2__seed_roles_capabilities.sql is the single source of truth for who-can-do-what — read it before designing per-element gates (F7 found Owner lacks EDIT_PAST_DAY — a past-day-write button for Owner would 403 on every use).
- **"If they have access, they can download"** — user's export rule: scope = capability-grant scope; relief auto view-only (BR:106) covers the relief-download case; no per-role UI branching, route gate is the boundary, 403 = silent backstop.
- Prior-session patterns unchanged: falsification-before-claim, facts-vs-taste split, backend-authoritative (ADR-0007), #93 handler-based MockEngine, sub-issues wiring via numeric DB id (`-F sub_issue_id=...`, NOT `-f` — string vs integer), route authz harness NotFoundException mapping, same-package imports tolerated, ktlint route-literal consistency, unpushed commits on `ralph/company-app-full-build`.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — 5 open children, all unassigned:

| # | Title | Type | Notes |
|---|-------|------|-------|
| 117 | Add finance read-back endpoints: compensation list + branch-day users + expense edit (graduated from #101) | task | AFK — **first pick**: only unblocked build ticket; Finance build blocked on it |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked.** #110 ("Fix hardcoded-month dates...") remains open + unassigned but NOT a child of map #89 — standalone (Aug rollover mechanically fixed in `3a56fde`; #110 may be stale).

## Recommended next pick

- **#117** — the only AFK ticket on the map; resolves the last finance gap so the #101-graduated Finance build becomes possible. Same pattern as #114/#115/#116 (falsification → build → gates → test-DB cleanliness → /code-review → commit, not pushed). Note per ticket: compensation list + branch-day users both need **user names embedded** (userId-only responses are useless to the frontend — F3); gates = ASSIGN_COMPENSATION branch-scoped; PATCH expense = EDIT_BRANCH_DATA + day-state via record; test-DB trackOwned discipline from #115/#116 handoffs.
- **#103 (Remittance)** — next HITL prototype if the user prefers human-driven work; **falsify at grill time**: remittance endpoints are draft/lines/day-breakdowns/submit + monthly summary (MonthlyRemittanceSummaryRoutes) — check current shapes rather than the ticket's premises (drafts, submit, detail).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. **#117 (AFK task):** /implement-style per #98/#111/#114/#115/#116 (falsification → build → `:backend:detekt :backend:ktlintCheck :backend:test` → test-DB cleanliness `bash scripts/clean-test-db.sh` → /code-review parallel axes → commit on `ralph/company-app-full-build`, not pushed). **#103 (HITL):** /prototype + /grilling + /domain-modeling per map Notes.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`; also keep the child-tickets table + closing paragraph current).
4. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by` using numeric DB ids; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<numeric .id>` — **use `-F` not `-f` (integer vs string), verify new tickets appear in the sub-issues query**).
5. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling.

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: row #101 → ✅ closed; row #117 added (🔓 unblocked).
- Decisions-so-far: new entry for #101 (falsification F1-F10; D1-D8 locked; graduation → #117; no-ADR assessment; new fog: commission-split drill-down, cross-day comp history, expense-soft-deleted question).
- Closing paragraph rewritten: frontier = 4 HITL prototypes + #117 AFK task; #101 gist; Finance build blocked on #117.
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, LocalNavHostController + poller-VM patterns, NotificationState.clear() maintenance point, pushed-route TopAppBar pattern.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for #117 (AFK backend build; hand untracked/stale-staged files to the sub-agents explicitly if any).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #103–#106 (feature screen prototypes; falsification-first per screen; plain-language framing for multi-option questions — this session's Q8/Q9 "what are we talking about here" moments showed concrete scenarios beat abstract labels).
- **`/research`** — for any Ktor / Compose / Material3 API surface the prototype needs to verify.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
