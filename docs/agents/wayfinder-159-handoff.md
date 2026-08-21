# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 56

## What this is

A wayfinder decision session on map **#89**. The frontier was EMPTY; per the #158 handoff's top pick, session 56 created the **relief invite flow grilling ticket (#159)** from the #105/#106 fog line, claimed it, and resolved it live with the user under a NEW discipline — the **decision loop** (falsification on every claim, facts verified in code before choices, simple-language presentation; institutionalized as `docs/agents/decision-loop.md` + root AGENTS.md pointer). **#159 = the ELEVENTH fog-graduation; the relief invite flow is decided and its build (#160) is the new frontier.** Resolution: https://github.com/jsongalvez/company_app/issues/159#issuecomment-5295232045. Map #89 updated (Decisions-so-far #159 entry + relief-invite fog line graduated + frontier paragraph rewritten).

**Next-session state:** **#160 (Build — Relief invite flow) is open, unblocked, unclaimed — the next pick.** Beyond it: the standing fog lines (keep-last port, DayStatus move, #92 Q4/Q5 machinery, the #158 ARCH pair, k6 for branch-scoped writes, root-AGENTS.md stale pre-push-iOS line, #110, #139, the #106 user-creation/role-assignment flow, the request-flow grant/deny UI gap).

## Session outcome

**#159 (Grilling — Relief invite flow) — created, claimed, grilled live, resolved, closed (HITL).** No code shipped this session (decision ticket + institutionalization only).

- **Locked design (all confirmed live by the user)**:
  - **Q1 = separate `relief_invite` table** (id, branch_day_id, invited_by, invitee, status PENDING/ACCEPTED/DECLINED/RETRACTED, responded_at) — reuse-with-kind falsified (role semantics flip: the invitee is both grant holder AND approver; `idx_one_grant_per_day` stays correct only keyed on the grant holder). The capability write is extracted into a **shared relief-grant helper** used by both flows.
  - **Q2 = one day per invite** (enforcement #157, the grant row, the read entry #158, the domain are all day-scoped; a range = N writes with partial-failure semantics).
  - **Q3 = inviter = active `user_branch_assignment` at the branch** — anyone assigned can invite, symmetric with anyone-can-request. **Fact-falsification killed both earlier candidates live**: (a) clocked-in-at-the-day is impossible by construction (clock-ins exist only for today; the use case is future-day planning); (b) BRANCH `EDIT_BRANCH_DATA` is a **VACUOUS gate** — verified in V2/V16 (role seeds documentation-only, V16 derives GLOBAL-only codes — "a role cannot express which branch" — no production user has a role row, BRANCH-scoped grants flow only from delegate/manual writes): zero eligible inviters = dead feature. **This was a false-premise defect on my part, caught by the decision-loop L1 discipline mid-grilling — the exact failure class the user had complained about; owning it built the discipline.** Invitee = any active user minus self minus already-PENDING/ACCEPTED/GRANTED for the day; no home-branch restriction (capabilities ≠ assignments; redundant grants harmless).
  - **Q4 = branch-scoped candidate search** (`GET /api/branches/{branchId}/relief-candidates?q=&date=`, same gate; `GET /api/users` is MANAGE_USERS-gated — falsified).
  - **Q5 = Option A: the Notifications screen gains a "Relief invites" section** — badge = pending invites + unread reminders; invites render until resolved, not until read; **the notification table stays untouched** (session-bound). Falsified: storage extension (unread-only list drops read-but-unanswered invites — read semantics fight action semantics; mixed row kinds) and a dedicated screen (list infra duplication, no badge channel, the invited branch absent from the invitee's BranchSelect).
  - **Q6 = accept/decline/retract** (retract while PENDING); **accept writes the grant immediately** (grantedBy = inviter, grantedAt = accept time, validTo = next-day-04:00); **day-state is the expiry** (past/REMITTED accept 400s, stale renders "expired", no cron); **multiple invitees can accept the same day** (user-confirmed; the one-grant rule is per person — matches the request flow's per-requester backstop); re-invite after decline/retract.
- **Institutionalization (the user's ask — "phased loops like code review, sub-agents per lens")**: new `docs/agents/decision-loop.md` (standing frame: dev-stage / zero migration cost / best long-term / falsify every claim / facts are the agent's job / simple language, technically accurate; lenses L1 fact integrity, L2 domain coherence, L3 long-term architecture, L4 adversarial falsification, L5 comprehension; parallel sub-agents per pass, HARD/SOFT triage, exit on one full zero-HARD pass) + root AGENTS.md pointer + document-map row (pointer tightened per /writing-for-agents — no duplication, leading words: the false-premise defect).
- **Domain-modeling**: CONTEXT.md gained **Relief Invite** (branch-initiated offer of single-day relief access; distinct from Relief Request); Relief Duty's grant line amended (two grant paths now).

## Patterns + learnings (cumulative)

- **Session-56 additions**:
  - **The decision loop** — the HITL mirror of the code-review loop. The first live win: my own BRANCH-EDIT_BRANCH_DATA inviter gate was a vacuous gate; the V2/V16 fact check killed it mid-grilling. The discipline: never present a choice built on an unverified claim (the false-premise defect); the human's confusion is the comprehension lens's failure signal.
  - **The user's presentation frame**: simple language, short sentences, gist at a glance — NOT strict STE (STE was only a frame of reference), and technical accuracy is never sacrificed. The user explicitly values being "convinced" decision-by-decision ("run me through each decision and convince me why it's the best") and falsification on every claim ("run falsification").
  - **Migration hygiene is NOT a consideration**: dev-stage, zero users — always the best long-term option, never the cheapest.
  - **The clocked-in gate class**: any gate keyed on a clock-in cannot authorize future-day planning (clock-ins exist only for today). Check satisfiability before proposing a gate.
  - **The vacuous-gate class**: a capability gate with zero production holders (V2 seeds doc-only, V16 GLOBAL-only derivation, no role rows) is a dead feature. Fact-check who can actually pass a gate before offering it.
- Prior-session patterns unchanged: claim first, facts-via-code, one-ticket-per-session, resolution → close → map update → handoff.

## Current frontier (verified live post-session)

Per `gh issue list --state open`: **#89 (map) → one open unblocked child: #160 (Build — Relief invite flow, unclaimed)**. Also open: #139 (standalone OpenAPI map), #110 (standalone hardcoded-month test fix).

## Recommended next pick

**#160 — the build** (the #159 graduation). It is the only unblocked child; its spec is the #159 resolution (V18 `relief_invite` + enum + partial unique; shared relief-grant helper extraction from `ReliefAccessRepository.grantWithCapability`; 7 endpoints — create/list-sent/retract branch-assignment-gated, received/accept/decline bearer-gated, candidate search; frontend NotificationsScreen invites section + badge sum + BranchSelect inviter side; day **find-only** resolution, the #158 shape; k6 feasible only if DevSeeder seeds assigned inviter + invitee users, else the logged #149/#152 deferral). Beyond it, the standing fog lines in rough priority: keep-last-results port for the remaining full-screen lists; the DayStatus shared/domain move; the #92 Q4/Q5 403-refresh machinery; the #158-graduated ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant); the request-flow grant/deny UI gap (ReliefAccessViewModel unwired — the invite flow's notifications-section pattern is the natural home when it graduates); k6 for branch-scoped writes; the root-AGENTS.md stale pre-push-iOS line; #110; #139.

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations"), and — for any HITL design work — `docs/agents/decision-loop.md`.
2. The frontier is #160 (unclaimed) — claim it via `gh issue edit 160 --add-assignee @me` **before any work**.
3. Build per module AGENTS.md + the phased code-review loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **grep-verify every batch fix against the file before committing** (the fix-that-didn't-land class).
4. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #159 entry (locked design + falsification record + decision-loop landing + resolution link), inserted before Not-yet-specified.
- Not-yet-specified: the relief-invite fog line graduated (replaced by a graduation pointer); frontier paragraph rewritten — **#160 (Build) open unclaimed**, remaining fog lines listed.
- Also added to the fog index this session (from the #159 fact map): the **request-flow grant/deny UI gap** (`ReliefAccessViewModel` unwired dead code — no request/grant/deny UI exists anywhere) and the **user-creation/role-assignment flow** (#106, already a fog line — re-indexed).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" steps (frontier = #160, unclaimed — claim first).
- **`/implement` + `docs/agents/code-review-loop.md`** — #160 is a combined backend+frontend build; the register carries the split-day stale-state shape, the fix-that-didn't-land hazard, and the count-0 family.
- **`docs/agents/decision-loop.md`** — for any future HITL design ticket; the standing frame (dev-stage, zero migration cost, best long-term, falsify every claim, facts in code before choices, simple language) applies to ALL human-facing questions, not just grilling tickets.
