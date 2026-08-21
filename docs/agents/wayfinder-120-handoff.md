# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 23

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 23 resolved the AFK task **#120** ("Build — Remittance screen (graduated from #103)") — `wayfinder:task`, the full D1–D10 frontend build consuming #118 + #119. Resolution comment: https://github.com/jsongalvez/company_app/issues/120#issuecomment-5230294548. Ticket closed; map #89 updated (Decisions-so-far #120 entry, closing paragraph). Commit `ec82f41` on `ralph/company-app-full-build` (12 files, +3381/−20), **not pushed** (per ticket + precedent; pre-commit gate passed). **The remittance chain (#103→#118→#119→#120) is complete. Frontier now: 2 unblocked HITL prototypes (#105 Reports, #106 User Management) + 1 unblocked AFK task (#124 REMITTED-day required reason).**

**Next session pick:** **#124 (REMITTED-day required reason, unblocked, small standalone AFK)** or the **2 HITL prototypes (#105 Reports, #106 User Management)**. The remittance screen is fully built; no remittance work remains open. Finance build (graduated from #101) still ungraduated — its build ticket opens once the read-back gaps are closed (they are; the expense-GET-includes-soft-deleted question is the only open item for that build to decide).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #120 entry, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#120 resolution comment: https://github.com/jsongalvez/company_app/issues/120#issuecomment-5230294548** (build decisions + fog)
- #119 resolution comment: https://github.com/jsongalvez/company_app/issues/119#issuecomment-5230103071
- #118 resolution comment: https://github.com/jsongalvez/company_app/issues/118#issuecomment-5229863887
- #124 ticket body (the next AFK candidate — spec line 19, surfaced by #104 F6)
- Outline assets: `docs/prototypes/0103-remittance-outline.md` (branch `prototype/0103-remittance`), `docs/prototypes/0104-audit-log-outline.md` (branch `prototype/0104-audit-log`)
- Prior handoffs: `docs/agents/wayfinder-119-handoff.md` (this session's parent)

## Session outcome

**#120 (Build — Remittance screen) — resolved + closed (AFK).** D1–D10 all shipped:

- **`RemittanceListScreen`** — Drafts/Submitted/All tabs (default Drafts), rows (type label, method, date range, status, **Net on submitted SESSION rows only** via the snapshot join), create popup (type/method dropdowns, today→today Manila defaults, client-UUID idempotent create — server's `insertIgnore` returns the existing draft on duplicate, treated as success), load-on-entry + refresh, keep-last-list per tab, RouteGateCard `SUBMIT_REMITTANCE` at both NavHost call sites. Desktop dense table / mobile cards via `RemittanceRowList` expect split.
- **`RemittanceDetailScreen`** (pushed both platforms) — header editable in place while DRAFT (PATCH, version-locked, popup prefilled = D9); lines with labels joined from the #118 picker payloads (F7 bare lines) + type badge + added-at (relative) + amount + delete; tick-to-include **income pickers** (shared generic `IncomePickerDialog<T>` + `PendingQueueEffect<T>` add-queue; amount prefilled from finalPrice/totalAmountAtTime, editable at add time; sessions show time·status, product sales show qty); days-covered picker (effective statuses, **REMITTED greyed**, removable); submit confirm (covered-day **dates** via dayLabels join, line count, line total, method + the exact 48h warning copy) → on success the detail reloads and the frozen breakdown appears; **frozen-at-submission receipt block** (SESSION only; hairline BorderStroke card + Canvas-drawn lock glyph + muted "Frozen at submission" + snapshottedAt) with **lazy cached drift expander** (fetch once on first expand, re-arm on failure; gross never drifts; PRODUCT hides); **undo within 48h** (reason required single-line; button keyed on `submittedAt != null && status == SUBMITTED` + JVM-clock check — `remittanceCanUndo` in RemittanceUi.kt).
- **`RemittanceViewModel`** — list/pickers/drift/undo/updateHeader added; **ADR-0022 axes on all 7 mutations**: 403 → silent exit (Loading→Idle without Success), 409 → mutation state Idle + changed-elsewhere banner + detail reload (`reloadDetailAfterConflict`); expectedVersion from `detail.version` for submit/undo/PATCH (line/day mutations take version server-side — no request field, verified against #119's repository); banner clears on the user's next successful mutation.
- **Build decisions recorded**: (1) **shared-breakdown-day = accept-save-wins** — picker greys effective-REMITTED days only; server authoritative (closes the #119 fog); (2) undo-button hide key per #119 decision 4; (3) createDraft duplicate = success, no special case; (4) audit UPDATE-row reasons already rendered generically by #123's `AuditLogEntryRow` — no change; (5) pickers load on detail entry (Idle **or Error** — failed pickers retry on the next detail reload), cached after.
- **New fog**: (a) **cross-draft line-session raw 500** — a session already in *another* draft's line hits `idx_remittance_line_session` as insertIgnore → `.single()` NoSuchElement; undetectable client-side; backend hardening candidate (exclude sessions already in any line from the G2 picker, or clean 409); (b) **iOS expect/actual list coverage gap** — `AppNavHost`/`ClientResultList`/`AuditLogEntryList`/`RemittanceRowList` actuals exist for android+desktop only; iOS compile was already failing on HEAD before this change (verified by stash); (c) `added-by` (`createdBy` UUID) has no name join anywhere.
- **Falsification note**: the ticket's premises all verified correct this time (no backend corrections needed — #118/#119 had already falsified the backend side). The one build-time discovery was the `kotlin.time.Clock` vs `kotlinx.datetime.Clock` module reality (project uses `kotlin.time.*` — stdlib, not kotlinx) and the picker-queue 409 stall (found by /code-review round 1, not by tests).

## Key decisions (recorded in full on the resolution comment)

1. **Shared breakdown days: accept-save-wins** — the picker greys days whose *effective* status is REMITTED (from #118 G4); a day locked by submitted B greys until B is undone, then becomes selectable in draft A. No server-side exclusion (consistent with #119's "aggregates may shift — accepted").
2. **409-mid-batch queue handling** — a 409 during a multi-row picker add resets the mutation state to Idle (VM); `PendingQueueEffect` treats Idle like Error: **abort the batch** (clear queue, dialog stays open showing the error/reloaded state). Without this the dialog stalled permanently (Add disabled + dismissal blocked). Round-1 /code-review catch.
3. **Banner lifetime** — changed-elsewhere notice survives the conflict reload (`resetNotice=false` inside `reloadDetailAfterConflict`) but clears on the user's next successful mutation (default `resetNotice=true` on success reloads).
4. **Drift load-once with re-arm** — `driftRequested` flag: first expand fetches; later toggles only open/close; a failed fetch re-arms so the next expand retries.
5. **Line labels from picker joins** — no backend change; the detail loads the three pickers on entry and joins labels by id (fallback generic "Session"/"Product sale" labels).
6. **k6 deferred** — consistent precedent: the Remittance screen is now the first real consumer of these endpoints; k6 for `/api/remittances*` can graduate whenever load testing is wanted (not a blocker).

## Patterns + learnings (cumulative across sessions)

- **The #114 exact-path lesson keeps biting — now 5×**: #120's contribution is the *route-scoped* variant — every new route family needs its own per-route coverage check AND its own before-filter. (Not this session's issue — backend untouched — but the pattern list grows.)
- **409-reset semantics must be handled by every state machine consuming a mutation flow**: the VM resets mutation states to Idle on 403/409 ("silent exit"); any UI state machine keyed on Success/Error alone (like the picker add-queue) deadlocks on Idle. The picker queue needed an explicit Idle → abort branch. When building dialog/queue machinery over the ApiCallHandler flows, enumerate Idle as a terminal state.
- **`kotlin.time.Clock`/`Instant` (stdlib) — NOT `kotlinx.datetime.Clock`**: this codebase uses `kotlin.time.*` for `Clock.System.now()` + `Instant.parse` (TimestampFormat.kt precedent). kotlinx-datetime is used for `LocalDate`/`TimeZone`/`toLocalDateTime` (Manila business dates). Don't import the kotlinx Clock — it's a different type and won't resolve against the project's `now - instant` Duration arithmetic.
- **Expose every UI assumption to review**: the drift-refetch and the 409-queue-stall were both code-review catches, not test catches (MockEngine tests cover the VM, not the dialogs — per map Notes: no UI e2e). The dialog-level state machines are the highest-risk untested surface; keep them as simple as possible.
- **Sticky banner anti-pattern**: "one-shot" notices that survive every later reload become effectively permanent. If a notice must clear, decide *when* (here: on the user's next successful mutation) — a passing `resetNotice=false` on every success path is a smell.
- Prior-session patterns unchanged: falsification-first against live code, /code-review Standards + Spec parallel sub-agents (round 1 full diff + round 2 fix delta — both PASS after round-1 fixes), commit on `ralph/company-app-full-build` not pushed, k6 deferred, JMH in CI. Pre-commit gate passed (ktlint + backend gate + shared compile + Postgres).
- **iOS compile is pre-existing-broken**: `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals for `AppNavHost`, `ClientResultList`, `ClientDetailLayout`, `AuditLogEntryList`) — verified by stashing this session's changes. Any future iOS work (or the pre-push gate's multi-target compile) hits this first.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` + dependency summaries (`blocked_by` = open blockers only):

| # | Title | Type | Status |
|---|-------|------|--------|
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |
| 124 | Add required reason to REMITTED-day edits (spec line 19, surfaced by #104 F6) | task | 🔓 unblocked |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next pick

- **#124 (REMITTED-day required reason)** — small standalone AFK backend task; spec line 19; touches ~8 services; interacts with #123's flag-reason display (the Audit Log screen already renders `entry.reason` generically, so a reason on REMITTED-day edits will display without frontend work). Good closing-batch candidate.
- **HITL alternatives: #105 (Reports)** — D8 of #101 already specified the download-button surface; the Reports prototype also owns the monthly-summary gate-split question (F9: VIEW_BRANCH_DATA = Accountant-readable) — and **#106 (User Management)**. Both need /prototype + /grilling + /domain-modeling with falsification-before-claim per screen.
- **Finance build (graduated from #101)** — not yet ticketed; its read-back gaps are closed (#117). When a session wants it: graduate the build ticket; it owns the expense-GET-includes-soft-deleted question (D6 dimmed-deleted rows vs the live `deleted_at IS NULL` filter — #117's fog note).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend build flow (#124): falsification-first against live code (ticket premises unreliable — #118 found a security hole; #119 found a missing timestamp column), /code-review (Standards + Spec, round 1 full diff + round 2 fix delta) at the end, commit on `ralph/company-app-full-build`, not pushed, k6 deferred (no frontend consumer precedent).
3. HITL prototype flow (#105/#106): /prototype + /grilling + /domain-modeling per map Notes; falsification-before-claim per screen (every prior prototype falsified most of its ticket's premises).
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` **typed**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #120 entry (D1–D10 shipped, 6 build decisions, /code-review round 1+2 outcomes, 20 new tests, new fog — cross-draft line-session 500, iOS expect/actual gap, added-by no-name-join; "remittance chain complete").
- Closing paragraph: frontier now 2 HITL prototypes + #124 (remittance chain complete; #120 closed this session with resolution link + commit); carried-forward items re-listed (audit-log chain complete, #119/#118/#103/#117 closed, Finance build still ungraduated + expense-GET question open); fog unchanged + new #120 fog.
- Not-yet-specified: unchanged (SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, pushed-route topbar pattern, audit branch-name display, audit date-picker refinement).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for any AFK build ticket (#124 backend; future frontend builds); module flow per `backend/AGENTS.md` or `composeApp/AGENTS.md`.
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; round 2 on the fix delta).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #105/#106 (falsification-first per screen).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
