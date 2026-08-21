# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 68

## What this is

The migration has **RUN** — this session ran from the VPS (`company-app-vps`, tailnet `100.84.96.51`). The frontier was EMPTY; per the #170 handoff's pick #1, this session graduated the **pre-onError failure-surface residue** fog line (#168's P5 graduate) and shipped its build ticket. Deliverable: **2 commits `e68a6c1`→`93e39d9`** on `ralph/company-app-full-build` (each pre-commit gate green on the VPS), desktop suite **376**, 0 failures.

**Ticket #169 — Build — Pre-onError failure-surface residue (the #168 P5 graduate) — CLOSED.** The catch+rethrow residue over `ApiCallHandler.launchStateless` is dead: `onError` is now the single failure surface for the whole state-less family. **Count falsification: the fog said "~8" — the code had 11 blocks at 6 sites** (`catch (CancellationException){throw e} catch (Exception){<surface>; throw e}` at AuditLog fetchFlagged/acknowledge/fetchPage ×2, FinanceReports fetchPage/loadSection ×2, User runMutation ×1), all ported — block/transform → plain bodies, the surface + in-flight-guard clear moved into `onError`; the `CancellationException` import died in all 3 files (grep: zero residue outside the handler). Behavior-preserving (P3 traced all 6 flows + repeated attempts + every error path; the falsification attempt failed): surface runs exactly once per failure at the same point; CancellationException bypasses onError exactly as it bypassed the residue blocks; the generation guards (browse/feed/editData) evaluate at the same instant; onNonSuccess byte-identical; every in-flight marker clears exactly once. **The only behavior delta (documented)**: the null-message fallback at the 5 two-catch sites collapses from ("network error" block-catch / "parse error" transform-catch) to "network error" — non-null `e.message` (the common case) renders identically; no test or screen matches the fallback strings (grep-verified). A dead `@Suppress("TooGenericExceptionCaught")` (the last catch died in the port) dropped pass 1.

**Loop outcome: 2 passes + P5, exit on 0 HARD all four phases.** Pass 1: 0 HARD — P1 clean, P2 2 SOFT, P3 1 SOFT, P4 2 SOFT; the one actionable finding (dead @Suppress) fixed in-ticket. Pass 2: 0 HARD all four phases → exit — all 3 accepted SOFTs confirmed two-sighted+. **P5: 0 fix-in-ticket, 4 ARCH graduated** — **stateless-launch stale-gate (#165 class)** (the `if (gen == X) { surface; finish }` guard hand-rolled at 4+ stateless sites while the stateful launch owns this class in-handler via stamp/fallback; a `stale: () -> Boolean` gate on launchStateless would absorb the per-site gates), **onError wiring needs a transport-failure VM pin per ported surface** (fetchFlagged + feed fetchPage + loadSection have only HTTP-status pins — deleting their onError blocks would pass green), **loadMonthlyRollup onError-less** (pre-existing, the #168 P5 sibling — confirmed the tree's sole onError-less stateless site), **unused `CancellationException` import residue (SessionBootstrapViewModel:15)** (pre-existing, same family as the port's 3 import removals). Accepted SOFTs (3 two-sighted): the fallback collapse (documented deviation); runMutation onError transform coverage (unreachable — 204 ops never deserialize; strictly better than pre-port silent swallow); the 2-site onError glue (locality over a 4-param factory). Register: no new classes.

**Map updated** — #169 Decisions-so-far entry appended, the "pre-onError failure-surface residue" fog line deleted and replaced by the three new graduates (the loadMonthlyRollup line stays), frontier paragraph rewritten. Frontier remains **EMPTY**.

## Session outcome

- **2 commits** on `ralph/company-app-full-build`, both gate-green on the VPS (ktlint/detekt/backend tests/cleanliness/shared-compile/Postgres via the installed pre-commit hook). Branch is **ahead 10, NOT pushed** (standing "commit local not pushed" pattern).
- `git config user.name/email` still set from session 67 on this box (Jayson Galvez / jaysonsullanogalvez@gmail.com) — a fresh clone would need it again.
- ktlint formatting ran staged-only via the hook (cached ktlint 1.8.0 CLI).
- Map #89 edited via `gh issue edit --body-file` (full-body replace); the working file was staged in `/tmp/opencode` (root-owned on this VPS — `sudo chown ubuntu` fixed it).

## Current frontier (verified post-session)

**Frontier EMPTY** — no open unblocked child of #89. Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix). Standing fog lines (changed from #170: the pre-onError line is closed; three new graduates ride):

- **loadMonthlyRollup missing onError** — the tree's sole onError-less stateless site (FinanceReportsViewModel:449-486): a transport failure parks `_monthlyRollup` on Loading forever. Small AFK fix, the loadReliefDay shape. The #168 P5 sibling — the natural #169 follow-up.
- **Stateless-launch stale-gate (#165 class)** — the generation-gated `if (gen == X) { surface; finish }` hand-rolled at 4+ stateless sites while the stateful launch owns this class in-handler (#165 stamp/fallback). A `stale: () -> Boolean` gate on launchStateless would absorb them. Medium handler-API change.
- **OnError wiring needs a transport-failure VM pin per ported surface** — 3 of 6 ported surfaces (fetchFlagged, feed fetchPage, loadSection) have only HTTP-status pins; a throw-IOException test per surface would pin the onError wiring.
- **Unused `CancellationException` import residue (SessionBootstrapViewModel:15)** — one-line delete, no behavior change.
- Unchanged from #170: mirror-only KeepLastByKey split; stale-response guards partitioned (documentation only); update-one map-if sub-shape (marker: a SECOND KeepLast-family site); unifier-family-naming-coherence (marker: the family's next change); the DayStatus shared/domain move; the #92 Q4/Q5 403-refresh machinery; the #158 ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant) + the two #160 graduates (shared-Exposed-query-op-helpers; screen-scoped-VM-seam); k6 for branch-scoped writes (needs #106 seeding); the root-AGENTS.md stale pre-push-iOS line; the hardcoded-month test dates (#110); the user-creation/role-assignment flow (#106); the request-flow grant/deny UI gap (ReliefAccessViewModel unwired); the standalone OpenAPI map (#139).

## Recommended next pick

The frontier is EMPTY and the standing fog lines are the only chartable work. The AFK-able picks in order:

1. **loadMonthlyRollup missing onError** — the direct continuation of this session's work (the #168 P5 sibling the #170 handoff listed as pick #2): small, AFK, closes the tree's only failure-surface hole. Fix-shape: add the onError hook (the loadReliefDay shape), pinned by a transport-failure test (rides the "onError transport-pin per surface" graduate).
2. **Stateless-launch stale-gate (#165 class)** — medium handler-API change (a `stale: () -> Boolean` gate on launchStateless), absorbs the generation guards at 4+ sites. Real seam, no marker needed. More design surface than #1.
3. **OnError transport-pin per ported surface** — small test-coverage ticket; pairs naturally with #1 (the loadReliefDay fix would add a pin for its own surface).
4. The grilling/HITL items (fog-graduation picks, #92 Q4/Q5 machinery) need a human — take only if the human is present.

If the migration's runbook/persistence work isn't done (Coolify GitHub App for the private repo — the #169 critical follow-up), that's the standing human-side item; the wizard run itself is COMPLETE.

## Migration notes (VPS handover — follow-ups still standing)

- **The switch holds** — third VPS session, toolchain stable. The first-commit watch-item (VPS pre-commit gate) stays green.
- Standing human/runbook follow-ups unchanged from #170: Coolify GitHub App (or token-in-URL) for the private-repo app resource; wizard bootstrap should pin Asia/Manila; gh-auth stage needs `gh auth setup-git`; Postgres stage guard-create `company_app_test`; the test suite needs a TZ fixture (RouteValidation/ReliefAccess/RemittanceLine depend on machine TZ — on this VPS the TZ is already Asia/Manila); check for other Windows-mount-hidden exec bits; shellcheck still not installed; ntfy topic rotation; the loop-infra zombie-detector real exercise. **New this session**: `/tmp/opencode` is root-owned on the VPS — a fresh session needs `sudo chown ubuntu:ubuntu /tmp/opencode` (or write to `$HOME`) before staging scratch files there.

## Patterns + learnings

- **Count-falsification paid a fourth time**: the fog said "~8" blocks; grep showed 11 at 6 sites. Same discipline as #166/#167/#168. The ticket was scoped to the truth (11), not the fog approximation.
- **The port's falsification check**: the strongest counter-hypothesis — "the block-catch ran the surface BEFORE the handler's logError; onError runs after — is there an observable ordering delta?" — died on inspection: `logError` is non-suspend, both run in one coroutine frame, no state read between them. P3 confirmed every guard clears exactly once and cancellation never surfaces.
- **The only deviation to document was the null-message fallback collapse** ("parse error" → "network error" at the 5 two-catch sites) — a genuinely invisible edge (transport and deserialization exceptions virtually always carry a message; no test or screen matches the fallback strings). Documented in the ticket + resolution; 8 review sightings confirmed it stays SOFT.
- Prior-session patterns unchanged: claim-first (this session created+claimed #169), facts-via-code, one-ticket-per-session, commit local not pushed, gate green before commit.

## Constitution update (this session)

**No new register classes.** The loop ran clean — no fix-that-didn't-land, no vacuous-test recurrence (the dead-@Suppress find was ordinary hygiene). The session's P5 graduates (stateless-launch stale-gate, onError transport-pin gap, unused import) are architecture residue, not bug classes.

## Critical follow-ups (human)

- **Coolify GitHub App** (or token-in-URL) for the private repo — needed before the app-resource stage works; the #169 migration log flagged it; still open.
- **shellcheck** still not installed — `shellcheck -S warning scripts/vps-migration-wizard.sh` on the VPS.
- **ntfy topic rotation** from session 61 stands (`wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`).
- **Loop-infra zombie-detector** still needs a real exercise (a deliberate 10-min-sleep sub-agent probe during a review phase).
- **The branch is ahead 10 and NOT pushed** — the next push (k6 baseline + multi-target compile, ~3 min) is a deliberate act; fine to leave until the map needs remote sync or the user wants it.
- Wizard fold-in follow-ups (#169) unchanged: Asia/Manila pin, `gh auth setup-git`, guard-create test DB, TZ fixture, exec-bit sweep (`git ls-files -s | grep 100644` on scripts).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" (frontier empty → AFK fog-graduation; the loadMonthlyRollup onError line is the natural pick).
- **`/implement` + `docs/agents/code-review-loop.md`** — for the build ticket; register watch-lenses: **truth-class**, **count-falsification** (the fog's "~8" was wrong twice running), **fix-that-didn't-land**, **keyed-mirror ordering**, **vacuous-cancellation-test** (runCurrent-before-cancel; mutation-probe to prove).
- **`/writing-for-agents`** — any agent-facing markdown edit (AGENTS.md, docs/agents/, this file).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket (standing frame applies).
