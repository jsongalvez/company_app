# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 72

## What this is

This session ran from the VPS. The frontier was EMPTY; per the #174 handoff's pick #1, this session shipped **ticket #173 — Build — Stateless-launch stale-gate (the #165 class)** — the top fog candidate (6+ generation-guarded stateless sites, a uniquely-observable test instance, a concrete fix-shape). Deliverable: **6 commits `833b3cd`→`0d166df`** on `ralph/company-app-full-build` (each pre-commit gate green), desktop suite 386→**389**, +3 tests, 0 failures. **Ticket #173 — CLOSED.** The stateless-launch stale-gate fog line is gone end-to-end.

## Session outcome

- **6 commits** on `ralph/company-app-full-build`: `833b3cd` (the gate + the 12-site port + 3 tests + AGENTS.md line), `a28195d` (pass-1 batch: 4 SOFTs), `199efe2` (pass-2 truth-class fix on my own comment reword), `3397e12` (pass-2 exit: co-pin + header phrasing), `ca0edfb` (exit: header qualification), `0d166df` (P5: exportMode trailing-Unit drop). Branch is **ahead 30, NOT pushed** (standing "commit local not pushed" pattern).
- **The fix-shape**: `ApiCallHandler.launchStateless` gained `stale: () -> Boolean = { false }`; a landing reading stale()==true skips transform/onNonSuccess/onError wholesale. Grep-verified count: **12 sites / 36 guards** (NOT the fog's "6+" — and "flagged-shared" is an in-flight-slot, not a generation gate) — AuditLog fetchPage + FinanceReports feed/rollup/relief-day/loadSection + the 7 edit-action sites, all ported to `stale = { generation != <field> }`. Default `{ false }` keeps the 6 non-gated callers byte-identical; stateful `launch` untouched (the #165 partition). Semantics consequence (documented): stale bodies are never deserialized — observable behavior identical, the guard+swallowed-onError already made stale+malformed landings inert.
- **#172's uniquely-observable pin survived the port** — both negative controls verified red: dropping the feed's `stale` wiring turns `refreshFeed_staleTransportFailure_doesNotBleedOntoNewBranch` red; stripping the handler's onError gate turns it red AND turns the new `stateless_stale_exception_skips_onError` red. Zero residual in-hook `generation ==` guards (grep).
- **Loop: 5 passes + P5 + P5 loop-back, exit on 0 HARD.** Pass 1: 0 HARD all four, 4 SOFTs fixed in-ticket. Pass 2: **1 HARD truth-class — caught in MY OWN reworded test comment** (claimed a coroutine-start evaluation would read false and fail; false — it runs inside runCurrent after the flip). Pass 3: 0 HARD, 2 one-sighting SOFTs fixed. Pass 3-exit: 0 HARD, 1 P2 SOFT fixed (header overbroad for the structural-`{true}` non-success test). Final confirmation: 0 HARD. **P5: 2 ARCH graduates + 1 fix-in-ticket (P5 loop-back 0 HARD).** Accepted SOFTs: NONE carried — every finding fixed in-ticket.
- Ticket lifecycle: #173 created with `Part of #89` + native sub-issue link (`-F sub_issue_id=<db-id>`), claimed, worked, closed with resolution comment. Map #89 edited verbatim-verified (diffed the staged body vs the live body before pushing — 4 intended edits only: child-table row, #173 Decisions-so-far entry APPENDED after #172, stale-gate fog line replaced by its successor graduates, frontier paragraph rewritten).

## Current frontier (verified post-session)

**Frontier EMPTY** — 0 open sub-issues of #89. Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix). Standing fog lines (changed from #174: the stale-gate line is CLOSED and replaced by two successor graduates; the #165 partitioned-guards line SHARPENED; the #158 "generation-aware ApiCallHandler variant" half marked SATISFIED):

- **Stateful stale-failure legs (NEW — #173's P5 graduate, the top fog candidate alongside the mirror-only split)** — the #165 stamp guards ONLY the success commit; a superseded stateful landing that FAILS still writes `UiState.Error` onto the moved-on surface (`ApiCallHandler.launch` :74/:84). Deliberately pinned by #165's own "non-success → Error even with a structurally-stale stamp" test (the documented contract, not a regression), but the "superseded landing is inert" guarantee the stateless gate now provides at 12+ sites does NOT hold for the stateful failure legs — a stale load's Error can clobber the newer surface's Success. Fix-shape (a decision when it graduates): gate the onNonSuccess default-Error + onError state writes on the stamp too, or justify the loud stale failure. Marker: the stateful family's next change, or a report of a stale-Error clobber.
- **Relief loadSent stale holdout (NEW — #173's P5 graduate)** — `ReliefInviteViewModel.loadSent` (224-239) hand-rolls `if (stamp == sentStamp)` — the 13th stateless guard (the sole remaining one), behavior-identical to `stale = { stamp != sentStamp }` (newest-launch-wins preserved; keyed-mirror commit target — the SKIP gate is safe, the #165 substitution shape is not). Not ported in #173: sentStamp is a Long not a generation. Marker: loadSent's next change, or the mirror-only split ticket absorbs it.
- **Stale-response guards partitioned (SHARPENED by #173)** — the repo now has four stale families (substitution / ordering / coalescing / skip). The partition rule holds with one nuance: the ordering family may ride the SKIP gate (never substitution). Marker: a THIRD stale-response site or the loadSent holdout.
- **Relief-surface trichotomy state (#158 graduate)** — its "generation-aware ApiCallHandler variant" half is CLOSED by #173 (the stateless leg); the trichotomy-state half stands.
- Unchanged from #174: mirror-only KeepLastByKey split; transport-pin family fixture (marker: a pin in a THIRD VM file); stateless onError message-convention pair; update-one map-if sub-shape (marker: a SECOND KeepLast-family site); unifier-family-naming-coherence (marker: the family's next change); the DayStatus shared/domain move; the #92 Q4/Q5 403-refresh machinery; the two #160 graduates (shared-Exposed-query-op-helpers; screen-scoped-VM-seam); k6 for branch-scoped writes (needs #106 seeding); the root-AGENTS.md stale pre-push-iOS line; the hardcoded-month test dates (#110); the user-creation/role-assignment flow (#106); the request-flow grant/deny UI gap (ReliefAccessViewModel unwired); the standalone OpenAPI map (#139). The #171 ARCH pair (Bootstrap 401-split; Bootstrap+Dashboard handled-silently seam) still rides the onError-family fog.

## Recommended next pick

The frontier is EMPTY; the standing fog lines are the only chartable work. AFK-able picks in order:

1. **Mirror-only KeepLastByKey split (#166 P5 graduate)** — now a tied-top candidate: 2 consumers (Remittance full-shape + ReliefInvite mirror-only), a real seam with a 10+ session lineage. Note the cross-wire with #173: the loadSent holdout fog says the split ticket should also absorb the `stale = { stamp != sentStamp }` port (the two are natural neighbours — the split touches exactly the file the holdout needs). Small-to-medium AFK refactor.
2. **Stateful stale-failure legs (#173's P5 graduate)** — more design surface (a documented #165 contract to revisit, not just a port): a decision ticket first (grilling-lite/AFK analysis), then a build. The #173 stale-gate is the seam's precedent for the fix shape.
3. **Relief loadSent stale holdout** — trivial as a standalone (one `stale` param), but its natural home is the mirror-only split ticket (pick #1) — marker-driven, better fused than standalone.
4. **Transport-pin family fixture** — marker only (same two test files); wait for a third VM file.
5. The other items (DayStatus shared/domain move, #92 Q4/Q5 machinery, message-convention pair as a drive-by) need a human or a specific trigger — take only if present.

If the migration's runbook/persistence work isn't done (Coolify GitHub App for the private repo — the standing critical follow-up), that's the human-side item; the wizard run itself is COMPLETE.

## Migration notes (VPS handover — follow-ups still standing)

- **The switch holds** — seventh VPS session, toolchain stable. Pre-commit gate green on all 6 commits. Postgres reachable at localhost:5432; `company_app_test` clean.
- Standing human/runbook follow-ups unchanged from #174: Coolify GitHub App (or token-in-URL) for the private-repo app resource; wizard bootstrap should pin Asia/Manila; gh-auth stage needs `gh auth setup-git`; Postgres stage guard-create `company_app_test`; the test suite needs a TZ fixture (this VPS is already Asia/Manila); check for other Windows-mount-hidden exec bits; shellcheck still not installed; ntfy topic rotation; the loop-infra zombie-detector real exercise. `/tmp/opencode` ownership is stable (ubuntu-owned).

## Patterns + learnings

- **The pre-claim count falsification paid twice.** The fog's "6+" and its "flagged-shared" instance were both wrong (12 real sites; flagged is an in-flight-slot). The ticket body carried the grep-verified count, and the #172-pin uniqueness survived because the ticket SPEC refused to rely on the fog's enumeration. Same discipline as #174's fog-line-precision lesson — applied once more, pre-claim.
- **The pass-2 HARD was on my OWN reworded comment, and the lenses caught it.** I rewrote a test comment ("coroutine-start evaluation would read false and fail") that was false under the scheduler mechanics (a coroutine-start read runs inside runCurrent after the flip). Truth-class applies to the driver's own delta, not just doc lines — the register's memory worked as designed. Worth keeping a reflexive "the scheduler proof goes BOTH ways" check on any test comment that claims to pin an evaluation point: pin what the fixture can distinguish, nothing more.
- **Comment fixes are cheap but fertile ground for truth-class slips** — 4 of the 5 passes' work was comment-delta (dedupe, rewording, qualifying); the one HARD landed there. Each reword deserves the same lens discipline as code: a comment that names a pinning claim must survive the delete-the-mechanism test in the reader's head.
- **Self-review of the map edit worked again** (the #174 lesson, repeated): staged the body, diffed vs live, pushed only after the diff showed exactly the 4 intended edits. The "append, don't replace" rule (my earlier #171-entry replacement) held.
- **Prior-session patterns unchanged:** claim-first, facts-via-code, one-ticket-per-session, commit local not pushed, gate green before commit, the phased loop until 0 HARD, negative-controlled pins (probe-red evidence recorded in the resolution).

## Constitution update (this session)

**No new register classes.** The loop ran clean; HARD findings died by pass 2 (the pass-2 truth-class was fixed, not re-seen). Register row bumped (truth-class occurrences: #170, #172, #173 appended to the origin row in `docs/agents/code-review-loop.md`). The P5 observations are architecture residue, not bug classes: two graduate new fog lines (stateful stale-failure legs; relief loadSent holdout), one satisfies the #158 "generation-aware ApiCallHandler variant" half, one sharpens the #165 partition line.

## Critical follow-ups (human)

- **Coolify GitHub App** (or token-in-URL) for the private repo — needed before the app-resource stage works; still open since #169.
- **shellcheck** still not installed — `shellcheck -S warning scripts/vps-migration-wizard.sh` on the VPS.
- **ntfy topic rotation** from session 61 stands (`wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`).
- **Loop-infra zombie-detector** still needs a real exercise (a deliberate 10-min-sleep sub-agent probe during a review phase).
- **The branch is ahead 30 and NOT pushed** — the next push (k6 baseline + multi-target compile, ~3 min) is a deliberate act; fine to leave until the map needs remote sync or the user wants it.
- Wizard fold-in follow-ups (#169) unchanged: Asia/Manila pin, `gh auth setup-git`, guard-create test DB, TZ fixture, exec-bit sweep (`git ls-files -s | grep 100644` on scripts).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" (frontier empty → AFK fog-graduation; the mirror-only split fusing the loadSent holdout is the natural pick, with the stale-failure-legs decision-ticket close behind).
- **`/implement` + `docs/agents/code-review-loop.md`** — for the build ticket; register watch-lenses: **truth-class** (including the driver's OWN comment deltas — the pass-2 catch this session), **vacuous-test** (a pin a second suppressor or a no-dispatch keeps green — the P4 siblings-coverage check), **fix-that-didn't-land**, **keyed-mirror ordering**.
- **`/writing-for-agents`** — any agent-facing markdown edit (AGENTS.md, docs/agents/, the handoff — loaded this session for the composeApp/AGENTS.md + register edits).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket (stateful stale-failure legs would be the natural first user; standing frame applies).