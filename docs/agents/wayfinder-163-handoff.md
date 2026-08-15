# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 60

## What this is

A wayfinder build session on map **#89**. Session 60 picked the standing fog line **the keep-last mutation write (the #162 P5 graduate)**, graduated it into **#163 (Build — KeepLast.mutate unifier)**, claimed it, and built it under the phased code-review loop. **#163 = the THIRTEENTH fog-graduation's build; the #162 P5 "keep-last mutation write" fog line is now CLOSED end-to-end — the WRITE side of the keep-last unifier is unified.** Resolution: https://github.com/jsongalvez/company_app/issues/163#issuecomment-5299982877. Map #89 updated (Decisions-so-far #163 entry + keep-last-mutation-write fog line REPLACED by the new mutateRemoved ARCH-graduate fog line + frontier paragraph rewritten).

**Next-session state:** the frontier is **EMPTY** — no open unblocked child of #89. The next pick comes from the standing fog lines: the **#162/#163 P5 ARCH graduates** (stale-substitution guard — identical at NotificationVM + ReliefInviteVM, handler-level stale-guarded launch; **KeepLast.mutateRemoved** — the new #163 P5 graduate, the filterNot+size-compare remove-shape at NotificationVM + ReliefInviteVM, natural follow-on to this ticket's own helper, AFK-capable; per-key in-flight guard — one consumer, hand-rolled at UserVM + FinanceReportsVM; handler state-less launch — kills the throwaway state targets), the DayStatus shared/domain move, the #92 Q4/Q5 403-refresh machinery, the #158-graduated ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant), the two #160 graduates (shared-Exposed-query-op-helpers — the third `ilike` copy; screen-scoped-VM-seam), k6 for branch-scoped writes (needs #106 seeding), the root-AGENTS.md stale pre-push-iOS line, #110 (hardcoded-month test dates), #139 (standalone OpenAPI map), the user-creation/role-assignment flow (#106), the request-flow grant/deny UI gap (ReliefAccessViewModel unwired).

## Session outcome

**#163 (Build — KeepLast.mutate unifier) — claimed, built, reviewed (5-pass + P5 + P5 loop-back), resolved, closed.** 5 commits `6b3aea5`→`1a1c8d2` on `ralph/company-app-full-build`, each pre-commit gate green (ktlint/detekt/903 backend tests/cleanliness/shared-compile/Postgres); desktop suite 350→**355** (+5: 4 KeepLast mutate tests + 1 swap no-op test). Frontend-only — backend untouched.

- **New helper** (`composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/KeepLast.kt`): `KeepLast.mutate(transform: (T) -> T?): Boolean` — the exact-sync-read (`freshestValue` discipline) + changed-guard (transform-null = no write) + Success write in one call; the Boolean reports whether a write happened, consumed by the badge-decrement/stamp-bump guards (NotificationVM moveToRead, ReliefInviteVM removeReceived).
- **Ports (behavior-preserving — all existing VM pins stayed green)**: UserVM `mutateUser` + `swapSlotsInPlace` (**single write** — the old two sequential writes' intermediate frame never reached a screen; absent-user → null → no-write) + `mutateAssignmentSlot`; NotificationVM `moveToReadThisSession` + `moveAllToReadThisSession`; ReliefInviteVM `removeReceived`. `withSlot` extracted (3 slot-copy sites). After the port, the only `stateFlow.value = UiState.Success` in the viewmodel dir is inside `mutate` itself (grep-verified).
- **The pass-2 HARD (truth-class, 2 strikes on one claim)**: my first reword of the swap comment claimed the old intermediate frame was "never observable" — the #162 lesson struck: pass-2's "never readable by any consumer" ALSO overshot (the old second write read the intermediate via `freshestValue`; the mirror collector processed it inline on Main.immediate). Final exact form: "never reached a screen" (both old writes ran synchronously; deferred composition conflated). **When reworking a truth-class claim, check it against the OLD code's readers, not just the new code.**
- **P5 (exit)**: 1 ARCH **graduated — `KeepLast.mutateRemoved(predicate)`** (the identical filterNot+size-compare remove-shape at NotificationVM + ReliefInviteVM = real seam; the #163 P5a confirmation; natural next fog pick — small, AFK); 1 fix-in-ticket (swap absent-user test); 1 rejected (write-count pin — vacuous under StateFlow conflation: two same-frame writes deliver one emission to any collector under the test dispatcher, so a count test passes either way — the useless-test class).
- **P5 loop-back: 1 HARD** — my own absent-user test claimed an unpinnable no-crash pin: transform exceptions are swallowed by ApiCallHandler onto the private throwaway `mutation` flow, so a regressed `first{}` NPE still passes every public-surface assert (empirically verified). Fixed: renamed `swap_with_missing_user_is_noop`, honest comment (pins no-write, not no-crash), + `swapBodies`/`actionErrors` wire asserts (a reroute regression now fails). **Snag recorded**: the throwaway-flow Error surface is unobservable in tests — crash-class transform regressions are invisible through VM public surfaces; dies with the handler state-less launch fog line.
- **Loop**: pass 1 (0 HARD — 2 truth-class KDoc fixes + `withSlot` dedupe + 2 accepted SOFTs); pass 2 (0 HARD — the swap-comment truth-class re-fix); pass 3 (0 HARD all four phases → exit); P5 (above); P5 loop-back (1 HARD → fixed); loop-back re-pass (0 HARD → exit).

## Patterns + learnings (cumulative)

- **Session-60 additions**:
  - **A truth-class reword must be checked against the OLD code's readers, not just the new code**: "never readable by any consumer" was false because the old second write itself read the intermediate (via freshestValue) and the mirror collector processed it inline — only "never reached a screen" (deferred composition conflation) is exact. The #162 "grep the claim across the file set" lesson extends to "grep the claim against the code it describes, old shape included".
  - **Unpinnable test claims are truth-class, and the fix is honest wording + wire asserts**: "no_crash" pins failed because ApiCallHandler swallows transform exceptions onto the private throwaway flow (empirically verified — the test passed with the guard regressed). The honest fix renamed the pin to what it proves (no-write) and added wire assertions (`swapBodies` size + `actionErrors` empty) that DO catch the reroute class. When a test can't observe its target regression through the public surface, say so in the comment — never let the name imply a pin that can't fire.
  - **StateFlow conflation makes write-count pins vacuous**: two same-frame writes deliver one emission to any collector under the test dispatcher — a "must write exactly once" emission-count test passes either way. Write-count claims stay structural (single assignment in `mutate`) or pinned at the wire, never at the flow.
- Prior-session patterns unchanged: claim first, facts-via-code, one-ticket-per-session, resolution → close → map update → handoff, batch-fix commits per pass, `git diff <last-pass-commit>` per pass. **No fix-that-didn't-land this session** (every batch grep-verified).

## Current frontier (verified live post-session)

Per `gh issue list --state open` + the map's sub-issues: **no open unblocked child of #89 — frontier EMPTY.** Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix).

## Recommended next pick

The standing fog lines in rough priority: (1) **the #162/#163 P5 ARCH graduates** — the **KeepLast.mutateRemoved remove-unifier** (2 identical sites — NotificationVM + ReliefInviteVM; small mechanical AFK build, the natural follow-on to this ticket's own helper), the **stale-substitution guard** (2 identical sites — NotificationVM + ReliefInviteVM; handler-level stale-guarded launch; ApiCallHandler API change + both VMs), the **per-key in-flight guard** (guard-only type split; adopt at UserVM + FinanceReportsVM), the **handler state-less launch** (kills sentListFlow + UserVM mutation flow + the #163 snag — the unobservable throwaway-Error surface dies with it); (2) the **DayStatus shared/domain move** (rides the Finance REMITTED-banner gap + the ride's read side); (3) the **#92 Q4/Q5 403-refresh machinery**; (4) the #158 ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant) + the #160 graduates (ilike unifier — small mechanical; screen-scoped-VM-seam); (5) the request-flow grant/deny UI gap (ReliefAccessViewModel unwired); (6) k6 for branch-scoped writes (needs #106 seeding); then the docs/maintenance lines (#110, root-AGENTS.md pre-push-iOS line, #139, #106).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations"), and — for any HITL design work — `docs/agents/decision-loop.md`.
2. The frontier is EMPTY — the next pick is a fog-graduation: grill the user to graduate one of the standing fog lines into a decision ticket (or take an AFK build like the mutateRemoved unifier directly). Any new ticket: create as a child of #89 (POST `/issues/89/sub_issues` with the child's numeric DB id via `--input -`; sub-issue listing is paginated at 30 — `--paginate`), claim before work.
3. Build per module AGENTS.md + the phased code-review loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **grep-verify every batch fix against the file before committing** (the fix-that-didn't-land class), **check truth-class rewording against the OLD code's readers** (the pass-2 class — the swap-comment claim was reworded twice before it became exact), and **never name a test after a pin it cannot fire through the public surface** (the loop-back HARD class — the throwaway-flow Error surface is unobservable in tests).
4. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #163 entry (build summary + loop outcome + P5 graduate + resolution link), inserted before Not-yet-specified.
- Not-yet-specified: the **keep-last mutation write fog line REMOVED (closed)**; **1 new fog line added** (KeepLast.mutateRemoved remove-unifier — the #163 P5 ARCH graduate).
- Frontier paragraph rewritten: frontier EMPTY — the write side of the keep-last unifier fully landed; the new graduate re-indexed.

## Constitution update (this session)

None — the review-loop constitution (two-sighting rule + P5 split) carried this session unchanged. The crash-fuzzer idea stays a map fog line (infra-first — needs a UI-test-driver or CI nav-walk harness before it can exist).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" steps (frontier empty — fog-graduation session: grill the next standing fog line into a ticket, or take an AFK build like mutateRemoved).
- **`/implement` + `docs/agents/code-review-loop.md`** — for any build/audit ticket; the register rows to watch this session's classes: **truth-class** (the two-strike swap comment — check claims against old code's readers) and the new **unpinnable-test-claim** shape (a test whose name promises more than the public surface can prove).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket; the standing frame (dev-stage, zero migration cost, best long-term, falsify every claim, facts in code before choices, simple language) applies to ALL human-facing questions.
