# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 39

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 39 resolved the **Tier-1 pilot audit** — the decision in flight from session 38's tail (user picked "Pilot audit now"; the surface was chosen THIS session via the question tool: **Notifications chain** over Clients/User Management). The audit ticket **#141** ("Audit — Notifications chain (phased loop)") — `wayfinder:task`, AFK — was created, wired as a sub-issue of #89, claimed, run through the phased code-review loop (9 passes, 13 HARD findings, all fixed), resolved, and closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/141#issuecomment-5248103316. 9 fix commits `0e952de`→`4bb0ec4` on `ralph/company-app-full-build` (unpushed; each pre-commit-gate passed: ktlint/detekt/backend-tests/cleanliness/shared-compile/Postgres). Map #89 updated (Decisions-so-far #141 entry + frontier paragraph).

**Next-session state:** **0 unblocked tickets** — unchanged. The audit stream's remaining Tier 1 surfaces (Clients screens, User Management, Audit Log, backend grants #132–#134, day-gates #136–#138) are listed in the map's frontier paragraph, **pending the user's go-ahead** — each graduates as `wayfinder:task` "Audit — <surface> (phased loop)" children of #89.

## Session outcome

**#141 (Audit — Notifications chain (phased loop)) — created + resolved + closed (AFK).**

- **Created the ticket** (surface picked by the user: Notifications chain — the handoff's lean), wired sub-issue of #89, claimed, then the usual AFK flow.
- **The audit found the predicted bug reservoir** — 13 HARD findings over 9 loop passes, fixed in 9 batch commits:
  - **Ownership/mutation (3)**: (a) `NotificationService.markRead` mutated ANOTHER user's row before the ownership check — the update committed, then 404'd (three phases independently flagged it; the pre-fix test asserted only the 404, never the unmutated row). Fixed: ownership in the repository WHERE clause `(id AND user_id)`, `updated == 0 → null → 404`, foreign-row-untouched assert added. (b)+(c) the stale pre-action GET landing class: markAll-mid-reload → rows + Mark-all resurrect under badge 0, **persistent** (the poll re-syncs only the count, never the list; no in-screen polling per D5); markRead-mid-reload → re-tap double-decrement + Read duplicates (#112 decision-4's named race). Fixed: `actionStamp` (bumped on every successful action; loads capture it; a mismatched landing substitutes the post-action list — `currentUnreadList() ?: emptyList()`, fail-toward-invariant — and re-issues so arrivals surface) + Read-section dedupe (a row already in `readThisSession` can never move/decrement again).
  - **Comment-truth (3)** — the recurring P2/P4 class: the D5/Q5 silent-refresh comment inverted the axis (the axis MANDATES rendering last-successful; the fix ended up as the VM-side `lastUnread` keep-last-results cache — ClientsScreen #113 D2 precedent, hoisted from the screen because a `remember` dies on composition re-entry); the "app-wide shape, no screen caches" claim was false (ClientsScreen has the cache — the acceptance was load-bearing); the Main-FIFO guarantee was false (scheduling-queued collector can lag the landing — fixed by reading the current Success synchronously).
  - **Silent-failure (2)**: markRead/markAll errors were invisible (`markReadResult` was dead state, never collected — the #135 round-1 bug class); fixed with inline action-error lines + logWarn. Actions during Loading/Error no-oped against a rendered list (transforms' guards were Success-only; markAll from Error-with-cache persisted rows with badge 0) — fixed by the lastUnread fallback.
  - **Stale-state (2)**: re-entry reload flashed the cold spinner over prior data; the resurrect frame persisted for a full RTT after the re-issue (the handler commits `Success(stale)` after the transform returns) — fixed by the substitution + the re-issue.
  - **Stuck-Loading (1)**: the 404-onNonSuccess path left `markReadResult` Loading forever — reset to Idle.
  - **MagicNumber (1)**: bare `status.value == 404` → `HttpStatusCode.NotFound` (sibling pattern).
  - **Test-harness hang (1)**: an uncancelled poll loop spins runTest's final drain (TestBuilders.kt:402 runs `advanceUntilIdle` OUTSIDE the timeout) — try/finally-dispose invariant, class-documented. This hung three runs before the thread dump revealed it.
- **Shipped**: markRead ownership in the WHERE clause; inline action-error lines; 404 = absent/foreign defense (Idle + reload — the backend 200s an already-read OWN row, so 404 is unreachable today; pure defense for a future deletion/expiry surface); VM-side `lastUnread` (collector-mirrored, entry-scoped survival, single writer); `actionStamp` + substitution; Read dedupe; masked UUID logs (listUnread fires every 60s per user); badge-poll in-flight guard; `loadUnreadNotifications`/`markRead`/`markAllRead` all return Job (join idiom); HOLD_MS const.
- **Harness lessons (new, documented in the test KDocs)**: (a) mock-handler delays run on the engine's own context = REAL time; the poll LOOP's delay is virtual (viewModelScope) — to hold a request virtually, wrap the handler delay in `withContext(StandardTestDispatcher(testScheduler))`; (b) 2xx mock responses drain inline under `runCurrent`, non-2xx complete the launch's continuation on a real thread (Ktor response-pipeline dispatch — the retry plugin's exponential delays for 5xx) — `job.join()` after `runCurrent` for action-state asserts; (c) the 401 test needed `secondGetStatus = Unauthorized` (the bearer-auth plugin re-issues once on 401); the 500-reload test needed a non-retried status (403) for a deterministic Error; (d) `HttpRequestRetry maxRetries=3` retries 5xx app-wide — safe for the chain (mutations idempotent), worth remembering for future chains.
- **Tests**: desktop suite 141 → **146** (+5: 404-defense, markRead-during-reload [extended to land the stale GET + assert no-resurrect + convergence], markAll-mid-reload, markAll-from-Error, badge slow-poll guard). Backend +1 assert (foreign-row untouched). All gates green.
- **Accepted residuals (documented in code)**: re-entry load-load race (no in-flight guard on the list VM — same-stamp GETs can land out of order); badge poll fetches the full unread body per 60s (no count endpoint); empty-list reload flash (D5 nothing-to-show clause); session-end clear vs in-flight poll write (bounded, invisible); pre-markAll poll bounce (ADR-0022 axis); stale inline error persists across push/pop (truthful, self-clears); the 404-stamp substitution untested (defense path).
- **Finding rate (the calibration)**: 13 HARD + ~25 fixed SOFTs on ~30 files; 7 of 13 HARDs were the stale-state/interaction class the pre-loop lens missed. **The pilot validated the audit premise** — recommend proceeding with the rest of Tier 1.
- **App-wide follow-up surfaced (not this ticket's scope)**: keep-last-results now exists ONLY in Notifications + Clients — Audit Log, User Management, Remittance still flash on reload; candidates for the Tier-1 audits.

## Patterns + learnings (cumulative across sessions)

- **Session-39 additions**:
  - **The loop converges HARD-count to zero pass by pass** (3 → 1 → 2 → 2 → 2 → 3 → 1 → 1 → 0 → 0): each pass's fixes are the next pass's delta; the "same-lens twice" failure mode is gone — P3/P4 caught the classes P1/P2 missed and vice versa.
  - **Comment-truth is a real finding class**: claims vs code (axis inversions, precedent falsehoods, FIFO overclaims) recurred every pass — verify every comment against the code it describes.
  - **"Accepted" is never load-bearing**: the pass-5 "grace-window" acceptance of the stale-overwrite became pass-6's 3 HARDs; the pass-7 acceptance of the resurrect frame became pass-8's HARD. Re-examine accepted SOFTs every pass with a fresh angle.
  - **The stale-GET-resurrection class**: any snapshot fetched BEFORE an action commits must be neutralized (stamp + substitute + re-issue). Generalizes to any list-with-actions surface.
  - **Test-harness hazards**: uncancelled poll loops hang runTest's final drain (try/finally); mock-handler delays are real-time (virtualize via withContext); non-2xx completes off-scheduler (join); auth-plugin 401 re-issue + retry-plugin 5xx retries break naive status mocks.
  - **runTest's final drain is outside the timeout** — the infinite-spin class; the only cure is no live loop at drain time.
- Prior-session patterns unchanged: AFK flow = red-first falsification (N/A here — audit, not build), quality gate, phased loop per `docs/agents/code-review-loop.md` (this session USED it end-to-end for the first time), k6 (N/A — no endpoint changes), resolution comment → close → map Decisions-so-far + frontier paragraph + handoff.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V18 taken; next is **V19** (`ls backend/src/main/resources/db/migration/` before picking).
- **Test-DB pollution on partial `--tests` runs**: `bash scripts/clean-test-db.sh` before AND after partial runs.

## Current frontier (verified live post-session)

Per `gh issue list --state open`: only #89 (map), #110 (standalone hardcoded-month test fix, NOT a child), #139 (standalone OpenAPI map) open. **0 open children of #89** — #141 created+closed this session.

## Recommended next picks

- **The rest of Tier 1 (the audit stream)** — the user's go-ahead is pending; the frontier paragraph lists the surfaces: Clients screens, User Management, Audit Log, backend grants #132–#134, day-gates #136–#138. **Recommendation: Clients screens next** (the interaction-heavy surface closest to the Notifications chain's proven bug reservoir — search/debounce/detail/anonymize matrix; also the screen with the existing keep-last-results cache, so the audit can check that precedent against the loop). User Management is the freshest surface (already loop-reviewed in #135 — lower calibration value). Graduate as `wayfinder:task` "Audit — <surface> (phased loop)" children of #89, P1 spec = the ORIGINAL tickets' resolutions (e.g. #99/#113/#114 for Clients), run the loop, fix HARDs in-ticket, record per-surface verdict + finding rate.
- **#97-grad Build — Dashboard** (prototype exists: `prototype/0097-session-dashboard`) — the natural next frontend build; the clock-out → dashboard-state-clear transition fog (#97 outline) is a decision inside it; the shell-scoped poller + `NotificationState.clear()` maintenance notes apply to its session-end paths. Note: the #141 audit hardened the notification chain this session-end paths touch — the Dashboard build should read `NotificationBadgeHost` + the App.kt clear paths as its constraint sources.
- **Merged Finance & Reports build** (from #101 + #105) — still gated on the #117 expense-GET-includes-soft-deleted question (HITL UX decision; the map is now 1/1 HITL-pending).
- **Relief-request dialog on BranchSelect** — #91's one-liner, needs its own grilling.
- **Remaining Not-yet-specified candidates** — unchanged from the #140 handoff (user-create flow, non-admin self-slot-edit, F7, desktop token-storage, pushed-route topbar, audit branch-name, detekt gate strategy, #110, route-path constants).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-40 has NO unblocked ticket — pick from "Recommended next picks". If continuing the audit stream: confirm the surface with the user (Clients recommended), graduate "Audit — Clients screens (phased loop)" as `wayfinder:task` child of #89, claim, run the loop per `docs/agents/code-review-loop.md` (P1 spec = the #99/#113/#114 resolutions; the #141 resolution's finding-rate + residual list is the calibration reference).
3. Claim BEFORE work: `gh issue edit <N> --add-assignee @me` — verify no concurrent sessions.
4. AFK flow: red-first falsification, /implement per module AGENTS.md, the phased loop with `git diff <last-pass-commit>` per pass (first pass = `git diff <pre-ticket-commit>`; hand untracked files explicitly), batch-fix commits, exit at one full pass with zero HARD.
5. Post the answer as a **resolution comment** (per-pass record + per-surface verdict + finding rate), then `gh issue close <N>`, then append a context pointer to map #89's Decisions-so-far (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND clear graduated fog lines).
6. Graduate fog (create-then-wire): `gh issue create --label wayfinder:task` → sub-issue via `gh issue edit <n> --parent 89` (verify via the child's `parent` field via GraphQL — the REST read can be stale; the POST dedup guard errors "may only have one parent" when the link already landed).
7. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #141 entry (full shipped-scope summary + the 13-HARD class breakdown + Tier-1 calibration + resolution link).
- Not-yet-specified: unchanged (the #102 read-history fog line stays — the audit re-confirmed it as the 404-defense rationale).
- Frontier paragraph: rewritten with the #141 audit outcome first (created+closed, 9 loop passes, 13 HARDs, finding rate), the remaining Tier 1 surfaces listed pending the user's go-ahead, rest reworded "closed previous session", 0 unblocked held.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket — 0 unblocked; the audit stream's next surface is the pending pick).
- **`docs/agents/code-review-loop.md`** — the phased loop is now proven end-to-end (9 passes, converged); read the phase templates before spawning sub-agents; the #141 resolution is the reference example of the per-pass record format.
- **`/implement`** — for either the Dashboard build or the next audit (audits fix HARDs in-ticket, so /implement applies to the fix batch).
- **`/grilling` + `/domain-modeling`** — if the #97-grad Dashboard build graduates: the clock-out question is a decision (HITL-ish); same for the merged Finance build's #117 gate.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
