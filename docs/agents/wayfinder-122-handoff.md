# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 19

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 19 resolved the AFK task **#122** ("Add audit log browse + table-list endpoints with capability-window scoping") — `wayfinder:task`, falsification-first against live code. Resolution comment: https://github.com/jsongalvez/company_app/issues/122#issuecomment-5229525045. Ticket closed; map #89 updated (frontier table, Decisions-so-far incl. new #122 row, closing paragraph). Commit `2ad1460` on `ralph/company-app-full-build` (12 files, +1267/−99), **not pushed** (per ticket + #114–#117/#121 precedent; pre-commit gate passed cleanly).

**Next session pick:** either **#123 (Build — Audit Log screen — now UNBLOCKED, its only blocker #122 is closed)** or **#118 / #119 (remittance read-back AFK tasks, unblocked)** or **#124 (REMITTED-day required reason, unblocked)** or the **2 HITL prototypes (#105 Reports, #106 User Management)**. No `grilling`/`research` tickets remain; frontier is all `task` + 2 `prototype`.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #122 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#122 resolution comment: https://github.com/jsongalvez/company_app/issues/122#issuecomment-5229525045**
- #104 resolution comment (parent decision, D1–D10): https://github.com/jsongalvez/company_app/issues/104#issuecomment-5204913626
- #121 resolution comment (foundation: branch_id + registry): https://github.com/jsongalvez/company_app/issues/121#issuecomment-5206092884
- Outline asset: `docs/prototypes/0104-audit-log-outline.md` (branch `prototype/0104-audit-log`)
- Prior handoff: `docs/agents/wayfinder-121-handoff.md`

## Session outcome

**#122 (Audit log browse + table-list endpoints) — resolved + closed (AFK).** Falsification corrected the ticket's gate premise in the *worse* direction: the GLOBAL `ASSIGN_COMPENSATION` before-filter matches only the exact `/api/audit-log` path (Javalin exact-path matching, the #114 lesson), so `GET /flagged` and `PATCH /acknowledge` were **completely ungated** — not merely 403-for-everyone. Removing the filter per D9 (no route gate, service-authoritative scoping) fixed the lockout and the leak in one move. Shipped: **`GET /api/audit-log/entries`** (filters tableName/action/callerName-ILIKE/dateFrom-dateTo as inclusive Manila days, keyset cursor `(changed_at, id)` opaque base64url, limit 20 default/100 max, `changedByName`+`branchId` in DTO), **`GET /api/audit-log/tables`** (serves #121's `AuditLogTableRegistry`, NOT `DISTINCT table_name` per D4), per-record + `/flagged` window-scoped with `changedByName`, acknowledge = read-window minus editor with **self-ack 409** (D2) and out-of-window/unknown/acked → 404. **No new index migration**: the ticket's conditional ("check `idx_audit_time`/existing before adding") resolved — `idx_audit_time (changed_at)` (V1) covers the keyset anchor; `idx_audit_branch` (V12) covers the window filter.

## Key decisions (recorded in full on the resolution comment)

1. **Gate removal, not rework** — D9 says no route gate (always-visible, backend authoritative); the #104 F2 gate was removed entirely. Zero-grant callers get 200 + empty list (the "No branch access" empty state), never 403.
2. **Scoping declared in ONE place: `AuditLogReadScope`** (service pkg). Window = distinct BRANCH-context grants from `active_user_capabilities` (new `CapabilityRepository.findBranchWindow`). **GLOBAL `VIEW_BRANCH_DATA` grant → window = all branches** (dev Owner; Accountant "read-only across all branches" per V2 seed) — this also powers the NULL-row fallback.
3. **Branchless policy extended beyond the ticket's named trio** (judgement call, accepted by /code-review): `client`/`concern` → any-holder `EDIT_BRANCH_DATA`; `product`/`product_category` → GLOBAL `MANAGE_PRODUCTS`; `app_user`/`branch` → GLOBAL `MANAGE_USERS`. Without this, product/category/concern rows (the #121 NULL write-sites) would be Owner/Accountant-only — wrong for MANAGE_PRODUCTS holders. Unlisted NULL legacy rows → GLOBAL `VIEW_BRANCH_DATA` proxy (unreachable today: backfill was overridden off in #121).
4. **Cursor = opaque base64url(`changedAt|id`)**, strictly-before keyset, `limit+1` fetch detects the next page (no empty tail page). First keyset usage in the backend (#104 fog — now implemented).
5. **Date params = inclusive Manila calendar days** (`yyyy-MM-dd`); dateFrom inclusive (`greaterEq` — a /code-review Spec fix; boundary test added), dateTo exclusive (day+1 start).
6. **`findById` leftJoins app_user** (/code-review Spec fix) — a scrubbed `changed_by` user must not hide the entry; `changedByName` is nullable.
7. Capability checks route through `CapabilityService` facade (/code-review Standards fix) — new delegates `findBranchWindow`/`hasCapabilityAnyContext`.

## Patterns + learnings (cumulative across sessions)

- **Falsification first holds every session**: this ticket's gate premise was wrong in the opposite direction (ungated, not merely 403) — the #114 exact-path lesson generalizes: *any* audit/route rework must grep which paths a `before` filter actually matches.
- **Exposed `insert {}` lambda's receiver is the Table object** — inside `transaction { AuditLogTable.insert { it[AuditLogTable.recordId] = recordId } }`, the unqualified `recordId`/`tableName` resolve to the **table's** members, silently emitting `audit_log.record_id` as a column ref (SQL: `VALUES (..., audit_log.record_id, 'audit_log', ...)` → PG "invalid reference to FROM-clause entry"). Test code must use local names that don't collide (`val recId = recordId`). This bit in AuditLogServicePostgresTest and took an SQL dump to find.
- **Exposed 1.3.1 `Query` lives in `org.jetbrains.exposed.v1.jdbc`**, not `.core` (renamed from the old `AbstractQuery`).
- **Javalin testtools `patch(url, body, consumer)`**: no-body PATCH must pass `null` as body (`client.patch(url, null, asUser(u))`), else the consumer is serialized as the body (kotlinx "Serializer not found").
- **Audit endpoints now serve the read window** — `/flagged`, per-record, `/tables`, `/entries` are all capability-window-scoped or ungated; the old GLOBAL gate is gone. `AuditLogViewModel` (composeApp, #108 foundation) still calls the old 3 endpoints — DTO gains are additive, no frontend break, but #123 must add the browse/tables calls.
- Prior-session patterns unchanged: /code-review Standards + Spec parallel sub-agents at the end (both PASS after the facade + boundary + leftJoin fixes), commit on `ralph/company-app-full-build` not pushed, k6 deferred (no frontend consumer), JMH gate in CI (no pre-push JMH).

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` + dependency summaries:

| # | Title | Type | Status |
|---|-------|------|--------|
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |
| 118 | Add remittance read-back endpoints (graduated from #103) | task | 🔓 unblocked |
| 119 | Add remittance undo + draft editing endpoints (graduated from #103) | task | 🔓 unblocked |
| 120 | Build — Remittance screen (graduated from #103) | task | 🔒 blocked (#118, #119) |
| 123 | Build — Audit Log screen (graduated from #104) | task | 🔓 unblocked — #122 closed, its only blocker |
| 124 | Add required reason to REMITTED-day edits (spec line 19, surfaced by #104 F6) | task | 🔓 unblocked |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next pick

- **#123 (Build — Audit Log screen)** — now fully unblocked; the complete backend contract is in the #122 resolution comment: browse (`AuditLogBrowseResponse {entries, nextCursor}`, feed cursor back as `?cursor=`), tables (`[{tableName, label}]`), `/flagged` scoped, **self-ack → 409** (don't offer Acknowledge on the caller's own flagged row — keep row + inline error per ADR-0022), zero-grant = 200 + empty (D9 "No branch access" state). Frontend build flow per composeApp/AGENTS.md; `AuditLogViewModel` exists (#108) and needs browse/tables wiring. Falsification watch: D2 pool = "any active-grant holder at the flag's branch" — the window is capability-sourced, not assignment-sourced.
- **#118 / #119 (Remittance read-backs / undo+edit)** — unblocked AFK backend; #118 unblocks #120 (Remittance build). #103 D1–D10 in the resolution comment.
- **#124 (REMITTED-day required reason)** — small standalone AFK; spec line 19; touches ~8 services; note it interacts with #123's flag-reason display.
- HITL alternatives: **#105 (Reports)** — falsification watch: #101 F10 verified export endpoints exist (daily/monthly/all-time, CSV/PDF, VIEW_BRANCH_DATA); **#106 (User Management)** — falsification watch: user list / deactivate / slot-ordering endpoints + MANAGE_USERS global-gate reachability (the #104 lesson: verify grants exist before trusting gates).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend build flow: falsification-first against live code (ticket premises unreliable), /code-review (Standards + Spec) at the end, commit on `ralph/company-app-full-build`, not pushed, k6 deferred (no frontend consumer).
3. HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes; falsification-before-claim per screen.
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` **typed**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; **verify new tickets appear in the sub-issues query + dependency summaries — the #113 mislink and the 422-on-string lesson both bite**).

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #122 row → ✅ closed (was 🔓 unblocked); #123 blockers → #122 only (now unblocked).
- Decisions-so-far: new entry for #122 (falsification incl. ungated-/flagged discovery + no-index decision, gate removal per D9, browse/tables shipped, `AuditLogReadScope` single-source policy, /code-review fixes, #123 handoff).
- Closing paragraph rewritten: frontier now 2 HITL + 2 AFK + #124; #123 unblocked on #122 only; #122 gist; #121/#104/#103/#117 state carried forward; Finance build still ungraduated; fog unchanged; JMH-noise warning carried (gate moved to CI).
- Not-yet-specified: unchanged (no new fog — the session produced a resolution + handoff, not tickets).
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, pushed-route topbar pattern.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for any AFK build ticket (#123/#118/#119/#124); backend flow per `backend/AGENTS.md`.
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; hand untracked files to the sub-agents explicitly).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #105/#106 (falsification-first per screen).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
