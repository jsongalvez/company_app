# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 8

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). This session began by claiming #107 (drawer shell + visual chrome build, graduated from #96 in session 7), hit a hidden-ambiguity mid-build (the #107 body cited `expect fun AppNavHost`, `object SessionState`, `class DrawerViewModel`, `LinearTheme.CardTitle` as pre-existing code, none of which existed in the working tree — every prior decision ticket was decision-only), escalated to the user per handoff line 103, the user picked fork B (graduate foundation ticket, block #107), the session then created #108, claimed it, built the foundation, verified compile/format/tests, posted resolution comment + closed #108 + updated map Decisions-so-far + handoff.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 — Destination, Notes, Decisions-so-far (now includes #108 entry), child-tickets table (#107 now 🔓 unblocked, #108 ✅ closed), Not-yet-specified (Foundation-gap fog line removed post-#108 close; Orphan-code fog line reworded for action-readiness once #108 closed), Out-of-scope.
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Setup-handy conventions: `docs/agents/issue-tracker.md` → "Wayfinding operations"
- composeApp conventions: `composeApp/AGENTS.md`
- ADRs consumed by #108 (NOT graduated): ADR-0001 (`docs/adr/0001-composeapp-navhost-routing.md` — typed-route NavHost), ADR-0020 (`docs/adr/0020-composeapp-responsive-platform-target-split.md` — compile-time platform-target split via `expect/actual`), ADR-0021 (`docs/adr/0021-composeapp-capability-refresh-two-slice-model.md`), ADR-0022 (`docs/adr/0022-composeapp-pessimistic-inline-editing.md`).
- Pre-built drawer build checklist (still unblocked, ready-to-pick): `.scratch/frontend-rebuild/issues/11-navigation-drawer.md`
- Linear theme + design tokens: `composeApp/DESIGN.md` + `composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/theme/LinearTheme.kt` (foundation edited the latter: `titleLarge` now `FontWeight.SemiBold` mapping DESIGN.md:54-59 card-title slot)
- Spec: `docs/specs/0001-frontend-rebuild.md`
- Domain glossary: `CONTEXT.md`; business rules: `docs/business-requirements.md`; architecture: `docs/architecture.md`
- **#108 resolution comment (this session's build receipt): https://github.com/jsongalvez/company_app/issues/108#issuecomment-5077323751**
- Pre-session-7 handoff doc: `docs/agents/wayfinder-96-handoff.md` (session-7 patterns the session-8 build absorbed)
- **The 4 new files + 4 modified files of #108** (uncommitted on working tree; user to commit at discretion):
  - New (untracked): `composeApp/src/commonMain/kotlin/com/companyb/companyapp/navigation/Route.kt`, `/state/SessionState.kt`, `/navigation/AppNavHost.kt` (expect declaration), `/viewmodel/DrawerViewModel.kt`, `composeApp/src/androidMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.android.kt`, `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.desktop.kt`, `composeApp/src/commonTest/kotlin/com/companyb/companyapp/viewmodel/DrawerViewModelTest.kt`
  - Modified: `gradle/libs.versions.toml` (+navigation-compose 2.9.2 entry), `composeApp/build.gradle.kts` (commonMain dep added), `composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/theme/LinearTheme.kt` (titleLarge→SemiBold+inline comment), `composeApp/src/commonMain/kotlin/com/companyb/companyapp/App.kt` (rewritten — 98→51 lines, AppNavHost wired)

## Session outcome

**#108 (frontend foundation ApNavHost+SessionState+DrawerVM skeleton Build) — resolved + CLOSED** via `/implement` skill (NOT `/grilling` — `wayfinder:task` ticket does an execution, not decisions; body assesses its own blueprint).

The build covered all 12 checklist items in #108's body, with one body-vs-build-reality deviation surfaced + captured (foundation body section E proposed `val CardTitleTextStyle` top-level — `Font()` requires `@Composable` scope, so top-level non-composable val impossible; resolution maps DESIGN.md:54-59 card-title spec onto Material3's existing `titleLarge` Typography slot via `fontWeight = FontWeight.SemiBold` adjustment, documented inline at the call site with 3-line DESIGN.md reference comment).

**Files built:** 4 modified + 7 new (incl. 3 new packages: `navigation/`, `state/` in commonMain; `navigation/` in androidMain + desktopMain).

**Verification tasks all green:**
- `./gradlew ktlintFormat` — auto-fixed `Routes.kt`→`Route.kt` filename (single-class ktlint rule). Pattern absorbed: writing one `sealed class X {}` file → name the file `X.kt`, not `Xs.kt`.
- `./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid` — both pass. Only warnings: (a) `SessionState.kt:28` `GlobalScope` delicate-API — accepted cost (singleton lifetime = app lifetime; GlobalScope matches; intent inline-documented); (b) pre-existing `Modifier.menuAnchor()` deprecation in un-touched `SessionCreateScreen.kt` (not #108's concern; CodeReview of #94-grad will surface it when refactor lands).
- `./gradlew :composeApp:detekt :composeApp:ktlintCheck` — both pass.
- `./gradlew :composeApp:desktopTest` — 14 tests across 4 suites (`ComposeAppCommonTest` 3, `AuthViewModelTest` 2, `DrawerViewModelTest` 4, `UiStateTest` 5), all pass. DrawerViewModelTest exercises: all-cap visible → 8 items visible; empty-caps → only Notifications + AuditLog visible (capabilityCode==null rule); partial caps → only matching + always-visible; capability-changes-after-construction → reactive SessionState.capabilities.collect emits + uiState updates.

**Fork-B graduation:** the user picked "Graduate foundation ticket, block #107 (Recommended)" when confronted with the hidden ambiguity. #108 was created, linked as sub-issue to #89 (via GraphQL `addSubIssue` mutation — pattern absorbed: shell `gh api graphql` + mutation with global IDs from `repos/:owner/:repo/issues/<n>` `.id` field — see "Conventions confirmed" below for exact incantations). #107 was blocked against #108 via REST `POST repos/:owner/:repo/issues/107/dependencies/blocked_by -F issue_id=<#108 db id 4974550828>` — returns the full updated #107 issue JSON in the response; verify success via `gh api repos/jsongalvez/company_app/issues/107 --jq '.issue_dependencies_summary'` showing `blocked_by == 1`. Once #108 closed, that summary went to `blocked_by == 0` automatically, clearing #107's frontier placement ✅.

**Decisions consumed (NOT graduated):** #108 specifically consumed ADR-0001 (typed-route NavHost) + ADR-0020 (compile-time platform-target split via `expect/actual`) + #92 (SessionState singleton shape) + #94 (login flow "don't trust cached state" axis preserved in App.kt's unauthorized handler) + ADR-0022 (pessimistic inline-update axis — extended consistently in App.kt's clear-on-401 handling). **No ADR-0023 graduated** — per session-7 discriminator (handoff line 129): build-phase ticket that activates a locked axis in code is NOT establishing a new architectural axis. Builds absorb ADRs as code activations; the test "establishes new axis or extends existing" applies symmetrically to BUILD tickets + decision tickets.

**Out-of-scope named + deferred per "scope boundaries don't silently widen":**
- `SessionState` writer-call-site integration (login → `GET /api/me → setUser + setCapabilities`, clock-in → `setSelectedBranch`) — deferred to #94-grad. Foundation declares the writer contract API; doesn't wire call-sites. Intentional: avoids extending #108 into another 2-4h of SessionState→Auth/Attendance VM call-site integration. AppNavHost actuals use `tokenStore.getToken() != null` (NOT `SessionState.currentUser`) for `startDestination` computation specifically as a result.
- `HomeScreen` / `ClientSearchScreen` / `SessionCreateScreen` refactor into NavHost-mounted screens — orphan code pending refactor at #94-grad graduation OR a dedicated cleanup ticket. Map #89 `## Not yet specified`'s "Orphan code paths" entry has been reworded post-#108-close to reflect "sharp + ready-to-ticket, next-session graduates or absorbs".
- `PrimaryHover` token migration to `LinearTheme.kt` — pre-existing fog from #96 Q2; out of #108 scope; #107 will use raw `Color(0xFF828FFF)` at the call site.
- iOS AppNavHost actual — out of effort scope (map Notes).
- `LinearTheme.kt` `Material3 Typography` augmentation with a new `cardTitle` slot — addressed via existing `titleLarge` slot remap (not new slot); the rationale is inline-documented at `LinearTheme.kt` line 102.

## Current frontier (verified live)

**12 unblocked tickets** (#107 unblocked by #108's close, total restored to 12):

| # | Title | Type | Status |
|---|-------|------|--------|
| 93 | Set up testing infrastructure (MockEngine + commonTest) | task | 🔓 unblocked |
| 98 | Add GET /api/me/branches endpoint (assigned branches + clock-in status) | task | 🔓 unblocked |
| 99 | Prototype the Clients screen UX (search, detail, anonymization) | prototype | 🔓 unblocked |
| 100 | Prototype the Inventory screen UX (stock levels, product sales, drill-down) | prototype | 🔓 unblocked |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | 🔓 unblocked |
| 102 | Prototype the Notifications screen UX (list, unread badge, mark-as-read) | prototype | 🔓 unblocked |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | 🔓 unblocked |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | 🔓 unblocked |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | 🔓 unblocked |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | 🔓 unblocked |
| 107 | Build A — Drawer shell + visual chrome (graduated from #96) | task | 🔓 unblocked (#108 unblocked it) |

**Zero blocked tickets.** All decision tickets (#90-#97) closed; one build ticket closed (#108); one critical build ticket (#107) is now unblocked and at maximum freshness for execution; 8 feature-screen prototype tickets (#99-#106) are independent; 2 build-infrastructure tickets (#93 testing, #98 backend endpoint) still open and unblocked.

## Recommended next pick: #107

**#107 (drawer shell + visual chrome build)** is now the strongest pick. Why:
- Foundation #108 closed → #107's prerequisites (AppNavHost, SessionState, DrawerViewModel, LinearTheme CardTitle) are landed concrete code; #107's body now reads correct references, not anticipated spec text.
- #107 was drafted in session 7 as #96's graduate — its body is an explicit build-checklist reciting #96's eight locked Qs verbatim. Picks up where session 7 left off with the highest freshness + lowest required context-loading.
- The drawer shell is shared chrome the 8 feature-screen prototypes (#99-#106) will sit inside — first-session-with-drawer-build gives those future prototype builds a concrete `DrawerShell` artifact to reference in their build-checklists.
- Build ticket (`wayfinder:task`), not a prototype grilling — produces a deliverable; cleanly distinct from decision session.

**Recommended for #107:** load `/implement` skill (per session-7 handoff line 132/135), NOT `/grilling`. Do NOT re-litigate any decision in #107's body — all locked; explicit pointers to #96's resolution comment for any reasoning the build agent wants to revisit. Fetch #107's full body first:
```bash
gh issue view 107 --json body --jq .body
```
Before starting, also verify the foundation code is actually present on the user's current branch (the session-8 doc commits to `ralph/company-app-full-build` branch — typically the branch sequence expected; verify with `git log --oneline -5` that the foundation is committed or follow user commit instructions). If foundation files appear missing (uncommitted on the working tree), prompt the user — do NOT silently re-build the foundation, that risks frame drift.

**Alternative picks if #107 prefers a different session type:**
- **Graduate a #94-grad Build — Login + capabilities fetch flow + BranchSelect surface + SessionState writer integration** — the map's "Orphan code paths" fog entry is now sharp enough to graduate (now that #108's actual-built `AppNavHost` signature is concrete code at `composeApp/src/commonMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.kt`); the work includes POST-login `GET /api/me → setUser + setCapabilities` integration, clock-in → `setSelectedBranch`, HomeScreen refactor into BranchSelectScreen mounted at `Route.BranchSelect`. Roughly the #94 decision ticket's build-phase graduation.beros: NOT pre-slice the fog if you accept Fork A; the re-check action item is on the map says next session picks.
- **#93 (testing infra — MockEngine + commonTest)** — AFK-capable, independent, ready-for-agent. existing commonTest already has helpers (uiStateTest/AuthViewModelTest/ComposeAppCommonTest/TestHelpers.mockApiClient) — #93 already added value would be either more MockEngine test helpers OR a common test-patterns guide. Worth a fresh read of #93's body to confirm its actual scope; it may have been overtaken by what #108's #94-grad #97-grad build tickets will produce.
- **One of #99–#106 (feature-screen prototype grilling)** — HITL; simultaneously helps clarify whether feature-screen build tickets need to write to AppNavHost's composable blocks (replacing PlaceholderRoute).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map at https://github.com/jsongalvez/company_app/issues/89; refresh `gh issue view 89 --json body --jq .body` to see session-8's child-tickets table state (post-#108-close) + Decisions-so-far entry for #108.
2. Pick the next ticket (#107 recommended per above).
3. `gh issue edit <N> --add-assignee @me` — claim FIRST, before any work. Verify no concurrent session has already claimed (check `gh issue view <N> --json assignees --jq '.assignees[].login'`).
4. If #107: load `/implement`. Run `./gradlew ktlintFormat :composeApp:detekt :composeApp:ktlintCheck :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest`. Apply at any site where #107's body says "intentionally NOT <default reflex>" — the inline-comment requirement (per #96 Q2) lives at the call site ("next to the code that looks wrong to a glance"), not in a ticket thread.
5. If one of #99-#106 (prototype): use `/prototype` + `/grilling` + `/domain-modeling` per map Notes; same pattern as #96 + #97. User-prefence carryforward at "Conventions confirmed" below.
6. Post the answer as a resolution comment on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** section via `gh issue edit 89 --body-file /tmp/opencode/89-body.md` — fetch body first, edit the section, push back.
7. Graduate any fog the answer makes specifiable. Per the fog guidance: "don't pre-slice; one patch may graduate into several tickets, or none."
8. If the answer reveals a ticket sits past the destination, close it and add a line to **Out of scope**.
9. If the decision meets the ADR bar (hard to reverse + surprising without context + real trade-off), offer an ADR per `/domain-modeling` — apply the session-7 discriminator first: check whether the new ticket *establishes* an axis or *extends* an existing one. #108 did NOT graduate ADR-0023 (consumed ADR-0001+ADR-0020+ADR-0021+ADR-0022 + #92 + #94 axes — activates them in code, doesn't establish new).

**One-ticket-per-session limit** (research is the only exception). When the chosen ticket is done, stop and hand off again — don't start another ticket in the same session. Use `/handoff` skill or this handoff-pattern.

## The map is near completion

The map's frontier is now almost entirely execution tickets — no decision ticket is open. The only decision-inflected fog remaining is the Orphan-code-paths/sharp-now patch (which is sharpest for attending at #94-grad graduation). The map reaches its destination when all prototype tickets are resolved AND the route from current state (foundation + drawer-chrome + login/BranchSelect/Dashboard builds + 8 feature-screen builds) to the fully-built frontend is clear enough for any agent to pick up an execution ticket and build.

Currently open tasks standing between the destination and now (rough order): #107 (drawer chrome) → graduate #94-grad (login/BranchSelect/caps fetch flow) OR orphan-code-dedicated-refactor → 8 feature-screen build tickets (graduating from #99-#106 prototypes) → ticket B (notification badge state plumbing, graduating from #107) → final smoke-test build ticket. Each of these can be done in parallel by multiple sessions where the dependencies permit.

## Conventions confirmed this session (cumulative across 8 sessions)

- GitHub sub-issues API uses GraphQL `addSubIssue` mutation (NOT REST). Sub-issue linking incantation:
  ```bash
  # Get global IDs via GraphQL
  gh api graphql -f query='query { repository(owner:"jsongalvez", name:"company_app") { parent: issue(number:89) { id } child: issue(number:108) { id } } }' --jq '.data.repository'
  # Returns e.g. {"parent":{"id":"I_kwDORLu6_c8AAAABJeLhaQ"}, "child":{"id":"I_kwDORLu6_c8AAAABKIGfLA"}}
  # Link as sub-issue
  gh api graphql -f query='mutation { addSubIssue(input: {issueId: "<parent-global-id>", subIssueId: "<child-global-id>"}) { issue { number } subIssue { number } } }'
  ```
  `gh api repos/:owner/:repo/issues/89/sub_issues` (REST) does NOT work — returns 404.

- Blocking edges via REST with public `issue_dependencies_summary` field on the issue JSON:
  ```bash
  # Block #107 against #108 (need #108's REST numeric db id: 4930593129 for #89 / 4974550828 for #108):
  # First get the BLOCKER's REST id:
  gh api repos/jsongalvez/company_app/issues/108 --jq .id  # → 4974550828
  # POST the blocking edge:
  gh api --method POST repos/jsongalvez/company_app/issues/107/dependencies/blocked_by -F "issue_id=4974550828"
  # Verifies on success — response is the full updated #107 issue JSON; inspect .issue_dependencies_summary.blocked_by == 1
  # After #108 closes, the BLOCKED issue's blocked_by goes to 0 automatically — no explicit clear needed.
  ```
  This contradicts session-7 handoff line 120's "*Blocking uses `issues/<child>/dependencies/blocked_by` with `-F issue_id=<blocker db id>` (integer)*" — the session-7 wording was right but didn't note the URI structure / the GraphQL addSubIssue caveat; this handoff clarifies both.

- Map body edit protocol (unchanged from session 7): fetch with `gh issue view 89 --json body --jq .body > /tmp/opencode/89-body.md`, edit the markdown file (table row insertions in `## Child tickets (frontier)`, append one-line gist + URL in `## Decisions so far`, append patch in `## Not yet specified`, optionally delete resolved patches from same section), push with `gh issue edit 89 --body-file /tmp/opencode/89-body.md`.

- Resolution comments: write to `/tmp/opencode/<N>-resolution.md`, post with `gh issue comment <N> --body-file /tmp/opencode/<N>-resolution.md`. Then `gh issue close <N> --comment "<short receipt summary>"` if a closing reason is wanted.

- Dependency overview (verified this session): for composeApp under CMP 1.10.2, navigation-compose at `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` (based on androidx navigation 2.9.7 per https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.10.2 — JetBrains KMP forked artifact, not the AndroidX native one, because that lacks multiplatform targets). `libs.versions.toml` updated to include `navigation = "2.9.2"` + `navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }`. The artifact IS multiplatform KMP-compatible (works in both `commonMain.dependencies{}` AND for desktop + android compilation targets). Lifecycle currently pinned `2.9.6` (older than CMP 1.10.2's `2.10.0-beta01` pairing; backward-compatible — bumping lifecycle in a future ticket would be a separate axis, do not silently bump in a build ticket of narrower scope).

- Detekt config reference (unchanged from session 7): `config/detekt/detekt.yml:629-646` — `MagicNumber` rule active, `ignoreNamedArgument: true` carve-out at line 643 lets color literals pass as named args; `ignoreConstantDeclaration: true` lets `private val Color(0xFF...)` pass at file top. No `ForbiddenImport` or color-literal lint rule. #108 added one new `Color(0xFF...)` site to be aware of? No — actually it added NO color literals; the `LinearTheme.kt:102` edit just added `fontWeight = FontWeight.SemiBold` parameter. Existing colors unchanged.

- Material3 source-jar lookup pattern (unchanged from session 7): `unzip -p /home/jayson/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material3/material3/1.10.0-alpha05/<hash>/material3-1.10.0-alpha05-sources.jar commonMain/...kt`. Sources jar path may version-bump; check `find ~/.gradle/caches -path "*material3*alpha*" -name "*sources.jar"` for current.

- Compose Multiplatform Material3 `Typography` class — does NOT support arbitrary extra slots (no `cardTitle` slot, only the standard 22 Material3 typography slots `displayLarge` through `labelSmall`). Custom typography surfaces are typically (a) mapped onto an existing slot via `TextStyle` overrides (foundation's chosen path for CardTitle→titleLarge); (b) exposed as a wrapper `LinearTypography` dataclass around `MaterialTheme.typography` (over-engineering for one slot, rejected by foundation for CardTitle). Pattern: prefer existing-slot remap unless the semantic divergence between the Material3 slot role + the custom token's role causes future consumers to confuse them; ifso, evaluate the wrapper.

- Compose `Font()` from `org.jetbrains.compose.resources` requires `@Composable` scope — top-level non-composable `val textStyle = TextStyle(fontFamily = FontFamily(Font(Res.font.X)))` does NOT compile. Whenever a build-spec proposes a "declared top-level TextStyle" with `Font()` calls, the implementer must either choose a composable-scoped `@Composable fun textStyle(): TextStyle` OR map to existing Material3 Typography slots. Foundation picked the latter (lower surface, smaller footprint, Material3's slot reuse philosophy).

- KtLint single-class-filename naming rule (incubating catch for foundation): a file containing one top-level sealed/object/class named `Route` must be named `Route.kt` — `Routes.kt` is auto-rejected. Future tickets writing `sealed class X {}` should name the file `X.kt`. Authored files comply by ktlintFormat auto-fix when possible (filename rename) OR fail at lint time.

- **CMP version-aligned dependency lookup pattern (used this session, surfaced for future tickets):** when adding a non-default Compose Multiplatform-family artifact (`navigation-compose`, `navigation-compose-adaptive`, `material3.adaptive`, `lifecycle-viewmodel-savedstate`, etc.), check the matching CMP version's release manifest for the aligned artifact version. The lookup URL is: `https://github.com/JetBrains/compose-multiplatform/releases/tag/v<CMP-VERSION>` — the page lists every library group + version pairing (Runtime, UI, Material3, Lifecycle, Navigation, Navigation3, Navigation Event, Savedstate, WindowManager Core). Alternatively query Maven Central `https://repo1.maven.org/maven2/org/jetbrains/androidx/navigation/navigation-compose/maven-metadata.xml` for available versions + pick the one matched. Don't afternoon-guess.

- **Pattern surfaced for BUILD tickets (carryforward, applies to all future `wayfinder:task` build tickets):**
  1. **Body as spec-hint, not as law** — when the build-phase ticket body proposes a code shape that doesn't survive build-reality (deps, language constraints, API surface), resolve via the smallest deviation that aligns body intent with the existing constraint; document the deviation inline + in the resolution comment AND in the map Decisions-so-far entry. Don't silently swallow the deviation; don't silently re-pick the body alternative without context.
  2. **The "scope boundaries don't silently widen" rule applies symmetrically** — applying to BOTH spec-text expansion (don't grow #108 into #94-grad / #98 / etc. call-site integration) AND to code expansion (don't pre-implement consumer-of-this-foundation wiring beyond what the consumer actually depends on). Foundation produced the SURFACE the consumer needs without integrating writer-side call-sites (which the consumer doesn't read) — same axis applies to the next build's spec-text + interaction with future consumer tickets.
  3. **Consuming-locked-ADRs-as-code is the build-phase manifestation of "establishes new axis vs. extends existing"** — session-7's ADR discriminator (handoff line 129) applies equally to BUILD tickets. BUILD tickets that activate a locked ADR's axis in code DO NOT graduate a new ADR. BUILD tickets that chart an architectural axis the ADRs didn't lock DO graduate. #108 activated ADR-0001 + ADR-0020 + #92 + #94 + ADR-0022's locked axes; therefore no ADR-0023.
  4. **Hidden ambiguity surfaced mid-build isemapscalated via fork-choice, not silently resolved** — handoff line 103's instruction applies to BUILD tickets too. Pattern: surface the gap → enumerate fork options + present a recommendation (B = graduate prerequisite + block dependent = strict-scoped re-scope via graduation; C = silently swallow scope = forbidden by "don't widen silently"; A = narrow-foundation-only = risks orphaned chrome). User picks the fork; agent never silently re-decides.

- **Verification recipe for composeApp builds (gradle tasks order — claude session-budget-aware):**
  ```bash
  # Single invocation typically suffices (all requested tasks in one config-cache pass):
  ./gradlew ktlintFormat --console=plain                                     # auto-fix format; surfaces lint rules
  ./gradlew :composeApp:compileKotlinDesktop --console=plain                 # catches commonMain + desktopMain fast
  ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:detekt :composeApp:ktlintCheck :composeApp:desktopTest --console=plain
  # All four go in one invocation; BUILD SUCCESSFUL line at the end means everything passed.
  ```
  Don't run pre-commit hooks (`bash scripts/setup-hooks.sh`) for foundation-style builds that don't touch backend — the hook requires Postgres reachability + runs `:backend:test`, neither needed for composeApp work. User can commit manually with hooks; verify work via gradle tasks above.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for #107 (drawer shell + visual chrome build). Build ticket, not a grilling ticket; #107's body is the explicit build-checklist. If rendering questions surface mid-build and weren't covered in #96, escalate to the user rather than silently re-deciding.
- **`/prototype`** + **`/grilling`** + **`/domain-modeling`** — for any of #99–#106 (feature screen prototypes). Per map Notes; same pattern as #96 + #97. Check the ticket body first — some prototypes are HITL ("What should this look like?") and some are logic ("Does this state model feel right?"); pick the prototype branch accordingly.
- **`/research`** — for any Compose Multiplatform / Ktor / Material3 API surface the build or prototype needs to verify. Use the Material3 source-jar lookup pattern (unzip + read sources) for any "the docs say X" claim before locking on it; use the CMP-version-aligned dependency lookup pattern (CMP releases page) for any unfamiliar JB-forked androidx artifact version.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact and hand off again (this file pattern: `docs/agents/wayfinder-<N>-handoff.md` where <N> is the just-resolved ticket number).