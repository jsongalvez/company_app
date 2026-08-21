# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 71

## What this is

This session ran from the VPS. The frontier was EMPTY; per the #173 handoff's pick #1, this session shipped **ticket #172 — Build — OnError transport-pin tests for the remaining 3 surfaces (AuditLog fetchFlagged + Finance feed fetchPage + loadSection)** — the #170 graduate's remaining transport-pin coverage, a **test-only** ticket. Deliverable: **5 commits `e6580c3`→`885948a`** on `ralph/company-app-full-build` (each pre-commit gate green), desktop suite 378→**386**, 0 failures (+8 tests).

**Ticket #172 — CLOSED.** The #169-graduated "transport-failure VM pin per ported surface" fog line is gone end-to-end. All three remaining surfaces now carry throw-IOException MockEngine pins proving the `launchStateless` onError wiring completes their error surfaces: AuditLog `fetchFlagged` (cold loud → Error + in-flight-guard clear; refresh silent → keep-list + refresh-error line — new `AuditHarness.flaggedFailure` switch), FinanceReports feed `fetchPage` (cold loud, refresh silent keep-list, loadMore keep-list, plus the **unique** stale-refresh guard pin) and `loadSection` (one section Error while the other three land Success — per-section wiring independence). **Zero production-code change** — the first pass over the fog line was itself slightly imprecise: the surfaces already shipped the #169-ported onError hook; a malformed-body test even rode the same hook via deserialization. The accurate framing (in the ticket body, grep-verified before claiming): *no IOException transport pin in the suite*.

## Session outcome

- **5 commits** on `ralph/company-app-full-build`: `e6580c3` (the 6 pins + harness switch), `ea8ec93` (pass-2 batch: rename + comment + 2 pins), `1e9c54f` (pass-3: dropped a guard-unrelated flag assert), `1a62862` (P5: rename the over-named cold-stale pin), `885948a` (cosmetic: drop a review-loop label from a shipped comment). Branch is **ahead 23, NOT pushed** (standing "commit local not pushed" pattern).
- Ticket lifecycle: #172 created with `Part of #89` + native sub-issue link (`-F sub_issue_id=<db-id>`, the `-F` integer form), claimed, worked, closed with resolution comment. Map #89 edited via `gh issue edit --body-file` (working body staged at `/tmp/opencode/map89.md`, ubuntu-owned): child-table row added, Decisions-so-far entry appended (after the #171 entry — careful, my first edit REPLACED #171's entry by mistake and was restored), the transport-pin fog line replaced with its successor marker, the #165 stale-gate line re-sighted, frontier paragraph rewritten. Frontier remains **EMPTY** (verified: 0 open sub-issues of #89).
- First commit was the full build batch (not incremental): pass-1's two-sighted flow-5 catch needed the unique guard pin, which landed in the pass-2 batch.

## Current frontier (verified post-session)

**Frontier EMPTY** — no open unblocked child of #89. Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix). Standing fog lines (changed from #173: the transport-pin line is CLOSED and replaced by its successor marker; the #165 stale-gate line RE-SIGHTED a third time):

- **Stateless-launch stale-gate (#165 class) — RE-SIGHTED by #170 AND #172's P5 — NOW THE TOP FOG CANDIDATE** — the `if (generation == XGeneration) { <surface>; finish }` guard is hand-rolled at 6+ stateless launchStateless sites (AuditLog fetchPage, FinanceReports fetchPage cold/refresh/loadMore + loadSection + loadMonthlyRollup + loadReliefDay) while the stateful `launch` owns this class in-handler (#165's stamp/fallback). #172's transport pins gave it its **first uniquely-observable instance**: `refreshFeed_staleTransportFailure_doesNotBleedOntoNewBranch` proves `handlePageFailure(Refresh)` writes `refreshError` unconditionally, so the generation gate is the sole suppressor of a stale refresh failure (guard-deletion verified red). Fix-shape unchanged: a `stale: () -> Boolean` gate on launchStateless wrapping the hooks. Marker: the next stateless site that writes the generation guard, or when launchStateless next changes.
- **Stateless onError message-convention pair (unchanged from #173)** — the onNonSuccess prefix ("monthly rollup failed: <status>") vs the onError bare `e.message` within one surface. #172 added no new divergence (all three new surfaces prefix both hooks). Trivial fix-shape if anyone picks it up; marker: the next onError added or the messages unified.
- **Transport-pin family fixture (NEW, graduated by #172's P5 — successor marker to the ridden transport-pin line)** — the onError transport-pin tests now span the whole failure family; each Finance/Feed test re-hand-rolls the branches/accessible + else-NotFound preamble while AuditLog models its failure switch in a harness. Marker: a transport-pin test in a THIRD VM file (outside the two existing test classes) warrants a shared gated/throw handler fixture.
- **AuditLog browse `loadMore` transport leg** (minor, noted by #172's P3) — HTTP-status-pinned but not throw-IOException; it rides the same fetchPage onError + finish path as the transport-pinned refresh, so the pin's marginal value is low. Deliberately out of #172's scope.
- Unchanged from #173: mirror-only KeepLastByKey split; stale-response guards partitioned (documentation only); update-one map-if sub-shape (marker: a SECOND KeepLast-family site); unifier-family-naming-coherence (marker: the family's next change); the DayStatus shared/domain move; the #92 Q4/Q5 403-refresh machinery; the #158 ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant) + the two #160 graduates (shared-Exposed-query-op-helpers; screen-scoped-VM-seam); k6 for branch-scoped writes (needs #106 seeding); the root-AGENTS.md stale pre-push-iOS line; the hardcoded-month test dates (#110); the user-creation/role-assignment flow (#106); the request-flow grant/deny UI gap (ReliefAccessViewModel unwired); the standalone OpenAPI map (#139). The #171 ARCH pair (Bootstrap 401-split; Bootstrap+Dashboard handled-silently seam) still rides the onError-family fog.

## Recommended next pick

The frontier is EMPTY; the standing fog lines are the only chartable work. AFK-able picks in order:

1. **Stateless-launch stale-gate (#165 class)** — now the top fog candidate: 6+ sites, a uniquely-observable test instance to prove the seam, and a concrete fix-shape (`stale: () -> Boolean` gate on launchStateless absorbing the generation guards). Medium handler-API change + a port across the sites. More design surface than a test ticket but fully AFK; the #172 stale-refresh pin is the multi-pass review it will live or die under.
2. **Stateless onError message-convention pair** — trivial fix-shape (prefix the onError messages to match onNonSuccess), but it's a convention decision — the right moment is the next change to the family, or a drive-by now.
3. **Transport-pin family fixture** — marker only (same two test files); wait for a third file.
4. The grilling/HITL items (fog-graduation picks, #92 Q4/Q5 machinery) need a human — take only if the human is present.

If the migration's runbook/persistence work isn't done (Coolify GitHub App for the private repo — the standing critical follow-up), that's the human-side item; the wizard run itself is COMPLETE.

## Migration notes (VPS handover — follow-ups still standing)

- **The switch holds** — sixth VPS session, toolchain stable. The first-commit watch-item (VPS pre-commit gate) stays green; all 5 commits this session passed it.
- Standing human/runbook follow-ups unchanged from #173: Coolify GitHub App (or token-in-URL) for the private-repo app resource; wizard bootstrap should pin Asia/Manila; gh-auth stage needs `gh auth setup-git`; Postgres stage guard-create `company_app_test`; the test suite needs a TZ fixture (on this VPS the TZ is already Asia/Manila); check for other Windows-mount-hidden exec bits; shellcheck still not installed; ntfy topic rotation; the loop-infra zombie-detector real exercise. `/tmp/opencode` ownership is stable (ubuntu-owned).

## Patterns + learnings

- **The fog line's precision was itself the first find of the session.** The standing line said "only HTTP-status VM pins" — an AuditLog malformed-body test already exercised the same onError hook (via deserialization). Grep-verifying the ticket's own premise before claiming (the #172-handoff lesson, applied again) caught it, and the ticket body carried the accurate claim. The register's truth-class occurrence bumps — self-reported, pre-claim-verified this time.
- **The two-sighted flow-5 catch was this session's real value.** The rollup's guard pin (#170) uniquely pins its guard; the feed's cold mirror looked equivalent but cold `handlePageFailure` keep-last double-suppresses, so the "superseded" test could NOT isolate the guard — P3 and P4 caught it independently, and the fix (a stale-REFRESH pin, whose error write has no keep-last) is strictly stronger coverage than the mirror was. Lesson: when copying a pin family to a new surface, re-check WHICH suppressor makes the observable hold — a second mechanism can silently turn a guard pin into a composition pin.
- **The self-review loop on the map edit worked:** my first map edit replaced (not appended after) the #171 Decisions-so-far entry; the restore-in-same-batch caught it. Map body edits should diff before pushing.
- **Prior-session patterns unchanged:** claim-first, facts-via-code, one-ticket-per-session, commit local not pushed, gate green before commit, the phased loop until 0 HARD, preempting the likely P4 lens (the guard-uniqueness complaint gets fixed before it's raised again).

## Constitution update (this session)

**No new register classes.** The loop ran clean; no HARD findings survived pass 2 onward. The truth-class occurrence was recorded honestly (1 correction: the fog line's "only HTTP-status" overclaim, corrected in-ticket-body and resolution). The P5 observations are architecture residue, not bug classes: one re-sights an existing fog line (#165 stale-gate), one graduates a new marker fog (transport-pin family fixture).

## Critical follow-ups (human)

- **Coolify GitHub App** (or token-in-URL) for the private repo — needed before the app-resource stage works; still open since #169.
- **shellcheck** still not installed — `shellcheck -S warning scripts/vps-migration-wizard.sh` on the VPS.
- **ntfy topic rotation** from session 61 stands (`wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`).
- **Loop-infra zombie-detector** still needs a real exercise (a deliberate 10-min-sleep sub-agent probe during a review phase).
- **The branch is ahead 23 and NOT pushed** — the next push (k6 baseline + multi-target compile, ~3 min) is a deliberate act; fine to leave until the map needs remote sync or the user wants it.
- Wizard fold-in follow-ups (#169) unchanged: Asia/Manila pin, `gh auth setup-git`, guard-create test DB, TZ fixture, exec-bit sweep (`git ls-files -s | grep 100644` on scripts).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" (frontier empty → AFK fog-graduation; the stale-gate is now the natural pick given its unique observable pin).
- **`/implement` + `docs/agents/code-review-loop.md`** — for the build ticket; register watch-lenses: **truth-class** (re-grep predecessor claims before adoption — applied again this session, keep it), **vacuous-test** (a pin that a second suppressor keeps green), **fix-that-didn't-land**, **keyed-mirror ordering**.
- **`/writing-for-agents`** — any agent-facing markdown edit (AGENTS.md, docs/agents/, this file).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket (standing frame applies).