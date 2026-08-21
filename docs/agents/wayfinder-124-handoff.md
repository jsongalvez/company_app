# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 24

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 24 resolved the AFK task **#124** ("Add required reason to REMITTED-day edits (spec line 19, surfaced by #104 F6)") — `wayfinder:task`, reason-threading across all 19 REMITTED-day write paths. Resolution comment: https://github.com/jsongalvez/company_app/issues/124#issuecomment-5230521604. Ticket closed; map #89 updated (Decisions-so-far #124 entry, closing paragraph). Commit `45306ba` on `ralph/company-app-full-build` (37 files, +650/−66), **not pushed** (per ticket + precedent; pre-commit gate passed: ktlint + detekt + 657 tests + cleanliness + shared compile). **The audit-log chain (#104→#121→#122→#123→#124) is complete. Frontier now: 2 unblocked HITL prototypes (#105 Reports, #106 User Management).**

**Next session pick:** either HITL prototype — **#105 (Reports)** or **#106 (User Management)** — both need /prototype + /grilling + /domain-modeling with falsification-before-claim per screen. No open AFK tasks remain on the map. The Finance build (graduated from #101) still ungraduated — its build ticket opens once the expense-GET-includes-soft-deleted question (#117 fog note) is decided; the remittance chain + audit chain are both complete.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #124 entry, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#124 resolution comment: https://github.com/jsongalvez/company_app/issues/124#issuecomment-5230521604** (falsification findings + build decisions + fog)
- #123 resolution comment: https://github.com/jsongalvez/company_app/issues/123#issuecomment-5229651300
- Prototype outlines: `docs/prototypes/0101-finance-outline.md` (Finance — the Reports prototype's sibling), `docs/prototypes/0103-remittance-outline.md`, `docs/prototypes/0104-audit-log-outline.md`
- Prior handoffs: `docs/agents/wayfinder-120-handoff.md` (this session's parent)

## Session outcome

**#124 (REMITTED-day required reason) — resolved + closed (AFK).** All 19 write paths threaded:

- **Falsification first — the ticket's core premise was OUTDATED**: `BranchDayService.assertEditableState` (from #77/`ab16a05`) already threw 400 "A reason is required to write on a REMITTED day" when REMITTED + no reason; only expense-delete threaded a reason, so every other REMITTED-day write was **blocked (400), not reason-less** — the work was unblocking via reason-threading, not adding a missing rule. Full path list verified from `isRemitted` usages: 9 files / 19 call sites (ticket estimated ~8 services).
- **Reason field on 14 request DTOs** (nullable `reason: String? = null`, defaulted last — backward-compatible; frontend untouched per ticket Notes); `editReason` on RestockRequest + InventoryMovementRequest only (movement's `reason` is already the movement-type enum — domain collision); void/unvoid reuse their existing non-blank `voidReason`/`unvoidedReason` (audit rows now carry them on all days — expense-delete precedent).
- **Audit writes**: `AuditLogRepository.recordInsert` gained a `reason` param (recordUpdate/recordDelete already had it); every covered path passes `isFlagged = isRemitted, reason = reason`. **Movement audits gained isFlagged + reason** — #77 deliberately skipped inventory; spec line 19's "flagged for audit review" requires For-review visibility, so `StockValidator.validateMovement` now returns isRemitted (the gate outcome IS the flag source) and `MovementRecorder` threads flag+reason to both rows (card update + movement insert).
- **Body-lenient endpoints**: grant/deny relief PATCH + practitioner/concern DELETE parse a body only when one is sent, via new `Context.bodyIfPresent<T>()` (RoutesUtil.kt) — body-less clients (composeApp ReliefAccessViewModel, SessionViewModel) keep working on OPEN/PAST days; the shared gate still 400s them on REMITTED days without a reason. Round-1 /code-review caught that the first cut of the two DELETE handlers used a mandatory body parse → would have 400'd the existing frontend on **every** call.
- Tests: 11 new (suite 646→657, all green) — REMITTED-day no-reason 400 (session status, expense create) + with-reason success + audit flag+reason for session status, void, concern add, expense create, compensation create, allowance create, product sale, relief grant, inventory movement (+ restock). New shared fixtures: `DatabaseTestHelper.createRemittedBranchDay` + `grantEditPastDay`.
- **Build decisions recorded**: (1) `editReason` naming (with rationale); (2) void/unvoid reuse existing reason fields; (3) movement flag added (in-ticket "keep the flag"); (4) bodyIfPresent backward-compat; (5) validateMovement returns isRemitted; (6) k6 deferred (#98/#115 precedent).
- **New fog**: `SessionConcernService.getForSession` — a READ path — calls the write gate `checkBranchDayEditable` without a reason → GET concerns on a REMITTED-day session 400s even for legitimate readers (pre-existing, untouched this session; read-path day-gate question for a future ticket).
- **Test-data traps re-hit** (all documented in #118's resolution): insertIgnore silent-skip on `idx_client_one_pending_session` (REMITTED-day session fixtures need a fresh client), Exposed insert-lambda receiver shadowing a same-named test field (`branchDayId` → VALUES literal → Postgres "invalid reference to FROM-clause entry"), `valid_range` capability constraint (relief-grant fixture must use a same-day REMITTED day so the grant's valid_to window is future), teardown-FK (trackOwned on every created row). `AuditLogAuthzTest` count-bleed from mid-run teardown failures recovered via `bash scripts/clean-test-db.sh` (#119 documented procedure).

## Key decisions (recorded in full on the resolution comment)

1. **Reason-required was already enforced centrally** — the ticket's "carry no reason" premise was false; the gap was that only expense-delete threaded a reason. No new validation rule; pure threading + audit-carry.
2. **`editReason` (not `reason`) on the two inventory DTOs** — movement's `reason` field is the movement-type enum (TESTER/SAMPLE/MISSING/ADJUSTMENT); the REMITTED-day edit justification is a distinct concept. Restock mirrors movement for pair consistency.
3. **Body-lenient parse for endpoints that historically took no body** — `Context.bodyIfPresent<T>()` returns null on empty body; grant/deny + DELETE practitioner/concern use it. Mandatory body parse would have broken the live frontend (round-1 catch).
4. **Movement audit rows flagged** — the flag did not exist for inventory (#77 scope); spec "keep the flag" + For-review inbox require it.
5. **Void/unvoid audit reason = their existing reason fields** — no new DTO field; audit carries the reason on all days (matches expense-delete precedent).

## Patterns + learnings (cumulative across sessions)

- **The #114 exact-path lesson — now 6×**: this session's variant: **endpoint API-shape changes must be checked against live body-less consumers** — two endpoints got a mandatory body parse in the first cut and would have 400'd the existing frontend on every call; the round-1 /code-review Spec axis caught it, not tests. When adding a request-body field, decide explicitly: body-lenient (bodyIfPresent) or mandatory, and check who currently sends no body.
- **`assertEditableState` is the single enforcement point for REMITTED-day reasons** — services only need to *thread*; the 400 is centralized. New write paths that call `checkBranchDayEditable` inherit the rule automatically.
- **Exposed insert-lambda receiver shadowing (the #118 trap) bites again**: a test field named like a table column (`branchDayId`) becomes a COLUMN REFERENCE inside `insert { }` lambdas (receiver = table) → `INSERT VALUES (…, grant_relief_access.branch_day_id, …)` → Postgres "invalid reference to FROM-clause entry". Local variable rename avoids it.
- **insertIgnore silently skips on unique constraints** — a second PENDING session for the same client renders as "row not found" downstream. REMITTED-day session fixtures need a fresh client per session.
- **Capability `valid_range` constraint**: relief-grant fixtures on past-dated REMITTED days fail — the grant's capability window (`valid_from` now → `valid_to` = day+1 04:00 Manila) must be future. Same-day REMITTED fixture works.
- Prior-session patterns unchanged: falsification-first against live code, /code-review Standards + Spec parallel sub-agents (round 1 full diff + round 2 fix delta — both PASS after the bodyIfPresent fix), commit on `ralph/company-app-full-build` not pushed, k6 deferred, JMH in CI. Pre-commit gate passed.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals for `AppNavHost`, `ClientResultList`, `ClientDetailLayout`, `AuditLogEntryList`, `RemittanceRowList`) — any future iOS work (or the pre-push gate's multi-target compile) hits this first.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100` + dependency summaries (`blocked_by` = open blockers only):

| # | Title | Type | Status |
|---|-------|------|--------|
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #124 closed this session.)

## Recommended next pick

- **HITL prototypes (#105 / #106)** — the only remaining frontier. Both need /prototype + /grilling + /domain-modeling with falsification-before-claim per screen (every prior prototype falsified most of its ticket's premises).
  - **#105 (Reports)** also owns the **monthly-summary gate-split question** (F9: VIEW_BRANCH_DATA = Accountant-readable) and inherits #101's D8 (download button = daily CSV/PDF; monthly/all-time stay #105's). Its sibling outline is `docs/prototypes/0101-finance-outline.md`.
  - **#106 (User Management)** — deactivate + slot ordering; the UserBranchAssignment surface.
- **Finance build (graduated from #101)** — not yet ticketed; its read-back gaps are closed (#117). When a session wants it: graduate the build ticket; it owns the expense-GET-includes-soft-deleted question (D6 dimmed-deleted rows vs the live `deleted_at IS NULL` filter — #117's fog note).
- **Candidate backend fog**: the cross-draft line-session raw 500 (#120 fog — `idx_remittance_line_session` insertIgnore→`.single()` NoSuchElement, undetectable client-side) and the `getForSession` read-path day-gate 400 (this session's fog) are both small AFK hardening tickets when the HITL sessions need a breather.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. HITL prototype flow (#105/#106): /prototype + /grilling + /domain-modeling per map Notes; falsification-before-claim per screen (every prior prototype falsified most of its ticket's premises); outline asset to `docs/prototypes/010X-*.md` on a `prototype/010X-*` branch.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` **typed**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #124 entry (premise falsified, 19 paths threaded, 6 build decisions, /code-review r1+r2 outcomes, 11 new tests, new fog — getForSession read gate; "audit-log chain complete").
- Closing paragraph: frontier now 2 HITL prototypes (#124 closed this session with resolution link + commit `45306ba`); carried-forward items re-listed (remittance chain complete, Finance build still ungraduated + expense-GET question open).
- Not-yet-specified: unchanged from #120 handoff (SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, pushed-route topbar pattern, audit branch-name display, audit date-picker refinement) + the #120 fogs (cross-draft line-session 500, iOS expect/actual gap, added-by no-name-join).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #105/#106 (falsification-first per screen; outline assets on `prototype/010X-*` branches).
- **`/implement`** — for any future AFK build ticket (backend per `backend/AGENTS.md`, frontend per `composeApp/AGENTS.md`).
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
