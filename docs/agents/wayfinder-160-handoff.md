# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 57

## What this is

A wayfinder build session on map **#89**. Session 57 picked the frontier ticket **#160 (Build — Relief invite flow, the #159 graduation)**, claimed it, and built it end-to-end under the phased code-review loop. **#160 = the TWELFTH fog-graduation's build; the #105/#106 relief-invite line is now CLOSED end-to-end (decision #159 → build #160).** Resolution: https://github.com/jsongalvez/company_app/issues/160#issuecomment-5296322231. Map #89 updated (Decisions-so-far #160 entry + frontier paragraph rewritten).

**Next-session state:** the frontier is **EMPTY** — no open unblocked child of #89. The next pick comes from the standing fog lines: keep-last-results port for the remaining full-screen lists (the #143 shape, top candidate), the DayStatus shared/domain move, the #92 Q4/Q5 403-refresh machinery, the #158-graduated ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant), the two NEW #160-graduated ARCH lines (shared-Exposed-query-op-helpers — the third private `ilike` copy in ReliefInviteRepository/ClientRepository/AuditLogRepository; screen-scoped-VM-seam — ReliefInviteViewModel serves two screens with disjoint flows), k6 for branch-scoped writes (incl. relief invites — needs #106 seeding), the root-AGENTS.md stale pre-push-iOS line, #110 (hardcoded-month test dates), #139 (standalone OpenAPI map), the user-creation/role-assignment flow (#106), the request-flow grant/deny UI gap (ReliefAccessViewModel unwired).

## Session outcome

**#160 (Build — Relief invite flow) — claimed, built, reviewed (4-pass loop), resolved, closed.** 5 commits `2cbd00f`→`c7b29c7` on `ralph/company-app-full-build`, each pre-commit gate green (ktlint/detekt/903 backend tests/cleanliness/shared-compile/Postgres); desktop suite 313→**328**; backend suite 875→**903** (+24 route tests + registry).

- **Backend**: V19 `relief_invite` + `relief_invite_status` enum + partial unique `(invitee, branch_day_id) WHERE status IN ('PENDING','ACCEPTED')`; shared relief-grant writer extracted (`ReliefAccessRepository.grantReliefCapability(GrantReliefCapabilityParams)`) — `grantWithCapability` delegates, the invite accept calls it; 7 endpoints (create/list-sent/candidates assignment-gated; received/accept/decline/retract bearer + invitee-match); accept = atomic PENDING→ACCEPTED + grant in ONE tx (WHERE-status backstop → 409); day-state is the expiry (past accept 400, no cron); create 409 per-person guard (pre-check + partial-unique insertIgnore count-0); received list serves PENDING-only (resolved rows leave — re-entry can't resurrect).
- **Frontend**: Notifications "Relief invites" section (Accept/Decline, expired-pending renders "Expired", keep-last + no-refire #143, entry-scoped VM, 409→Idle+reload — the #113 pattern); badge = pending invites + unread reminders (`NotificationState.badgeSum` at both consumers, optimistic decrement + 60s poll overwrite); BranchSelect inviter panel per branch card (date pick defaulting to tomorrow, per-query-cancel search, tap-to-invite, sent list with Retract, commit-stamped branch scoping — `sentBranch` flips only at commit so a passing gate always means the rendered list is the panel's).
- **Deviations (recorded in the resolution, not silent)**: create resolves the day resolve-or-create NOT find-only (the #158 find-only discipline governs gates — candidate search IS find-only, pinned by a dayCount==0 test; future-day planning is the use case, lazy day rows would 404 the primary flow); past-day create → 403 via checkBranchDayEditable (the ticket's "past-day 400" = accept's expiry — accept 400s, tested); k6 deferred with the logged #149/#152 reason (DevSeeder seeds only the dev user — no assignment, no second user; land with #106); migration is V19 (V18 taken by the expense `deleted_reason` migration).
- **Loop**: pass 1 (2 HARD: resolved-row resurrect — caught by 3 phases independently; cross-branch sent-list bleed), pass 2 (2 HARD: the pass-1 sent-branch fix didn't deliver its own claim — pre-set label passed the gate while keep-last held foreign rows → commit-stamping; received-side substitution read), pass 3 (**0 HARD — exit**), P5 (2 ARCH fixed in-ticket: conflict-reload unifier + the vacuous search-cancel test → real in-flight cancel with a "cancelled response never commits" assert; 2 graduated: **shared-Exposed-query-op-helpers**, **screen-scoped-VM-seam**).

## Patterns + learnings (cumulative)

- **Session-57 additions**:
  - **The commit-stamp pattern** — a UI gate keyed on "whose data is this" must flip the label at COMMIT, never at launch: a pre-set label makes the gate vacuous while the keep-last slot still holds the previous owner's rows (the pass-2 HARD — all four phases caught the pass-1 fix not delivering its own claim).
  - **The substitution-vs-re-issue division** — a stale-load transform has two jobs: substitution (the synchronous current state — prevents the resurrect) and re-issue (server-truth convergence — rows the action couldn't know). Pin BOTH with tests or one is dead code (P5 found the re-issue unpinned; the i3-lands assert fixes it).
  - **The FIFO-Main ordering contract** — keep-last mirror collectors + substitutions rely on Main.immediate dispatch order; a dispatcher change silently re-opens the resurrect class. Document the contract at the mirrors.
  - **Deviation discipline on truth-class**: the ticket's "find-only" create claim was internally inconsistent (the Q3 falsification record makes future-day planning the primary use case; lazy day rows don't exist for tomorrow). The build deviated with the reason recorded — the loop accepted it because the deviation was proven against the SPEC's OWN falsification record, not against a preference.
  - **The mount-revert hazard**: mid-session, the Windows mount reverted/vanished 4 files between a green build and the next check (3 files recreated from scratch; edits re-applied with grep verification each time; safety snapshot at `/tmp/opencode/ticket160-snapshot.patch`). Verify critical files after any long gap, and grep-verify every edit (the fix-that-didn't-land discipline caught 2 silent edit-misses on the DTO + routes mapping).
- Prior-session patterns unchanged: claim first, facts-via-code, one-ticket-per-session, resolution → close → map update → handoff, batch-fix commits per pass, `git diff <last-pass-commit>` per pass.

## Current frontier (verified live post-session)

Per `gh issue list --state open` + the map's sub-issues: **no open unblocked child of #89 — frontier EMPTY.** Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix).

## Recommended next pick

The standing fog lines in rough priority: (1) **keep-last-results port for the remaining full-screen lists** (User Management, Audit Log, Remittance — the #143 VM-held-list + no-refire shape; the Clients search-field reset on pop-back is a D9-deviation candidate riding it); (2) the **DayStatus shared/domain move** (rides the Finance REMITTED-banner gap + the ride's read side); (3) the **#92 Q4/Q5 403-refresh machinery**; (4) the ARCH graduates: shared-Exposed-query-op-helpers (small, mechanical — 3 private `ilike` copies), relief-surface trichotomy state, generation-aware ApiCallHandler variant; (5) the request-flow grant/deny UI gap (ReliefAccessViewModel unwired — the invites notifications-section pattern is its natural home when it graduates); (6) k6 for branch-scoped writes (needs #106 seeding); then the docs/maintenance lines (#110, root-AGENTS.md pre-push-iOS line, #139, #106).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations"), and — for any HITL design work — `docs/agents/decision-loop.md`.
2. The frontier is EMPTY — the next pick is a fog-graduation: grill the user to graduate one of the standing fog lines into a decision ticket (or take an AFK build like keep-last or the ilike unifier directly). Any new ticket: create as a child of #89, wire blocking edges in a second pass, claim before work.
3. Build per module AGENTS.md + the phased code-review loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **grep-verify every batch fix against the file before committing** (the fix-that-didn't-land class — and watch the mount-revert hazard: re-verify critical files after long gaps).
4. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #160 entry (build summary + deviations + loop outcome + P5 graduates + resolution link), inserted before Not-yet-specified.
- Frontier paragraph rewritten: **frontier EMPTY** — the #105/#106 relief-invite line fully landed; the remaining fog lines re-indexed incl. the two new ARCH graduates (shared-Exposed-query-op-helpers; screen-scoped-VM-seam).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" steps (frontier empty — fog-graduation session: grill the next standing fog line into a ticket).
- **`/implement` + `docs/agents/code-review-loop.md`** — for any build/audit ticket; the register now carries the commit-stamp class's shape, the substitution-vs-re-issue division, and the FIFO-Main contract (all new this session).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket; the standing frame (dev-stage, zero migration cost, best long-term, falsify every claim, facts in code before choices, simple language) applies to ALL human-facing questions.
