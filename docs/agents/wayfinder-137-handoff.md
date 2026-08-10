# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 36

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 36 resolved the AFK task **#137** ("Fix raw 500 on cross-draft duplicate line-session (graduated from #120 fog)") — `wayfinder:task`, driven by the agent alone (map remains 100% AFK). Ticket created this session (second fog graduation since the #106 set; the #136 pattern — ticket with falsification context + decision in the body, AFK), resolved, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/137#issuecomment-5241362535. Commit `f371ded` on `ralph/company-app-full-build` (3 files, +222/−10, not pushed; pre-commit gate passed: ktlint/detekt/757 tests/cleanliness/shared-compile). Map #89 updated (Decisions-so-far #137 entry, frontier paragraph rewritten, #124 fog line graduated into Not-yet-specified as a sharpened candidate).

**Next-session state:** **0 unblocked tickets** — unchanged (the new ticket was created AND closed in-session).

## Session outcome

**#137 (Fix raw 500 on cross-draft duplicate line-session) — created + resolved + closed (AFK).**

- **Created the ticket** (user pick: "AFK backend hardening" over HITL #117-decide/Finance-build and #94-grad build). Sharpest candidate fog: the #120 line-session line (handoff's first-listed backend hardening fog). Wired as sub-issue of map #89, claimed, then the usual AFK flow.
- **Shipped**: `RemittanceLineRepository.addLine` now 409s (`ConflictException`) on an active duplicate line-session or product-sale. The guard is an occupancy pre-check (`assertNotAlreadyIncluded`) **inside the addLine transaction** — the #119 tuple-occupancy precedent (`RemittanceRepository.updateHeader`) — filtered `deletedAt.isNull()` (soft-deleted lines do NOT block re-add; mirrors the partial indexes `idx_remittance_line_session`/`idx_remittance_line_product`, V1:509-510) and `id neq params.id` (idempotent retry excluded).
- **Falsification red-first**: the raw 500 was exactly as #120 described — `insertIgnore` swallowed the unique-violation, then `.single()` by the never-inserted line id threw `NoSuchElementException` → 500. 3 service + 1 route test all red pre-fix.
- **Race closed (round-1 Spec HARD finding)**: the naive `insertedCount == 0 → ConflictException` conflated two conflicts — a **PK-swallowed concurrent idempotent retry** (same line id racing the original insert; should return the existing line, req 4) vs a **unique-index swallow** (true duplicate; should 409). Fix: on count-0, re-read by line id — if present, `return@transaction` the existing line idempotently; else throw the duplicate 409. Verified sound by round-2 reviewer (no row-present-but-wrong case: PK conflict with same id = same logical op; ids are client-supplied idempotency keys).
- **Key decisions**: (1) repo-level pre-check inside the transaction (not service) — data-constraint mirror of the index, same layer as the #119 precedent, check+write atomic on one connection (the #136 guardFn lesson, applied without a callback since the repo owns the query); (2) 409-not-400 (resource exists, state conflicts — #119/#136 precedents); (3) per-type message "Session/Product sale already included in a remittance line" via `duplicateMessage`/`entityLabel` (message-literal dedup was a round-1 Standards SOFT finding); (4) idempotent same-line-id retry untouched (existing test green); (5) no migration/DTO/ADR (indexes pre-existed); (6) k6 deferred (#98/#115 precedent).
- **Tests** +5 (suite 752→757): service — cross-draft duplicate 409 + message assert, same-draft different-line-id 409, product-sale duplicate 409 + message assert, soft-delete-then-re-add succeeds; route — duplicate session in same draft 409 through HTTP (transaction rollback = zero audit rows + no version bump on failure). **No concurrency-interleave test** (flaky at this suite's shape — the #136 accepted tradeoff); deterministic disambiguation + unique indexes pin the invariant.
- **Review**: /code-review rounds 1+2. r1 — Standards: 0 HARD, 2 SOFT (message-literal dedup; when-entry bracing) + Spec: 1 HARD (count-0 conflation) + 1 minor (second indexed SELECT on hot path — accepted). r2 (fix delta vs `tmp/review-r1`) — Standards PASS (1 accepted judgement: repeated select-by-id read — codebase idiom) + Spec PASS. No third round. **Round-1→2 lesson: verify a thrown-count-0 fallback can't misfire on a different constraint class (PK vs unique index) — the green 752 didn't catch it.**
- **Process mishap (logged)**: the first `tmp/review-r1` build clobbered the route test — `git checkout` of the review-point branch dropped the uncommitted file (it was committed only on the temp branch). Recovered by backing up the 3 files to `/tmp/opencode/r2/`, reversing the fix hunks, committing the round-1 tree, then restoring. **Lesson: snapshot working files to /tmp BEFORE creating the review-point branch; or commit the round-1 tree first, then re-apply fixes.**

## Patterns + learnings (cumulative across sessions)

- Session-36 additions:
  - **Constraint-class disambiguation on `insertedCount == 0`** — after `insertIgnore`, a zero count means *some* constraint swallowed the insert; if the PK was swallowed it's a concurrent idempotent retry (return existing), if a partial unique index it's a real duplicate (409). Re-read by PK to tell them apart. Don't throw on count-0 without the re-read.
  - **Repo-level occupancy pre-check inside the transaction** — for a data-constraint mirror (unique-index semantics), the check belongs in the repository beside the write (the #119 `tupleOccupied` precedent), not in the service — keeps check+write atomic without a `guardFn` callback.
  - **Lost-uncommitted-file trap in the tmp-branch review-point trick** — `git checkout <branch>` removes uncommitted changes that exist only on the other branch's commit. Backup files to /tmp first (or reverse-apply fixes onto the committed round-1 tree).
- Prior-session patterns unchanged: AFK flow = /implement per the module AGENTS.md, red-first falsification, quality gate, /code-review rounds 1+2 (round 2 on the fix delta; tmp-branch review-point trick), k6 deferred, resolution comment → close → map Decisions-so-far + frontier paragraph + fog cleanup + handoff.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V18 taken; next is **V19** (`ls backend/src/main/resources/db/migration/` before picking).
- **Test-DB pollution on partial `--tests` runs**: running filtered test classes without a clean DB first can poison count-based assertions in later full runs (22 failures this session across `ReportsReadScopeAuthzTest`/`UserManagementAuthzTest`/`UserServicePostgresTest` — `bash scripts/clean-test-db.sh` + full re-run recovered, 757 green).

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues`:

**0 open children** — all graduated tickets closed; #137 (created+closed this session) closed the #120 fog line. NOTE: the sub_issues list query does NOT show #136 or #137 (stale — their `parent_issue_url` DOES point at #89; re-add errors with "duplicate sub-issue"). Verify children via `gh api repos/jsongalvez/company_app/issues/<n> --jq .parent_issue_url` when in doubt.

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next picks

- **#124 fog — `SessionConcernService.getForSession` read-path day-gate 400** (now a sharpened Not-yet-specified line on the map) — GET concerns on a REMITTED-day session 400s because the READ calls the write gate (`checkBranchDayEditable`, "reason required"). Small AFK hardening ticket, exact #136/#137 pattern. **The natural next pick.**
- **Merged Finance & Reports build** (from #101 + #105) — the big remaining build; data surface fully built (feed #130, exports #128/#129/#131, accessible-branches #131, read-backs #117). **The gate is the #117 expense-GET-includes-soft-deleted question** (dimmed-deleted rows need a payload change per #101 D6) — still open (left as a fog note in #117's resolution). HITL-ish UX decision; the map is 100% AFK so far — flag to the user.
- **#94-grad Build — login + capabilities fetch + BranchSelect + SessionState writer integration** — ungraduated; its land populates `SessionState.capabilities`/`currentUser`, activating #135's drawer item + route gate (and every other code-only-gated drawer item).
- **Non-admin self-slot-edit (BR:67)** + **user-create flow (must assign roles)** + **relief-invite flow** — placement settled (BranchSelect territory / #106 fog), all post-launch-fog.

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill (`.agents/skills/wayfinder/SKILL.md`), tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-37 has NO unblocked ticket — pick from "Recommended next picks": graduate the #124 getForSession hardening fog (AFK, #136/#137 pattern), decide #117 then graduate the merged Finance & Reports build (HITL — flag to the user), or graduate #94-grad. If graduating a fog: `gh issue create` → sub-issue link → claim, then the usual AFK flow.
3. Claim BEFORE work: `gh issue edit <N> --add-assignee @me` — verify no concurrent sessions.
4. AFK flow: /implement per module AGENTS.md (backend or composeApp) + /code-review rounds 1+2 (parallel Standards + Spec; round 2 on the fix delta; tmp-branch review-point trick — **backup working files to /tmp first**).
5. Post the answer as a **resolution comment**, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND clear graduated fog lines).
6. Graduate fog (create-then-wire): `gh issue create --label wayfinder:task` → sub-issue via `gh issue edit <n> --parent 89` (or the REST endpoint; verify via the child's `parent_issue_url` since the parent's sub_issues list can be stale).
7. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #137 entry (409 guard + count-0 disambiguation + red-first falsification + key decisions + tests 752→757 + review outcome + resolution link; "#120 fog line resolved").
- Not-yet-specified: new sharpened line — **#124 `getForSession` read-path day-gate 400** (candidate AFK hardening ticket in the #136/#137 pattern). All other fog unchanged.
- Frontier paragraph: rewritten with the #137 session outcome first (created+closed, 2 review rounds, constraint-class lesson), rest reworded "closed previous session", 0 unblocked held.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket or decide #117 first — 0 unblocked).
- **`/grilling` + `/domain-modeling`** — if the merged Finance & Reports build graduates next: the #117 expense-GET-soft-deleted decision is HITL.
- **`/implement` + `/code-review`** — for either an AFK hardening ticket (the #124 getForSession fog is the pick) or the merged build (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
