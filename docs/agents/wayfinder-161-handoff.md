# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 58

## What this is

A wayfinder build session on map **#89**. Session 58 picked the frontier ticket **#161 (Build — Keep-last-results port, the #143 fog graduation)**, claimed it, and built it under the phased code-review loop. **#161 = the THIRTEENTH fog-graduation's build; the #143 keep-last fog line + the Clients D9 deviation are now CLOSED end-to-end.** Resolution: https://github.com/jsongalvez/company_app/issues/161#issuecomment-5299248706. Map #89 updated (Decisions-so-far #161 entry + fog graduation + frontier paragraph rewritten).

**Next-session state:** the frontier is **EMPTY** — no open unblocked child of #89. The next pick comes from the standing fog lines: the new **keep-last mirror + in-flight guard unifier** (the #161 P5 graduate — 6 VMs / 3 mirror-write mechanisms / 4 guard shapes, `KeepLast<T>`/`KeepLastByKey<K,T>` extraction, Clients `cachedResults` joins the VM-side shape), the DayStatus shared/domain move, the #92 Q4/Q5 403-refresh machinery, the #158-graduated ARCH pair (relief-surface trichotomy state; generation-aware ApiCallHandler variant), the two #160 graduates (shared-Exposed-query-op-helpers — the third `ilike` copy; screen-scoped-VM-seam), k6 for branch-scoped writes (needs #106 seeding), the root-AGENTS.md stale pre-push-iOS line, #110 (hardcoded-month test dates), #139 (standalone OpenAPI map), the user-creation/role-assignment flow (#106), the request-flow grant/deny UI gap (ReliefAccessViewModel unwired).

## Session outcome

**#161 (Build — Keep-last-results port) — claimed, built, reviewed (5-pass loop), resolved, closed.** 5 commits `4c103e7`→`fd2a037` on `ralph/company-app-full-build`, each pre-commit gate green (ktlint/detekt/903 backend tests/cleanliness/shared-compile/Postgres); Android compile verified (pre-push leg); desktop suite 313→**341** (+13). Frontend-only — backend untouched.

- **User Management**: VM-held `lastUsers` mirror (NotificationViewModel collector shape, init `filterIsInstance<Success>` collector); `loadUsers` gained a **synchronous Loading pre-set** — `_users.value = UiState.Loading` set BEFORE `handler.launch` so the state IS the double-fire guard (self-clears via the handler's own Error/Success assignments; no separate flag to wedge — the #140 class), layered under the kept #143 mutation-in-flight guard; mutations resolve the list via `Success ?? lastUsers` (row actions stay live over a mirror-rendered list — the NotificationVM `currentUnreadList` precedent); screen render gate = Success-data ?? mirror through Loading/Error (ErrorCard only when nothing was ever held), search stays enabled over the mirror.
- **Remittance**: VM-held per-`(branchId, status)` `lastByTab` mirror written by the list transform (screen-side `cachedByTab` remember — died on pop-back — deleted); **per-key synchronous in-flight guard** `listLoadsInFlight` (entry + tab effects' double-fire of the default tab coalesces; a tab switch during another tab's load is NOT skipped — per-key, not single-slot); mirror-keyed render gate — a cross-tab response can never render foreign rows (the old single-flow last-writer race is now invisible); shared `remittanceListKey` top-level helper (format coupling closed).
- **Audit Log — truth-class record**: the fog premise "Audit Log, Remittance same" was STALE — #123's D10 had already shipped the full shape (cold/silent split, synchronous `_flaggedLoadInFlight`, keep-last via throwaway flows, entry-effect silent-refresh-on-Success, tab re-entry silent refresh). Nothing built; the delta omitted it deliberately.
- **Clients — the D9 deviation is FIXED (shares the ported shape)**: search query VM-held (`_query` written synchronously by `onQueryChange`; screen binds the field; X-clear → `onQueryChange("")`; the `LaunchedEffect(query)` refire deleted). Pop-back restores query + kept list with no re-search (D9's no-auto-refresh holds; the #142-logged "pop-back clears the search one frame after the kept list re-renders" deviation closed).
- **Loop**: pass 1 (**1 HARD** — mutation-during-load race: the gate renders live rows during Loading and a mid-load deactivate PATCH was clobbered by the load's pre-mutation snapshot; 3 phases independently; the #141 stale-mask class → symmetric `runMutation` Loading-guard + screen `mutationsDisabled |= Loading`), pass 2 (**1 HARD** — dialog confirm silent-drop: the same-frame slip reached an ungated dialog confirm the VM guard swallowed silently → both dialog confirms gated on `mutationsDisabled`), pass 3 (0 HARD; SOFTs fixed: disabled-affordance — explicit error color yielded to M3 dimming; dialog field gated), pass 4 (0 HARD; SOFTs fixed: dimming consistency — `Color.Unspecified` under the gate so `disabledContentColor` applies, row + dialog), pass 5 (**0 HARD — exit**). P5: 3 ARCH, all graduated into one fog line (**keep-last mirror + in-flight guard unifier**).

## Patterns + learnings (cumulative)

- **Session-58 additions**:
  - **The state-as-guard pre-set**: `_users.value = UiState.Loading` set synchronously BEFORE `handler.launch` makes the state itself the double-fire guard — no separate flag to wedge (self-clears via the handler's own Error/Success assignments). Cheaper than the AuditLog bool-flag shape when the handler owns the same state flow; the pre-set is what makes it synchronous under StandardTestDispatcher (the handler's own Loading assignment is not guaranteed to land before launch returns — Main.immediate may execute inline).
  - **Keep-last has an action side**: rendering held rows during Loading/Error surfaces LIVE row actions — mutations must resolve the list via `Success ?? lastMirror` (dead taps) AND be gated during Loading (the load's pre-mutation snapshot silently reverts a mid-load PATCH — the pass-1 HARD). The belt+suspenders pair is the pattern: VM guard for same-frame slips + screen gate for the visible affordance (rows AND dialog confirms).
  - **The dialog gate hole**: a modal dialog open when a load starts (or a same-frame slip opens it) — its confirm button bypasses the row gates; the VM guard then swallows the confirm silently. Gate every confirm/save on the same `mutationsDisabled`.
  - **Disabled-affordance truth**: an explicit `color = MaterialTheme.colorScheme.error` overrides M3's `disabledContentColor` — a disabled-but-vivid-red button looks live. Yield to `Color.Unspecified` under the gate (resolves to LocalContentColor → the button's disabled color).
  - **Truth-class on fog premises**: the fog line's "Audit Log, Remittance same" was stale — #123's D10 already shipped the exact shape. Verify each target's current state in code before building; the fog line is an index, not a spec.
  - **Fix-that-didn't-land struck once in-build**: after binding ClientsScreen to the VM-held query, `onValueChange = { query = it }` silently remained (val reassignment — caught by compile, not by reading the diff). Grep-verify every edit; compile early.
- Prior-session patterns unchanged: claim first, facts-via-code, one-ticket-per-session, resolution → close → map update → handoff, batch-fix commits per pass, `git diff <last-pass-commit>` per pass.

## Current frontier (verified live post-session)

Per `gh issue list --state open` + the map's sub-issues: **no open unblocked child of #89 — frontier EMPTY.** Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix).

## Recommended next pick

The standing fog lines in rough priority: (1) **keep-last mirror + in-flight guard unifier** (the #161 P5 graduate — a genuine seam: 6 VMs, 3 mirror-write mechanisms, 4 guard shapes; `KeepLast<T>`/`KeepLastByKey<K,T>` extraction candidates; Clients `cachedResults` is the last screen-side mirror and should join the VM-side shape); (2) the **DayStatus shared/domain move** (rides the Finance REMITTED-banner gap + the ride's read side); (3) the **#92 Q4/Q5 403-refresh machinery**; (4) the ARCH graduates: shared-Exposed-query-op-helpers (small, mechanical — 3 private `ilike` copies), relief-surface trichotomy state, generation-aware ApiCallHandler variant, screen-scoped-VM-seam; (5) the request-flow grant/deny UI gap (ReliefAccessViewModel unwired); (6) k6 for branch-scoped writes (needs #106 seeding); then the docs/maintenance lines (#110, root-AGENTS.md pre-push-iOS line, #139, #106).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations"), and — for any HITL design work — `docs/agents/decision-loop.md`.
2. The frontier is EMPTY — the next pick is a fog-graduation: grill the user to graduate one of the standing fog lines into a decision ticket (or take an AFK build like the keep-last unifier or the ilike unifier directly). Any new ticket: create as a child of #89, wire blocking edges in a second pass, claim before work.
3. Build per module AGENTS.md + the phased code-review loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **grep-verify every batch fix against the file before committing** (the fix-that-didn't-land class — watch the mount-revert hazard: re-verify critical files after long gaps).
4. Post the answer as a **resolution comment**, close, append a context pointer to map #89's Decisions-so-far + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #161 entry (build summary + truth-class record + loop outcome + P5 graduates + resolution link), inserted before Not-yet-specified.
- Not-yet-specified: the keep-last port fog line + the Clients search-field D9 line REMOVED (both closed); new fog line added (keep-last mirror + in-flight guard unifier, the #161 P5 graduate).
- Frontier paragraph rewritten: **frontier EMPTY** — the keep-last line fully landed; the remaining fog lines re-indexed incl. the new unifier graduate.

## Suggested skills for next session

- **`/wayfinder`** — "Work through the map" steps (frontier empty — fog-graduation session: grill the next standing fog line into a ticket).
- **`/implement` + `docs/agents/code-review-loop.md`** — for any build/audit ticket; the register now carries the state-as-guard pre-set, the keep-last action side (Success ?? mirror + Loading-gated mutations + gated dialog confirms), and the disabled-affordance rule (all new this session).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket; the standing frame (dev-stage, zero migration cost, best long-term, falsify every claim, facts in code before choices, simple language) applies to ALL human-facing questions.
