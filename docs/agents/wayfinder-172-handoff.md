# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 69

## What this is

The migration has **RUN** — this session ran from the VPS (`company-app-vps`, tailnet `100.84.96.51`). The frontier was EMPTY; per the #171 handoff's pick #1, this session shipped **ticket #170 — Build — loadMonthlyRollup missing onError (the #168/#169 P5 sibling)** — the state-less family's last failure-surface hole. Deliverable: **3 commits `e50176b`→`0a8e182`** on `ralph/company-app-full-build` (each pre-commit gate green on the VPS), desktop suite 376→**378**, 0 failures. Session also shipped a user-requested AGENTS.md context-pointer change (commit `63db6e2`, mid-session interjection — see Session outcome).

**Ticket #170 — Build — loadMonthlyRollup missing onError (the #168/#169 P5 sibling) — CLOSED.** `loadMonthlyRollup` (FinanceReportsViewModel:449-494) gained the `onError` hook (the loadReliefDay shape, generation-guarded, `e.message ?: "Unknown error"`): a transport failure now moves `_monthlyRollup` Loading → Error (terminal) instead of parking on Loading forever. `onNonSuccess` untouched (404 → Success(null), other status → Error); CancellationException still bypasses onError; the screen renders the rollup Error as a logWarn only (no card — the ticket's documented scope, the observable effect is the state machine completing).

**Truth-class (2, both the ticket's own claim)**: the ticket's premise — "the tree's sole onError-less stateless site" — was **FALSE as shipped**: `ReliefInviteViewModel.loadSent` (224-239) is ALSO an onError-less launchStateless site, by #162 keep-last design (keyed-mirror commit, no UiState to park). The false "sole" premise was inherited from #168's P5 + #169's map entry (P1 + P4 hit it independently, register default HARD). Corrected claim: **the sole onError-less stateless site parking a UiState on Loading**. Pass 2 then caught my own comment regression: the test comment's present-tense "sole onError-less stateless site" contradicted the very fix it annotated (P2 truth-class) → "pre-fix" qualifier restored.

**Tests (+2, both negative-control-proven)**: `monthlyRollup_transportFailure_surfacesError` (MockEngine throws IOException("connection reset") → Error + message surfaced; red pre-fix; onError deleted → parks Loading → fails) + `monthlyRollup_supersededTransportFailure_staysInert` (the P4 pass-1 SOFT — the generation guard was unpinned): gated supersession via `CompletableDeferred` (the AuditLog gated pattern; MockEngine Unconfined, single `TestCoroutineScheduler` — deterministic), stale IOException landing after a newer Success must be suppressed; negative control proven (guard stripped → stale Error clobbers → fails).

**Loop outcome: 3 passes + P5, exit on 0 HARD all four phases.** Pass 1: P1 1 HARD (truth-class "sole") + P4 1 SOFT (guard-unpinned) + all-3 duplicated test comment → `075bf86`. Pass 2: P2 1 HARD (truth-class test-comment) → `0a8e182`; the two-sighted DAILY-mode stale-landing SOFT confirmed benign (lands only while DAILY — screen gates on `mode == MONTHLY`; wiped synchronously before any MONTHLY re-render; rides the stale-gate fog family). Pass 3: 0 HARD all four phases → exit. **P5: 0 fix-in-ticket, 2 ARCH graduates** — (1) **stateless-launch stale-gate (#165 class) — RE-SIGHTED** (existing fog line, now confirmed by 5+ hand-rolled `if (generation == X)` instances while the stateful launch owns the class in-handler via stamp/fallback); (2) **stateless onError message-convention pair** (NEW — the onNonSuccess prefix "monthly rollup failed: <status>" vs the onError bare `e.message` within one surface; loadReliefDay inherited the pair from #158; loadSection/fetchPage prefix both). No P5 loop-back. Register: no new classes; truth-class occurrence bumped (the false "sole" premise). Accepted SOFTs (2 two-sighted, benign): DAILY-mode stale-landing + the log-only rollup Error (pre-existing #105 F5 design, ticket-documented). **#169's "onError transport-pin per surface" graduate partially rides**: the rollup surface now has its own pin; fetchFlagged / feed fetchPage / loadSection stay open as fog.

**Map updated** — #170 Decisions-so-far entry appended, the "loadMonthlyRollup missing onError" fog line deleted, the message-convention pair graduated into the fog, the stale-gate line re-sighted, frontier paragraph rewritten. Frontier remains **EMPTY**.

## Session outcome

- **3 commits** on `ralph/company-app-full-build` (build + pass-1 + pass-2), each gate-green on the VPS (ktlint/detekt/backend tests/cleanliness/shared-compile/Postgres via the installed pre-commit hook) + **1 user-requested commit** (`63db6e2`) — the AGENTS.md permissions pointer (see below). Branch is **ahead 15, NOT pushed** (standing "commit local not pushed" pattern).
- **Mid-session interjection (user, direct)**: add a context pointer in AGENTS.md to request access only to the `permissions` section of `opencode.json`. Shipped as `63db6e2` — one bullet under Configuration details: "**Permissions** — `opencode.json`'s `permissions` array ... read **only that section** — the plugin/skill/server blocks are unrelated config". Written via the /writing-for-agents context-pointer discipline (front-loaded leading word, one trigger, no whole-file reads). Separate commit from the ticket (not part of #170's scope).
- `git config user.name/email` still set from session 67 (Jayson Galvez / jaysonsullanogalvez@gmail.com).
- ktlint formatting ran staged-only via the hook (cached ktlint 1.8.0 CLI).
- Map #89 edited via `gh issue edit --body-file`; the working body was staged in `/tmp/opencode` (owned by ubuntu — the `sudo chown` from session 68 held).
- Sub-issue wiring: this session created #170 with `Part of #89` in the body + a native parent link via `gh api --method POST repos/.../issues/89/sub_issues -F sub_issue_id=<db-id>` (the `-f` string form 422s — must be `-F` integer; #168 had the native link, #169 was body-only — both work, native is preferred).

## Current frontier (verified post-session)

**Frontier EMPTY** — no open unblocked child of #89. Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix). Standing fog lines (changed from #171: the loadMonthlyRollup line is closed; the stale-gate line is RE-SIGHTED; the message-convention pair is new; the onError transport-pin line is partially ridden):

- **Stateless-launch stale-gate (#165 class) — RE-SIGHTED by #170's P5** — the `if (generation == XGeneration) { <surface>; finish }` stale-suppression guard hand-rolled at 5+ stateless launchStateless sites (AuditLog fetchPage, FinanceReports fetchPage/loadSection/loadMonthlyRollup, loadReliefDay rides it) while the stateful `launch` owns this exact class in-handler (#165's stamp/fallback). Fix-shape: a `stale: () -> Boolean` gate on launchStateless wrapping transform/onNonSuccess/onError. Medium handler-API change; marker: the next stateless site that writes the generation guard, or when launchStateless next changes.
- **Stateless onError message-convention pair (NEW, graduated by #170's P5)** — within one surface the failure messages speak two languages: the onNonSuccess prefix (`"monthly rollup failed: <status>"`) vs the onError bare `e.message` (the loadReliefDay shape, inherited from #158; loadSection/fetchPage prefix both hooks). Same surface, two formats — a convention decision when the onError family next changes. Marker: the next onError added or the message formats unified. Trivial fix-shape (prefix the onError message) if anyone picks it up.
- **OnError wiring needs a transport-failure VM pin per ported surface (partially ridden by #170)** — 3 of the 6 ported failure surfaces still have only HTTP-status VM pins: AuditLog fetchFlagged, FinanceReports feed fetchPage, loadSection. The rollup surface got its pin in #170. Fix-shape: a throw-IOException MockEngine test per surface.
- **Unused `CancellationException` import residue (SessionBootstrapViewModel:15)** — one-line delete, no behavior change.
- Unchanged from #171: mirror-only KeepLastByKey split; stale-response guards partitioned (documentation only); update-one map-if sub-shape (marker: a SECOND KeepLast-family site); unifier-family-naming-coherence (marker: the family's next change); the DayStatus shared/domain move; the #92 Q4/Q5 403-refresh machinery; the #158 ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant) + the two #160 graduates (shared-Exposed-query-op-helpers; screen-scoped-VM-seam); k6 for branch-scoped writes (needs #106 seeding); the root-AGENTS.md stale pre-push-iOS line; the hardcoded-month test dates (#110); the user-creation/role-assignment flow (#106); the request-flow grant/deny UI gap (ReliefAccessViewModel unwired); the standalone OpenAPI map (#139).

## Recommended next pick

The frontier is EMPTY and the standing fog lines are the only chartable work. The AFK-able picks in order:

1. **Unused `CancellationException` import residue (SessionBootstrapViewModel:15)** — the smallest possible AFK ticket: delete one import line, no behavior change, the #169-family import-removal that that ticket explicitly excluded. Green-gate trivial. The clean next step after the onError family is closed.
2. **OnError transport-pin per remaining surface** — small test-coverage ticket: throw-IOException tests for AuditLog fetchFlagged + FinanceReports feed fetchPage + loadSection. The #170 rollup pin is the proven shape; the markers on all three surfaces fired.
3. **Stateless onError message-convention pair** — trivial fix-shape (prefix the onError messages to match onNonSuccess), but it's a convention decision — the right moment is the next change to the family, or a drive-by now.
4. **Stateless-launch stale-gate (#165 class)** — medium handler-API change (a `stale: () -> Boolean` gate on launchStateless), absorbs the generation guards at 5+ sites. Real seam, re-sighted twice; more design surface than 1-3.
5. The grilling/HITL items (fog-graduation picks, #92 Q4/Q5 machinery) need a human — take only if the human is present.

If the migration's runbook/persistence work isn't done (Coolify GitHub App for the private repo — the standing critical follow-up), that's the human-side item; the wizard run itself is COMPLETE.

## Migration notes (VPS handover — follow-ups still standing)

- **The switch holds** — fourth VPS session, toolchain stable. The first-commit watch-item (VPS pre-commit gate) stays green.
- Standing human/runbook follow-ups unchanged from #171: Coolify GitHub App (or token-in-URL) for the private-repo app resource; wizard bootstrap should pin Asia/Manila; gh-auth stage needs `gh auth setup-git`; Postgres stage guard-create `company_app_test`; the test suite needs a TZ fixture (RouteValidation/ReliefAccess/RemittanceLine depend on machine TZ — on this VPS the TZ is already Asia/Manila); check for other Windows-mount-hidden exec bits; shellcheck still not installed; ntfy topic rotation; the loop-infra zombie-detector real exercise. `/tmp/opencode` ownership is stable (ubuntu-owned since session 68).

## Patterns + learnings

- **The inherited-premise trap (truth-class, second occurrence of the #168/#169 "sole" claim)**: the ticket repeated the map's + #168's + #169's "sole onError-less stateless site" claim verbatim instead of falsifying it — `loadSent` had been onError-less since #162, and the reviews' tree-wide grep caught it in pass 1. The lesson: **a "the tree's only X" claim in a predecessor resolution must be re-grepped before adoption, even when it's the ticket's own premise.** P1 and P4 hit it independently (register default HARD, correct call).
- **The test comment repeated the claim in the present tense** — and the fix it annotated made that present-tense claim false the moment it landed. P2 caught it pass 2. Comments about a pre-fix state must say "pre-fix".
- **The `advanceUntilIdle` vs `runCurrent` distinction bit once**: my first transport test used `runCurrent` after `setMode(MONTHLY)` and stayed on Loading even with the fix (the exception path spans more dispatch hops than the synchronous 404 mock the sibling test uses). The proven loadReliefDay shape uses `advanceUntilIdle` — matched it, green. The register's vacuous-cancellation-test watch-lens is the cousin of this: timing assumptions need the sibling pattern, not invention.
- **Negative-control discipline held twice**: both new tests were mutation-probed (guard stripped → red; onError deleted → red) before the loop. Cheap on a 1-min desktop-test run; the register's vacuous-test class is the cost of skipping it.
- Prior-session patterns unchanged: claim-first (this session created+claimed #170), facts-via-code, one-ticket-per-session, commit local not pushed, gate green before commit, the phased loop until 0 HARD.

## Constitution update (this session)

**No new register classes.** The loop ran clean. The truth-class class absorbed 2 occurrence bumps (the ticket's "sole" premise + my pass-2 comment regression) — ordinary class application, not a new lesson. The session's P5 graduates (stale-gate re-sighting, message-convention pair) are architecture residue, not bug classes.

## Critical follow-ups (human)

- **Coolify GitHub App** (or token-in-URL) for the private repo — needed before the app-resource stage works; still open since #169.
- **shellcheck** still not installed — `shellcheck -S warning scripts/vps-migration-wizard.sh` on the VPS.
- **ntfy topic rotation** from session 61 stands (`wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`).
- **Loop-infra zombie-detector** still needs a real exercise (a deliberate 10-min-sleep sub-agent probe during a review phase).
- **The branch is ahead 15 and NOT pushed** — the next push (k6 baseline + multi-target compile, ~3 min) is a deliberate act; fine to leave until the map needs remote sync or the user wants it.
- Wizard fold-in follow-ups (#169) unchanged: Asia/Manila pin, `gh auth setup-git`, guard-create test DB, TZ fixture, exec-bit sweep (`git ls-files -s | grep 100644` on scripts).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" (frontier empty → AFK fog-graduation; the import-residue line or the remaining transport-pins are the natural picks).
- **`/implement` + `docs/agents/code-review-loop.md`** — for the build ticket; register watch-lenses: **truth-class** (the inherited-premise trap — re-grep predecessor "the tree's only X" claims before adoption), **vacuous-test**, **fix-that-didn't-land**, **keyed-mirror ordering**.
- **`/writing-for-agents`** — any agent-facing markdown edit (AGENTS.md, docs/agents/, this file).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket (standing frame applies).
