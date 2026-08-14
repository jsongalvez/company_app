# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 52

## What this is

A wayfinder grilling session on map **#89**. Session 52 picked the standing **SessionState capability context-model divergence** fog line (the #154 handoff's recommended next) — grilled live (HITL), **3 rounds**, resolved + closed. **#155 (Grilling — SessionState capability context model)** — the **TENTH fog-graduation**; the #99 F7 divergence is DECIDED. Two build tickets graduated from it: **#156** (frontend storage model) + **#157** (backend BRANCH_DAY enforcement fix). Resolution: https://github.com/jsongalvez/company_app/issues/155#issuecomment-5290360449. Map #89 updated (Decisions-so-far #155 entry + divergence fog line removed + relief line sharpened + frontier rewritten).

**Next-session state:** **2 unblocked children of #89** — #156 (frontend) + #157 (backend), both `wayfinder:task`, both unclaimed. #110 (standalone hardcoded-month fix) + #139 (separate OpenAPI map) also open.

## Session outcome

**#155 (Grilling — SessionState capability context model) — created + resolved + closed (HITL).** The user's picks: **Q1 = (a) full #92 model**, **Q2 = defer the 403-refresh machinery**, **Q3 = reframed** (see below). Three rounds because the user pushed back twice — once demanding verification of my "the clock-in refetch becomes redundant" claim (it was half-true: payloads identical, but the refetch keeps freshness + the #147 clearClockState reload — corrected), once demanding falsification of all options.

- **Q1 = (a)**: `SessionState.capabilities` becomes `List<UserCapabilityResponse>` (contexts preserved; BRANCH_DAY/MEDICAL_MISSION/PROVINCIAL_TOUR rows no longer dropped), `hasCapability(code, contextType, contextId)` resolving against `selectedBranchId` + day-row branchDayIds, plus `hasCapabilityAnyContext` for route gates (#92 Q3 "some branch"; backend `hasCapabilityAnyContext` precedent, CapabilityRepository.kt:91). The ADR-0021 two-slice *filters* (`globalCapabilities`/`capabilitiesForBranch`, SessionState.kt:103-122) die; the two-slice *fetch timing* stays. Rejected: (b) canonize the divergence (deferred cost, same price, implicit any-context semantics invisible in the type), (c) hybrid Set+map (two structures for one concept — the audit loop's coordination-cost class).
- **Q2 = defer** the #92 Q4/Q5 machinery (`UiState.Unauthorized` + 403-refresh-retry-once). Falsified: deactivation → DenyList → 401 (#145), not 403 — the common revocation path never produces 403; live 403s are narrow (expired relief validTo windows, mid-session grant revocation); no screen has a live revocation scenario. Honest note: design-orthogonal but same-diff — #156 touches the same consumers, so the marginal cost there is small; recorded, not dropped. #92 Q4/Q5 remain unimplemented locks.
- **Q3 = Finance day-gates resolve BRANCH_DAY — REFRAMED by falsification that caught a REAL BUG**: relief grants are written `(EDIT_BRANCH_DATA, BRANCH_DAY, branchDayId)` with a validTo window (`ReliefAccessRepository.kt:101-111`), but **no backend gate ever checks BRANCH_DAY** — all 6 `CapabilityFilter` variants (CapabilityFilter.kt:44-196) + `BranchDayService.assertEditable` check BRANCH/GLOBAL only; `CapabilityRepository.hasCapability` is exact-triple equality (CapabilityRepository.kt:27-49); `active_user_capabilities` does no context rewriting (V1:549, V16:52); `findBranchWindow` filters BRANCH only. **Consequence: relief grants authorize nothing — day-scoped relief editing 403s end-to-end, not just frontend-blind.** Frontend day-gates would be theater until fixed. **Decision: the enforcement gap graduates as its own ticket (#157); Finance day-gates ride it after it + #156 land (order locked).**
- **Graduated (both unblocked, both children of #89)**: **#156** (Build — SessionState full capability context model; this resolution is its spec) + **#157** (Fix — BRANCH_DAY relief grants written but never enforced).

## Patterns + learnings (cumulative across sessions)

- **Session-52 additions**:
  - **Falsify the load-bearing claim, not just the options**: Q3=A died because the *backend gate path* was verified (grant write → gate check → exact-triple match → view rewrite), not because of frontend reasoning. The register's dead-grant class (#106 F2, #104 F2, #105 F1) has a new member: BRANCH_DAY grants written but never checked.
  - **The user will (rightly) demand verification of your own recommendation's premises** — the "redundant refetch" claim was corrected mid-grilling by checking both fetch sites (SessionBootstrapViewModel:67-71 vs BranchSelectViewModel:94-96 — same endpoint, same payload, different client-side filter). Present claims with their evidence sites up front.
  - **"Two sources of truth" is a standing rejection axis** — hybrid option (c) died on it without needing new reasoning (the #144/#147 coordination-cost class).
  - **401-vs-403 distinction is load-bearing for revocation UX decisions**: deactivation kills tokens (DenyList, 401), capability-level revocation produces 403. Q2's deferral rests entirely on that split (#145).
- Prior-session patterns unchanged: claim first (assign before work), facts-via-code not user, grilling rounds with numbered frontier + recommendations, one-ticket-per-session, resolution comment → close → map update → handoff. **Migration-numbering check stands** (this session touched no migrations).

## Current frontier (verified live post-session)

Per `gh issue list --state open`: **#156 (Build — SessionState full capability context model)** + **#157 (Fix — BRANCH_DAY relief grants never enforced)** — both unblocked, unclaimed, children of #89. Also open: #89 (map), #139 (standalone OpenAPI map), #110 (standalone hardcoded-month test fix).

## Recommended next picks

- **#156 (frontend)** — the storage model build: ~10 consumer files (5 route-gate sites × 2 actuals, DrawerViewModel, FinanceReportsScreen/VM, SessionDashboardViewModel) + SessionState.kt + 4 test files (SessionCapabilitySliceTest replaced). Pure frontend, no backend deps, spec fully locked by #155. Phased loop expected 2-3 passes (interaction-light — mostly mechanical replacement + test rewrite; watch the fix-that-didn't-land class, grep-verify every batch fix).
- **#157 (backend)** — the BRANCH_DAY enforcement fix: new day-scoped gate semantics (BRANCH grant OR BRANCH_DAY grant for the specific day), which endpoints are relief-eligible, tests (grant → day-scoped write OK on granted day, 403 on non-granted day/expired window). Fix shape in the ticket body; design latitude flagged.
- Both can run in parallel sessions (disjoint surfaces: composeApp vs backend) — but **#157's frontend ride is explicitly NOT part of it** (Finance day-gates wait for #156 + #157 per the locked order).

## Standing fog lines (all still chartable)

Keep-last-results port for the remaining full-screen lists (Remittance/Audit/User Management flash on re-entry; the #143 in-flight-loadUsers guard is the shape a port must keep; also the Clients search-field pop-back reset); **relief invite flow — branch-initiated** (#105/#106: BranchSelect rebuild territory; **sharpened this session: the user-initiated request→grant/deny backend exists but its grants were never enforced — #157 fixes enforcement, the UI stays fog**); the #106 grant-UI fog (rides the k6-for-branch-scoped-writes channel); user-create flow + non-admin self-slot-edit (BR:67); F7; desktop token-storage hardening; pushed-route topbar (desktop ClientDetail + SessionDetail pushed); audit branch-name; detekt gate strategy; route-path constants; hardcoded-month test dates (#110); the root-AGENTS.md stale pre-push-iOS line (truth-class record); the DayStatus shared/domain move (the Finance REMITTED-banner gap rides it; **also relevant to #157's day-gated surface**); **#92 Q4/Q5 403-refresh machinery (UiState.Unauthorized) — deliberately deferred by #155, same-diff with #156, pick up whenever a revocation UX is wanted**.

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-53 has **2 unblocked tickets**: #156 (frontend storage model, spec = #155 resolution) or #157 (backend BRANCH_DAY enforcement). Both `wayfinder:task`, AFK builds per module AGENTS.md.
3. Claim via `gh issue edit <n> --add-assignee @me` **before any work**; if the user names one, use it, else pick.
4. Build per module AGENTS.md + the phased loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **grep-verify every batch fix against the file before committing** (the fix-that-didn't-land class, 6× in session-51).
5. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
6. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #155 entry (full grilling record: Q1/Q2/Q3 + the falsification finding + graduated #156/#157 + resolution link), inserted before Not-yet-specified.
- Not-yet-specified: **the SessionState divergence fog line REMOVED** (decided; lives only as #156); relief invite-flow line sharpened (grants were never enforced → #157).
- Frontier paragraph: rewritten — **2 unblocked tickets** (#156 + #157), both graduated this session.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; "Work through the map" steps (session-53 has 2 unblocked tickets — claim one).
- **`/implement` + `docs/agents/code-review-loop.md`** — the next picks are both builds; the register carries the fix-that-didn't-land mechanical-replacement hazard + the count-0 family + the dead-grant class (#157 is literally a dead-grant fix).
