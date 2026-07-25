# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 8

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 8 picked frontier ticket **#107** (Build A — Drawer shell + visual chrome) at user direction, resolved it, posted the resolution comment, closed it, and graduated ticket **#109** (Build B — Notification badge state plumbing) per #96 Q8's "B defers until A closes" plan.

The next session should pick the next frontier ticket.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. #107 row, Not-yet-specified incl. new fog entries below, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- Frontend conventions: `composeApp/AGENTS.md`
- Spec: `docs/specs/0001-frontend-rebuild.md` (drawer US-20–28 at lines 65–81; US-28 capability-filtering at line 81; drawer-capability spec at line 103; responsive scope at line 173; testing decisions line 141–160; iOS out-of-scope line 171)
- ADR 0001 (NavHost routing): `docs/adr/0001-composeapp-navhost-routing.md`
- ADR 0020 (responsive strategy — addendum added to `## Accepted cost` by this session): `docs/adr/0020-composeapp-responsive-platform-target-split.md`
- ADR 0021 (capability refresh two-slice model): `docs/adr/0021-composeapp-capability-refresh-two-slice-model.md`
- ADR 0022 (pessimistic inline editing): `docs/adr/0022-composeapp-pessimistic-inline-editing.md`
- Domain glossary: `CONTEXT.md`; business rules: `docs/business-requirements.md`; architecture: `docs/architecture.md`
- **#107 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/107#issuecomment-5077480046**
- **Commit (this ticket shipped):** `4f672ad` on `ralph/company-app-full-build` — `feat(#107): drawer shell + visual chrome`. 9 files changed: 5 new (`ui/drawer/DrawerContent.kt` + `NotificationBadge.kt` + `HamburgerWithBadge.kt`; `navigation/LocalNavHostController.kt` + `NavHostRouteExt.kt`), 4 edited (`AppNavHost.desktop.kt` + `AppNavHost.android.kt` + `LinearTheme.kt` + ADR-0020).
- **#109 created (graduation from #96 Q8 ticket B):** https://github.com/jsongalvez/company_app/issues/109 — `wayfinder:task` + `ready-for-agent` + `sub-issue of #89` linked. Body cites actual `DrawerContent(modifier: Modifier = Modifier)` signature shipped in #107 (honors the #96 Q1 avoid-stale-framing guard).
- Linear theme: `composeApp/DESIGN.md` + `composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/theme/LinearTheme.kt`
- DrawerViewModelTest + AuthViewModelTest + UiStateTest + ComposeAppCommonTest: `composeApp/src/commonTest/...` — all green after #107 changes.

## Session outcome

**#107 (drawer shell + visual chrome build) — resolved + closed + arrived on master-bound commit.** Session attempted /implement's flow: read locked-decision context → produce code → /code-review (Standards + Spec axes; both subagents initially FAIL) → refactor addressing FIX findings → re-verify (ktlintFormat + multi-target compile + commonTest all green) → commit → post resolution comment → close ticket → append to map #89 Decisions-so-far + frontier table + Not-yet-specified → graduate ticket B (#109).

The /code-review's two-axis parallel subagent passkey was the high-value step: surfaced Standards FAIL on raw hex literals (InkSubtle wasn't referenced despite being available as LinearTheme's `private val` — the build body's "raw Color(0xFF8A8F98) at call site" guidance let it through but didn't notice the token already existed as `private val InkSubtle` in LinearTheme, flagged by detekt `UnusedPrivateProperty`), on triplicated `if (isHighlighted) X else Y` color triad, and on duplicated `isPostClockIn` derivation across both actuals (borderline ADR-0020 smallest-divergent-subtree); surfaced Spec FAIL on DrawerContent signature widened to include `selectedRoute: Route?` + `onItemClicked: (Route) -> Unit` contrary to locked #96 Q7 plain `@Composable () -> Unit`. Refactor (composition-local pattern + token promotion + dedup hoists) addressed both FAILs clean:
1. New `LocalNavHostController` in `navigation/LocalNavHostController.kt` provides `NavHostController` via `staticCompositionLocalOf` with `error("not provided")` default; signed by both AppNavHost actuals via `CompositionLocalProvider(LocalNavHostController provides navController)` around the `*NavigationDrawer` container; consumed by DrawerContent internally for selection tracking + on-item nav. Signature reverts to `@Composable (Modifier) -> Unit` (Modifier per Q7 follow-up). The pattern preserves the locked Q7 seam — passing NavController directly as a param would have re-narrowed the locked entry-point signature (which was the /code-review Spec-axis HARD finding).
2. New `@Composable NavHostController.currentRoute(): Route?` extension in `navigation/NavHostRouteExt.kt` hoists the duplicated isPostClockIn derivation into commonMain (ADR-0020 smallest-divergent-subtree applied to the shared-logic-across-actuals pattern).
3. `LinearTheme.kt:30` change: `private val InkSubtle = Color(0xFF8A8F98)` → public `val InkSubtle = ...` — referenced by `DrawerContent` as the default-text + badge default-color token (was detekt's `UnusedPrivateProperty` flagged token — pre-existing flag now resolved).
4. DrawerRow's `colors` call dedup: hoisted local `unselectedFg` + `unselectedBg` + `primaryHover` vals (`NavigationDrawerItem(color = ...)` named args); `Color(0xFF828FFF)` raw literal stays ONE occurrence per #107 body's "do not extract private val SelectedItemText at file top" guidance (PrimaryHover migration explicitly out-of-scope per #107 body). The Q2 mandatory two-line inline comment lives above the `NavigationDrawerItem(...)` call statement — moved from "between args" placement (which ktlint's "no comment between args" rule blocks) — comment position above the call statement still preserves Q2's intent ("future-dev sees the rationale next to the non-default-color override").

Patterns + post-cache validations (cumulative):
- **Material3 source verification pattern (handoff line 125 of session 7):** applied this session for `PermanentNavigationDrawer` empty-`drawerContent` Row-collapses-to-no-op-arg behavior check (`NavigationDrawer.kt:621-624` source verified). Still the authoritative technique for any Material3 Compose Multiplatform API surface claim before locking on it: `unzip -p /home/jayson/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material3/material3/1.10.0-alpha05/8671428ba0ea55c3be69fcfc937f45e1c2d2ca1d/material3-1.10.0-alpha05-sources.jar commonMain/androidx/compose/material3/NavigationDrawer.kt` (sources jar path may version-bump; `find ~/.gradle/caches -path "*material3*alpha*" -name "*sources.jar"` re-resolves the current).
- **`ModalDrawerSheet(drawerState)` overload** — predictive-back-enabled variant (verified #96 Q5 close-out check this session too). The non-drawerState overload at `NavigationDrawer.kt:648` skips predictive-back handling. `gesturesEnabled=false` on the parent `ModalNavigationDrawer` is LOAD-BEARING (prevents a three-way edge-gesture collision); predictive-back handler is gated on `drawerState.isOpen` (NOT `gesturesEnabled`), so closed-drawer + gesturesEnabled=false registers no back interceptor — pushed-route back-pop wins cleanly. Q5 three-mechanism orthogonality confirmed by source + remains actionable for future pushed-detail-route tickets.
- **ktlint "no comment between args" rule** (new this session): design-rationale comments placed INSIDE a composable call's args list (between `arg = ...` and `arg2 = ...`) are auto-corrected-to-failktlint. Refix: place the comment ABOVE the composable call statement (which keeps it visually adjacent for "future-dev sees the rationale next to the override"). Initial draft of `DrawerContent.kt` had the Q2 inline comment INSIDE NavigationDrawerItem args — ktlintFormat FAILED on `DrawerContent.kt:135:2` + `DrawerContent.kt:139:50` "No comment expected at this location (cannot be auto-corrected)". After moving the comment ABOVE the NavigationDrawerItem call (still between statements in the function body), ktlintFormat succeeded. Same fix applied to the `gesturesEnabled=false` load-bearing comment in `AppNavHost.android.kt` — moved above `ModalNavigationDrawer(...)`.
- **detekt `MagicNumber` carve-out empirically narrow** (new this session, posting as fog on map #89 `Not yet specified`): the `ignoreNamedArgument: true` carve-out at `config/detekt/detekt.yml:643` exempts literals appearing as a *direct* named-arg value (`func(param = 42)`) — NOT literals wrapped inside a function call passed to the named arg (`selectedTextColor = Color(0xFF828FFF)` — the `0xFF828FFF` is a positional arg of `Color()`, not a named arg of `colors()` — detekt flags it as MagicNumber). The #96 body's claim that `color = Color(0xFF828FFF)` "passes detekt MagicNumber via the `ignoreNamedArgument` carve-out" is empirically wrong. `:composeApp:detektMetadataCommonMain` reports 58 weighted issues including every `private val Color(0xFF...)` in `LinearTheme.kt` (and the deprecated `Modifier.menuAnchor` calls in `SessionCreateScreen.kt` + ForbiddenTodo in `HomeScreen.kt:310`), all pre-existing and non-gate-blocking (pre-commit runs `:backend:detekt` only; pre-push runs `:composeApp:compile*` for multi-target but not detekt; `:composeApp:detekt` umbrella task is `NO-SOURCE`). The strategic cleanup fork documented in the new Not-yet-specified fog entry: (a) extend LinearTheme.kt with PrimaryHover + remaining missing tokens — `InkSubtle` already done this way this session; (b) `@Suppress("MagicNumber")` selectively (tactical, like `backend/src/main/kotlin/com/companyb/companyapp/api/routes/RoutesUtil.kt`); (c) introduce a detekt baseline file + add composeApp per-source-set `detektMetadataCommonMain`/`detektDesktopMain`/`detektAndroidDebug`/... tasks to pre-push for an effective composeApp gate.
- **`@Composable NavHostController.currentRoute(): Route?` helper hoisted** (new this session): `toRoute<Route>()` from `androidx.navigation.toRoute` works inside a `@Composable fun NavHostController.currentRoute(): Route?` extension via sealed-class polymorphic kotlinx-serialization dispatch (verified by working build).
- **`LocalNavHostController` CompositionLocal pattern** (new this session — new fog-not-fog entry on map): when a locked-slot composable (#96 Q7 style) needs NavController access (selection track + on-click nav), the CompositionLocal-register-at-AppNavHost-root + consume-internally pattern preserves the locked signature without re-narrowing. `staticCompositionLocalOf<NavHostController> { error("not provided") }` factory with explicit onError strengthens the contract for future cases. Future build tickets wanting the same shape should lift this pattern.
- **pre-commit hook is installed and active** (despite `.git/hooks/` showing only sample files — likely via core.hooksPath or hooks autodetected by `git commit` — pre-commit hook ran "Checking Postgres connectivity at localhost:5432..." → "All checks passed — commit allowed" this session; success, no failure). Pre-push hook (per AGENTS.md) runs full JMH suite + composeApp multi-target compile + k6 load-test; pre-push hook file not currently installed (sample only) — when next session pushes, run `git push` with sufficient timeout (600000ms+) since the pre-push takes ~4 minutes per AGENTS.md.

## Current frontier (verified live post-session)

**11 unblocked tickets** (per `gh api repos/jsongalvez/company_app/issues/89/sub_issues --jq '.[] | select(.state=="open") | .number'`):

| # | Title | Type | Suggested session shape |
|---|-------|------|--------|
| 93 | Set up testing infrastructure (MockEngine + commonTest) | task (AFK-capable) | build |
| 98 | Add GET /api/me/branches endpoint | task (AFK-capable) | backend build |
| 99 | Prototype the Clients screen UX | prototype | HITL grilling |
| 100 | Prototype the Inventory screen UX | prototype | HITL grilling |
| 101 | Prototype the Finance screen UX | prototype | HITL grilling |
| 102 | Prototype the Notifications screen UX | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX | prototype | HITL grilling |
| 109 | Build B — Notification badge state plumbing (graduated from #96) | task | build via `/implement` |

**Zero blocked.** All 8 feature-screen prototypes are independent. #93 is AFK-capable infra ticket. #98 is the only backend ticket. #109 is the freshly-graduated build ticket from #96 Q8 plan + cites the actual `DrawerContent` signature shipped this session — ready for any agent to pick up.

## Recommended next pick: #109 OR #93

**#109 (Build B — notification badge state plumbing)** is highest-leverage if executed next:
- The session just locked `DrawerContent(modifier: Modifier = Modifier)` + `HamburgerWithBadge` + `NotificationBadge`-properties + the `unreadCount: Int? = null` site (currently null in `DrawerContent.kt` and at the `HamburgerWithBadge` call in `AppNavHost.android.kt`); freshness is high — never cheaper to wire state than now
- #109's body recites #96 Q6's locked decisions verbatim (same explicit build-checklist inducture pattern as #107): `NotificationState` singleton shape mirroring `SessionState`, `NotificationBadgeViewModel` shell-scoped 60s poll, optimistic-decrement wire-up from `NotificationViewModel.markRead`, accepted-cost inline comment at the poll-overwrite site. Body cites actual `DrawerContent(modifier: Modifier = Modifier)` signature + the `unreadCount` null-literal sites to replace — less ambiguous than #107 was (which awaited #92/#94/#108 prior locks)
- `wayfinder:task` build ticket, not a grilling ticket — drive via `/implement` like #107, NOT via `/grilling` + `/domain-modeling`. Do NOT re-litigate any #96 Q6 decision; #109's body has the explicit build-checklist for any reasoning the build agent wants to revisit. The /code-review pattern (both axes) is prescribed at end of /implement — applied successfully this session, worth applying for #109 too.

**#93 (testing infrastructure — MockEngine + commonTest)** is highest leverage if a build session is undesirable next:
- The ViewModel-test infrastructure is what every future build ticket (including #109's `NotificationBadgeViewModel`) will rely on for its quality gate (`composeApp/AGENTS.md` + `docs/specs/0001-frontend-rebuild.md:149-160` — MockApiClient helper, `composeApp/src/commonTest/`, control StateFlow transitions). Note: foundation #108 already shipped a minimal commonTest (`TestHelpers.kt` with `mockApiClient`/`mockEngine` + the existing `DrawerViewModelTest` + `AuthViewModelTest` + `UiStateTest`); #93 is the *generalization + extension* ticket (multi-shell VM scope, commonTest framework patterns tightened, reached-test coverage). Independent, ready-for-agent.

Both are good picks. Appetite fork: #109 if you have time for a build-session with verification at compile + manual smoke test; #93 if you want an AFK-capable infra task. Wayfinder doctrine "you pick the next frontier ticket in order" permits either; the handoff's frontier table lets you instruct the session-9 agent by name.

For #109 specifically: drive via `/implement`, not `/grilling` + `/domain-modeling`. Before starting, fetch #109's full body:
```bash
gh issue view 109 --json body --jq .body
```

## How to drive the next session (wayfinder "Work through the map")

(Reproduced from session-7 handoff — same guidance; only update # of next ticket.)

1. `gh issue edit <N> --add-assignee @me` — claim FIRST, before any work (N = chosen ticket number). Verify no concurrent sessions on the same frontier (sub-issues assignees empty).
2. If #109: load `/implement` skill — `wayfinder:task` tickets are execution, not decisions. Do NOT re-grill #96 Q6 locked decisions; #109's body has the explicit build-checklist. If a hidden ambiguity surfaces, escalate to the user, don't silently re-decide. /code-review (both axes) at end.
3. If #93 (AFK infra): same `/implement` flow;门槛 set up testing patterns (multi-VM MockEngine harness, setUp/tearDown for SessionState across tests). Common-test has minimal existing infra from #108 foundation to extend.
4. If one of #99–#106 (prototype): use `/prototype` + `/grilling` + `/domain-modeling` per map Notes (same pattern as #96 + #97). Look up facts in the repo rather than asking. User preferences (from cumulative sessions): prior-locks-as-load-bearing (treat prior ADRs as constraints whose answer is implied); falsification-before-claim (check the number that could falsify before locking); cost-of-sidestep-over-cost-of-accept; accepted-costs-named-not-hidden; fog-as-actionable-not-vague; honest failure-mode; least-restrictive-signature-as-API-principle. Check for ktlint "no comment between args" rule placement opportunity BEFORE locking inline-comment placement (place above the call statement not between args).
5. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** section (fetch body → edit → `gh issue edit 89 --body-file`).
6. Graduate any fog the answer makes specifiable (e.g., #93 might graduate the `PrimaryHover` migration hardening sub-ticket if testing casts light on color tokens).
7. If the answer reveals a ticket sits past the destination, close it and add a line to **Out of scope**.
8. If the decision meets the ADR bar (hard to reverse + surprising without context + real trade-off), offer an ADR per `/domain-modeling`. Apply the **#96/#107 explicit discriminator**: when a ticket's decisions all trace back to existing ADRs through extensions of their axes (rather than *establishing* a new axis), the ticket does NOT graduate a new ADR — even if the ticket is dense. #107 was dense (5 files built, /code-review corrections, ADR-0020 addendum); #107 did NOT graduate ADR-0023 because every non-obvious choice traced back to ADR-0001/0020/0021/0022 already on record via extensions. ADR-0020's addendum is an *addendum to an existing ADR*, not a new ADR. Apply this same test to future tickets before creating ADR-0023+.

**One-ticket-per-session limit** (research is the only exception). When the chosen ticket is done, stop and hand off again — don't start another ticket in the same session. Write the next handoff doc to `docs/agents/wayfinder-<N>-handoff.md` where N is the resolved ticket number — follow the prior-session handoff format.

## Map state at session-end

Map #89 body updated this session:
- Frontier table: #97 → ✅ closed (was stale-listed as 🔓 unblocked; actually state closed well before this session — front table now reflects reality). #107 → ✅ closed. #109 added as 🔓 unblocked.
- Decisions-so-far: new long-line entry for #107 (cites commit `4f672ad`, detail-rich enough that future agents reading the map at low res can judge relevance without re-reading the #107 resolution comment URL).
- Not-yet-specified **new fog entries** (cumulative for next session to inbound):
  - **detekt `MagicNumber` carve-out empirically narrow + composeApp gate readiness** (cleanup fork: (a) LinearTheme.kt PrimaryHover migration + remaining missing tokens; (b) `@Suppress("MagicNumber")` tactical; (c) detekt baseline + pre-push composeApp gate).
  - **`LocalNavHostController` CompositionLocal pattern surfaced** (documentation; future locked-slot composables in need of NavController access can lift this pattern instead of widening the slot signature — not ticket material, no decision pending).
- Closing paragraph updated to reflect 11 unblocked tickets + #109 ready-for-pick-up.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for #109 (or any build ticket like #93 / #98) + the next prototype-graduated build ticket (like #96 → #107 → #109). Build tickets, not grilling tickets; #109's (or its sibling's) body has the explicit build-checklist.
- **`/prototype`** + **`/grilling`** + **`/domain-modeling`** — for any of #99–#106 (feature screen prototypes). Per map Notes; same pattern as #96 + #97. Check the ticket body first — some prototypes are HITL ("What should this look like?") and some are logic ("Does this state model feel right?"); pick the prototype branch accordingly.
- **`/research`** — for any Compose Multiplatform / Ktor / Material3 API surface the build or prototype needs to verify. Use the Material3 source-jar lookup pattern (unzip + read sources) for any "the docs say X" claim before locking on it. Sources jar path may version-bump; `find ~/.gradle/caches -path "*material3*alpha*" -name "*sources.jar"` re-resolves the current.
- **`/code-review`** — at end of `/implement` flow: parallel Standards + Spec subagents. Worth running: this session surfaced the two FAILs (raw hex literals + signature widening) that wouldn't otherwise have been caught post-build.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md` for the session-9 agent to pick up.