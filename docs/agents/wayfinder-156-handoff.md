# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 53

## What this is

A wayfinder build session on map **#89**. Session 53 picked **#156 (Build — SessionState full capability context model)** — the #155 handoff's recommended next (the frontend storage-model build, spec fully locked by #155). Built AFK, phased loop 3 passes, resolved + closed. **#156 = the ELEVENTH fog-graduation delivered; the #99 F7 divergence is dead end-to-end.** Resolution: https://github.com/jsongalvez/company_app/issues/156#issuecomment-5290777598. Map #89 updated (Decisions-so-far #156 entry + frontier rewritten to 1 ticket).

**Next-session state:** **1 unblocked child of #89** — **#157 (Fix — BRANCH_DAY relief grants never enforced)**, `wayfinder:task`, unclaimed. #110 (standalone hardcoded-month fix) + #139 (separate OpenAPI map) also open.

## Session outcome

**#156 (Build — SessionState full capability context model) — built + resolved + closed (AFK).** 4 commits `a482388`→`2ad7cd3` on `ralph/company-app-full-build`, each pre-commit-gate green. Shipped: `List<UserCapabilityResponse>` storage (contexts preserved); two-slice filters deleted, both fetches keep full-list storage; `hasCapability(code, contextType, contextId)` exact-triple (null contextId fails closed) + `hasCapabilityAnyContext` + `CapabilityContext` constants; consumers converted (4 route gates/actual → any-context, drawer any-context, dashboard `_canEdit` strict BRANCH, Finance → viewed-branch); `SessionCapabilitySliceTest` replaced by `SessionCapabilityMatcherTest`; ADR-0021 amended. Desktop suite **295 → 301**, 0 failures.

- **Phased loop: 3 passes** — pass 1: **1 HARD, all four phases converged** (Finance VM gates resolved the CLOCKED-IN `SessionState.selectedBranchId` while DayEditor + `pastDayReadOnlySelection` + the backend's day-row gates use the VIEWED branch — clocked A/viewed B hid the toggle despite B grants, or showed dead sections); fixed by resolving `_selectedBranchId` (viewed). Pass 2: 0 HARD + the viewed-vs-clocked split pinned (`hasEditCapabilities_resolvesTheViewedBranch`); superseded-PATCH test needed both-branch grant seeds (its section loads were gated by the wrong base before). Pass 3 (doc-only delta): 0 HARD all four phases → **exit**.
- **Truth-class record**: the #155 resolution + ticket "5 route-gate sites × 2 actuals" claim is FALSE — **4** gated routes per actual (Clients/Finance/Remittance/UserManagement); AuditLog's `hasAnyCapability` is the 5th `capabilities` read site but not a gate. Recorded in the resolution comment; nothing to fix.
- **Accepted SOFTs (final)**: DayEditor inline triples mirror the VM functions (deliberate — presentational params per #92 Q2; bases verified identical); Finance picker-default-coincides-with-clocked-branch (KDoc nuance, harmless); dashboard `_canEdit` init-time snapshot (pre-existing pattern, PATCH-403 self-heals, Q2-deferred).
- No migrations, no backend changes, no k6 (frontend-only; no new backend surface).

## Patterns + learnings (cumulative across sessions)

- **Session-53 additions**:
  - **The route-gate count claim ("5 sites") was wrong at the SOURCE (the #155 resolution), and the ticket inherited it** — P1 verified the count against code and recorded it as truth-class rather than "fixing" the (nonexistent) 5th gate. When a ticket's site-count claims are inherited from a grilling resolution, grep-verify the count during the build, not just the review.
  - **The one HARD was a mixed-resolution-base bug the grilling couldn't see**: #155 locked "branch-scoped vs selectedBranchId" — ambiguous between the clocked-in branch (SessionState) and the VIEWED branch (Finance's switchable picker, which the backend gates via the day row). All four phases converged on it independently; the fix was one resolution-base alignment. For surfaces with their own branch picker, "the selected branch" means the surface's viewed branch — verify against the backend's resolution source (day row → branch), not SessionState.
  - **A capability-gate fix can silently break a focused test's setup**: the superseded-PATCH lifecycle test (seeded grants at BRANCH_A only) broke after the base fix because its second phase loads sections at BRANCH_B — the gates were previously satisfied by the wrong base. Fix = seed both branches (the test's subject is the lifecycle, not the gates).
- Prior-session patterns unchanged: claim first, facts-via-code, one-ticket-per-session, resolution comment → close → map update → handoff. **Migration-numbering check stands** (no migrations touched).

## Current frontier (verified live post-session)

Per `gh issue list --state open`: **#157 (Fix — BRANCH_DAY relief grants never enforced)** — unblocked, unclaimed, child of #89. Also open: #89 (map), #139 (standalone OpenAPI map), #110 (standalone hardcoded-month test fix).

## Recommended next pick

- **#157 (backend)** — the BRANCH_DAY enforcement fix: new day-scoped gate semantics (BRANCH grant OR BRANCH_DAY grant for the specific day), which endpoints are relief-eligible, tests (grant → day-scoped write OK on granted day, 403 on non-granted day/expired window). Fix shape in the ticket body; design latitude flagged. Per the locked #155 order, **Finance day-gates ride #157** — the frontend day-gate consumers are now ready (the #156 matcher handles BRANCH_DAY triples; `SessionState.branchDayId` slot exists; Finance `hasCapability(code, BRANCH_DAY, dayRow.branchDayId)` is the call shape).
- Backend surface → pre-commit gate runs the full backend suite + detekt; k6 deferred if the #149/#152 reason applies (no HTTP self-grant channel — DevSeeder GLOBAL grants can't pass branch-scoped gates; check whether #157's gate change creates a seedable path).

## Standing fog lines (all still chartable)

Keep-last-results port for the remaining full-screen lists (Remittance/Audit/User Management flash on re-entry; the #143 in-flight loadUsers guard is the shape a port must keep; also the Clients search-field pop-back reset); **relief invite flow — branch-initiated** (#105/#106: BranchSelect rebuild territory; #157 fixes the enforcement gap, the UI stays fog); the #106 grant-UI fog (rides the k6-for-branch-scoped-writes channel); user-create flow + non-admin self-slot-edit (BR:67); F7; desktop token-storage hardening; pushed-route topbar (desktop ClientDetail + SessionDetail pushed); audit branch-name; detekt gate strategy; route-path constants; hardcoded-month test dates (#110); the root-AGENTS.md stale pre-push-iOS line (truth-class record); the DayStatus shared/domain move (the Finance REMITTED-banner gap rides it; also relevant to #157's day-gated surface); **#92 Q4/Q5 403-refresh machinery (UiState.Unauthorized) — deliberately deferred by #155, same-diff with #156 (which has now landed), pick up whenever a revocation UX is wanted**.

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-54 has **1 unblocked ticket**: #157 (backend BRANCH_DAY enforcement). `wayfinder:task`, AFK build per backend/AGENTS.md.
3. Claim via `gh issue edit <n> --add-assignee @me` **before any work**; if the user names one, use it, else pick.
4. Build per module AGENTS.md + the phased loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **grep-verify every batch fix against the file before committing** (the fix-that-didn't-land class, 6× in session-51).
5. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
6. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #156 entry (full build record: shipped surface + pass-1 HARD + truth-class route-gate-count note + resolution link), inserted before Not-yet-specified.
- Frontier paragraph: rewritten — **1 unblocked ticket** (#157; #156 closed, removed from frontier).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; "Work through the map" steps (session-54 has 1 unblocked ticket — claim it).
- **`/implement` + `docs/agents/code-review-loop.md`** — the next pick is a backend build; the register carries the fix-that-didn't-land mechanical-replacement hazard, the count-0 family, and the **dead-grant class (#157 is literally a dead-grant fix)** — the falsification evidence is in the #155 resolution (grant write `ReliefAccessRepository.kt:104` → gate check → exact-triple match → view rewrite).
