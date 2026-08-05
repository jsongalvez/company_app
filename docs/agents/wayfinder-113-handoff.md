# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 16

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 16 resolved the AFK ticket **#113** ("Build — Clients screen (graduated from #99)") — `wayfinder:task`, first AFK build since #112. Full /implement flow: locked #99 D1–D10 brief → build (13 files, +2151) → quality gate → parallel /code-review (Standards + Spec) → fixes → commit `d00b1ce` on `ralph/company-app-full-build` (**not pushed** — following the #111/#112 precedent; no PR from this branch). Resolution comment posted (https://github.com/jsongalvez/company_app/issues/113#issuecomment-5193287495), ticket closed, map #89 updated (frontier table, Decisions-so-far, 1 fog addition, closing paragraph). **Also fixed a wiring gap: #113 was missing from the map's sub-issues (created without the link); re-linked via the sub-issues API.**

**Next session pick:** one of the **6 remaining HITL prototypes** (#100 Inventory, #101 Finance, #103 Remittance, #104 Audit Log, #105 Reports, #106 User Management) — the map has **no AFK ticket left**.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #113 row, Not-yet-specified incl. 1 new fog entry, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- Frontend conventions: `composeApp/AGENTS.md` (ApiCallHandler, ViewModel patterns, logging)
- **#113 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/113#issuecomment-5193287495**
- Build commit: `d00b1ce` on `ralph/company-app-full-build` (local; not pushed)
- Locked #99 decisions + outline: `docs/prototypes/0099-clients-outline.md` (branch `prototype/0099-clients`)
- Prior handoff: `docs/agents/wayfinder-99-handoff.md`

## Session outcome

**#113 (Clients screen build) — resolved + closed.** Shipped D1–D10: search (D2 debounce/guard/keep-last/X-clear/states), desktop dense table + mobile cards (D3, re-scope per #95 — `expect fun ClientResultList`), detail with pessimistic inline per-field edit (D4), destructive anonymize confirm (D5), no create (D6), code-only capability route gate (D7/D8), stale search accepted (D9), anonymized husk guard (D10). `Route.ClientDetail` added, pushed on **both** platforms. 15 new VM tests; 46 desktop + 34 android green; ktlint clean; pre-commit hook passed.

## Key decisions (recorded in full on the resolution comment)

1. **D2 current-query guard = structured cancellation** — `ApiCallHandler.launch` gained a `scope: CoroutineScope = this.scope` override + `CancellationException` rethrow (before the generic catch). The debounced search launches the request as a **child of the debounce Job**, so a newer keystroke (or X-clear) cancels the in-flight request — stale commits + post-clear resurrection structurally impossible. **The initial version cancelled only the debounce, not the request — parallel /code-review (both axes) caught it; fixed before commit.** Test proves it with a virtual-time-suspended mock handler.
2. **BP = one pair editor, not two fields** — per-field BP commits deadlocked null-BP clients (both-or-neither + null=no-op PATCH = can never add BP). `BpPairEditor` commits both values in one PATCH; blur commits only once both sides are filled.
3. **Blank commits rejected inline** ("Value required" / "Name can't be blank") — clearing inexpressible in the API (null = no-op); honest message beats silent no-op.
4. **Back = content-level "Back" TextButton** (legacy precedent) — #96 Q5's nested-Scaffold back-chevron remains unimplemented → **fog added** (shell hamburger renders above pushed routes).
5. **D1 notice via new `ClientState` singleton** (`state/ClientState.kt`, NotificationState precedent) — detail entry's VM ≠ search entry's VM; the "Client anonymized" snackbar crosses the boundary; `clear()` wired at both App.kt session-end sites.
6. **Dialog confirm label = "Confirm"** per locked copy (review caught "Anonymize"); anonymize failure surfaced inline under the button (was invisible).
7. **Route gate** = `CapabilityCodes.EDIT_BRANCH_DATA in SessionState.capabilities` at both NavHost actuals → `RouteGateCard` (small, commonMain). #92's `UiState.Unauthorized` card still unimplemented — Error-card fallback (consistent with D8's error-card axis).
8. Accepted judgement calls (documented on the resolution comment, NOT fixed): duplicated host gate blocks (Notifications precedent), `viewModel` threaded into `ClientDetailContent`, composition-write cache (`cachedResults` — legacy ClientSearchScreen precedent), Esc-cancels-edit, detekt delta 53→67 weighted (baseline pre-failing, ungated per fog; `PencilIcon` geometry suppressed).

## Patterns + learnings (cumulative across sessions)

- **AFK build flow now proven twice (#112, #113)**: entry-scoped `viewModel()` per-route; `handler.launch` for all calls; parallel /code-review at the end catches real spec holes (this session: guard semantics + BP pair deadlock + locked-copy label). **Both reviews earned their keep this time.**
- **The /code-review Spec axis catches comment-vs-code overclaims** — my onQueryChange comment asserted a cancellation guarantee the code didn't provide; the reviewer verified the mechanism, not the comment. When claiming a correctness property in a comment, make the test prove the property (the suspended-handler test now does).
- **Ticket-body premises remain unreliable** — #113's brief was accurate (graduated post-falsification), but the #96 Q5 "nested-Scaffold back-chevron" comment in the NavHost actuals had zero code behind it. Same lesson as #99's stale premises: verify locks against code before building on them.
- **Untracked-files gotcha for /code-review**: `git diff <fixed>` doesn't include new files — the review sub-agents must be handed the untracked file list explicitly.
- Prior-session patterns unchanged: falsification-before-claim (HITL prototypes), handler-based MockEngine + Unconfined + `runTest(testScheduler)` drain + `vm.dispose()` (#93), pessimistic updates (ADR-0022), backend-authoritative (ADR-0007), kotlinx-datetime 0.7.1 pinned, `LocalNavHostController` pattern, Canvas-drawn icons (no material-icons dep).
- **5xx mock responses hang under `runCurrent()`-only** — the client's `HttpRequestRetry` (retryOnServerErrors) suspends in backoff on virtual time; failure tests must use 4xx statuses (or advance past the backoff). Noted in the test file.

## Current frontier (verified live post-session)

**6 unblocked HITL prototypes, zero AFK** (per `gh api repos/jsongalvez/company_app/issues/89/sub_issues`):

| # | Title | Type | Suggested session shape |
|---|-------|------|------------------------|
| 100 | Prototype the Inventory screen UX (stock levels, product sales, drill-down) | prototype | HITL grilling |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked. Zero assigned.** (#110 "Fix hardcoded-month dates in MonthlyRemittanceSummaryServicePostgresTest" remains open + unassigned but NOT a child of map #89 — standalone.)

## Recommended next pick

- **#100 (Inventory)** — first in frontier order, same grilling pattern as #99: falsification-before-claim (does a stock-levels endpoint exist at all? backend gap like #98 was?), facts-vs-taste split, option-framing with named costs. **Note per screen: branch-scoped per-element gates (if any) are structurally impossible until the SessionState context-model fog resolves — surface the code-only reality (D7 pattern).**
- **Fog-graduation option (charting session, no ticket needed):** (a) **SessionState context-model divergence** — now the biggest cross-cutting item, sharp and waiting: "implement #92's context model vs keep code-only + backend-403-enforcement"; (b) orphan-code-cleanup (HomeScreen/ClientSearchScreen/SessionCreateScreen) — ready to absorb into a #94-grad Build or dedicated refactor; (c) detekt-gate strategy fork.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes (pattern #96/#97/#99/#102/#103). Falsification-before-claim per screen: check the live endpoint payload + DTO before grilling any layout question.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
4. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by`; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>` — **verify new tickets appear in the sub-issues query; #113 was created without the link**).
5. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling.

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #113 → ✅ closed; 7 open → 6 (all HITL prototypes; **no AFK ticket remains**).
- Decisions-so-far: new entry for #113 (D1–D10 shipped, build-time decisions, review catches, test counts; cites resolution comment URL + commit).
- Not-yet-specified: **1 new fog entry** — pushed-route TopAppBar pattern unimplemented (#96 Q5's nested-Scaffold chevron has no code; ClientDetail uses content-level Back; shell hamburger renders above pushed routes; SessionDetail's build faces it again).
- Closing paragraph rewritten: 6 HITL prototypes; #113 gist; fog addition listed; JMH-noise warning carried forward.
- Prior fog preserved: SessionState context-model divergence, no client-create UI, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, pre-BranchSelect drawer empty-state, LocalNavHostController pattern, shell-scoped poller pattern, NotificationState.clear() maintenance point, detekt-gate strategy.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #100–#106 (feature screen prototypes; falsification-first per screen).
- **`/research`** — for any Ktor / Compose / Material3 API surface the prototype needs to verify (sources-jar unzip pattern from session-10).
- **`/code-review`** — at the end of any future /implement flow (parallel Standards + Spec; hand untracked files to the sub-agents explicitly).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
