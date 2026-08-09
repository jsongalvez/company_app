# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 20

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 20 resolved the AFK task **#123** ("Build — Audit Log screen") — `wayfinder:task`, falsification-first against live code. Resolution comment: https://github.com/jsongalvez/company_app/issues/123#issuecomment-5229651300. Ticket closed; map #89 updated (frontier table, Decisions-so-far incl. new #123 row, new fog, closing paragraph). Commit `88fcbcc` on `ralph/company-app-full-build` (14 files, +2285/−135), **not pushed** (per ticket + #114–#117/#121/#122 precedent; pre-commit gate passed cleanly). **The audit-log chain (#104→#121→#122→#123) is complete.**

**Next session pick:** either **#118 / #119 (remittance read-back AFK tasks, unblocked — #118 unblocks #120)** or **#124 (REMITTED-day required reason, unblocked, small standalone)** or the **2 HITL prototypes (#105 Reports, #106 User Management)**. No `grilling`/`research` tickets remain; frontier is all `task` + 2 `prototype`.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #123 row, Not-yet-specified incl. new audit fog, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#123 resolution comment: https://github.com/jsongalvez/company_app/issues/123#issuecomment-5229651300**
- #122 resolution comment (backend contract the build consumed): https://github.com/jsongalvez/company_app/issues/122#issuecomment-5229525045
- #104 resolution comment (parent decision, D1–D10): https://github.com/jsongalvez/company_app/issues/104#issuecomment-5204913626
- Outline asset: `docs/prototypes/0104-audit-log-outline.md` (branch `prototype/0104-audit-log`)
- Prior handoff: `docs/agents/wayfinder-122-handoff.md`

## Session outcome

**#123 (Audit Log screen) — resolved + closed (AFK).** All locked #104 D1–D11 shipped: two tabs (For review default / All activity); one-tap pessimistic acknowledge with self-ack button hidden on own rows (409 inline backstop); expandable changed-fields diff (INSERT added / DELETE removed + reason, `"null"` sentinel → "—", malformed side renders nothing); server-driven table dropdown from `/tables` (loading/tap-to-retry states); keyset-cursor Load more (opaque `nextCursor` fed back); filter bar (table/action dropdowns, From/To `yyyy-MM-dd`, caller, Reset); pushed `Route.AuditLogHistory` on both platforms (per-record endpoint, no ack affordance there); silent refresh with keep-last-list (manual + tab re-entry); zero-grant "No branch access" empty state from `SessionState.capabilities` emptiness; shared `AuditLogEntryRow` + `expect fun AuditLogEntryList` (desktop dense rows / android cards). /code-review (parallel Standards + Spec) found real bugs the build then fixed: **stuck-state paths** (deserialization failures froze ack/refresh in-flight flags — every failure path now clears them), **cross-tab refresh-error bleed** (error now renders inside the owning tab), **D10 re-entry reload was loud** (tab re-entry now silent, keep-last-list applies), **network-exception refresh failures were invisible** (now routed to refresh/load-more error lines), **failed refresh discarded the cursor** (Load more availability preserved). Also: shared `ErrorCard` extracted (3rd copy — Notifications/Clients/Audit Log), `formatRelativeTimestamp` extracted from NotificationsScreen, `AuditAction` vocabulary consolidated into one frontend enum.

## Key decisions (recorded in full on the resolution comment)

1. **Browse state model: throwaway call flow + accumulated-list mutation** — refresh/load-more failures can never clobber the accumulated pages (D10 keep-last-list made structural, not screen-side).
2. **Stuck-state hardening** — every failure path (HTTP status via `onNonSuccess`, network exception via block catch, deserialization exception via transform catch) clears in-flight flags + routes to the right error surface; ack/flagged-refresh use synchronous in-flight guards (state-based guards race a rapid double-tap because the handler assigns Loading only after launch).
3. **Tab re-entry refresh is silent** (`refreshFlagged`), first composition is loud (`loadFlaggedEntries`) — distinguished by a `hasVisitedAllActivity` flag.
4. **Failed refresh preserves the cursor** — `_nextCursor` only replaced on success, so a failed refresh keeps the old list AND its Load-more availability.
5. **Date fields = text inputs** (`yyyy-MM-dd`, light client validation, backend 400 → error card) — no material3 date-picker precedent in the alpha port; refinement = fog.
6. **No branch name on rows** — DTO carries `branchId` but the app holds only the *selected* branch's name; partial display misleading for all-branch reviewers (Accountant). Fog: needs a branch-name source (e.g. #98 branch list cached, or names joined into the DTO).
7. **Zero-grant detection** = `SessionState.capabilities` emptiness at the NavHost call site (the only client-side zero-grant signal; backend authoritative either way — 200 + empty).
8. **History screen (Route.AuditLogHistory) = trail + breadcrumb only** — no ack affordance (prototype D8; reviewer confirmed) via `showAcknowledge = false`.
9. **`AuditAction` frontend enum** consolidates dropdown/pill/diff vocabulary (backend enum serializes as name strings, #116 precedent); D4's "never hardcode labels" targets table labels.
10. **Cross-screen extractions**: `ui/screen/ErrorCard.kt` (shared by Notifications/Clients/AuditLog) + `util/TimestampFormat.kt` (`formatRelativeTimestamp`, #112 formatter moved; NotificationsScreen + its test updated).

## Patterns + learnings (cumulative across sessions)

- **The transform is a failure path too.** ApiCallHandler's `onNonSuccess` only covers HTTP statuses; exceptions in `block` AND in `transform` (deserialization!) both bypass it. Any per-call flag cleanup must be wrapped in both lambdas — the parallel /code-review caught the transform-throw freeze (row stuck "Acknowledging…", refresh buttons stuck disabled).
- **MockEngine failure responses need deeper drains than `runCurrent`** — 500-path tests with `advanceUntilIdle()`; success paths drained with a single `runCurrent` but failure paths did not complete (some extra dispatcher hop in the pipeline). No polling loops in this VM, so `advanceUntilIdle` is safe here.
- **`io.ktor.client.engine.mock.respond` needs an explicit import** (it's an extension in MockUtils, not a member) and nested test classes can't see outer-class extension functions (move to top-level).
- **Refresh that nulls its cursor before fetching kills Load-more on failure** — null the cursor only on success (transform), not at call time.
- **ktlint "Backing property" rule**: private-only StateFlows must not use the `_name` convention (no matching accessor) — rename to plain `name`.
- Prior-session patterns unchanged: falsification-first against live code, /code-review Standards + Spec parallel sub-agents at the end (both PASS after the fixes above), commit on `ralph/company-app-full-build` not pushed, k6 deferred (audit reads have a consumer now but no established load baseline), JMH in CI.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` + dependency summaries:

| # | Title | Type | Status |
|---|-------|------|--------|
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |
| 118 | Add remittance read-back endpoints (graduated from #103) | task | 🔓 unblocked |
| 119 | Add remittance undo + draft editing endpoints (graduated from #103) | task | 🔓 unblocked |
| 120 | Build — Remittance screen (graduated from #103) | task | 🔒 blocked (#118, #119) |
| 124 | Add required reason to REMITTED-day edits (spec line 19, surfaced by #104 F6) | task | 🔓 unblocked |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next pick

- **#118 (Remittance read-back endpoints)** — unblocked AFK backend; unblocks #120 (Remittance build). #103 D1–D10 in the resolution comment (https://github.com/jsongalvez/company_app/issues/103#issuecomment-5200537056). Falsification watch: draft-list/snapshot/drift endpoints don't exist yet; snapshot immutability is trigger-protected (V1:472-484 — undo needs a migration carve-out in #119).
- **#124 (REMITTED-day required reason)** — small standalone AFK; spec line 19; touches ~8 services; note it interacts with the audit flag-reason display (#123 now renders `entry.reason` — the flag reason will surface once required reasons land).
- HITL alternatives: **#105 (Reports)** — falsification watch: #101 F10 verified export endpoints exist (daily/monthly/all-time, CSV/PDF, VIEW_BRANCH_DATA); **#106 (User Management)** — falsification watch: user list / deactivate / slot-ordering endpoints + MANAGE_USERS global-gate reachability.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend build flow: falsification-first against live code (ticket premises unreliable), /code-review (Standards + Spec) at the end, commit on `ralph/company-app-full-build`, not pushed, k6 deferred (no frontend consumer — #98/#115 precedent).
3. HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes; falsification-before-claim per screen.
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` **typed**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; **verify new tickets appear in the sub-issues query + dependency summaries — the #113 mislink and the 422-on-string lesson both bite**).

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #123 row → ✅ closed (was 🔒 blocked on #122).
- Decisions-so-far: new entry for #123 (D1–D11 shipped, stuck-state hardening, build-time decisions, /code-review outcomes, audit-log chain complete).
- Closing paragraph rewritten: frontier now 2 HITL + 2 AFK + #124; #120 blocked on #118+#119 only; audit-log chain complete; #121/#122/#104/#103/#117 state carried forward; Finance build still ungraduated; fog carried + new #123 fog.
- Not-yet-specified: **+2 new fog patches** — audit rows show no branch name (needs branch-name source beyond selected branch); audit date fields are text inputs (date-picker refinement ticket if wanted).
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, pushed-route topbar pattern.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for any AFK build ticket (#118/#119/#124); backend flow per `backend/AGENTS.md`.
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; hand untracked files to the sub-agents explicitly).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #105/#106 (falsification-first per screen).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
