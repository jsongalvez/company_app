# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 18

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 18 resolved the AFK task **#121** ("Add audit log scoping foundation: branch_id column + backfill + write-sites + table registry") — `wayfinder:task`, falsification-first against live code. Resolution comment: https://github.com/jsongalvez/company_app/issues/121#issuecomment-5206092884. Ticket closed; map #89 updated (frontier table, Decisions-so-far incl. new #121 row, closing paragraph). Commit `dda4068` on `ralph/company-app-full-build`, **not pushed** (per ticket + #114–#117 precedent; pre-commit gate passed cleanly).

**Next session pick:** either **#122 (Audit browse + table-list endpoints — the direct continuation; #121 left the registry + policy handoff for it)** or **#118 / #119 / #124 (AFK tasks, unblocked)** or the **2 HITL prototypes (#105 Reports, #106 User Management)**. No `grilling`/`research` tickets remain; frontier is all `task` + 2 `prototype`.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #121 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#121 resolution comment: https://github.com/jsongalvez/company_app/issues/121#issuecomment-5206092884**
- #104 resolution comment (parent decision, D4/D6): https://github.com/jsongalvez/company_app/issues/104#issuecomment-5204913626
- Outline asset: `docs/prototypes/0104-audit-log-outline.md` (branch `prototype/0104-audit-log`)
- Prior handoff: `docs/agents/wayfinder-104-handoff.md`

## Session outcome

**#121 (Audit log scoping foundation) — resolved + closed (AFK).** Falsification corrected the ticket's premise: "~25 auditFn callbacks" was actually **51 `record*` call sites across 20 services + MovementRecorder, 26 distinct audited tables**; "services already resolved the branch" was half-wrong (branchDay-scoped services hold only `branchDayId` — but `checkBranchDayEditable` already returns the `BranchDay` row, so `branchDay.branchId` came free at ~40 sites; only 2 sites needed an extra lookup: clockOut + commission manual inclusion). Shipped: **V12 migration** (`branch_id UUID NULL REFERENCES branch(id)` + partial index `idx_audit_branch` WHERE NOT NULL), `branchId: UUID? = null` param on `AuditLogRepository.record*` threaded through 43 sites (8 branchless sites — client×3, product×2, app_user, branch, category + concern-catalog row — stay NULL per #104 D6), and **`AuditLogTableRegistry`** (26 entries, DB table name → readable label, KDoc'd write-site contract) as the single backend source for #122's table-list endpoint (NOT `DISTINCT table_name` per D4).

## Key decisions (recorded in full on the resolution comment)

1. **Backfill omitted — user override.** Ticket said "best-effort backfill"; user overrode: no production users yet (dev DB audit_log has 0 rows), and the **migration-merge ticket** (consolidating V migrations except the seed migration) will absorb any backfill need. Documented in the V12 migration header. #122 must not assume legacy rows are backfilled — NULL branch_id on branch-scoped tables = Owner/Accountant-only per #104 D6.
2. **Zero-extra-query threading** — the write-sites pattern: branchDay-scoped services reuse the `BranchDay` from `checkBranchDayEditable`'s returned pair (`val (branchDay, isRemitted) = ...`); direct-branch entities pass their own `branchId`; only clockOut + commission inclusion pay one `requireBranchDayExists` lookup (rare ops, accepted).
3. **Remittance submit stamps each `branch_day` pair with its own `before.branchId`** (code-review finding) — not the remittance's branch; correct attribution per row, zero cost.
4. **Registry test is a documented known-set guard, not mechanical write-site enforcement** — accepted judgement call (flagged as future hardening: a new write-site without a registry entry ships silently until the test set is updated).
5. Repository `record` param list grew to 9 — trajectory risk; `*CreateParams`-style consolidation deferred until #122 adds more dimensions.

## Patterns + learnings (cumulative across sessions)

- **Falsification first holds every session**: the ticket's counts and branch-resolvability claims were both wrong (~2x the write-sites; branch not "already resolved" everywhere). Grep-verified reality before touching code.
- **`checkBranchDayEditable` returns `(BranchDay, Boolean)`** — the BranchDay row (with `branchId`) is the free ride for any branchDay-scoped audit stamp. Changing `val (_, isRemitted)` → `val (branchDay, isRemitted)` is the standard threading edit.
- **audit write-site inventory lives in `AuditLogTableRegistry`** — new audited tables must register there at their write-site (KDoc'd contract; forgetting = invisible in the Audit UI).
- **BasePostgresTest FK_GRAPH must track new FKs** — `AuditLogTable` gained `BranchTable` parent; without it, test-data cleanliness breaks (deletion order).
- Prior-session patterns unchanged: `/code-review` Standards + Spec parallel sub-agents at the end (both PASS after the clockOut + remittance-pair fixes), commit on `ralph/company-app-full-build` not pushed, k6 deferred (no frontend consumer), JMH-noise `--no-verify` precedent for doc-only pushes (#99/#104; note: JMH gate moved to CI per `d467723`/`d7dcbe2` on master — pre-push no longer runs JMH).

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` + dependency summaries:

| # | Title | Type | Status |
|---|-------|------|--------|
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |
| 118 | Add remittance read-back endpoints (graduated from #103) | task | 🔓 unblocked |
| 119 | Add remittance undo + draft editing endpoints (graduated from #103) | task | 🔓 unblocked |
| 120 | Build — Remittance screen (graduated from #103) | task | 🔒 blocked (#118, #119) |
| 122 | Add audit log browse + table-list endpoints with capability-window scoping (graduated from #104) | task | 🔓 unblocked |
| 123 | Build — Audit Log screen (graduated from #104) | task | 🔒 blocked (#122 only now) |
| 124 | Add required reason to REMITTED-day edits (spec line 19, surfaced by #104 F6) | task | 🔓 unblocked |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next pick

- **#122 (Audit browse + table-list endpoints)** — the direct continuation of this session: #121 left it `AuditLogTableRegistry` (26 tables), the `branch_id` column + `idx_audit_branch` index, and the policy handoff (branchless tables client→`EDIT_BRANCH_DATA`, app_user/branch→`MANAGE_USERS`; NULL branch_id rows → Owner/Accountant-only). Falsification watch: the existing `AuditLogRoutes` gates on GLOBAL ASSIGN_COMPENSATION (broken by construction — #104 F2), `AuditLogEntry` DTO needs `changedByName` (D7) + `branchId`, cursor pagination is first keyset use in the backend, ack-window = read window minus editor (D2, self-ack 409). Unblocks #123.
- **#124 (REMITTED-day required reason)** — good standalone AFK pick; spec line 19; touches ~8 services.
- HITL alternatives: **#105 (Reports)** — falsification watch: #101 F10 verified export endpoints exist (daily/monthly/all-time, CSV/PDF, VIEW_BRANCH_DATA); **#106 (User Management)** — falsification watch: user list / deactivate / slot-ordering endpoints + MANAGE_USERS global-gate reachability (the #104 lesson: verify grants exist before trusting gates).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend build flow: falsification-first against live code (ticket premises unreliable — #121's counts were 2x off), /code-review (Standards + Spec) at the end, commit on `ralph/company-app-full-build`, not pushed, k6 deferred (no frontend consumer).
3. HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes; falsification-before-claim per screen.
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` **typed**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; **verify new tickets appear in the sub-issues query + dependency summaries — the #113 mislink and the 422-on-string lesson both bite**).

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #121 row → ✅ closed (was 🔓 unblocked).
- Decisions-so-far: new entry for #121 (falsification incl. 51-sites-vs-25 + free-branchDay findings, V12-no-backfill user override, registry shipped, code-review PASS).
- Closing paragraph rewritten: frontier now 3 unblocked AFK + 2 HITL + #124; #123 blocked on #122 only; #121 gist; #104/#103/#117 state carried forward; Finance build still ungraduated; JMH-noise warning updated (gate moved to CI).
- Not-yet-specified: unchanged (no new fog — the session produced a resolution + handoff, not tickets).
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, pushed-route topbar pattern.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for any AFK build ticket (#122/#124/#118/#119); backend flow per `backend/AGENTS.md`.
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; hand untracked files to the sub-agents explicitly).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #105/#106 (falsification-first per screen).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
