# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 67

## What this is

The migration has **RUN** — this session ran from the VPS (`company-app-vps`, tailnet `100.84.96.51`), the chain relocated per the #169 handoff's step-2 branch. The frontier was EMPTY; per the handoff's "handler state-less launch is the AFK-able pick", this session graduated that #162 P5 fog line and shipped its build ticket. Deliverable: **5 commits `663a211`→`c88c6f8`** on `ralph/company-app-full-build` (each pre-commit gate green on the VPS), desktop suite 372→**376**, 0 failures.

**Ticket #168 — Build — Handler state-less launch (the #162 P5 graduate) — CLOSED.** `ApiCallHandler.launchStateless` (the launch/launchUnit family's state-less sibling: no `state` param, no UiState writes; success → transform, non-success → Unit-returning onNonSuccess, exception → onError, CancellationException rethrown; no stamp/fallback — those guard a UiState commit that no longer exists). **Count falsification: the fog named 2 throwaway flows — the code had 7** (`sentListFlow`, UserVM `mutation`, AuditLog `flaggedPage`/`acknowledgeResult`/`pageFetch`, FinanceReports `pageFetch`, SessionDashboard `editResultFlow`), all grep-verified zero-read and deleted; 18 launch sites ported across 5 VMs, behavior-preserving (P3 traced all 7 flows + repeated attempts + every error path; the falsification attempt failed — no Loading gate read a throwaway, no real state depended on the throwaway Error write, every converted onNonSuccess returned `true` pre-port). Truth-class correction to the ticket: "13 FinanceReports sites" was a miscount — **12**. Tests +4.

**Loop outcome: 3 passes + P5 + P5 loop-back, exit on 0 HARD all four phases.** Pass 1: 1 spec-miss HARD (cancellation test) + 2 truth-class HARDs (AuditLog "handler assigns Error" comment; AGENTS.md blanket "handler manages Loading/Success/Error" claim → launchStateless documented + qualified in composeApp/AGENTS.md). Pass 2: 1 HARD — the shipped cancellation test was **vacuous** (cancel() fired before the coroutine started under StandardTestDispatcher; passed even with the rethrow clause deleted — mutation-probe proven, the ReliefInviteViewModelTest:221 "dead job" precedent) → `runCurrent()` before cancel; negative-control confirmed the test now FAILS without the rethrow. Pass 3: 0 HARD → exit — a phase-reported "flaky failure" was a **stale incremental-compile artifact** from the pass-2 mutation probe (the test correctly failed against a stale class), environmental not defect. P5: 2 ARCH **graduated** — **pre-onError failure-surface residue** (the ~8 `catch (CancellationException){throw e} catch(Exception){<surface>; throw e}` call-site blocks re-implement the handler's own catch; `onError` is the unifying interface — a future port folds the surface into onError) + **loadMonthlyRollup missing onError** (the tree's sole onError-less stateless site; transport parks Loading forever — pre-existing, now the only failure-surface hole); 2 ARCH fix-in-ticket (stale UserManagementViewModelTest comment naming the deleted flow; unused ContentType import). P5 loop-back: 0 HARD. Accepted SOFTs (2 two-sighted): the `onNonSuccess` default `{}` silent-swallow footgun for future callers (KDoc + AGENTS.md document it); loadMonthlyRollup stuck-Loading (pre-existing, graduates via P5). Register: no new classes.

**Map updated** — #168 Decisions-so-far entry appended, the "handler state-less launch" fog line deleted, two new fog lines written (pre-onError failure-surface residue; loadMonthlyRollup missing onError), frontier paragraph rewritten. Frontier remains **EMPTY**.

## Session outcome

- **5 commits** on `ralph/company-app-full-build`, all gate-green on the VPS (ktlint/detekt/backend tests/cleanliness/shared-compile/Postgres via the installed pre-commit hook — the first full VPS gate run, the #169 watch-item: it succeeded end-to-end). Branch is **ahead 5, NOT pushed** (the pre-push hook runs k6 + multi-target compile; the standing "commit local not pushed" pattern applies).
- First VPS session: `git config user.name/email` had to be set (matches the repo's existing identity: Jayson Galvez / jaysonsullanogalvez@gmail.com).
- ktlint formatting ran staged-only via the hook (the #169 noted setup-hooks.sh install; on this box it used the cached ktlint 1.8.0 CLI).

## Current frontier (verified post-session)

**Frontier EMPTY** — no open unblocked child of #89. Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix). The map's standing fog lines (unchanged from #169 plus this session's two graduates):

Standing fog lines: the P5 ARCH graduates — mirror-only KeepLastByKey split; stale-response guards partitioned (documentation only); **pre-onError failure-surface residue (NEW)**; **loadMonthlyRollup missing onError (NEW)**; update-one map-if sub-shape (marker: a SECOND KeepLast-family site); unifier-family-naming-coherence (marker: the family's next change) — plus the DayStatus shared/domain move, the #92 Q4/Q5 403-refresh machinery, the #158 ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant) + the two #160 graduates (shared-Exposed-query-op-helpers; screen-scoped-VM-seam), k6 for branch-scoped writes (needs #106 seeding), the root-AGENTS.md stale pre-push-iOS line, the hardcoded-month test dates (#110), the user-creation/role-assignment flow (#106), the request-flow grant/deny UI gap (ReliefAccessViewModel unwired), and the standalone OpenAPI map (#139).

## Recommended next pick

The frontier is EMPTY and the standing fog lines are the only chartable work. The AFK-able picks in rough order:

1. **pre-onError failure-surface residue** (this session's P5 graduate) — a real seam (the ~8 catch+rethrow blocks), AFK, medium-sized (a port across 3 VMs), and it directly follows the launchStateless work — the interface (`onError`) already exists, the port deletes inline try/catch and moves surface calls into onError. The #168 resolution carries the exact shape.
2. **loadMonthlyRollup missing onError** (this session's P5 graduate) — small AFK fix (add the onError hook, the loadReliefDay shape), closes the tree's only failure-surface hole.
3. **mirror-only KeepLastByKey split** or **update-one map-if sub-shape** — both need a marker (a third consumer / a second site) that hasn't fired; taking them now is premature.
4. The grilling/HITL items (fog-graduation pick, #92 Q4/Q5 machinery) need a human — take only if the human is present.

If the migration's runbook/persistence work isn't done (Coolify GitHub App for the private repo — the #169 critical follow-up), that's the standing human-side item; the wizard run itself is COMPLETE.

## Migration notes (VPS handover — follow-ups still standing)

- **The switch succeeded** — this session ran on the VPS with the full toolchain. First-commit watch-item (VPS pre-commit gate) is GREEN.
- **Git identity was unset on the VPS** — set locally (repo-scoped) to the standard identity. If a future session clones fresh, it will need this again.
- Standing human/runbook follow-ups from #169 unchanged: Coolify GitHub App (or token-in-URL) for the private-repo app resource; wizard bootstrap should pin Asia/Manila; gh-auth stage needs `gh auth setup-git`; Postgres stage guard-create `company_app_test`; the test suite needs a TZ fixture (RouteValidation/ReliefAccess/RemittanceLine depend on machine TZ — on this VPS the TZ is already Asia/Manila); check for other Windows-mount-hidden exec bits; shellcheck still not installed; ntfy topic rotation; the loop-infra zombie-detector real exercise.

## Patterns + learnings

- **Count-falsification paid again**: the fog said "two throwaway flows = real seam"; grep showed 7. The ticket was scoped to the truth (7), not the fog (2). Same discipline as #166/#167.
- **The vacuous-cancellation-test trap recurred** (the ReliefInviteViewModelTest:221 precedent): under `StandardTestDispatcher` a `cancel()` before `runCurrent()` hits a not-yet-started job and the test passes with the branch under test deleted. The #168 fix (runCurrent → cancel → runCurrent) is now the file's own precedent — future cancellation tests should copy it. The **mutation-probe** (temporarily delete the rethrow clause, confirm the test fails, restore) is the proof that pins it.
- **Stale incremental-compile artifacts can fake a flaky test**: after a mutation probe, the compiled class can lag the source; the pass-3 "1-fail-in-11-runs" report was the test correctly failing against a stale class from the pass-2 probe. Mutation probes should run `--rerun-tasks`/clean first, and probe artifacts restored before further review runs.
- **The first VPS gate is real**: the hook runs backend detekt/ktlint/test + cleanliness + shared compile + Postgres reachability; the wizard's TZ fix made the backend suite green on the VPS.
- Prior-session patterns unchanged: claim-first (this session created+claimed #168), facts-via-code, one-ticket-per-session, commit local not pushed, gate green before commit.

## Constitution update (this session)

**No new register classes.** The vacuous-cancellation test was the existing ReliefInviteViewModelTest:221 precedent recurring — now pinned correctly, with the #168 cancellation test as a second in-file example. The "stale incremental artifact masquerading as flaky" episode is a process note (mutation-probe hygiene), not a new class — it produced no code defect.

## Critical follow-ups (human)

- **Coolify GitHub App** (or token-in-URL) for the private repo — needed before the app-resource stage works; the #169 migration log flagged it; still open.
- **shellcheck** still not installed — `shellcheck -S warning scripts/vps-migration-wizard.sh` on the VPS.
- **ntfy topic rotation** from session 61 stands (`wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`).
- **Loop-infra zombie-detector** still needs a real exercise (a deliberate 10-min-sleep sub-agent probe during a review phase).
- **The branch is ahead 5 and NOT pushed** — the next push (k6 baseline + multi-target compile, ~3 min) is a deliberate act; fine to leave until the map needs remote sync or the user wants it.
- Wizard fold-in follow-ups (#169) unchanged: Asia/Manila pin, `gh auth setup-git`, guard-create test DB, TZ fixture, exec-bit sweep (`git ls-files -s | grep 100644` on scripts).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" (frontier empty → AFK fog-graduation; the pre-onError residue line is the natural pick).
- **`/implement` + `docs/agents/code-review-loop.md`** — for the build ticket; register watch-lenses: **truth-class**, **keyed-mirror ordering**, **loud-stop swallow**, and the now-two-case **vacuous-cancellation-test** shape (runCurrent-before-cancel; mutation-probe to prove).
- **`/writing-for-agents`** — any agent-facing markdown edit (AGENTS.md, docs/agents/, this file).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket (standing frame applies).
