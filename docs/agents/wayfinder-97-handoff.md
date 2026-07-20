# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 6

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). This session resolved ticket **#97** (prototype the session dashboard UX) and is handing off before the context limit. The next session should pick the next frontier ticket.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far, fog, out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues API is enabled): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- Frontend conventions: `composeApp/AGENTS.md`
- Spec: `docs/specs/0001-frontend-rebuild.md` (esp. line 103 — capabilities fetch on login; line 107 — polling + summary cards; line 111 — mobile card fields; line 113 — voided rendering; line 173 — responsive out of scope)
- ADR 0001 (NavHost routing): `docs/adr/0001-composeapp-navhost-routing.md`
- ADR 0007 (route-level capability gates — backend): `docs/adr/0007-route-level-capability-gates.md`
- ADR 0020 (responsive strategy — platform-target split): `docs/adr/0020-composeapp-responsive-platform-target-split.md`
- ADR 0021 (capability refresh two-slice model): `docs/adr/0021-composeapp-capability-refresh-two-slice-model.md`
- **ADR 0022 (pessimistic inline editing — this session's artifact): `docs/adr/0022-composeapp-pessimistic-inline-editing.md`**
- Domain glossary: `CONTEXT.md`; business rules: `docs/business-requirements.md`; architecture: `docs/architecture.md`
- **Prototype outline (this session's validated decision): `docs/prototypes/0097-session-dashboard-outline.md`**

## Session outcome

**#97 (session dashboard UX prototype) — resolved + closed.** Six UX questions resolved via one-question-at-a-time grilling, with a runnable Compose UI prototype covering Q2 (summary cards) and Q3 (voided rendering). The user's reasoning consistently grounded in: structural-difference-over-tweak (rejected wallpaper variants), ownership-boundaries (polling owns committed state, user owns active edit), evidence-from-domain (`business-requirements.md:204` for practitioners demotion, `:189` for basePrice read-only, `:212` for walk-in constraint), consistency-with-prior-axes (pessimistic inline edit = same "backend is authoritative" axis as ADR-0021/#92/#94), and naming-accepted-costs (mid-session affordance vanishing, voided status pill coexistence).

**The six decisions (full detail in the outline doc + resolution comment on #97):**

1. **Q1 — Column set + ordering.** Desktop 6 primary (booked time, client name + walk-in dot, type, status, final price, VOIDED slot) + 4 secondary in detail pane (base price, practitioners, next appointment, remarks, concerns). Mobile strict spec-line-111 card. **Two placements pressure-tested against the scan-vs-inspect axis:** practitioners demoted to secondary (self-assigned at work-time per `business-requirements.md:204`, not coordinator-dispatched); walk-in as dot inline with name not a chip (binary context, not a category — avoids three-chips-per-row inflation).

2. **Q2 — Summary cards.** Two equal-weight cards in a row + thin vertical hairline divider (Variant A, locked over B asymmetric-weight and C stacked). "Equal peers, distinct not one unit" — the divider + sublabels prevent the "two equal cards = one merged figure" misread for conceptually unrelated pools (session-revenue vs product-commission). No lavender on values (data, not actions). Totals enforced in `uiState` not rendering. **Three variants rendered as a runnable prototype** — the user picked A because asymmetric sizing (B) communicates importance not categorical difference, and stacking (C) overcorrects at the cost of vertical space.

3. **Q3 — Voided rendering.** Red-tinted row bg (`Danger` 22% over `Surface1`) + strikethrough on client name only + dimmed time/type/walk-in-dot + muted status pill + light-rose VOIDED pill (`Danger` 35% bg + bright `Ink`). **Orthogonality preserved:** status pill stays when voided (status is real — COMPLETED is still COMPLETED), just visually defers to VOIDED pill. The two axes (status, void) are not collapsed — the user explicitly rejected collapsing status→"Voided" early in Q3, then later asked to mute the status pill when voided (a middle path: keep both pills, but the status pill defers visually). Validated by rendering the worst-case density row (voided + COMPLETED + walk-in) on both desktop table-row and mobile card treatments. Several iterations on the VOIDED pill color (grey-on-Hairline was unreadable on the red row → `Danger` 35% + bright `Ink`) and active COMPLETED pill color (full-saturation over 18% alpha was muddy → brightened fg `#4EC769` over 30% alpha).

4. **Q4 — Inline editing (desktop only).** Type/status/finalPrice editable (basePrice read-only — domain rule per `:189`, not VM workaround). Hover-revealed affordance, silent when capability revoked (mid-session revocation: close edit mode + silently remove affordance). Per-control commit (dropdown on select, input on Enter/blur). Single-field scope (multi-field draft editing explicitly out of scope). **Pessimistic update model (ADR-0022)** — UI doesn't update until PATCH succeeds. Failure behavior (Model A): keep attempted value + inline error + stay in edit mode. 403 → silent exit + affordance vanishes. 409 → reload + indicate changed fields (implementation detail: highlight vs textual summary). **Mid-edit polling does not overwrite the edited field** — user's draft temporarily owns the cell.

5. **Q5 — Polling UX.** Silent refresh + last-updated timestamp (**last *successful* refresh, never last attempt** — a timestamp that lies during failures is misleading at the worst moment). Failure handling: silent retry → stale-notice after small number (default 2) → error state after prolonged (default 5); **thresholds are tuning params, not architecture** (changing 2→3 is not an ADR violation). Preserve last successful data on transport failure (same axis as #94 network-error token-preservation). **401 is NOT a polling-degradation path** — it's session termination (redirect to Login; stale-notice never appears for 401). Poll cadence: completion-then-wait, not wall-clock. Manual refresh (pull-to-refresh mobile / button desktop) available once stale indicated. **"Leaving Dashboard" = leaving the route** (desktop inline detail pane doesn't stop polling; mobile `SessionDetail` push route does).

6. **Q6 — States.** Cold-start → full-screen spinner; partial-region → render loaded region + spinner only in loading region. Empty → "No sessions yet today" + create-session action (shown if capabilities permit session creation, **phrased by function not capability constant** so it survives a future create/edit/void split) + summary cards show ₱0 (**zero is valid business information — "today's value is zero" ≠ "dashboard not applicable"**). Empty participates in polling (auto-transitions to populated). Error → per-region in-place card, **retry targets only the failed region**. Unauthorized (#92, confirmed not re-decided) → in-place 403 card. **State ownership follows rendering ownership** — cards own cards state, list owns list state (smallest-divergent-subtree applied to state, not just layout).

**ADR-0022 created:** `docs/adr/0022-composeapp-pessimistic-inline-editing.md` — centers narrowly on the update-model fork (pessimistic, not optimistic). The polling-ownership rule, 403 silent-exit, and 409 pre-commit handling are recorded as **consequences** (per the user's refinement that the ADR should stay focused on the architectural fork, not document every UX detail that follows from it). Meets the three-part test: hard to reverse (changing to optimistic would break the polling-ownership boundary), surprising without context (a future contributor might default to optimistic because it feels more responsive), real trade-off (optimistic is faster-feeling). Sits on the same "backend is authoritative" axis as ADR-0021/#92/#94.

**Prototype asset:** `docs/prototypes/0097-session-dashboard-outline.md` on `ralph/company-app-full-build` (the validated decision). Prototype code on throwaway branch `prototype/0097-session-dashboard` (runnable desktop Compose prototype covering Q2 + Q3; includes all three Q2 variants as the primary source per the prototype skill). To run: checkout the branch, temporarily set `mainClass = "com.companyb.companyapp.prototype.SessionDashboardPrototypeKt"` in `composeApp/build.gradle.kts`, `./gradlew :composeApp:run`.

**Graduation candidates flagged (for build ticketing, not fog):**
1. Enriched session-row DTO + endpoint — `SessionResponse` lacks `clientName`, practitioners list, inline voided-ness. Dashboard needs all three. Same shape as #94 → #98 (backend gap exposed by frontend prototype).
2. VM methods for inline edit — `SessionViewModel` has `updateStatus` but no `updateType` / `updatePrice`.
3. Clock-out → dashboard-state-clear transition — not specified in #94. Fog for now (may graduate when the dashboard build ticket is worked).

Map #89 body updated: Decisions-so-far now lists #90, #91, #92, #95, #94, #97. No new fog graduated (the three graduation candidates are build tickets, not fog → the "Feature screens" fog already graduated to #99-#106 in session 5; these are infrastructure prerequisites for the dashboard build ticket that will emerge from #97).

## Current frontier (verified live)

**11 unblocked tickets** (12 from session 5 minus #97):

| # | Title | Type | Status |
|---|-------|------|--------|
| 93 | Set up testing infrastructure (MockEngine + commonTest) | task | 🔓 unblocked |
| 96 | Prototype the navigation drawer (contents, capability gating, responsive form factor) | prototype | 🔓 unblocked |
| 98 | Add GET /api/me/branches endpoint (assigned branches + clock-in status) | task | 🔓 unblocked |
| 99 | Prototype the Clients screen UX (search, detail, anonymization) | prototype | 🔓 unblocked |
| 100 | Prototype the Inventory screen UX (stock levels, product sales, drill-down) | prototype | 🔓 unblocked |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | 🔓 unblocked |
| 102 | Prototype the Notifications screen UX (list, unread badge, mark-as-read) | prototype | 🔓 unblocked |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | 🔓 unblocked |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | 🔓 unblocked |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |

**Zero blocked tickets.** All blocking edges cleared. The map's decision phase is complete — the frontier is entirely prototype/execution tickets. No ticket depends on another.

## Recommended next pick: #96

Highest leverage now. #96 (navigation drawer prototype) has been unblocked since session 4 and is the last piece of shared chrome the feature screens (#99-#106) will sit inside. The drawer's capability gating (US-28) depends on #92 (locked), its responsive form factor depends on #95 (locked), and its contents (which items appear, in what order, with what badges) is the UX decision that frames every feature screen prototype below it. #98 (backend endpoint) and #93 (testing infrastructure) are task tickets that produce infrastructure the build tickets will consume — important but not downstream-gating for the remaining prototypes. The 8 feature screen prototypes (#99-#106) are independent and can be worked in any order or in parallel by multiple sessions.

#96 is a prototype ticket — work it WITH the user using `/prototype` (likely UI branch — "What should this look like?") + `/grilling` + `/domain-modeling` (per map Notes). Before prototyping, fetch #96's full body:
```bash
gh issue view 96 --json body --jq .body
```

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit 96 --add-assignee @me` — claim FIRST, before any work.
2. Resolve via `/prototype` + `/grilling` + `/domain-modeling` (per map Notes). Look up facts in the repo rather than asking. The user prefers: evidence over aesthetics (cites spec/ADR/business-requirements to override recommendations); consistency across decisions (flags when a new choice reintroduces coupling a prior choice removed); fog stays visible with its future cost named; scope boundaries don't silently widen ("requires re-scoping" > "default available"); accepted costs named explicitly rather than hidden; intentional asymmetries named so they aren't silently un-done by a future reader "fixing" them.
3. Post the answer as a **resolution comment** on #96, then `gh issue close 96`, then append a context pointer to map #89's **Decisions so far** section (fetch body → edit → `gh issue edit 89 --body-file`).
4. Graduate any fog the answer makes specifiable. Assess whether the drawer UX decision creates downstream execution tickets (e.g., "build NavDrawer composable", "build drawer-item expect/actual"). Per the fog guidance: "don't pre-slice; one patch may graduate into several tickets, or none."
5. If the answer reveals a ticket sits past the destination, close it and add a line to **Out of scope**.
6. If the decision meets the ADR bar (hard to reverse + surprising without context + real trade-off), offer an ADR per `/domain-modeling`. #96 may warrant one if it locks a non-obvious drawer structure or capability-gating-rendering decision.

**One-ticket-per-session limit** (research is the only exception). When #96 is done, stop and hand off again — don't start another ticket in the same session.

## The map is near completion

All six decision tickets (#90, #91, #92, #94, #95, #97) are resolved. The remaining frontier is entirely prototype/execution tickets — no decision ticket is open. The map reaches its destination when all prototype tickets are resolved and the route from the current state to the fully-built frontend is clear enough for any agent to pick up an execution ticket and build.

The prototype tickets (#96, #99-#106) are UX decisions that will graduate into build tickets. #93 (testing infrastructure) and #98 (backend endpoint) are task tickets that produce infrastructure/APIs the build tickets will consume. None of these block each other — they can be worked in parallel by multiple sessions.

## Conventions confirmed this session (cumulative across 6 sessions)

- GitHub sub-issues API is enabled. `gh api repos/:owner/:repo/issues/89/sub_issues --jq '.[].number'` lists children. Blocking uses `issues/<child>/dependencies/blocked_by` with `-F issue_id=<blocker db id>` (integer). A ticket is unblocked when `issue_dependencies_summary.blocked_by == 0`.
- Map body edits: fetch with `gh issue view 89 --json body --jq .body > /tmp/opencode/89-body.md`, edit the file, push with `gh issue edit 89 --body-file /tmp/opencode/89-body.md`.
- Resolution comments: write to a temp file under `/tmp/opencode/`, post with `gh issue comment <n> --body-file <path>` (avoids shell-escaping markdown).
- New ticket creation: `gh issue create --title "..." --body-file <path> --label "wayfinder:<type>" --label "ready-for-agent"`, then link as sub-issue in a second pass (`gh api --method POST repos/:owner/:repo/issues/89/sub_issues -F sub_issue_id=<child db id>` — needs the child's db id from `gh api repos/:owner/:repo/issues/<n> --jq .id`). No blocking edges needed if the ticket is unblocked.
- Prototype assets: committed to a throwaway branch named `prototype/00NN-<slug>`, file at `docs/prototypes/00NN-<slug>.md`. The main working branch (`ralph/company-app-full-build`) keeps only the validated decision (ADR if applicable + outline doc) — cherry-pick the docs, leave the prototype code on the throwaway branch.
- The user prefers: evidence over aesthetics; consistency across decisions (flags reintroduced coupling); fog stays visible with its future cost named; scope boundaries don't silently widen ("requires re-scoping" > "default available"); accepted costs named explicitly rather than hidden; intentional asymmetries named so they aren't silently un-done by a future reader "fixing" them.
- **ADR structure (refined across sessions 5 + 6):** when multiple decisions ride the same axis from a prior ADR, frame the primary fork as the ADR's center and the others as consequences or "same axis, applied elsewhere" siblings — NOT as co-equal decisions. ADR-0022 centers on the pessimistic update model; the polling-ownership rule, 403 silent-exit, and 409 pre-commit handling are consequences, not co-equal. This keeps the causal chain honest for a future reader.
- **UI prototype skill in this repo:** Compose Multiplatform desktop prototype via `expect/actual`-free standalone `fun main()` in `composeApp/src/desktopMain/...prototype/...kt`. Temporarily set `compose.desktop.application.mainClass` in `composeApp/build.gradle.kts` to the prototype's Kt file. Restore `mainClass` to `com.companyb.companyapp.MainKt` before committing on the working branch. Pre-commit hooks pass with the prototype code on a throwaway branch (detekt + ktlintCheck + tests are scoped to `:backend`, not `:composeApp`). Note: `Modifier.focusable()` and `Alignment.Stretch` for `Row` vertical alignment are NOT available in this Compose version — use clickable buttons instead of keyboard handlers, and drop `verticalAlignment = Alignment.Stretch` from `Row`.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/prototype`** — the method for #96 (navigation drawer UX — likely UI branch, "What should this look like?"). Check the ticket body first.
- **`/grilling`** + **`/domain-modeling`** — per map Notes; drawer UX may touch terminology (capability-gated item labels, badge rendering for unread notifications count US-24, relief-duty indicator).
- **`/handoff`** — when #96 is resolved and the session is near its limit, compact and hand off again to continue with #93, #98, or any of #99-#106.
