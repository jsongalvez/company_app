# ADR-0020: ComposeApp responsive strategy — compile-time platform-target split

**Status:** Accepted
**Date:** 2026-07-21

## Context

The frontend rebuild (spec 0001) targets desktop (JVM) and Android from one
Compose Multiplatform codebase. US-29/30 require the session dashboard to render
as a dense table with a master-detail side panel on desktop, and as a scrollable
card list on mobile. #91 confirmed the `SessionDetail` routing pattern — a shared
presentational `SessionDetailContent` + shared VM class, with each host owning VM
wiring/scoping (mobile: nav backstack entry; desktop: selection-keyed) — and
graduated the broader responsive strategy to #95.

The question is what mechanism drives the table-vs-card-list split: a runtime
window-width check (the JetBrains `material3.adaptive` / `WindowSizeClass` API,
the conventional CMP choice for a desktop+mobile app), or a compile-time
platform-target split (`expect/actual` in `androidMain` / `desktopMain`).

## Decision

Adopt a **compile-time platform-target split**. The dashboard's table-vs-card-list
divergence is resolved at build time via `expect/actual` in the platform source
sets, not at runtime via `WindowSizeClass`. No `material3.adaptive` /
`WindowSizeClass` dependency is introduced.

Five independent lines of evidence converge on this, any one of which would be
suggestive but together are decisive:

1. **Spec language.** US-29/30 frame the split as "desktop user" vs "mobile user"
   — platform-target language, not window-width language. The product persona
   split (coordinator at a branch desktop, relief user on a phone) maps to
   platform target, not to measured window width.
2. **Spec scope.** Spec 0001 line 173 explicitly rules out "responsive
   adaptations beyond table/card-list toggle" as out of scope. A `WindowSizeClass`
   framework is a general responsive system; introducing it for a single dashboard
   toggle would reintroduce the category the spec scoped out, by the back door.
3. **Codebase precedent.** All four existing `expect/actual` pairs
   (Engine, Log, TokenStore, AppConfig) are platform-mechanism splits; none are
   width-mechanism. A platform-target layout split follows the established grain
   rather than introducing a new axis.
4. **Prior decision consistency.** #91 already framed SessionDetail's desktop pane
   in platform-target terms ("desktop in-screen pane", "desktop selection-keyed
   on `selectedId`"), not width terms. A runtime-width mechanism would contradict
   a decision already made.
5. **No existing dependency.** `WindowSizeClass` is not currently a dependency;
   introducing it solely for this single toggle is a new artifact with no second
   use in this effort.

## Accepted cost

A desktop window resized to phone-width still renders the dense table. This is
the literal, stated definition of the desktop target — desktop *is* the
dense-table persona per spec, independent of the window chrome's pixel width at
any given moment. A user who resizes a desktop window small hasn't become a
mobile user in any sense the spec cares about (their capabilities, workflow, and
input model haven't changed); they've made a desktop window small, which is a
self-inflicted UX situation, not a case the product needs to serve. This cost was
named and explicitly accepted, not overlooked.

A narrow desktop window also reduces the permanent navigation drawer to a
cramped-but-functional rail (the drawer eats a disproportionate fraction of the
already-narrow viewport). No runtime fallback to modal drawer — that would
re-introduce `WindowSizeClass` ADR-0020 rejected and contradict the
compile-time platform-target split. Named in #96 (Q1 confirmed-cost addendum)
and propagated by build ticket #107; compounds with the narrow-window
dense-table degradation above as a distinct desktop-narrow degradation axis.

## Consequences

**Smallest-divergent-subtree split rule (from #95 Q2).** When a screen's layout
diverges by platform, the `expect/actual` split lands at the smallest divergent
subtree, not the whole screen. Shared chrome stays in `commonMain`; only the
divergent piece gets `expect/actual`. For the dashboard: `SessionDashboardScreen`
(commonMain) holds shared chrome — summary cards (US-12), the voided-session
*rule* in `uiState` (US-14), the polling trigger — and calls
`expect fun SessionList(uiState, onSelect, modifier)`, with the `androidMain`
actual rendering the card list and the `desktopMain` actual rendering the table.
This is the same shape as #91's `SessionDetailContent` factoring: share what's
shared, split only what differs. Whole-screen `expect/actual` would duplicate the
shared chrome across both actuals — reintroducing the coupling #91 removed.

**Host-level split.** The NavHost host itself splits via
`expect fun AppNavHost(...)`: `App()` in `commonMain` does shared setup (token,
auth, `LinearTheme`, `SessionState`) then calls the expect host. The `androidMain`
actual renders the mobile NavHost (all detail routes are push routes); the
`desktopMain` actual renders the desktop NavHost where `Dashboard` composes a
master-detail `Row` with the `SessionDetailContent` pane inline. **#152 (the #151
Q6 scoped revision): desktop gained a pushed `SessionDetail` route — but ONLY for
the notification entry point** (desktop notification tap = markRead + push; the
route fetches via the session-detail GET). The dashboard inline pane is untouched;
the pushed route exists because the notification push has no dashboard row to
select. Wiring differences are never resolved by splitting a screen — that is the
host's job.

**ViewModels stay in `commonMain`.** Every VM has one test target, per spec's
no-UI-tests / ViewModel-tests-only rule. The platform split is confined to
presentational composables and host wiring.

**Dashboard-only scope boundary (from #95 Q3).** The desktop-pane /
platform-divergent-layout pattern is dashboard-only for this effort. Any other
screen opting in is past the destination — it requires explicit re-scoping per
spec line 173, not a default available to feature tickets. This tightens #91's
"opt-in if UX asks for inline side-by-side": a judgment call disguised as a rule
is precisely what silently walks past a scope boundary, because each individual
instance looks locally reasonable without anyone re-checking it against the spec
line that rules out the category. The mechanism for a future opt-in (if
re-scoped) is fully specified by the smallest-divergent-subtree rule and the
host-level `expect/actual` — no capability is lost, only the ability to add it
silently. The decision rests on the scope argument (the spec excludes by
default), not on the empirical headcount (zero of 13 non-dashboard routes
currently want this) — "nobody wants it yet" could change; "the spec excludes it
by default" governs until deliberately reopened.

## Alternatives considered

**Runtime window-width check via `WindowSizeClass`.** The conventional CMP
choice for a desktop+mobile app: one adaptive composable in `commonMain` that
switches table-vs-cards on a width threshold, backed by the JetBrains
`material3.adaptive` artifact. This would handle a desktop-window-resized-small
edge case and an Android-tablet/foldable edge case. Rejected on all five
evidence lines above — most fundamentally because spec line 173 scopes out the
general responsive system this option would install, and because #91 already
committed the pane pattern to platform-target framing. The edge cases it handles
(resized desktop window, Android tablet) are out of scope per spec and not served
by the product's persona split. The accepted cost (small desktop window shows the
dense table) is the explicit trade for staying inside the spec's scope boundary.
