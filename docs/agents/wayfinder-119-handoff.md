# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 22

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 22 resolved the AFK task **#119** ("Add remittance undo + draft editing endpoints (graduated from #103)") — `wayfinder:task`, falsification-first against live code. Resolution comment: https://github.com/jsongalvez/company_app/issues/119#issuecomment-5230103071. Ticket closed; map #89 updated (frontier table, Decisions-so-far incl. new #119 row, closing paragraph). Commit `a9d2ce2` on `ralph/company-app-full-build` (13 files, +1226/−24), **not pushed** (per ticket + precedent; pre-commit gate passed cleanly). **#120 (Remittance build) now unblocked — `blocked_by: 0` (open blockers), the whole remittance backend chain (#103→#118→#119) is complete.**

**Next session pick:** **#120 (Build — Remittance screen, unblocked frontend build on locked #103 D1–D10)** or **#124 (REMITTED-day required reason, unblocked, small standalone AFK)** or the **2 HITL prototypes (#105 Reports, #106 User Management)**. No `grilling`/`research` tickets remain; frontier is all `task` + 2 `prototype`.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #119 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#119 resolution comment: https://github.com/jsongalvez/company_app/issues/119#issuecomment-5230103071** (falsification corrections + decisions)
- #118 resolution comment (read-back endpoints, G1–G7): https://github.com/jsongalvez/company_app/issues/118#issuecomment-5229863887
- #120 ticket body (the next build ticket — full frontend scope of #103 D1–D10)
- Outline asset: `docs/prototypes/0103-remittance-outline.md` (branch `prototype/0103-remittance`)
- Prior handoffs: `docs/agents/wayfinder-118-handoff.md` (this session's parent)

## Session outcome

**#119 (undo + header PATCH) — resolved + closed (AFK).** All G8 shipped:

- **`POST /api/remittances/{remittanceId}/undo`** — 48h window server-enforced (inclusive boundary), reason required + single-line (400s), status→DRAFT + version bump + `submitted_at` cleared, days unlocked, snapshot deleted, audit rows with the reason on **remittance + per-day + snapshot-delete** (`recordUpdate` gained optional `reason` — additive). Per-route `SUBMIT_REMITTANCE` before-filter.
- **`PATCH /api/remittances/{remittanceId}`** — header edit (type/method/dateRangeStart/End), DRAFT-only 400, optimistic version 409, same validation as create via **extracted shared `parseHeader`** (deduped with handleCreateDraft), **tuple-occupancy 409** (see falsification #3). Covered by the existing 3-segment filter (authz-tested).
- **V13 migration** — `remittance.submitted_at TIMESTAMPTZ` (nullable, no backfill) + **trigger carve-out**: `fn_remittance_snapshot_immutable` REPLACEd — UPDATE always blocked, DELETE allowed only when parent remittance is DRAFT.
- Additive nullable `submittedAt` on `RemittanceResponse`/`RemittanceDetailResponse`/`RemittanceSubmitResponse` (D10 client-side 48h hide; null on DRAFT — undo clears it).

**Falsification findings:**
- **No submission timestamp existed** — `submitted_date` is a calendar DATE (set to today at submit); 48h window needs an instant → V13 `submitted_at`; SESSION fallback = `snapshot.snapshotted_at` (written in submit's txn); neither → 400.
- **Day unlock must be an explicit write** — the ticket's "status revert so lazy `evaluateStatus` re-derives" is half-wrong: `evaluateStatus` only lazily rewrites OPEN→PAST; a REMITTED column stays REMITTED (the #118 G4 picker greys it). Undo writes `evaluateStatus(OPEN, date, today)` per breakdown day.
- **PATCH type-flip into an occupied `UNIQUE (branch_id, type, submitted_date)` tuple = raw 500** — caught live by the authz fixture (PATCH PRODUCT→SESSION collided with the fixture's SUBMITTED SESSION row). Fixed: pre-check occupancy (same branch/type/submitted_date, different id) → clean **409**. F13's constraint, now loud for PATCH (createDraft's insertIgnore still swallows → idempotent create).
- Undo→resubmit cycle verified safe — undo keeps `submitted_date`; re-submit overwrites with today; tuple stays with the same row or moves — no no-op swallow (test covers the full cycle).

## Key decisions (recorded in full on the resolution comment)

1. **Window clock = JVM** (`OffsetDateTime.now(UTC)` via internal `undoAt(now)` for test injection) — documented in the undo KDoc: DB-side `now() - interval` not expressible in Exposed DSL (no DateTimeUnit in Exposed 0.61.0); 48h skew tolerance; same JVM-clock-for-boundaries / DB-clock-for-writes precedent as `evaluateStatus`/`checkBranchDayEditable`. Both code-review axes flagged it round 1; the documented-decision treatment resolved it.
2. **Day unlock = eager explicit write** — spec's "lazy re-derive" mechanism corrected (see findings); end state identical (OPEN if date ≥ today else PAST — nothing writes PAST explicitly; submit is the only REMITTED writer).
3. **Tuple-occupancy 409 on PATCH** — prevents the raw constraint 500; frontend treats it as the create-side "already exists" (F13) mirror.
4. **`submitted_at` cleared on undo** — a reverted draft has no submission instant; DRAFT responses show null (client hide logic: `submittedAt != null && status == SUBMITTED`).
5. **Window-expired → 400** (ValidationException; ticket allowed 400/409; matches #118's non-draft mutation pattern).
6. **Snapshot carve-out = DRAFT-conditional DELETE** (UPDATE still blocked) — narrowest protection that admits undo; reachable exclusively via undo (the only SUBMITTED→DRAFT transition); tested (direct DELETE while SUBMITTED still raises).
7. **k6 deferred** (#98/#115/#118 precedent — no frontend consumer yet; #120 is the consumer).

## Patterns + learnings (cumulative across sessions)

- **The #114 exact-path lesson keeps biting — now 4×**: verify per-route before-filter coverage for every new route family (`/undo` is 4-segment; PATCH rides the 3-segment filter — both authz-tested). Also: a type-flip PATCH can hit a DB UNIQUE as a raw 500 — when a route mutates columns under a unique tuple, pre-check for a clean 409.
- **`insertIgnore` swallows silently — and raw UPDATEs on the same unique explode loudly** (F13's `UNIQUE (branch_id, type, submitted_date)`): createDraft no-ops on collision, updateHeader threw 500 until pre-checked. The F13 lesson now has both faces.
- **Leftover-test-rows cascade into AuditLogAuthzTest counts** ("expected 11 but was 13") — mid-suite failures (e.g. an untracked debug test) leave audit rows that break later classes. Recovery = `bash scripts/clean-test-db.sh` (drop + recreate; Flyway re-migrates V1–V13 + V2 seeds). TRUNCATE is NOT safe (wipes V2 seeds).
- **kotlinx JSON spacing trap**: route responses via `KotlinxSerializationMapper` are compact (`"method":"X"`, `"version":3`) while `AuditLogRepository.buildJson` output has spaces (`"type": "SESSION"`). Test assertions must match per-surface — both directions bit this session.
- **`recordUpdate<T>` audit rows with reason** — base `record()` had `reason`; the convenience `recordUpdate` overloads lacked it. Added optional `reason` (backward compatible) so undo's reason rides the UPDATE row. #123's diff display will now render reasons on UPDATE rows too (was deletes-only) — check the frontend build's expectations.
- Prior-session patterns unchanged: falsification-first against live code, /code-review Standards + Spec parallel sub-agents (round 1 full diff + round 2 fix delta, both PASS), commit on `ralph/company-app-full-build` not pushed, k6 deferred, JMH in CI.
- **Exposed `CurrentTimestampWithTimeZone` is usable in WHERE** as an expression, but there's no interval arithmetic (`DateTimeUnit` absent in Exposed 0.61.0) — DB-side "now() - 48h" comparisons aren't cleanly expressible in pure DSL.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` + dependency summaries (`blocked_by` = open blockers only):

| # | Title | Type | Status |
|---|-------|------|--------|
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |
| 124 | Add required reason to REMITTED-day edits (spec line 19, surfaced by #104 F6) | task | 🔓 unblocked |
| 120 | Build — Remittance screen (graduated from #103) | task | 🔓 unblocked (blocked_by 0; #118+#119 closed) |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next pick

- **#120 (Build — Remittance screen)** — unblocked frontend build; the remittance backend chain is complete (#118 pickers/drift/read-backs + #119 undo/PATCH). Scope = #103 D1–D10 (tabs Drafts/Submitted/All + Net; create popup; line pickers w/ grey-out; days picker; submit confirm + 48h copy + frozen P&L + 409-reload; receipt-styled detail + drift expander; undo affordance w/ reason dialog; header PATCH in place). **Build-time questions the backend sessions surfaced**: (a) shared breakdown days across remittances — a day in draft A's breakdown can also sit in submitted B's; undo of B unlocks it while A references it — server-side exclusion vs accept-save-wins; (b) the #118 G4 picker greys REMITTED days, but nothing server-side blocks adding one (draft-only check is per-remittance); (c) `submittedAt`-keyed undo-button hide (`submittedAt != null && status == SUBMITTED`); (d) audit rows now carry reasons on UPDATE rows too (undo) — the Audit Log screen's diff display may want to render them; (e) createDraft idempotency: 2nd create of the same (branch, type, date) returns the existing draft (F13) — the create popup should treat "already exists" as success-or-navigation.
- **#124 (REMITTED-day required reason)** — small standalone AFK; spec line 19; touches ~8 services; interacts with #123's flag-reason display.
- HITL alternatives: **#105 (Reports)** / **#106 (User Management)**.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend build flow: falsification-first against live code (ticket premises unreliable — #118 found a security hole + a wrong F13 premise; #119 found a missing timestamp column + a raw-500 PATCH path), /code-review (Standards + Spec, round 1 full diff + round 2 fix delta) at the end, commit on `ralph/company-app-full-build`, not pushed, k6 deferred (no frontend consumer precedent).
3. Frontend build flow (#120): composeApp/AGENTS.md conventions, ApiCallHandler + MockEngine ViewModel tests (no UI e2e per map Notes), pushed-route topbar pattern, `LocalNavHostController` CompositionLocal, per #113/#123 build precedents; the frontend gates (composeApp detekt is ungated — match #113's pattern-consistent stance).
4. HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes; falsification-before-claim per screen.
5. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
6. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` **typed**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #119 row → ✅ closed (was 🔓 unblocked); #120 → 🔓 unblocked (was 🔒 blocked on #119).
- Decisions-so-far: new #119 entry (G8 shipped, 3 falsification corrections — missing submitted_at → V13, eager day-unlock write, PATCH tuple-occupancy 409; window-clock decision; submittedAt cleared on undo; 25 new tests; /code-review round 1+2 outcomes; test-data hygiene).
- Closing paragraph: frontier now 2 HITL + #120 + #124; #120 unblocked, its build owns the shared-breakdown-day question; #124 carried; #123/#121/#122/#104/#103/#117 state carried forward; Finance build still ungraduated; fog carried unchanged + new #119 fog (shared breakdown days; audit UPDATE rows now carry reasons — #123 display).
- Not-yet-specified: unchanged (SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, pushed-route topbar pattern, audit branch-name display, audit date-picker refinement).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for any AFK build ticket (#120 frontend, #124 backend); module flow per `composeApp/AGENTS.md` or `backend/AGENTS.md`.
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; round 2 on the fix delta).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #105/#106 (falsification-first per screen).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
