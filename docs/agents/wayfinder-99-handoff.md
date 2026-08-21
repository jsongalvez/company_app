# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 15

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 15 resolved frontier ticket **#99** ("Prototype the Clients screen UX (search, detail, anonymization)") — a HITL `wayfinder:prototype`, driven with grilling (same pattern as #96/#97/#102). Prototype asset committed (`074200b` on `prototype/0099-clients`, doc-only, pushed `--no-verify`) + resolution comment posted + ticket closed + map #89 updated (frontier table + Decisions-so-far entry + 3 new fog entries + closing paragraph). **Graduated build ticket #113** ("Build — Clients screen (graduated from #99)") — `wayfinder:task`, **unblocked** (all endpoints exist, no backend work). **The map now has an AFK pick again: #113 — the first since #112.**

**Next session pick:** either **#113 (AFK build)** — the /implement flow per pattern #109/#112 with the locked #99 D1–D10 — or one of the **6 remaining HITL prototypes** (#100 Inventory, #101 Finance, #103 Remittance, #104 Audit Log, #105 Reports, #106 User Management).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #99 row + #113 row, Not-yet-specified incl. 3 new fog entries, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- Frontend conventions: `composeApp/AGENTS.md` (ApiCallHandler, ViewModel patterns, logging)
- **#99 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/99#issuecomment-5192891683**
- Prototype asset: `docs/prototypes/0099-clients-outline.md` on branch `prototype/0099-clients` (pushed)
- Graduated build ticket: https://github.com/jsongalvez/company_app/issues/113 (body carries the full locked-decision brief — D1–D10 + backend facts + conventions)
- Prior handoff: `docs/agents/wayfinder-112-handoff.md`

## Session outcome

**#99 (Clients screen prototype) — resolved + closed.** HITL grilling: falsification-before-claim (backend payload reality for all four ticket questions), 9 decision questions (Q1–Q9), D1–D10 locked. Falsification findings corrected the ticket's premises: search param is `q` (not `search`), limit 20 no pagination; anonymized clients invisible to search (soft delete); no `anonymized` flag in `ClientResponse`; backend gates on **GLOBAL**-context `EDIT_BRANCH_DATA` (not BRANCH); `SessionState.capabilities` implemented as code-only `Set<String>` — the #92 context model is **unimplemented** (F7, cross-cutting divergence → fog).

## Key decisions (recorded in full on the resolution comment + outline)

1. **D1 Post-anonymize → back-to-search + confirmation** — record gone from active data (soft-deleted), screen reflects active reality. 204 no-body response.
2. **D2 Search box:** 300ms debounce, fire ≥2 chars, keep-last-results while typing with current-query guard (out-of-order protection), X-clear, distinct empty ("Search clients by name or phone") vs no-results ("No clients found for 'query'") states. Backend is fuzzy (trigrams) — no spelling correction UI needed.
3. **D3 Re-scope per #95 — desktop dense table + mobile cards** — deliberate opt-in of the dashboard-only responsive pattern: search results are the scan persona. Table columns name/phone/gender/age; mobile cards name-primary/phone-secondary. Only the result list diverges (smallest-divergent-subtree, `expect` list per SessionDashboardScreen shape); shared chrome common.
4. **D4 Detail: desktop two-column (Identity | Contact+Health), mobile single column; inline per-field edit** — pencil → edit in place → commit Enter/blur/select, **pessimistic** (ADR-0022/#97 Q4 axis: failure keeps value + inline error + stays in edit mode; 403 silent exit; 409 reload + changed-fields indication), **partial PATCH**, BP pair inline validation. No form-mode state machine.
5. **D5 Anonymize = destructive confirm dialog** — client name for verification, "cannot be undone" copy, gender/age-retained note, Confirm/Cancel, no typing-to-confirm. Button bottom action row (mobile) / right column (desktop).
6. **D6 Create out of scope** — natural home is SessionCreate rebuild (orphan-code-cleanup fog). No-create-UI gap recorded as fog.
7. **D7 Capability gate = code-only** `"EDIT_BRANCH_DATA" in SessionState.capabilities` — matches the implemented `Set<String>`; backend GLOBAL gate + #92 403-refresh-Unauthorized backstop (ADR-0007). The GLOBAL-vs-BRANCH context question is deferred to the fog'd SessionState context-model decision.
8. **D8 No in-screen disabled state** — drawer hidden (US-28), route gate 403-card, `UiState.Unauthorized` card absorbs both gated actions (edit/anonymize). #92's disabled-not-hidden layer never renders on this screen.
9. **D9 Stale search list accepted** — no auto-refresh on return; re-search to refresh.
10. **D10 Build-time guard** — null-name detail = anonymized husk (gender+age only, no edit/anonymize affordances, no PATCH surface).

## Graduated tickets

- **#113 — Build — Clients screen (graduated from #99)** — `wayfinder:task`, **unblocked**. Body carries the full brief: D1–D10, backend facts, conventions (entry-scoped `viewModel()` per #112 pattern, ApiCallHandler, desktop-table/mobile-cards split, #93 test patterns). First AFK ticket since #112.

## Patterns + learnings (cumulative across sessions 7-15)

- **Falsification-before-claim keeps paying off** — every #99 ticket question was premised on wrong backend assumptions (search param, anonymized visibility, capability context). The #102/#111/#112 flow held: check the live endpoint payload + DTO before grilling any layout question.
- **Ticket bodies can carry stale premises from earlier locks** — #99's body cited "EDIT_BRANCH_DATA on BRANCH context (per #92)"; backend reality is GLOBAL. When a ticket's Context section contradicts the live backend, the falsification finding wins and the user decides the resolution path.
- **#92's context model is unimplemented** — `SessionState.capabilities` is `Set<String>`; the locked `List<UserCapabilityResponse>` + `hasCapability(code, contextType, contextId)` never landed. Any future screen needing branch-scoped or day-state-aware per-element gates (Inventory, Sessions, Finance, Remittance) must either work code-only + backend-403-enforcement or drive the context-model decision first. Fog entry on map.
- **#95's re-scope mechanism works** — D3 is the first deliberate opt-in: "search results are the scan persona" = the justification; the mechanism (smallest-divergent-subtree, expect list) was ready. Future screens (#100–#106) that want desktop tables just need the same explicit re-scope decision during their prototype session.
- **JMH pre-push gate is noise-sensitive on a loaded machine** — load 17/8 cores (user program at 148% CPU) dropped SessionTypeBenchmark ~34% → baselines FAILED → push blocked on a doc-only commit. Pushed with `--no-verify` after user confirmation (doc-only, environmental). **Re-verify `bash scripts/check-baselines.sh` on an idle machine before the next benchmark-sensitive push.** Doc-only pushes may use `--no-verify` if the noise is confirmed environmental.
- Prior-session patterns unchanged: entry-scoped `viewModel()` per-route (#112), handler-based MockEngine + Unconfined + `runTest(testScheduler)` drain + `vm.dispose()` (#93), pessimistic updates (ADR-0022), backend-authoritative (ADR-0007), kotlinx-datetime 0.7.1 pinned, `LocalNavHostController` pattern.

## Current frontier (verified live post-session)

**6 unblocked HITL prototypes + 1 unblocked AFK build** (per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — #99 closed, #113 added):

| # | Title | Type | Suggested session shape |
|---|-------|------|------------------------|
| 113 | Build — Clients screen (graduated from #99) | task | **AFK /implement** (pattern #109/#112) |
| 100 | Prototype the Inventory screen UX (stock levels, product sales, drill-down) | prototype | HITL grilling |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked. One AFK ticket (#113).** (#110 "Fix hardcoded-month dates in MonthlyRemittanceSummaryServicePostgresTest" remains open + unassigned but NOT a child of map #89 — standalone.)

## Recommended next pick

- **#113 (Build — Clients screen)** — the AFK pick; map's first since #112. /implement flow: ticket body + outline (D1–D10) + live code + test patterns → implement → quality gate → /code-review (parallel) → commit. Expect the same build-time decisions as #112 (entry-scoped `viewModel()`, MockEngine handler routing, jsonRespond helper, 401-for-deterministic-failure). Note: `ClientViewModel` (legacy, `viewmodel/ClientViewModel.kt`) already has search/detail/update/anonymize state flows via ApiCallHandler — the rebuild may extend it rather than create fresh (decide during build; legacy `ClientSearchScreen` is orphan-code fog).
- **HITL prototypes #100–#106** — same grilling pattern as #99: falsification-before-claim per screen (Inventory: does a stock-levels endpoint exist at all, or is that a backend gap like #98 was? Finance: P&L endpoint reality? Remittance: drafts/submit payload shape? Audit Log: filter params + before/after diff fields? Reports: export endpoints? User Management: deactivate + slot-ordering endpoint reality?), facts-vs-taste split visible, option-framing with named costs. Note per-screen: **branch-scoped per-element gates (if any) are structurally impossible until the SessionState context-model fog resolves** — surface the code-only reality during each grilling (D7 pattern).
- **Fog-graduation option (charting session, no ticket needed):** (a) **SessionState context-model divergence** — now sharp: "implement #92's context model vs keep code-only + backend-403-enforcement" — cross-cutting, gates future per-element gates; (b) orphan-code-cleanup (HomeScreen/ClientSearchScreen/SessionCreateScreen) — still ready (absorb into #94-grad Build or dedicated refactor); (c) detekt-gate strategy fork — still cross-foggy.

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST, before any work. Verify no concurrent sessions (sub-issues assignees empty).
2. If #113 (AFK build): /implement flow — composeApp/AGENTS.md + ticket body + locked outline + live VM/state code; entry-scoped `viewModel()`; end with /code-review (parallel Standards + Spec). Pre-commit hook gates: ktlint scoped-to-staged + `:backend:detekt` + `:backend:test` + test-data cleanliness + `:shared:compileKotlinJvm` + Postgres connectivity.
3. If #100–#106 (prototype): /prototype + /grilling + /domain-modeling per map Notes (same pattern as #96/#97/#99/#102). Falsification-before-claim per screen.
4. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
5. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking edges with `gh api .../dependencies/blocked_by -F issue_id=<db-id>` — `gh api repos/<owner>/<repo>/issues/<n> --jq .id` for db-ids; clearing each graduated patch from **Not yet specified**).
6. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling. #99 didn't (traces through #95 mechanism + ADR-0022/0007 axes).

**One-ticket-per-session limit** (research is the only exception). When the chosen ticket is done, stop and hand off again. Write the next handoff doc to `docs/agents/wayfinder-<N>-handoff.md` where N is the resolved ticket number — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #99 → ✅ closed; **new #113 row (Build — Clients screen, task, 🔓 unblocked)**. 8 open → 7 (6 HITL prototypes + 1 AFK build).
- Decisions-so-far: new entry for #99 (D1–D10 + F1–F7 falsification findings; cites resolution comment URL + prototype branch).
- Not-yet-specified: **3 new fog entries** — SessionState capability context-model divergence (#92 lock vs `Set<String>` impl; cross-cutting, gates future per-element gates); no client-create UI anywhere (defers to SessionCreate rebuild); anonymize-with-PENDING-session backend gap (no guard; SessionDetail would show nameless client).
- Closing paragraph rewritten: 6 HITL prototypes + 1 AFK build (#113); #99 D1–D10 gist; fog additions listed; JMH noise warning (re-verify baselines on idle machine; doc-only pushes may --no-verify if environmental).
- Prior fog preserved: k6 deferrals, route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, pre-BranchSelect drawer empty-state, orphan-code-cleanup (sharp), LocalNavHostController pattern, shell-scoped poller pattern, NotificationState.clear() maintenance point, detekt-gate strategy.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for #113 (pattern #109/#112; entry-scoped `viewModel()`; /code-review always at the end).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #100–#106 (feature screen prototypes; falsification-first per screen).
- **`/code-review`** — at end of any /implement flow: parallel Standards + Spec subagents.
- **`/research`** — for any Ktor / Compose / Material3 API surface the build or prototype needs to verify (sources-jar unzip pattern from session-10).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md` for the next session to pick up.
