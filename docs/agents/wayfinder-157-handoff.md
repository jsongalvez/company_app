# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 54

## What this is

A wayfinder build session on map **#89**. Session 54 picked **#157 (Fix — BRANCH_DAY relief grants never enforced)** — the #156 handoff's recommended next (the #155 falsification's fix, backend-only, AFK build per backend/AGENTS.md). Built AFK, phased loop 4 passes, resolved + closed. **#157 = the TWELFTH fog-graduation delivered; the #155 dead-grant class is closed end-to-end (grants written → now enforced).** Resolution: https://github.com/jsongalvez/company_app/issues/157#issuecomment-5292621564. Map #89 updated (Decisions-so-far #157 entry + frontier rewritten to EMPTY).

**Next-session state:** **0 unblocked children of #89 — the frontier is EMPTY.** The #155-locked order's final step is the **Finance day-gate ride** (frontend day-detail gates resolve `hasCapability(code, BRANCH_DAY, day.branchDayId)`) — backend ready, #156 matcher + `SessionState.branchDayId` slot ready; needs its own ticket. #110 (standalone hardcoded-month fix) + #139 (separate OpenAPI map) also open.

## Session outcome

**#157 (Fix — BRANCH_DAY relief grants never enforced) — built + resolved + closed (AFK).** 5 commits `fdfbb83`→`85fa5c4` on `ralph/company-app-full-build`, each pre-commit-gate green. Shipped: `CapabilityRepository.hasCapabilityForBranchDay` (one OR-query: `(BRANCH @ branchId) OR (BRANCH_DAY @ branchDayId)`, exact-triple legs, window view-enforced) + `CapabilityService.requireCapabilityForBranchDay` + `CapabilityFilter.requireBranchOrBranchDayCapability[ForExpense|ForSession]`; relief-eligible surface = expenses (all verbs + read) / product-sale create / session create + mutations; session create resolves today **find-only** (`BranchDayService.findToday`/`findByBranchAndDate` — filters never create rows) and shares the resolved day with the service via a request attribute (`GATED_BRANCH_DAY_ATTR` → `SessionService.create(gatedBranchDayId)` with a parent-child scoped guard, 404). **GLOBAL never satisfies** (#131 strictness). ADR-0007 amendment + family-list cleanup; backend/AGENTS.md day-scoped paragraph. Tests: `ReliefDayGateAuthzTest` 21 route-level tests (granted-day 201/200, non-granted 403, other-branch 403, wrong-day 403, expired-window 403 with −5h/−3h skew-tolerant seed, BRANCH regression 201, GLOBAL pin, session-create day/no-day paths incl. no-row-created assert, product-sale 201/403, direct-service foreign-day 404, real-JWT 401); `DatabaseTestHelper.grantCapability` gained `validFrom`/`validTo`. Suite 835 → **860**, 0 failures.

- **Phased loop: 4 passes** — pass 1: **2 HARD**: (a) P2's expense-tracking claim **REJECTED as false positive** (rows tracked by `branchDayId`; cleanliness gate passed empirically — rejection with evidence, the discipline held); (b) P4's **day-boundary TOCTOU** ACCEPTED — the session-create gate resolved today at filter time while the handler re-resolved independently; a request straddling Manila midnight could gate against the granted day and land on the ungranted next day (the mixed-resolution-base class, #156 pass-1's shape) — fixed by handing the resolved day to the service. In-pass also caught + reverted the **inventory-movement parent-child trap** (day from body vs branch-scoped path — the naive day-gate version 500'd a cross-branch test; inventory ruled out of the surface). Pass 2: 0 HARD (5 cheap SOFTs: guard shape → scoped-lookup 404 convention, KDoc dangle, ADR family list, live card version, stale comment). Pass 3: 1 HARD-class doc finding (ADR-0007 family list still named the deleted `requireBranchCapabilityForExpense`) + guard reworked + expired-window margin widened. **Pass 4: 0 HARD all four phases → exit.**
- **Truth-class records**: the pass-1 commit's "23 tests" claim was wrong (20 @Test methods; now 21) — recorded in the pass-3 commit; the #155 evidence line "every write (expense/session/compensation)" enumerated surfaces but allowance/compensation gate on ASSIGN_COMPENSATION — never grant-covered, excluded by code, not by decision.
- **Decisions (the ticket's design latitude)**: relief-eligible surface = EDIT_BRANCH_DATA-gated day-resolvable writes + their read mirrors; NOT covered: inventory movements (parent-child trap), allowance/compensation (ASSIGN_COMPENSATION), commission (VIEW_BRANCH_DATA/ASSIGN_COMPENSATION/EDIT_PAST_DAY), remittance (SUBMIT_REMITTANCE), session void/unvoid (VOID_SESSION), `/today` day-status read (zero consumers — revisit with the Finance ride).
- No migrations, no shared/DTO changes, no frontend changes. k6 deferred (#149/#152 reason — DevSeeder GLOBAL grants still can't pass strict gates; the OR adds no seedable channel).

## Patterns + learnings (cumulative across sessions)

- **Session-54 additions**:
  - **The naive day-gate had a parent-child trap the ticket's shape couldn't see**: applying the day-grant OR to the inventory movement route (day in the body, branch in the path) would let a day grant at branch A authorize a write against branch B — caught when the naive version 500'd an existing cross-branch authz test. When a gate's day comes from the body but the route is branch-scoped via the path, the branch must stay the gate's base (or the day's branch must be validated against the path first).
  - **A filter that creates rows is a test-hygiene bomb**: the first `/today` gate version resolved today via `getToday` (resolve-or-create) inside the before-filter — a 403'd request left an untracked branch_day row → tearDown FK failure → **4 downstream test classes failed from contamination**. The find-only gate (`findToday`) fixed the class. Filters must never mutate.
  - **The find-only gate needs the no-grant case too**: "grant implies the day row exists" (the grant references the day id; `grant_relief_access.branch_day_id` FK) — so a missing day row means no day grant is possible and the plain branch gate covers the fallback; the no-day 403 test pins that no row is created.
  - **Same-clock discipline applies to test seeds**: the expired-window seed (JVM clock) against the view's DB `now()` needed a ≥3h margin to be skew-safe — the AGENTS.md "JVM clock and DB clock may diverge" rule applies to test fixtures, not just production writes.
- Prior-session patterns unchanged: claim first, facts-via-code, one-ticket-per-session, resolution comment → close → map update → handoff. The rejected-HARDs discipline held once more (P2's tracking claim disproven by the existing trackOwned + the empirically-green cleanliness gate).

## Current frontier (verified live post-session)

Per `gh issue list --state open`: **#89 (map)** — no open unblocked children (frontier EMPTY). Also open: #139 (standalone OpenAPI map), #110 (standalone hardcoded-month test fix).

## Recommended next pick

- **The Finance day-gate ride (graduation ticket, frontend)** — the #155 Q3=A decision's last step, order-locked behind #157 + #156 (both landed). Finance day-detail gates resolve `hasCapability(code, BRANCH_DAY, day.branchDayId)` via the #156 matcher (`SessionState.branchDayId` slot exists); the backend now accepts day grants on the day surface. Scope: the Finance day-detail day-gate consumers only (per the #155 resolution), NOT the whole Finance surface. Needs: the backend `/today`-read question revisited (branch-gated today; a relief user's day-status read has no consumer endpoint — decide with the ride), and the day-feed read surface (daily-summaries gates VIEW_BRANCH_DATA — the ride's read-side question).
- Frontier is empty otherwise: the standing fog lines are the remaining picks (below).

## Standing fog lines (all still chartable)

Keep-last-results port for the remaining full-screen lists (Remittance/Audit/User Management flash on re-entry; the #143 in-flight loadUsers guard is the shape a port must keep; also the Clients search-field pop-back reset); **relief invite flow — branch-initiated** (#105/#106: BranchSelect rebuild territory; #157 closed the enforcement gap, the UI stays fog); the #106 grant-UI fog (rides the k6-for-branch-scoped-writes channel); user-create flow + non-admin self-slot-edit (BR:67); F7; desktop token-storage hardening; pushed-route topbar (desktop ClientDetail + SessionDetail pushed); audit branch-name; detekt gate strategy; route-path constants; hardcoded-month test dates (#110); the root-AGENTS.md stale pre-push-iOS line (truth-class record); the DayStatus shared/domain move (the Finance REMITTED-banner gap rides it; also relevant to the Finance day-gate ride's read side); **#92 Q4/Q5 403-refresh machinery (UiState.Unauthorized) — deliberately deferred by #155, same-diff with #156 (which has now landed), pick up whenever a revocation UX is wanted**; k6 for branch-scoped writes (the #157 OR adds no HTTP seedable channel — still deferred).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. The frontier is EMPTY — the next session either (a) takes the Finance day-gate ride as a graduation ticket (the #155 order's last step; needs a ticket created from this handoff's shape), or (b) picks a fog line. If the user names a ticket, use it.
3. Claim via `gh issue edit <n> --add-assignee @me` **before any work**.
4. Build per module AGENTS.md + the phased loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **grep-verify every batch fix against the file before committing** (the fix-that-didn't-land class, 6× in session-51).
5. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
6. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #157 entry (full build record: shipped surface + pass-1 HARD + trap record + resolution link), inserted before Not-yet-specified.
- Frontier paragraph: rewritten — **EMPTY** (0 unblocked children; #157 closed, removed from frontier; the Finance day-gate ride flagged as the next graduation).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; "Work through the map" steps (the frontier is empty — the Finance ride is the natural graduation, or pick a fog line).
- **`/implement` + `docs/agents/code-review-loop.md`** — the Finance ride is a frontend build; the register carries the mixed-resolution-base class (#156 pass-1, recurred as the #157 TOCTOU), the fix-that-didn't-land mechanical-replacement hazard, and the count-0 family. The ride's read-side question (day feed gates) is a decision, not a build — grill it before building.
