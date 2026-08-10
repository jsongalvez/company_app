# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 37

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 37 resolved the AFK task **#138** ("Fix read-path day gate: GET session concerns 400s on REMITTED days") — `wayfinder:task`, driven by the agent alone (map remains 100% AFK). Graduated from the #124 fog line (third fog graduation since the #106 set; the #136/#137 pattern — ticket with falsification context + decision in the body, AFK), resolved, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/138#issuecomment-5241885106. Commits `5b522d4` + `291aa5a` on `ralph/company-app-full-build` (5 files, +149/−8, not pushed; pre-commit gate passed: ktlint/detekt/765 tests/cleanliness/shared-compile). Map #89 updated (Decisions-so-far #138 entry, #124 fog line graduated out of Not-yet-specified, frontier paragraph rewritten).

**Next-session state:** **0 unblocked tickets** — unchanged (the new ticket was created AND closed in-session).

## Session outcome

**#138 (Fix read-path day gate: GET session concerns 400s on REMITTED days) — created + resolved + closed (AFK).**

- **Created the ticket** (user pick: "Graduate #124 fog (AFK)" over the HITL #117-decide/merged-Finance-build and #94-grad; asked via question tool since 0 unblocked). Wired as sub-issue of map #89, claimed, then the usual AFK flow.
- **Shipped**: `BranchDayService.checkBranchDayReadable(callerId, branchDayId)` + pure `assertReadableState(effectiveStatus, hasEditPastDay)` — the read-path mirror of the editable pair. OPEN always readable; PAST/REMITTED require `EDIT_PAST_DAY` (403 kept per the backend/AGENTS.md read-gate rule) but never a reason (a read mutates nothing). Capability lookup deduped into private `hasEditPastDayCapability` shared by both gates (behavior-identical refactor of `checkBranchDayEditable`). `SessionConcernService.getForSession` now calls the read gate; 404-before-gate preserved. Both `GET /api/sessions/{sessionId}/concerns` AND the session-detail GET (embeds concerns via the same `SessionService.getSessionConcerns` pass-through) fixed by the one change.
- **Falsification red-first**: the fog premise held exactly — `getForSession` was the ONLY read-path caller of `checkBranchDayEditable` (all 22 other call sites are writes, grep-verified). New service test threw `ValidationException` (the reason-required 400) and the new route test 400'd against the unfixed code; the no-capability 403 pin stayed green (behavior intentionally unchanged).
- **Key decisions**: (1) keep-403-drop-only-400 — the AGENTS.md read-gate rule is explicit ("Read endpoints must also gate on capabilities"); the #117 un-gated expense read is a different pattern, not this ticket; (2) read-variant gate, NOT a synthetic-reason hack — reason is write-only semantics; assertion branches stay separate pure functions (`assertEditableState` vs `assertReadableState`), unit-testable without a DB; (3) `@Suppress("TooManyFunctions")` on the object (12 > threshold 11; 10 existing precedents — hit mid-gate, fixed before commit); (4) round-1 SOFT taken: `[CHECK-BRANCH-DAY-READABLE]` log line for observability parity with the write gate; (5) no migration/DTO/ADR/k6 (read-path hardening, #136/#137 pattern).
- **Tests** +8 (suite 757→765): 5 pure `assertReadableState` unit tests (all status×capability combos incl. REMITTED-with-grant-no-reason = the headline) + 2 service (REMITTED-day GET with EDIT_PAST_DAY returns concerns — red pre-fix; without EDIT_PAST_DAY 403 — pin) + 1 route (GET concerns through HTTP on a 3-day-old REMITTED day → 200 — red pre-fix; fixture age guarantees REMITTED not PAST under lazy evaluation).
- **Review**: /code-review rounds 1+2. r1 — Standards: 0 HARD + 3 SOFT judgement calls (duplicated gate skeleton — accepted mirror pattern, different return types; log-line asymmetry — taken as the fix; error-message shape "required to write/read on a X day" — trivial, skipped) + Spec: PASS, all ticket claims code-verified (only-read-caller, both-routes-fixed, 404-before-gate, 403-kept, symmetry, test coverage). r2 (fix delta vs `5b522d4`) — Standards PASS (log line matches convention; `isRemitted` omission correct — read has no such concept; `allowed` constant-true mirrors the sibling gate) + Spec PASS. No third round.
- **Process note**: the pure-function unit tests can't compile before the function exists — demonstrated red via compile error (unresolved reference), temporarily commented the block to let the behavioral tests run red against unfixed code, restored after the fix. Worked cleanly; no /tmp backup needed this time (fix delta committed directly, round 2 diffed `git diff 5b522d4` — no review-point branch).

## Patterns + learnings (cumulative across sessions)

- Session-37 additions:
  - **Read/write day-gate pair** — a read-path variant of a write gate keeps the capability check (AGENTS.md read-gate rule) and drops only the write-only semantics (reason). Shared capability lookup via private helper; assertion branches stay separate pure functions — a flag-parameter merge would be worse.
  - **Compile-red for not-yet-existing pure functions** — comment the new unit-test block (with a "re-enable after X lands" note) so behavioral tests can run red against unfixed code; restore after the fix. Clean alternative to stubbing.
  - **`TooManyFunctions` on shared services** — BranchDayService hit 12 > 11 at detekt; `@Suppress` on the object is the established pattern (10 precedents). Expect it when adding a function to an 11-function object.
  - **Round-2 without a review-point branch** — when round-1 fixes are few and applied after the commit, round 2 can diff `git diff <commit>` (uncommitted delta) directly; no tmp-branch trick needed.
- Prior-session patterns unchanged: AFK flow = red-first falsification, quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred, resolution comment → close → map Decisions-so-far + frontier paragraph + fog cleanup + handoff.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V18 taken; next is **V19** (`ls backend/src/main/resources/db/migration/` before picking).
- **Test-DB pollution on partial `--tests` runs**: running filtered test classes without a clean DB first can poison count-based assertions in later full runs — `bash scripts/clean-test-db.sh` before AND after partial runs.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues`:

**0 open children** — all graduated tickets closed; #138 (created+closed this session) closed the #124 fog line. NOTE: the sub_issues list query does NOT show #136/#137/#138 (stale — their `parent_issue_url` DOES point at #89; re-add errors with "duplicate sub-issue"). Verify children via `gh api repos/jsongalvez/company_app/issues/<n> --jq .parent_issue_url` when in doubt.

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Post-session addendum

**New standalone map #139 created** (same session, user request): "Map: OpenAPI documentation for the backend API" (`wayfinder:map`, no tickets yet — route looks clear, single-session scale). #89 gained an **Out of scope** section pointing at it — OpenAPI docs are a backend-documentation effort, not a step toward the frontend rebuild; never graduates here.

## Recommended next picks

- **Merged Finance & Reports build** (from #101 + #105) — the big remaining build; data surface fully built (feed #130, exports #128/#129/#131, accessible-branches #131, read-backs #117). **The gate is the #117 expense-GET-includes-soft-deleted question** (dimmed-deleted rows need a payload change per #101 D6) — still open (left as a fog note in #117's resolution). HITL-ish UX decision; the map is 100% AFK so far — flag to the user.
- **#94-grad Build — login + capabilities fetch + BranchSelect + SessionState writer integration** — ungraduated; its land populates `SessionState.capabilities`/`currentUser`, activating #135's drawer item + route gate (and every other code-only-gated drawer item).
- **Non-admin self-slot-edit (BR:67)** + **user-create flow (must assign roles)** + **relief-invite flow** — placement settled (BranchSelect territory / #106 fog), all post-launch-fog.
- **Remaining Not-yet-specified candidates** (no sharp backend-hardening fog left in the #136/#137 pattern — the map's backend-fog lines are down to the merged-build gate; next AFK-able backend candidates would have to come from new findings, e.g. the #117 un-gated expense read being read without EDIT_PAST_DAY — noted but deliberately NOT graduated (it's #117's own fog territory; un-gated reads per #117's decision) — re-examine only if the merged build re-opens read-gating).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill (`.agents/skills/wayfinder/SKILL.md`), tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-38 has NO unblocked ticket — pick from "Recommended next picks": decide #117 then graduate the merged Finance & Reports build (HITL — flag to the user; would be the map's first HITL session), graduate #94-grad (AFK frontend build), or stop. If graduating a fog: `gh issue create` → sub-issue link → claim, then the usual AFK flow.
3. Claim BEFORE work: `gh issue edit <N> --add-assignee @me` — verify no concurrent sessions.
4. AFK flow: red-first falsification, /implement per module AGENTS.md (backend or composeApp) + /code-review rounds 1+2 (parallel Standards + Spec; round 2 on the fix delta — `git diff <commit>` suffices when fixes are uncommitted on top).
5. Post the answer as a **resolution comment**, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND clear graduated fog lines).
6. Graduate fog (create-then-wire): `gh issue create --label wayfinder:task` → sub-issue via `gh issue edit <n> --parent 89` (or the REST endpoint; verify via the child's `parent_issue_url` since the parent's sub_issues list can be stale).
7. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #138 entry (read-gate pair + keep-403-drop-only-400 + red-first falsification + key decisions + tests 757→765 + review outcome + resolution link; "#124 fog line resolved").
- Not-yet-specified: **#124 `getForSession` line removed** (graduated). All other fog unchanged.
- Frontier paragraph: rewritten with the #138 session outcome first (created+closed, 2 review rounds, read/write gate-pair lesson), rest reworded "closed previous session", 0 unblocked held.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket or decide #117 first — 0 unblocked).
- **`/grilling` + `/domain-modeling`** — if the merged Finance & Reports build graduates next: the #117 expense-GET-soft-deleted decision is HITL.
- **`/implement` + `/code-review`** — for either the #94-grad frontend build or a future backend hardening ticket (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
