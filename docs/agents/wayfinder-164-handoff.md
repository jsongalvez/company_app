# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 61

## What this is

A wayfinder build session on map **#89**. Session 61 picked the standing fog line **KeepLast.mutateRemoved remove-unifier (the #163 P5 graduate)** — the handoff's top pick, an AFK build — graduated it into **#164 (Build — KeepLast.mutateRemoved remove-unifier)**, claimed it, and built it under the phased code-review loop. **#164 = the FOURTEENTH fog-graduation's build; the #163 P5 "KeepLast.mutateRemoved" fog line is now CLOSED end-to-end — the REMOVE sub-shape of the keep-last mutate unifier is unified.** Resolution: https://github.com/jsongalvez/company_app/issues/164#issuecomment-5300370375. Map #89 updated (Decisions-so-far #164 entry + mutateRemoved fog line REPLACED by the new update-one (map-if) sub-shape ARCH-graduate fog line + frontier paragraph rewritten).

**Next-session state:** the frontier is **EMPTY** — no open unblocked child of #89 (verified live: open issues = #139, #110, #89 only). The next pick comes from the standing fog lines: the **#162/#163/#164 P5 ARCH graduates** (stale-substitution guard — identical at NotificationVM + ReliefInviteVM, handler-level stale-guarded launch; **per-key in-flight guard** — one consumer, hand-rolled at UserVM + FinanceReportsVM; handler state-less launch — kills the throwaway state targets AND the #163 snag; **update-one (map-if) sub-shape** — the new #164 P5 graduate, UserVM.mutateUser's map-if update, one adapter — a second adapter graduates a `mutateUpdated` sibling, AFK-capable), the DayStatus shared/domain move, the #92 Q4/Q5 403-refresh machinery, the #158-graduated ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant), the two #160 graduates (shared-Exposed-query-op-helpers — the third `ilike` copy; screen-scoped-VM-seam), k6 for branch-scoped writes (needs #106 seeding), the root-AGENTS.md stale pre-push-iOS line, #110 (hardcoded-month test dates), #139 (standalone OpenAPI map), the user-creation/role-assignment flow (#106), the request-flow grant/deny UI gap (ReliefAccessViewModel unwired).

## Session outcome

**#164 (Build — KeepLast.mutateRemoved remove-unifier) — claimed, built, reviewed (3 passes + P5 + P5 loop-back), resolved, closed.** 4 ticket commits `9398256`→`e0f9a30` on `ralph/company-app-full-build`, each pre-commit gate green (ktlint/detekt/903 backend tests/cleanliness/shared-compile/Postgres); desktop suite 355→**358** (+3: mutateRemoved helper tests). Frontend-only — backend untouched. Plus 2 separate wayfinder-loop infra fix commits (below).

- **New helper** (`composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/KeepLast.kt`): `fun <T> KeepLast<List<T>>.mutateRemoved(predicate: (T) -> Boolean): Boolean` — top-level extension (the element predicate needs a list payload), delegating to `mutate` (exact-sync-read + changed-guard + Success write + Boolean contract inherited); `filterNot` semantics: EVERY matching element leaves.
- **Ports (behavior-preserving — all existing VM pins stayed green)**: NotificationVM `moveToReadThisSession`, ReliefInviteVM `removeReceived` — both collapse to `keptX.mutateRemoved { it.id == id }`; the Boolean unchanged → badge-decrement / `_readThisSession`-append / stamp-bump guards fire only on a real removal.
- **The loop's review-window lesson**: the diff `fc69094..HEAD` swept **7 wayfinder-loop runner-infra commits** (the chain runner's daemon auto-committed them mid-session) — the phases flagged "scope creep"/"contamination" but the ticket delta is 9398256 only. Triage re-scoped; pass 2+ diffs were ticket-window. **When reviewing on this branch, check whether the window contains runner daemon commits and re-scope to the ticket commits.**
- **Security HARD (P2 pass 1)**: the README had committed the LIVE ntfy push topic `wf-ky1wf3r3vo` while `.wayfinder-loop.env` is gitignored specifically to keep it secret (truth-class + bearer-capability: ntfy topics are read/publish-by-URL; anyone with repo access recovers it via `git log -S`). Redacted to placeholder; **topic ROTATED** (human re-subscribed — see Critical follow-ups).
- **Truth-class HARD (P4 pass 2)**: my identity-pin comment claimed "a redundant Success(same-list) write would still be a write (new instance)" — FALSE: `MutableStateFlow` conflates equal-value updates (the update is skipped, the OLD instance kept), so `===` cannot distinguish no-write from a same-content write. Reworded to what the pin actually proved (content-changing writes only). **When writing flow-level identity pins, remember StateFlow conflation — equal writes never replace the instance.**
- **P5 (exit)**: 0 HARD. P5a: 1 fix-in-ticket — the `=== before` identity pin is **VACUOUS** (any content-changing write fails the content asserts anyway; equal-content writes are conflation-hidden — the #162 write-count-pin class recurring) → dropped pin + comment; 1 ARCH **graduated — update-one (map-if) sub-shape** (UserVM.mutateUser's `map { if (it.id == userId) transform else it }` is the update-one sibling of mutateRemoved, inline at one site — one adapter, correctly not ported; a second adapter graduates a `mutateUpdated(id, transform)` sibling). P5b: 0 findings (cosmetic comment reflow fixed).
- **P5 loop-back: 0 HARD** (1 SOFT — dropped pin also dropped the implicit state-stays-Success assert → one-line reviewer-suggested assert added).
- **Loop**: pass 1 (0 HARD on ticket delta — re-scope + P2 HARD ntfy redaction + 2 accepted SOFTs); pass 2 (P4 truth-class reword + ntfy rotation + wayfinder-loop.sh silent-death ESCALATE fix + 1 SOFT); pass 3 (0 HARD all four phases → exit); P5 (above); P5 loop-back (0 HARD → exit).

**Side deliverables (not #164's delta, 2 commits)**: wayfinder-loop.sh — (a) silent-death guard: a failing `api get /api/model` under `set -euo pipefail` aborted the script BEFORE the `die` guard (no FATAL log, no push — the loop's own Resilience section claims to handle dead sessions); `|| true` on the capture keeps `die` reachable; (b) the die message named the failure honestly (the old wording blamed "model not found" for API-down — a headless loop user reads only the push). Two-sighted SOFT + in-window ESCALATE, both fixed separately so the ticket delta stays clean.

## Patterns + learnings (cumulative)

- **Session-61 additions**:
  - **StateFlow conflation makes flow-level identity pins vacuous**: an `===` assert on `state.value` cannot distinguish "no write" from an equal-content write (MutableStateFlow skips the update, keeps the old instance). Pins must target content (content asserts) or structure (single-assignment in `mutate`), never flow identity. This is the #162 write-count-pin class, second occurrence — the register should absorb it (see Register).
  - **Review windows on this branch sweep runner daemon commits**: the chain runner auto-commits dirty worktree files at its own commit windows (this session it scooped my README + test fixes into its `WAYFINDER_MODEL` commit — content landed intact, header unrelated). Phases flag "scope creep" on the wrong artifact; triage must re-scope to the ticket commits. Verify `git log fc69094..HEAD` and separate runner commits before adjudicating scope findings.
  - **A committed secret's rotation must be real, not cosmetic**: redacting a tracked copy while the live value sits in a gitignored env file leaves the exposure intact (recoverable from history; still live). The README's own "treat as exposed and rotate" rule demanded rotation, and the two-sighted review caught that the redaction alone was cosmetic. Rotation needs the human (phone subscription) — flag it loudly in the handoff, don't let it ride silently.
  - **The die-guard-reachability class**: under `set -euo pipefail`, a failing command substitution in an assignment aborts the script before a subsequent `[ -n ... ] || die` guard — silent death with no log and no push. `|| true` on the capture (or capturing the status separately) keeps the guard reachable. Matches nothing in the register — candidate for a register row (see Register).
- Prior-session patterns unchanged: claim first, facts-via-code, one-ticket-per-session, resolution → close → map update → handoff, batch-fix commits per pass, `git diff <last-pass-commit>` per pass, **grep-verify every batch fix before committing** (no fix-that-didn't-land this session), **truth-class rewords checked against the OLD code's readers** (this session's variant: checked against StateFlow semantics).

## Current frontier (verified live post-session)

Per `gh issue list --state open` + the map's sub-issues: **no open unblocked child of #89 — frontier EMPTY.** Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix).

## Recommended next pick

The standing fog lines in rough priority: (1) **the #162/#163/#164 P5 ARCH graduates** — the **update-one (map-if) sub-shape** (UserVM.mutateUser is the single adapter; small mechanical AFK build — watch for a second site writing "update the row matching id"; natural follow-on to this ticket's own family), the **stale-substitution guard** (2 identical sites — NotificationVM + ReliefInviteVM; handler-level stale-guarded launch; ApiCallHandler API change + both VMs), the **per-key in-flight guard** (guard-only type split; adopt at UserVM + FinanceReportsVM), the **handler state-less launch** (kills sentListFlow + UserVM mutation flow + the #163 snag — the unobservable throwaway-Error surface dies with it); (2) the **DayStatus shared/domain move** (rides the Finance REMITTED-banner gap + the ride's read side); (3) the **#92 Q4/Q5 403-refresh machinery**; (4) the #158 ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant) + the #160 graduates (ilike unifier — small mechanical; screen-scoped-VM-seam); (5) the request-flow grant/deny UI gap (ReliefAccessViewModel unwired); (6) k6 for branch-scoped writes (needs #106 seeding); then the docs/maintenance lines (#110, root-AGENTS.md pre-push-iOS line, #139, #106).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations"), and — for any HITL design work — `docs/agents/decision-loop.md`.
2. The frontier is EMPTY — the next pick is a fog-graduation: grill the user to graduate one of the standing fog lines into a decision ticket (or take an AFK build like the update-one sub-shape directly). Any new ticket: create as a child of #89 (POST `/issues/89/sub_issues` with the child's numeric DB id via `--input -`; sub-issue listing is paginated at 30 — `--paginate`), claim before work.
3. Build per module AGENTS.md + the phased code-review loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **check the review window for runner daemon commits and re-scope to the ticket commits before adjudicating scope findings** (this session's lesson), **grep-verify every batch fix against the file before committing**, and **remember StateFlow conflation when writing flow-level pins** (identity pins are vacuous; pins must target content or structure).
4. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #164 entry (build summary + loop outcome + P5 graduate + resolution link), inserted before Not-yet-specified.
- Not-yet-specified: the **KeepLast.mutateRemoved remove-unifier fog line REMOVED (closed)**; **1 new fog line added** (update-one (map-if) sub-shape — the #164 P5 ARCH graduate).
- Frontier paragraph rewritten: frontier EMPTY — the remove sub-shape fully landed; the new graduate re-indexed.

## Constitution update (this session)

Two register candidates surfaced (neither yet appended — the driving session owns register upkeep):
- **flow-identity-pin-vacuous** (the #162 write-count-pin class, 2nd occurrence): StateFlow equal-value conflation keeps the old instance, so `===`/emission-count pins at the flow cannot distinguish no-write from equal-write. Pins target content or structure.
- **die-guard-reachability**: under `set -euo pipefail`, a failing command substitution in an assignment aborts before a following `[ -n ... ] || die` guard — silent chain death (no FATAL log, no push). `|| true` on the capture keeps the guard reachable.

Nothing else changed. The crash-fuzzer idea stays a map fog line.

## Critical follow-ups (human)

- **ntfy topic ROTATED**: old topic `wf-ky1wf3r3vo` was committed to git history (README at commits 3820796..9398256) — recoverable by anyone with repo access (read/publish by URL). The live topic is now `wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`. **Re-subscribe your phone to the new topic** (README has the instructions). The old topic's pushes are dead. History-rewrite of the exposed commits is optional (private repo, branch not pushed) — if this repo ever goes public or gains collaborators, rewrite the history to purge `ky1wf3r3vo`.

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" steps (frontier empty — fog-graduation session: grill the next standing fog line into a ticket, or take an AFK build like update-one).
- **`/implement` + `docs/agents/code-review-loop.md`** — for any build/audit ticket; the register rows to watch this session's classes: **truth-class** (the StateFlow-conflation comment — check claims against flow semantics, not intuition) and the **vacuous-pin** shape (identity/emission pins that StateFlow conflation makes unobservable).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket; the standing frame (dev-stage, zero migration cost, best long-term, falsify every claim, facts in code before choices, simple language) applies to ALL human-facing questions.
