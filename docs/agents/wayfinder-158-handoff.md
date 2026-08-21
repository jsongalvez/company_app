# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 55

## What this is

A wayfinder build session on map **#89**. Session 55 picked the **Finance day-gate ride (#158)** — the #155 Q3=A order's final step, the handoff-recommended next (frontier was EMPTY; the ride needed its ticket created from the handoff shape). Grilled the read-side question live (Option A locked), built the frontend day-gates + read entry + backend read legs, phased loop 4 passes + P5, resolved + closed. **#158 = the ELEVENTH fog-graduation; the #155 Q3=A order is now COMPLETE end-to-end (grants written #157 → enforced #157 → frontend reachable + day-gated #158).** Resolution: https://github.com/jsongalvez/company_app/issues/158#issuecomment-5294462597. Map #89 updated (Decisions-so-far #158 entry + frontier paragraph rewritten).

**Next-session state:** **0 unblocked children of #89 — the frontier is EMPTY.** The #155 order is fully shipped; the remaining picks are the standing fog lines (relief invite flow #105/#106, keep-last port, DayStatus move, #92 Q4/Q5 machinery, the #158-graduated ARCH pair, k6 for branch-scoped writes, root-AGENTS.md stale pre-push-iOS line, #110, #139).

## Session outcome

**#158 (Build — Finance day-detail day-gates) — grilled + built + resolved + closed (HITL grilling → AFK build).** 6 commits `73302de`→`08f6906` on `ralph/company-app-full-build`, each pre-commit-gate green.

- **Grilled decisions (live, session 55)**: **Q1 = Option A (day-scoped read entry)** — single-day `GET /api/branches/{branchId}/daily-summary?date=` + `GET /api/branches/{branchId}/today` gain BRANCH_DAY legs; relief users land in a relief mode (date → single-day summary → day detail + editor); the multi-day browse stays VIEW_BRANCH_DATA-only; **Q2 = dashboard `_canEdit` deferred** (Finance day-detail consumers only per the #155 scope).
- **Backend (read legs)**: `CapabilityFilter.requireBranchOrGlobalOrBranchDayCapabilityForBranchId` (BRANCH/global `VIEW_BRANCH_DATA` OR BRANCH_DAY `EDIT_BRANCH_DATA`; null day → day leg impossible); daily-summary single-day resolves date→day **find-only** then gates; `/today` resolves today find-only → `requireBranchOrBranchDayCapability` (fallback: plain BRANCH gate — **GLOBAL never passes `/today`**, the #131 strictness; the summary read's global leg is the deliberate asymmetry, doc-pinned); `BranchDayService.findByBranchAndDate` (find-only). Suite 860 → **875** (+15 route-level tests: granted 200 / non-granted 403 / other-branch 403 / no-day-row fallback / wrong-day / expired / BRANCH + GLOBAL VIEW regressions / GLOBAL EDIT 403s both reads / real-JWT 401).
- **Frontend**: `hasCapabilityAtContextType` + `hasDayGrant` + `hasBranchOrDayCapability` matchers (SessionState.kt); `DrawerItem.dayGrantCode` (Finance visible for day-grant holders); both NavHost `Route.Finance` gates OR the day grant; `FinanceReportsViewModel.hasEditBranchDataCapability` day leg vs the VIEWED day + `hasEditCapabilities` routes EDIT_BRANCH_DATA through it (ASSIGN_COMPENSATION/EDIT_PAST_DAY stay BRANCH-only); DayEditor `canEditExpenses` day leg + nullable `onExportDayEditor` (relief mode hides exports — the backend export gate is VIEW-only); `loadReliefDay(date)` (generation-guarded, manual state writes via the `pageFetch` dummy) + `reliefDay` state + `clearReliefState()` (hybrid exit); `ReliefDaySection` (date field + parse guard + day detail + Edit toggle + DayEditor, no picker/feed/modes); **Relief-day chip for hybrid holders** (day grant + VIEW elsewhere — the #98 picker window is BRANCH-grant-only, so the relief branch is otherwise unreachable). Desktop suite 301 → **313** (matcher +4, drawer +2, VM +6).
- **Phased loop: 4 passes + P5; pass 4: 0 HARD all four phases → exit.** Pass 1: **2 HARD** — (a) P3/P4 **split-day mutation** (the #144 stale-state class, relief flavor): `loadReliefDay` kept the old day's armed edit sections/generation → day B header with day A's rows, multi-grant user could mutate A's records under B's UI — fixed with entry reset + generation guard (mirrors `refreshWindowAndFeed`/`loadSection`); (b) P2 **truth-class**: backend/AGENTS.md + ADR-0007 claimed `/today` "branch-gated — revisit when the ride lands" — the ride landed, docs amended. P4's hybrid-reachability SOFT **re-rated HARD by triage** (the ride's core promise fails for a real class — day grant + VIEW at another branch, picker can't reach the relief branch) → the Relief-day chip. Pass 2: **2 HARD** — (a) P2 truth-class: docs overstated the fallback — `/today` falls back to branch-only, GLOBAL never (only the summary read has the global leg); split + pinned with a GLOBAL-403 test; (b) P2/P4 hybrid surface corruption: `loadReliefDay` permanently overwrote the VM `_selectedBranchId` → post-exit the reports toolbar pinned the relief branch (blank picker name, wrong-branch edit gates/export URLs) — the write removed (the relief surface reads `SessionState.selectedBranchId`), chip/exit reset edit state + `clearReliefState`. Pass 3: **0 HARD** (3 cheap SOFTs fixed; the hybrid-clock-at-own-branch fail-open shape proven domain-contradictory). Pass 4: **0 HARD → exit** (shared `submitDate` lambda — Load + retry must not drift). **P5**: 2 ARCH fixed in-ticket (`hasBranchOrDayCapability` matcher unifies the day-gate OR — VM + DayEditor + the backend `hasCapabilityForBranchDay` is the third shape; the daySwitch test is now load-bearing — arms sections with a real BRANCH_DAY grant before asserting the clear); **2 graduated**: the relief-surface trichotomy (reliefOnly | hybrid | plain — three booleans at call sites, needs one derived state) + a **generation-aware `ApiCallHandler.launch` variant** (the third hand-rolled generation-guard pattern — `refreshWindowAndFeed`/`loadSection`/`loadReliefDay` all bypass the handler's single-state interface).
- **Accepted SOFTs (final, 3)**: selectedDay collapse on a no-load hybrid roundtrip (deliberate clean-state tradeoff, one tap to restore); FilterChip-as-nav-entry with `selected=false` (visual judgment, no DESIGN.md breach); hybrid fail-open shape (impossible-by-construction — a grant references an existing day row, test-pinned).
- No migrations, no shared/DTO changes. k6 deferred (#149/#152 reason — no HTTP channel seeds BRANCH_DAY grants).

## Patterns + learnings (cumulative)

- **Session-55 additions**:
  - **A day-switch entry point must reset the editor before fetching** (the split-day class): any surface that changes the day under an armed editor needs the `refreshWindowAndFeed` reset shape — edit mode off, sections cleared, generation bumped — synchronously at entry, BEFORE the request. The relief section is the third generation-guard consumer in this VM.
  - **A shared-state write in a scoped surface corrupts the host surface** (the `_selectedBranchId` lesson): when a scoped surface (relief mode) reads the clocked-in branch from a global singleton, the VM must NOT also write its own branch flag — the write pins the host surface's (reports) branch to the scoped one and survives exit. The scoped surface should READ the global, never WRITE shared VM state it doesn't own.
  - **A grant's branch is not derivable client-side**: capability rows carry contextId = branchDayId, not branchId — so a day-grant holder's branch is the clocked-in branch (relief clock-in = the grant's branch by construction). The #98 picker window is BRANCH-grant-only, so hybrids need a dedicated entry (the chip) — the picker can't reach a day-granted branch.
  - **P4 found it, P3 proved it domain-contradictory**: the hybrid fail-open shape (day grant + VIEW at another branch clocked at the wrong branch) looks scary until the domain check — relief clock-in happens AT the grant's branch; the broken shape needs a grant overlapping a day clocked elsewhere, which the grant write prevents. The discipline: check the domain before rating fail-open shapes HARD.
  - **Doc claims that say "revisit when X lands" must be amended IN the landing ticket** — the ride's pass-1 caught two (backend/AGENTS.md + ADR-0007 both claimed `/today` was branch-gated pending the ride). Truth-class register, third doc-line occurrence this map.
- Prior-session patterns unchanged: claim first, facts-via-code, one-ticket-per-session, resolution → close → map update → handoff. The rejected/re-rated HARDs discipline held (hybrid SOFT → HARD via triage; fail-open HARD-class → SOFT via domain proof — both directions exercised in one ticket).

## Current frontier (verified live post-session)

Per `gh issue list --state open`: **#89 (map)** — no open unblocked children (frontier EMPTY). Also open: #139 (standalone OpenAPI map), #110 (standalone hardcoded-month test fix).

## Recommended next pick

The #155 order is fully landed — there is no order-locked next. The standing fog candidates, in rough priority:

1. **The relief invite flow — branch-initiated (#105/#106)** — the biggest remaining relief gap: today's flow is user-initiated request→grant/deny only; the user wants branch-initiated invites ("add someone as relief for a future date → they accept/decline"). Placement was settled: BranchSelect rebuild territory (invites happen per-branch). **#158 sharpened it further**: the day-gate + read-entry machinery is now built, so the invite flow's UI (grant picker, day picker, validFrom/validTo window) plugs into working enforcement. This is a decision ticket (grilling) + build.
2. **Keep-last-results port for the remaining full-screen lists** (Remittance/Audit/User Management flash on re-entry) — the #143 in-flight guard is the shape a port must keep.
3. **DayStatus shared/domain move** — rides the Finance REMITTED-banner gap; also relevant to the ride's read side (the relief day-status read).
4. **The #158-graduated ARCH pair** — the relief-surface trichotomy state (small, rides the next Finance touch) + the generation-aware `ApiCallHandler.launch` variant (rides the next VM work).
5. **#92 Q4/Q5 403-refresh machinery** — deliberately deferred by #155; same-diff with #156 which has now landed; pick up whenever a revocation UX is wanted.

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. The frontier is EMPTY — pick a fog line (the list above) and create its ticket from the fog line's shape, or take a user-named ticket.
3. Claim via `gh issue edit <n> --add-assignee @me` **before any work**.
4. Build per module AGENTS.md + the phased loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **grep-verify every batch fix against the file before committing** (the fix-that-didn't-land class).
5. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
6. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #158 entry (grilling decisions + shipped surface + pass-by-pass loop record + resolution link), inserted before Not-yet-specified.
- Frontier paragraph: rewritten — **EMPTY** (0 unblocked children; #158 closed; the #155 order fully landed; the standing fog lines listed as the remaining picks).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; "Work through the map" steps (frontier empty — pick a fog line).
- **`/grilling` + `/domain-modeling`** — the relief invite flow (#105/#106) is a HITL decision ticket (flow shape: branch-initiated invite → accept/decline; grant window UI rides the #157/#158 enforcement).
- **`/implement` + `docs/agents/code-review-loop.md`** — for build tickets; the register carries the split-day stale-state shape (this session's pass-1), the fix-that-didn't-land hazard, and the count-0 family.
