# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 25

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 25 resolved the HITL prototype **#105** ("Prototype the Reports screen UX (daily, monthly, all-time exports)") — `wayfinder:prototype`, resolved by grilling with a **user-ordered merge of Reports + Finance into one screen**. Resolution comment: https://github.com/jsongalvez/company_app/issues/105#issuecomment-5230790663. Ticket closed; map #89 updated (Decisions-so-far #105 entry, table rows #120/#124 corrected to closed, 4 graduated tickets added, fog + closing paragraph). Asset `docs/prototypes/0105-reports-outline.md` on throwaway branch `prototype/0105-reports` (commit `00b4a1a`, pushed doc-only). **Frontier now: 1 unblocked HITL prototype (#106 User Management) + 4 unblocked backend tasks graduated from #105 (#128–#131).**

**Next session pick:** either **#106 (User Management prototype)** — HITL, needs /prototype + /grilling + /domain-modeling — or any of the 4 AFK backend tasks **#128–#131** (all unblocked, independent, no blockers): **#128** public branch-type exports (trivial gate drop), **#129** date-range export (new rollup endpoint), **#130** paged daily-summaries feed endpoint, **#131** Reports access scope (GLOBAL VIEW = all branches + accessible-branches endpoint — the meatiest). The merged Finance & Reports build ticket stays ungraduated until #128–#131 land + the #117 expense-GET-soft-deleted question is decided.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #105 entry, Not-yet-specified, Out-of-scope, frontier table)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#105 resolution comment: https://github.com/jsongalvez/company_app/issues/105#issuecomment-5230790663**
- **Prototype outline: `docs/prototypes/0105-reports-outline.md`** (on branch `prototype/0105-reports`) — the merged-screen spec (D1–D7) + graduation scopes
- The 4 graduated tickets: #128 / #129 / #130 / #131 (bodies carry their full scopes)
- #101's Finance outline (`prototype/0101-finance` branch) — the edit-mode spec of the merged screen
- Prior handoff: `docs/agents/wayfinder-124-handoff.md` (this session's parent)

## Session outcome

**#105 (Reports prototype) — resolved + closed (HITL, user merge order).**

- **Falsification first (9 findings, most premises wrong)**: (F1) all 5 export routes gate BRANCH-scoped `VIEW_BRANCH_DATA` — ACCOUNTANT (GLOBAL-only seed, V2:132-138), relief (BRANCH_DAY), and dev user all 403; `AuditLogReadScope` already solved this shape for audit (GLOBAL VIEW = all branches) — export/summary routes never got it → **resolves #103 F9's gate-split question**; (F2) no picker source for global-view users (`GET /api/branches` = GLOBAL `MANAGE_USERS` at BranchRoutes.kt:21-27; `me/branches` = assigned+clocked-in only — Finance D1's picker premise wrong); (F3) BR lists 5 report kinds (BR:397-403) vs ticket's 3 — Provincial Tour + Medical Mission endpoints exist, GLOBAL-gated → **user: PUBLIC reports, any logged-in user**; (F4) `format=csv|pdf` required, bytes+attachment, no composeApp byte-fetch precedent; (F5) no-data = 404; (F6) JSON twins exist for daily+monthly only, no list/pagination endpoints anywhere; (F7) monthly/all-time = live SUBMITTED-remittance view (48h Undo shifts aggregates); (F8) relief flow = request→grant/deny only — branch-initiated invite doesn't exist; (F9) route/drawer premises verified (Finance+Reports placeholders; drawer gates COLLIDE — ASSIGN_COMPENSATION vs VIEW_BRANCH_DATA).
- **D1 — THE MERGE (user override)**: Reports + Finance = one screen "Finance & Reports", one drawer item gated `VIEW_BRANCH_DATA`; default read-only day feed; Edit toggle appears only for holders of any edit capability at the selected branch; **edit acts on the day you're viewing** (the feed replaces #101's day picker — D3 dissolves); per-element guards = #101 D1 matrix (expenses `EDIT_BRANCH_DATA`, comp/allowances `ASSIGN_COMPENSATION`, past-day `EDIT_PAST_DAY`); Accountant never sees the toggle. #101's outline = the edit-mode spec.
- **D2–D7**: 5 report kinds (Daily/Monthly/All-time existing + **Date range** new rollup endpoint + Provincial/Medical Mission public section); access-scoped branch picker (Accountant all / staff granted) + new accessible-branches endpoint, **no relief affordance** (user override); mode tabs with always-day feed (Monthly = month rollup card + that month's days; All-time = unbounded + calendar jump; Date range = from/to window); compact day rows default + rows/cards toggle, tap → day detail (desktop inline expand / mobile modal) with full daily figures + per-day CSV/PDF; CSV+PDF everywhere; mobile = same compact feed + tap-detail.
- **Graduated 4 unblocked backend tasks** (all sub-issues of #89, all `wayfinder:task`, no blockers): **#128** public branch-type exports (drop GLOBAL gate, JWT stays), **#129** date-range export (whole-range rollup), **#130** paged daily-summaries feed (keyset cursor, #122 precedent), **#131** Reports access scope (GLOBAL VIEW = all branches via shared AuditLogReadScope-style helper + accessible-branches endpoint).
- **New fog**: relief-invite feature (add-as-relief-for-future-date → accept/decline; placement undecided — User Management / BranchSelect territory); merged-screen build ticket ungraduated (needs #128–#131 + #117 expense-soft-deleted decision).
- No new ADR — every decision traces through locked principles or explicit user decisions recorded in the outline.

## Key decisions (recorded in full on the resolution comment + outline)

1. **Finance + Reports merge** — user-ordered; default read-only feed + per-capability Edit toggle; edit acts on the viewed day. #101's D1–D8 become the edit-mode spec.
2. **Provincial Tour + Medical Mission reports are PUBLIC** — any logged-in user (gate drop; routes stay under `/api/*` so JWT still applies). Not tabs — a secondary section.
3. **Date-range mode** — new rollup export endpoint (whole-range total, NOT visible rows — user corrected my export-visible-rows proposal).
4. **Feed always shows whole days** (user corrected my "one row per day" framing — each day = full daily figures; rows/cards toggle; tap → detail).
5. **No relief affordance in Reports** — user walked back the picker-relief idea ("we're working in reports"), but wants the branch-initiated invite flow as a future feature elsewhere.
6. **GLOBAL VIEW_BRANCH_DATA = all branches** — the #103 F9 gate-split question resolved: export + summary routes must adopt the AuditLogReadScope pattern (#131).

## Patterns + learnings (cumulative across sessions)

- **The #114 exact-path lesson — export route gates were never Accountant-reachable**: the export/summary routes' BRANCH-scoped gate + the GLOBAL-only Accountant seed = a dead screen for the persona BR intends. Pattern: **read routes must check against the AuditLogReadScope-style window (GLOBAL VIEW = all branches), not bare BRANCH context** — #131 ships the shared helper.
- **Branch-list endpoints are all persona-scoped wrong**: `GET /api/branches` = MANAGE_USERS, `me/branches` = assigned+clocked-in. A picker for global-view users needs a dedicated endpoint — this bit Finance D1's outline too.
- **User corrections beat my synthesis**: three times in one session the user corrected an over-engineered proposal (export-visible-rows → whole-range rollup; one-row-per-day feed → whole-days feed; relief-in-picker → out of Reports). The CloudWatch-style preview feed came from the user. Grilling rounds 3-4 confirmed everything else in one pass.
- **Map table hygiene**: the frontier table had stale rows (#120, #124 marked unblocked though closed — the closing paragraph was updated but the table wasn't). Fixed this session; next sessions: check both when updating the map.
- Prior-session patterns unchanged: falsification-first against live code, /prototype + /grilling + /domain-modeling for HITL, outline asset on `prototype/010X-*` branch (pushed doc-only), resolution comment → close → map Decisions-so-far + closing paragraph, create-then-wire graduated tickets (sub-issue via `POST .../issues/89/sub_issues` with the **integer** DB id — use `-F`, not `-f`, or GitHub 422s on string-typed ids).
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals for `AppNavHost`, `ClientResultList`, `ClientDetailLayout`, `AuditLogEntryList`, `RemittanceRowList`) — any future iOS work (or the pre-push gate's multi-target compile) hits this first.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100` + dependency summaries (`blocked_by` = open blockers only):

| # | Title | Type | Status |
|---|-------|------|--------|
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |
| 128 | Public branch-type exports: drop VIEW_BRANCH_DATA gate on provincial/medical-mission (graduated from #105) | task | 🔓 unblocked |
| 129 | Add date-range export endpoint (graduated from #105) | task | 🔓 unblocked |
| 130 | Add paged daily-summaries endpoint for the Reports feed (graduated from #105) | task | 🔓 unblocked |
| 131 | Fix Reports access scope: GLOBAL VIEW_BRANCH_DATA = all branches + accessible-branches endpoint (graduated from #105) | task | 🔓 unblocked |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #105 closed this session.)

## Recommended next pick

- **#106 (User Management)** — the last HITL prototype; deactivate + slot ordering; the UserBranchAssignment surface. Needs /prototype + /grilling + /domain-modeling with falsification-before-claim (every prior prototype falsified most of its ticket's premises — expect the same here: slot endpoints, deactivate semantics, MANAGE_USERS global gate).
- **AFK backend tasks #128–#131** — all independent, unblocked, no dependencies; any is a clean session. Suggested order: #131 (access scope — unblocks the whole Reports read surface) → #130 (feed) → #129 (range export) → #128 (public gate drop, smallest). Each per `backend/AGENTS.md` (quality gate, /code-review rounds 1+2, tests, k6 deferral precedent #98/#115).
- **Merged Finance & Reports build ticket** — ungraduated; opens when #128–#131 land + #117's expense-GET-includes-soft-deleted question is decided. Its spec = #105's outline (D1–D7) + #101's outline (edit-mode D1–D8) + #117 read-backs.
- **Candidate backend fog (small AFK hardening tickets)**: the cross-draft line-session raw 500 (#120 fog — `idx_remittance_line_session` insertIgnore→`.single()` NoSuchElement) and the `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. HITL prototype flow (#106): /prototype + /grilling + /domain-modeling per map Notes; falsification-before-claim per screen; outline asset to `docs/prototypes/0106-*.md` on a `prototype/0106-*` branch. AFK flow (#128–#131): /implement per `backend/AGENTS.md`, /code-review rounds 1+2.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update BOTH the table row AND the closing paragraph).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` **typed integer**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Table: #105 → ✅ closed; **#120 + #124 rows corrected to ✅ closed** (stale from prior sessions — table vs closing-paragraph drift, fixed); 4 new rows #128–#131 (unblocked).
- Decisions-so-far: new #105 entry (merge headline, 9 falsification findings, D1–D7, 4 graduations, relief-invite fog, resolution link).
- Not-yet-specified: + relief-invite flow entry, + merged Finance & Reports build ticket note (opens after #128–#131 + #117 decision).
- Closing paragraph: frontier = #106 + #128–#131; chains complete (remittance, audit-log); merged build ungraduated.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #106 (falsification-first per screen; outline asset on `prototype/0106-*` branch).
- **`/implement` + `/code-review`** — for the AFK backend tasks #128–#131 (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
