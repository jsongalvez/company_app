# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 21

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 21 resolved the AFK task **#118** ("Add remittance read-back endpoints: draft list + line pickers + days list + snapshot + drift (graduated from #103)") — `wayfinder:task`, falsification-first against live code. Resolution comment: https://github.com/jsongalvez/company_app/issues/118#issuecomment-5229863887. Ticket closed; map #89 updated (frontier table, Decisions-so-far incl. new #118 row, closing paragraph). Commit `93eda41` on `ralph/company-app-full-build` (8 files, +2195/−24), **not pushed** (per ticket + precedent; pre-commit gate passed cleanly). **#120 (Remittance build) now blocked on #119 only.**

**Next session pick:** **#119 (remittance undo + draft editing, unblocked AFK backend — unblocks #120)** or **#124 (REMITTED-day required reason, unblocked, small standalone)** or the **2 HITL prototypes (#105 Reports, #106 User Management)**. No `grilling`/`research` tickets remain; frontier is all `task` + 2 `prototype`.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #118 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#118 resolution comment: https://github.com/jsongalvez/company_app/issues/118#issuecomment-5229863887**
- #103 resolution comment (parent prototype, D1–D10): https://github.com/jsongalvez/company_app/issues/103#issuecomment-5200537056
- #119 ticket body (the next AFK build; inherits the per-route filter pattern + `recordDelete` audit precedent)
- Outline asset: `docs/prototypes/0103-remittance-outline.md` (branch `prototype/0103-remittance`; the G8 block = #119's scope)
- Prior handoff: `docs/agents/wayfinder-123-handoff.md`

## Session outcome

**#118 (remittance read-back endpoints) — resolved + closed (AFK).** All G1–G7 shipped: G1 `GET /api/remittances?branchId=&status=` list (status filter DRAFT/SUBMITTED/ALL, LEFT JOIN snapshot → `netIncome` additive-nullable on `RemittanceResponse` for SUBMITTED SESSION rows only, `createdAt DESC, id DESC` tiebreak, no pagination, branch-exists 404); G2/G3/G4 branch-parented pickers `GET /api/branches/{branchId}/remittance-sessions|product-sales|days?from=&to=` (clientName from name parts/null-if-anonymized, bookedAt, finalPrice, sessionStatus; productName/qty/totalAmountAtTime/soldAt; days with **effective** status via lazy `evaluateStatus` in-memory — no per-day DB round-trip); G5 `DELETE /api/remittances/{remittanceId}/day-breakdowns/{breakdownId}` (draft-only 400, parent-child scoped — both ids in WHERE, audit DELETE row, **no version bump — matches existing ADD**); G6 additive nullable `snapshot` block in detail (`RemittanceFinancialSnapshotResponse`, SUBMITTED SESSION only); G7 `GET /api/remittances/{remittanceId}/drift` (frozen vs current comp/expenses/net over breakdown days, reusing submit's exact sums via **extracted `calculateCompensationSum`/`calculateExpenseSum` + shared `netOf`** — drift numbers equal a re-submit; 404 when no snapshot).

**Falsification findings:**
- **F9 — SECURITY HOLE fixed**: the existing `before("/api/remittances/{remittanceId}")` filter is 3-segment and did NOT cover the 4–5-segment sub-routes — `POST .../lines`, `DELETE .../lines/{lineId}`, `POST .../day-breakdowns`, `POST .../submit` were **capability-ungated** (any authenticated user could mutate any remittance; the #114 exact-path lesson; #103 F8 checked codes+roles, not filter coverage). Fixed in-scope per ticket convention: 6 new per-route `SUBMIT_REMITTANCE` @ BRANCH filters + 5 authz regression tests.
- **#103 F13 ("drafts overlap freely") is wrong** — DB enforces `UNIQUE (branch_id, type, submitted_date)` (V1:443): one SESSION + one PRODUCT draft per branch per calendar day; `insertIgnore` swallows the violation silently (2nd same-type draft = no-op, not 409). Frontend should treat the 2nd create as "already exists" (idempotent create pattern, #100 D7 precedent).
- Voided sessions must be excluded from the sessions picker — void status is **separate** from `session_status` (a voided session can be COMPLETED), so the frontend cannot grey them out; LEFT JOIN `active_session_voids` (V1:539 "always use the view").

## Key decisions (recorded in full on the resolution comment)

1. **Per-route before-filters on every remittance sub-route** — fixes F9; each filter calls `requireBranchCapabilityForRemittance` (detail-style gate). #119 must follow suit (its new routes: undo, header PATCH).
2. **Pickers are branch-parented** (`/api/branches/{branchId}/remittance-*`) — avoids static-vs-`{remittanceId}` filter ambiguity (the #114 lesson); uniform `SUBMIT_REMITTANCE` gate via `requireBranchCapabilityForBranchId`; from/to required + `to >= from` 400.
3. **Picker DTOs carry grey-out fields** — `sessionStatus` (sessions) so the frontend can dim non-remittable rows; quantity+soldAt (product sales) for display. Deliberate scope additions beyond G2/G3's minimal "name + amount" — justified by the D3/D4 grey-out pattern.
4. **Voided sessions excluded server-side** (see findings).
5. **Day-breakdown DELETE does NOT bump remittance version** — matches the existing ADD (which doesn't bump either; lines own the version / 409-axis). If #119 wants breakdown mutations version-locked, that's a deliberate change to both ADD and DELETE.
6. **Drift = submit's exact sums** — extracted + wrapped in their own `transaction {}` (they were private + naked, only safe inside submit's SERIALIZABLE transaction — calling them standalone threw "No transaction in context" 500, caught by tests); `netOf` shared so submit and drift can't diverge.
7. **`status=ALL` accepted as explicit no-filter** (ticket's "DRAFT/SUBMITTED/all").
8. **Drift 404 on no-snapshot** (DRAFT or PRODUCT) — D6 hides the expander for PRODUCT; 404 is the honest "no baseline" signal.
9. **`createdAt`-ordering test uses insert-then-`UPDATE`** — raw INSERT with explicit `created_at` hit a PG 18 partial-EXCLUDE constraint plan error ("invalid reference to FROM-clause entry"); the two-step avoids it (RouteValidationTest's insert shape works because it never sets created_at).
10. **Exposed insert-lambda Table receiver shadows test fields** — fields named like columns (`branchId`, `submittedDate`, `createdAt`) resolve to the COLUMN (SQL rendered `remittance.branch_id` as a VALUES literal — "invalid reference to FROM-clause entry"). Fix: local aliases + comment at `insertDraftDirect`. Future tests: beware `Table.insert { }`/`update { }` receivers.
11. **insertIgnore silently skips on non-unique errors too** — the remittance unique, `idx_client_one_pending_session`, `idx_remittance_line_session` all manifest as "row not found after insert" not conflicts. Tests must use distinct types/clients/statuses deliberately.

## Patterns + learnings (cumulative across sessions)

- **The #114 exact-path lesson keeps biting**: `before("/api/remittances/{remittanceId}")` (3-seg) doesn't cover 4–5-seg sub-routes. ALWAYS verify per-route filter coverage for every new/changed route family (audit, inventory, remittance — three times now).
- **INSERT ... ON CONFLICT DO NOTHING swallows FK/NOT-NULL/CHECK errors too** in PG — Exposed's `insertIgnore` masks real data bugs as silent no-ops; the "row not found after insert" `error()` in createDraft is the tripwire. When a test hits it, suspect a suppressed constraint, not the insert itself.
- **Exposed query `.where {}` on a stored `val query = Table.leftJoin(...).selectAll()`** — fine (AuditLogRepository precedent). The earlier FROM-clause failure in `list orders` was the test's own INSERT (shadowing), not the join.
- **`row.getOrNull(column)` for LEFT JOIN nullable reads** (Exposed 1.3.1) — verified available; snapshot-net join uses it.
- Prior-session patterns unchanged: falsification-first against live code, /code-review Standards + Spec parallel sub-agents (round 1 full diff + round 2 fix delta, both PASS), commit on `ralph/company-app-full-build` not pushed, k6 deferred, JMH in CI.
- **Test-DB hygiene**: failed mid-suite runs leave untracked rows that cascade into later test classes (AuditLogAuthzTest broke with "expected 11 but was 658" from leftover audit rows). Recovery = drop + recreate `company_app_test` (Flyway re-migrates V1–V12 + V2 seeds on next run). TRUNCATE is NOT safe — it wipes V2 capability/role seeds and Flyway won't re-run them.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` + dependency summaries:

| # | Title | Type | Status |
|---|-------|------|--------|
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |
| 119 | Add remittance undo + draft editing endpoints (graduated from #103) | task | 🔓 unblocked |
| 124 | Add required reason to REMITTED-day edits (spec line 19, surfaced by #104 F6) | task | 🔓 unblocked |
| 120 | Build — Remittance screen (graduated from #103) | task | 🔒 blocked (#119) |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next pick

- **#119 (remittance undo + draft editing)** — unblocked AFK backend; unblocks #120. Scope = the outline's G8: undo (48h window server-enforced, reason required, snapshot delete + trigger carve-out via migration, day unlock with lazy status re-derivation, audit trail, version bump) + header PATCH (draft-only, version-locked). **Precedent now live**: per-route before-filters for every new sub-route (`/api/remittances/{id}/undo`, `PATCH /api/remittances/{id}`); `recordDelete` audit shape from #118's day-breakdown DELETE; the snapshot immutability trigger (`trg_remittance_snapshot_immutable`, V1:472-484) needs a migration carve-out (F11). Falsification watch: verify the trigger's exact protection + whether `submittedDate` reset matters for the UNIQUE (branch,type,submitted_date) constraint on re-submit; the `insertIgnore`-swallows-unique lesson means an undo→resubmit cycle could silently no-op if the unique is hit.
- **#124 (REMITTED-day required reason)** — small standalone AFK; spec line 19; touches ~8 services; interacts with #123's flag-reason display.
- HITL alternatives: **#105 (Reports)** / **#106 (User Management)**.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend build flow: falsification-first against live code (ticket premises unreliable — #118 found a security hole + a wrong F13 premise), /code-review (Standards + Spec, round 1 full diff + round 2 fix delta) at the end, commit on `ralph/company-app-full-build`, not pushed, k6 deferred (no frontend consumer — #98/#115/#118 precedent).
3. HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes; falsification-before-claim per screen.
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` **typed**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #118 row → ✅ closed (was 🔓 unblocked); #120 blocked-by → #119 only (was #118, #119).
- Decisions-so-far: new entry for #118 (F9 hole + fix, F13 correction, G1–G7 shipped, `netOf`/sum extraction, voided-session exclusion, status=ALL, 54 new tests, /code-review round 1+2 outcomes, test-data traps).
- Closing paragraph: frontier now 2 HITL + #119 + #124; #120 blocked on #119 only; #118 closed this session; #123/#121/#122/#104/#103/#117 state carried forward; Finance build still ungraduated; fog carried unchanged (no new #118 fog patches — the F13 correction and F9 fix are decisions, not fog).
- Not-yet-specified: unchanged (SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, pushed-route topbar pattern, audit branch-name display, audit date-picker refinement).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for any AFK build ticket (#119/#124); backend flow per `backend/AGENTS.md`.
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; round 2 on the fix delta).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #105/#106 (falsification-first per screen).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
