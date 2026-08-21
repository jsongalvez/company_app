# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 47

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 47 picked the recommended small-AFK next build — the **#147 accepted-SOFT desktop empty/ERRORED refresh affordance** — graduated it as **#150 ("Build — Desktop dashboard empty-state refresh affordance (graduated from #147 accepted SOFT)")**: `wayfinder:task` frontend build, created + wired as a child of #89 + claimed, run through the phased code-review loop (**3 passes, 0 HARD every pass — the cleanest loop yet**; the pass-2/3 fix deltas were the copy-duplication + copy-paste-contingent-row extractions), resolved, and closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/150#issuecomment-5261170371. 2 commits `21b75a4`→`88f11ae` on `ralph/company-app-full-build` (unpushed; each pre-commit gate passed: ktlint/detekt/806 backend tests/cleanliness/shared-compile/Postgres). Map #89 updated (Decisions-so-far #150 entry + child-tickets row + frontier paragraph rewritten with the #150 outcome + "dashboard refresh affordance story COMPLETE").

**Next-session state:** **0 unblocked tickets** — unchanged. The frontend build chain advances: the HITL gates (#117 expense-GET question, the notifications→SessionDetail path), or another fog graduation.

## Session outcome

**#150 (Build — Desktop dashboard empty-state refresh affordance) — graduated + created + resolved + closed (AFK build).**

- **The #147 accepted-SOFT line is CLOSED.** Code-verified at graduation: the ERRORED half of "desktop empty/ERRORED lacks a refresh button" was already covered by its Retry card (InPlaceCard → `viewModel::refresh`) — the empty state was the single gap (a genuinely empty branch had no manual recovery; only the 30s auto-poll or nav away/back).
- **Shipped**: `internal expect fun DashboardEmptyState` (the #95 SessionList platform-split precedent); `EmptyStateContent` — the centered empty-state text hoisted into commonMain for both actuals (ClientNameText precedent — one copy, no drift); shared `onManualRefresh` closure (explicit `() -> Unit` coerces `refresh()`'s Job away) used by BOTH the table's Refresh row and the empty-state button; desktopMain actual = `DashboardRefreshRow` + `EmptyStateContent` where `DashboardRefreshRow` (internal, SessionList.desktop.kt) is shared with `TableHeaderRow` — **the Refresh affordance sits in the same top-right position in the empty and list states by construction (single copy)**; androidMain actual = pass-through to `EmptyStateContent` — behavior byte-identical to the pre-split `EmptySessions` (P3-verified vs `f7001cd`), `onRefresh` contract-required + intentionally unused (documented). **No VM changes** (refresh/thresholds/keep-last paths pre-tested); **no tests added** (no composable test infra exists in the repo — grep-verified; the change is wiring to an already-tested `refresh()`). Fire-and-forget (no spinner — same as the table's row).
- **Phased loop: 3 passes — 0 HARD every pass; exit at pass 3.** Pass 1: 0 HARD all four phases; 3 distinct accepted SOFTs (iOS actual absent — pre-existing pattern across every UI expect, tracked on map #89; android `onRefresh` unused — expect/actual contract parity; click-during-Loading silently absorbed — fire-and-forget spec) + the P2+P4-INDEPENDENT copy-duplication finding (text block byte-identical across both actuals) → fixed by hoisting `EmptyStateContent`. Pass 2 (delta 21b75a4): 0 HARD; P2 flagged the refresh row now copy-paste-contingent with `TableHeaderRow` (the position-stability claim's drift risk) + the expect KDoc's "empty/ERRORED" phrasing → fixed by extracting `DashboardRefreshRow` + KDoc scope clarification ("ERRORED half was already covered by its Retry card"). Pass 3 (delta 88f11ae): **0 HARD + 1 ESCALATE, adjudicated REJECTED with proof** — P2 re-rated the no-iOS-actual SOFT as ESCALATE (claimed build-break vs the documented pre-push iOS compile); proof of inertness: `.githooks/pre-push:23-30` compiles **desktop + android only** (iOS deliberately excluded — comment cites docs/specs/0001-frontend-rebuild.md:171 + the missing AppNavHost.ios.kt actual), so no break is reachable; the missing-actual pattern is pre-existing across every UI expect. Exit.
- **Accepted SOFTs (re-rated each pass, none upgraded)**: iOS actual absent (pre-existing, tracked on map #89); android `onRefresh` unused (contract parity); click-during-Loading absorbed (fire-and-forget, matches the table row); desktop empty text centers below the Refresh row, not screen-center (inherent to the header-row design — the ticket's position-stability intent); branchId==null stuck-latch (pre-existing unreachable guard, invisible on desktop).
- **Pre-existing observation (out of delta, recorded)**: **root AGENTS.md stale line** — "pre-push runs … composeApp multi-target compilation (desktop + Android + iOS)" is FALSE as shipped; `.githooks/pre-push:23-30` compiles desktop+android only (iOS excluded by design, documented in the hook). Truth-class record, consequence-free (no gate trusts the sentence), **doc-fix candidate for a docs session**.

## Patterns + learnings (cumulative across sessions)

- **Session-47 additions**:
  - **A tiny composable-only ticket converges in 3 passes with 0 HARD — and the fix deltas were still both reviewer-driven**: the first (P2+P4 independently) caught byte-identical text duplicated across platform actuals — the hoist; the second (P2) caught the NEW copy-paste-contingent row the first fix's own pattern implied — the shared-composable extraction. The lesson: on platform-split UI, hoisting one duplicate surfaces the next; keep hunting until single-sourced.
  - **The ESCALATE-adjudication record**: pass-3's ESCALATE (no-iOS-actual = build-break) was rejected with `.githooks/pre-push:23-30` proof — the hook's own comment documents the deliberate exclusion. When a phase claims a gate runs something, READ the hook file, not the AGENTS.md description (which was stale — see the recorded observation).
- Prior-session patterns unchanged: AFK flow = quality gate, phased loop per `docs/agents/code-review-loop.md`, resolution comment → close → map Decisions-so-far + frontier paragraph + child-ticket row + handoff. No k6 (frontend-only; backend untouched).
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (missing actuals incl. `SessionList`; pre-push gate excludes iOS intentionally — `.githooks/pre-push:23-30`).
- **`testDebugUnitTest` has a pre-existing 1-failure flake** (SessionBootstrapViewModelTest teardown) — not gated, not mine, recorded.
- **Flyway migration numbering**: V1–V18 taken; next is **V19**. No migration in this session.
- **Test-DB pollution on partial `--tests` runs**: `bash scripts/clean-test-db.sh` before AND after partial backend runs. (Unchanged.)
- **The root-AGENTS.md pre-push description is stale** (says iOS compiled; the hook doesn't) — recorded in the #150 resolution; a docs session should fix the one line.

## Current frontier (verified live post-session)

Per `gh issue list --state open`: only #89 (map), #110 (standalone hardcoded-month test fix, NOT a child), #139 (standalone OpenAPI map) open. **0 open children of #89** — #150 created+closed this session. The #147 accepted-SOFT refresh line is RESOLVED; the HITL gates (notifications→SessionDetail path; the #117 expense-GET question gating the merged Finance & Reports build) + the standing fog lines stay open; the k6-for-the-PATCH-endpoints note is recorded against the #106 grant-UI fog.

## Recommended next picks

- **The notifications→SessionDetail data path** — the #147/#149 SessionDetail renders a limited state when navigated from Notifications (row=null). Needs its own grilling: a session-detail GET endpoint + its read gate (attendance-scoped like the dashboard? capability-scoped? the #138 read-gate rule) — a genuine HITL decision, then a small backend + screen build.
- **Merged Finance & Reports build** (from #101 + #105) — still gated on the #117 expense-GET-includes-soft-deleted question (HITL UX decision; ungraduated).
- **Remaining Not-yet-specified candidates** — unchanged from the #149 handoff's predecessors (user-create flow, non-admin self-slot-edit, F7, desktop token-storage, pushed-route topbar, audit branch-name, detekt gate strategy, #110, route-path constants, Clients search-field pop-back reset, keep-last-results port, hardcoded-month test dates, the #106 grant-UI fog — which unblocks the deferred PATCH-k6). **New this session**: the root-AGENTS.md stale pre-push-iOS line (one-line doc fix, docs session).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-48 has NO unblocked ticket — pick from "Recommended next picks". The notifications→SessionDetail grilling is the chartable HITL move (and the #150 handoff's top pick); the #117 gate is the other HITL option; no AFK-sized build remains chartable (the refresh affordance was the last small one — the next builds are the merged Finance & Reports build and the notifications path, both HITL-gated).
3. Claim BEFORE work: `gh issue edit <n> --add-assignee @me` — verify no concurrent sessions.
4. AFK flow: red-first falsification, /implement per module AGENTS.md, the phased loop with `git diff <last-pass-commit>` per pass, batch-fix commits, exit at one full pass with zero HARD. The register now has SIX classes with six occurrence bumps — check the count-0 class on every swallowed-write/version path, and the truth-class on every doc line a phase cites (read the hook file when a gate claim is disputed — the #150 ESCALATE).
5. Post the answer as a **resolution comment**, then `gh issue close <n>`, then append a context pointer to map #89's Decisions-so-far (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND the child-tickets table row).
6. Graduate fog (create-then-wire): `gh issue create --label wayfinder:task` → child via `gh issue edit <n> --parent 89` (verify via the child's `parent` field — `gh issue view <n> --json parent`).
7. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: #150 row added (✅ closed).
- Decisions-so-far: new #150 entry (full shipped-scope summary + the 3-pass/0-HARD loop + the ESCALATE-rejection record + resolution link), inserted before Not-yet-specified.
- Frontier paragraph: rewritten — #150 outcome first (SEVENTH fog-graduation, 3 passes 0 HARD, refresh-affordance story COMPLETE across all states), 0 unblocked held, what remains chartable listed (the HITL gates, the k6 note tied to the #106 grant-UI fog, the recorded stale-AGENTS.md line).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket — 0 unblocked).
- **`/grilling` + `/domain-modeling`** — for the notifications→SessionDetail gate decision (HITL) or the #117 gate.
- **`docs/agents/code-review-loop.md`** — the register now carries six classes; the #150 resolution is the reference for a 3-pass-zero-HARD small-UI convergence + the ESCALATE-adjudication shape (hook-file proof) + the hoist-one-duplicate-surfaces-the-next lesson.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
