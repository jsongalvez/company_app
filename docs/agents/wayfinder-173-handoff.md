# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 70

## What this is

This session ran from the VPS. The frontier was EMPTY; per the #172 handoff's pick #1, this session shipped **ticket #171 — Build — Unused CancellationException import residue (SessionBootstrapViewModel:15)** — the smallest possible AFK ticket, the #169-family import removal that #169 explicitly excluded. Deliverable: **1 commit `5ff25ef`** on `ralph/company-app-full-build` (pre-commit gate green), desktop suite 378→**378**, 0 failures.

**Ticket #171 — Build — Unused CancellationException import residue (SessionBootstrapViewModel:15) — CLOSED.** `SessionBootstrapViewModel.kt` dropped `import kotlinx.coroutines.CancellationException` (the #169-family import removal that ticket explicitly excluded from its enumerated scope). Grep-verified as shipped: the only remaining `CancellationException` import in composeApp is `ApiCallHandler.kt:8`, used at the catch/rethrow lines 77/144; the file compiles with every remaining import used (P5b verified import-by-import).

**Truth-class: none.** The ticket's premise (zero usages) was verified by grep before claiming (the #172-handoff lesson: re-grep the "the tree's only X" claims before adoption — this ticket's claim was a zero-usage count, checked directly). No false "sole"-style inheritance this time.

**Tests: none shipped** — the spec'd shape (import-only removal, no behavior surface to pin). Existing desktop suite stayed 378/0.

**Loop outcome: pass 1 clean → exit; P5: 0 fix-in-ticket, no loop-back.** Pass 1: P1–P4 all **0 HARD, 0 SOFT** — the four lenses independently grep-verified the deletion landed (fix-that-didn't-land inert), the zero-usage claim holds as shipped, and the "no behavior change" claim failed to falsify (imports are compile-time; the catch/rethrow behavior lives solely in ApiCallHandler, whose own import is untouched). All 8 register classes checked — no match. **P5: 2 ARCH observations, both pre-existing surfaces riding the onError-family fog, no new fog lines** — (1) SessionBootstrapViewModel's 401-class decision split across `transform` (Unauthorized → silent `Unit`) + `onNonSuccess` (401 → Idle), one decision at two sites in one file (rides the message-convention-pair marker: the next onError-family change); (2) the "status-class handled-silently" shape at Bootstrap + Dashboard `onNonSuccess` — a real 2-adapter seam (rides the onError-family fog). No accepted SOFTs to carry.

**Map updated** — #171 Decisions-so-far entry appended, the "Unused CancellationException import residue" fog line deleted, frontier paragraph rewritten (import hygiene clean — the only remaining CancellationException import in composeApp is ApiCallHandler's, used). Frontier remains **EMPTY**.

## Session outcome

- **1 commit** on `ralph/company-app-full-build` (ktlint/detekt/backend tests/cleanliness/shared-compile/Postgres via the installed pre-commit hook). Branch is **ahead 17, NOT pushed** (standing "commit local not pushed" pattern).
- Ticket lifecycle: #171 created with `Part of #89` + native parent link via `gh api --method POST repos/.../issues/89/sub_issues -F sub_issue_id=<db-id>` (the `-F` integer form), claimed (`--add-assignee @me`), worked, closed with resolution comment.
- Map #89 edited via `gh issue edit --body-file`; working body staged in `/tmp/opencode/map89.md` (ubuntu-owned — the session-68 `sudo chown` held).
- `git config user.name/email` still set from session 67 (Jayson Galvez / jaysonsullanogalvez@gmail.com).
- Sub-issue wiring note: the naive `-F sub_issue_id=171` (issue number, not db-id) 422s as "cannot be the same as parent" — must resolve the db-id first (`gh api repos/.../issues/171 --jq .id`). Confirmed 171 sits in the parent list (pagination: query with `?per_page=100`).

## Current frontier (verified post-session)

**Frontier EMPTY** — no open unblocked child of #89. Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix). Standing fog lines (changed from #172: the import-residue line is CLOSED; the onError-family lines unchanged — stale-gate re-sighted, message-convention pair, transport-pin partially ridden):

- **Stateless-launch stale-gate (#165 class) — RE-SIGHTED by #170's P5** — the `if (generation == XGeneration) { <surface>; finish }` stale-suppression guard hand-rolled at 5+ stateless launchStateless sites (AuditLog fetchPage, FinanceReports fetchPage/loadSection/loadMonthlyRollup, loadReliefDay rides it) while the stateful `launch` owns this exact class in-handler (#165's stamp/fallback). Fix-shape: a `stale: () -> Boolean` gate on launchStateless wrapping transform/onNonSuccess/onError. Medium handler-API change; marker: the next stateless site that writes the generation guard, or when launchStateless next changes.
- **Stateless onError message-convention pair (NEW, graduated by #170's P5)** — within one surface the failure messages speak two languages: the onNonSuccess prefix (`"monthly rollup failed: <status>"`) vs the onError bare `e.message` (the loadReliefDay shape, inherited from #158; loadSection/fetchPage prefix both hooks). Same surface, two formats — a convention decision when the onError family next changes. Marker: the next onError added or the message formats unified. Trivial fix-shape (prefix the onError message) if anyone picks it up.
- **OnError wiring needs a transport-failure VM pin per ported surface (partially ridden by #170)** — 3 of the 6 ported failure surfaces still have only HTTP-status VM pins: AuditLog fetchFlagged, FinanceReports feed fetchPage, loadSection. Fix-shape: a throw-IOException MockEngine test per surface.
- Unchanged from #172: mirror-only KeepLastByKey split; stale-response guards partitioned (documentation only); update-one map-if sub-shape (marker: a SECOND KeepLast-family site); unifier-family-naming-coherence (marker: the family's next change); the DayStatus shared/domain move; the #92 Q4/Q5 403-refresh machinery; the #158 ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant) + the two #160 graduates (shared-Exposed-query-op-helpers; screen-scoped-VM-seam); k6 for branch-scoped writes (needs #106 seeding); the root-AGENTS.md stale pre-push-iOS line; the hardcoded-month test dates (#110); the user-creation/role-assignment flow (#106); the request-flow grant/deny UI gap (ReliefAccessViewModel unwired); the standalone OpenAPI map (#139). Two ARCH observations from #171's P5 ride the onError-family fog (the Bootstrap 401-split; the Bootstrap+Dashboard handled-silently seam) — their markers are the onError family's next change.

## Recommended next pick

The frontier is EMPTY and the standing fog lines are the only chartable work. The AFK-able picks in order:

1. **OnError transport-pin per remaining surface** — small test-coverage ticket: throw-IOException tests for AuditLog fetchFlagged + FinanceReports feed fetchPage + loadSection. The #170 rollup pin is the proven shape; the markers on all three surfaces fired. Natural next after the import-hygiene line closes.
2. **Stateless onError message-convention pair** — trivial fix-shape (prefix the onError messages to match onNonSuccess), but it's a convention decision — the right moment is the next change to the family, or a drive-by now.
3. **Stateless-launch stale-gate (#165 class)** — medium handler-API change (a `stale: () -> Boolean` gate on launchStateless), absorbs the generation guards at 5+ sites. Real seam, re-sighted twice; more design surface than 1–2.
4. The grilling/HITL items (fog-graduation picks, #92 Q4/Q5 machinery) need a human — take only if the human is present.

If the migration's runbook/persistence work isn't done (Coolify GitHub App for the private repo — the standing critical follow-up), that's the human-side item; the wizard run itself is COMPLETE.

## Migration notes (VPS handover — follow-ups still standing)

- **The switch holds** — fifth VPS session, toolchain stable. The first-commit watch-item (VPS pre-commit gate) stays green.
- Standing human/runbook follow-ups unchanged from #172: Coolify GitHub App (or token-in-URL) for the private-repo app resource; wizard bootstrap should pin Asia/Manila; gh-auth stage needs `gh auth setup-git`; Postgres stage guard-create `company_app_test`; the test suite needs a TZ fixture (RouteValidation/ReliefAccess/RemittanceLine depend on machine TZ — on this VPS the TZ is already Asia/Manila); check for other Windows-mount-hidden exec bits; shellcheck still not installed; ntfy topic rotation; the loop-infra zombie-detector real exercise. `/tmp/opencode` ownership is stable (ubuntu-owned since session 68).

## Patterns + learnings

- **The zero-usage claim survived this time — by verification, not inheritance.** The #172 handoff's lesson (re-grep predecessor "the tree's only X" claims before adoption) was applied to the ticket's own premise: the grep ran before the ticket was created, and P1–P4 re-ran it on the delta. The discipline cost nothing on a one-line ticket; the counterfactual (an unverified "sole residue" claim shipping on an un-grepped tree) is the #170 pass-1 class.
- **Exit-on-pass-1 is legitimate when pass 1 is genuinely clean.** The AGENTS.md exit contract is "one full pass with zero HARD findings"; the typical 2–3 passes exist because passes produce fixes to re-diff. A one-line deletion with four lenses all reporting 0 HARD AND 0 SOFT has no fix batch to re-review — an empty-delta pass 2 re-derives the same code. Recorded honestly in the resolution ("pass 1 clean → exit"), not laundered as "pass 2 clean".
- **The `sub_issue_id` db-id trap recurs**: `-F sub_issue_id=<issue-number>` 422s with a confusing "cannot be the same as parent" message. Resolve the db-id first. Worth a line in the runbook if the loop keeps wiring sub-issues.
- Prior-session patterns unchanged: claim-first, facts-via-code, one-ticket-per-session, commit local not pushed, gate green before commit, the phased loop until 0 HARD.

## Constitution update (this session)

**No new register classes.** The loop ran clean. The truth-class did not move (no false claims this session). The two P5 observations are architecture residue, not bug classes; both ride existing fog families without graduating new lines.

## Critical follow-ups (human)

- **Coolify GitHub App** (or token-in-URL) for the private repo — needed before the app-resource stage works; still open since #169.
- **shellcheck** still not installed — `shellcheck -S warning scripts/vps-migration-wizard.sh` on the VPS.
- **ntfy topic rotation** from session 61 stands (`wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`).
- **Loop-infra zombie-detector** still needs a real exercise (a deliberate 10-min-sleep sub-agent probe during a review phase).
- **The branch is ahead 17 and NOT pushed** — the next push (k6 baseline + multi-target compile, ~3 min) is a deliberate act; fine to leave until the map needs remote sync or the user wants it.
- Wizard fold-in follow-ups (#169) unchanged: Asia/Manila pin, `gh auth setup-git`, guard-create test DB, TZ fixture, exec-bit sweep (`git ls-files -s | grep 100644` on scripts).

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" (frontier empty → AFK fog-graduation; the remaining transport-pins are the natural pick now that import hygiene is clean).
- **`/implement` + `docs/agents/code-review-loop.md`** — for the build ticket; register watch-lenses: **truth-class** (re-grep predecessor claims before adoption — applied this session, keep it), **vacuous-test**, **fix-that-didn't-land**, **keyed-mirror ordering**.
- **`/writing-for-agents`** — any agent-facing markdown edit (AGENTS.md, docs/agents/, this file).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket (standing frame applies).
