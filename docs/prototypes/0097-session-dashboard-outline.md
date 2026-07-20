# Prototype #97 — Session Dashboard UX

Prototype asset for #97 (session dashboard UX), on throwaway branch `prototype/0097-session-dashboard`. Runnable via:

```bash
git checkout prototype/0097-session-dashboard
# temporarily set mainClass to the prototype in composeApp/build.gradle.kts:
#   mainClass = "com.companyb.companyapp.prototype.SessionDashboardPrototypeKt"
./gradlew :composeApp:run
```

The prototype covers Q2 (summary card layout) and Q3 (voided rendering). Q1, Q4, Q5, Q6 were resolved by grilling without visual prototyping (the decisions were architectural/interaction-model, not visual-layout).

## Q2 — Summary cards (Variant A locked)

Three variants were rendered and compared side-by-side:

- **A — Equal row + hairline divider** (LOCKED): two equal-weight cards, thin vertical hairline between them. "Equal peers, distinct not one unit."
- B — Primary + secondary weighted: asymmetric sizing. Rejected — communicates relative importance, not categorical difference.
- C — Stacked: full-width cards in a column. Rejected — strongest "different things" signal but overcorrects at the cost of vertical space; dashboard summary cards should stay compact.

**Locked treatment (both platforms, shared in `commonMain` per ADR-0020):**

| Card | Label | Value | Sublabel |
|------|-------|-------|----------|
| Gross income | "Gross income" (eyebrow, `InkSubtle`) | ₱12,450 (titleLarge, `Ink`) | "Today · completed, non-voided" (bodySmall, `InkSubtle`) |
| Your commission | "Your commission" (eyebrow, `InkSubtle`) | ₱1,840 (titleLarge, `Ink`) | "from N product sales" (bodySmall, `InkSubtle`) |

- Card surface: `Surface2` (`#141516`), 12px radius, hairline border, 16dp padding.
- **No lavender on values** — values are ink (data, not actions). Lavender reserved for actionable elements per Linear convention. Any differentiation tracks the "distinct pools" fact, not "one is personally yours."
- **Sublabels distinguish pool sources** (session-revenue vs product-commission) — the two cards measure conceptually unrelated things; the divider + sublabels together prevent the "equal peers → one merged figure" misread.
- **Totals enforced in `uiState` (commonMain), not rendering.** Gross income excludes voided rows at the state-computation layer; rendering just shows voided-ness.

## Q3 — Voided rendering (locked, validated by rendering)

Worst-case density row prototyped: voided + COMPLETED + walk-in. Both desktop table-row and mobile card treatments rendered side-by-side with a voided/active toggle.

**Locked treatment:**

| Signal | Desktop table row | Mobile card |
|--------|-------------------|-------------|
| Row/card background | `Danger` 22% alpha over `Surface1` (red-tinted "deleted" signal) | Same |
| Strikethrough | Client name only (`LineThrough`), other cells normal | Client name only |
| Client name color | `InkSubtle` (dimmest) when voided, `Ink` when active | Same |
| Time + type fonts | `InkSubtle` when voided, `InkMuted` when active | n/a (card layout) |
| Walk-in dot | Grey (`InkSubtle`) when voided, lavender 70% when active | Same |
| Status badge | Muted when voided (bg alpha × 0.4, fg 50% over `InkSubtle`); full color when active | Same |
| VOIDED pill | `Danger` 35% bg + bright `Ink` text (light rose, high contrast) | Same — inline with price on mobile (right-aligned, same row) |

**Orthogonality preserved:** status pill stays when voided (status is real — COMPLETED is still COMPLETED), just visually defers to the VOIDED pill. The two axes (status, void) are not collapsed. VOIDED pill uses the red axis (matching the row tint) but is NOT a colored status chip — it's an annotation. Colored-chip vocabulary stays owned by status.

**Voided pill placement:** desktop — right-aligned in a dedicated slot after the final-price column (22% width, gives left-margin separation from the price cell). Mobile — inline in the same row as the price, right-aligned (was a separate row, which enlarged the card; moved inline).

**Totals exclusion** enforced in `uiState`, not rendering (per ADR-0020: the *rule* is shared state in commonMain).

## Q1 — Column set + ordering (locked by grilling, no visual prototype)

**Desktop table — 6 primary columns:**

1. Booked time (narrow)
2. Client name + walk-in dot (medium)
3. Type (badge, narrow)
4. Status (badge, narrow)
5. Final price (narrow)
6. VOIDED pill slot (right-aligned, 22%)

**Desktop secondary (detail pane per #91 master-detail):** base price, practitioners, next appointment date, remarks, concerns.

**Mobile card (spec line 111, strict):** client name + walk-in dot, type badge, status badge, final price. VOIDED pill inline when voided.

**Two placements pressure-tested against the "scan-the-day vs inspect-one-session" axis:**
- **Practitioners demoted to secondary.** Self-assigned by practitioners at work-time (`business-requirements.md:204`), not coordinator-dispatched. Coordinator's day-scan is financial/flow, not load-balancing. Not an inline-edit field (US-16). Mobile card (practitioner-persona device) omits it. Accepted cost: practitioner-load scanning requires row expansion; if that need turns out real, it's a future re-scoping (fog candidate).
- **Walk-in as dot inline with client name, both platforms.** Binary context, not a category — a dot, not a chip. Avoids three-chips-per-row badge inflation. Cell-level (in the name cell), so it doesn't collide with the row-level voided treatment.

## Q4 — Inline editing (desktop only, locked by grilling)

**4a — Editable:** type, status, finalPrice. basePrice read-only (domain rule: base = rate snapshot, final = practitioner-adjustable per `business-requirements.md:189`; not a VM-completeness workaround). VM gap (no `updateType`/`updatePrice`) is a build prerequisite.

**4b — Affordance:** hover-revealed edit icon on desktop. Silent when capability revoked (no disabled control — #92 per-element guard). Mid-session revocation: close edit mode if open (discard draft), then silently remove affordance. Accepted cost: a user staring across a revocation sees the pencil vanish — might read as glitch; alternative (explaining every cap change) is notification-spam for a rare event.

**4c — Per-control:** dropdown commits on select (type, status); number input commits on Enter/blur (finalPrice). Walk-in constraint: NO_SHOW/CANCELLED disabled in status dropdown when `isWalkIn`, with tooltip "Walk-in sessions cannot become No Show or Cancelled." Backend still validates (`SessionService:147`); UI is graceful-preview.

**4d — Single-field scope:** no Save/Cancel buttons; commit on select/enter, cancel on Esc/click-away. **Explicit scope boundary:** inline editing operates on a single field only; multi-field draft editing is out of scope (requires re-scoping the interaction model). Blur-equality: no PATCH when normalized value equals original (`100` → `100.00` = no-op).

**4e — Failure behavior (Model A):**
- PATCH in flight → pessimistic loading (dimmed input + small spinner), value NOT updated yet.
- Success → exit edit mode, display new value.
- Failure (generic) → keep attempted value, inline red error below cell, stay in edit mode; retry or Esc.
- 403 → exit edit mode, discard attempt, affordance vanishes (silent; ties to 4b mid-session).
- 409 → inline error + Reload action; after reload, indicate which fields changed remotely (field-highlight vs textual summary = implementation detail, not committed).
- Esc anywhere → clear error, restore original, exit edit mode.

**Update model: PESSIMISTIC (explicit).** UI does not update displayed value until PATCH succeeds. Same axis as #92's "backend is authoritative" (401 token-clear, 403 cap-refresh) applied to inline edits. A future contributor implementing half-optimistic/half-pessimistic would break this.

**Mid-edit polling:** while a cell is in edit mode (including PATCH in flight), polling does not overwrite that field; other rows/fields update normally. The user's draft temporarily owns the cell.

## Q5 — Polling UX (locked by grilling)

**5a — Silent + last-updated timestamp.** No spinner/animation on poll (would flicker every 30s). Timestamp = **last successful refresh**, never last attempt (a timestamp that reads "Updated 12:30" while failing for 3 min is misleading at the worst moment). No pulse/flash — refresh animations are for user-initiated refresh, not scheduled polling.

**5b — Failure handling (thresholds are defaults, not architecture):**
- Transient → silent retry next tick.
- Small number consecutive (default 2) → stale-data notice (banner / amber timestamp), last-successful data preserved.
- Prolonged consecutive (default 5) → error state (Q6) with Retry.
- **Preserve last successful data** on transport failure (network failure isn't authoritative; last fetch is best truth — same axis as #94 network-error token-preservation).
- **401 is NOT a polling-degradation path** — it's session termination (`ApiClient.onUnauthorized` → Login with "session expired"). Stale-data banner never appears for 401; redirect happens first.

**5c — Lifecycle:**
- Poll while dashboard route is active; stop on navigating away.
- **"Leaving Dashboard" = leaving the route, not opening detail.** Desktop inline detail pane stays on dashboard route → polling continues. Mobile `SessionDetail` push route leaves → polling stops.
- **Cadence: completion-then-wait, not wall-clock.** Next interval 30s after previous poll completes (avoids overlapping requests).
- **Manual refresh:** pull-to-refresh (mobile) / refresh button (desktop) — triggers same fetch as poll. Available once stale data is indicated, so user isn't forced to wait for next tick.
- **Mid-edit exception:** polling continues during edit mode but does not overwrite the edited field (per Q4 ownership rule).
- **Clock-out:** polling stops (flag for build: clock-out → dashboard-state-clear transition not fully specified in #94).

## Q6 — States (locked by grilling)

**6a — Loading:** cold start (nothing loaded) → full-screen spinner, no skeletons. Partial-region (one loaded, other loading) → render loaded region, spinner only in loading region. Skeletons imply a structure the user hasn't confirmed; for blocking initial load, spinner is honest.

**6b — Empty:** "No sessions yet today" + "Sessions will appear here as practitioners log them." + branch context. Primary action (create session) shown if capabilities permit session creation — **phrased by function (session creation), not today's capability constant** (`EDIT_BRANCH_DATA`), so it survives a future create/edit/void capability split. Summary cards show ₱0 (zero is valid business information — "today's value is zero" ≠ "dashboard not applicable"). **Empty state participates in polling** — auto-transitions to populated when sessions appear, no user action needed.

**6c — Error:** in-place error card per-region with Retry. **Retry targets only the failed region** — a healthy region doesn't flash loading because its sibling failed. Phrased: "if regions are backed by independent fetches, each renders its own loading/error state" (no impossible states if they share an endpoint). No stale-notice confusion (this is persistent-failure escalation from 5b, not transient stale-notice).

**6d — Unauthorized (#92, confirmed):** in-place 403 card, no redirect. 401 → auth (Login); 403 → authorization (in-place card + Switch branch / Refresh permissions). 401/403 distinction stays clean.

**6e — State ownership follows rendering ownership:** cards own cards state, list owns list state. Dashboard does not compute one monolithic `UiState`; each region receives its own slice. Smallest-divergent-subtree principle (ADR-0020) applied to state, not just layout.

## Graduation candidates (flagged during #97, for ticketing)

1. **Enriched session-row DTO + endpoint.** `SessionResponse` lacks `clientName`, practitioners list, and inline voided-ness. The dashboard needs all three. Same shape as #94 → #98 (a backend gap exposed by a frontend prototype). Build ticket.
2. **VM methods for inline edit.** `SessionViewModel` has `updateStatus` but no `updateType` / `updatePrice`. Build ticket.
3. **Clock-out → dashboard-state-clear transition.** What happens to the dashboard VM on clock-out? Not specified in #94. Fog for now — may graduate when the dashboard build ticket is worked.

## ADR bar assessment

The dashboard UX decisions are mostly consequences of principles already locked in ADR-0020 (responsive split), ADR-0021 (capability refresh), and #92 (capability gating). The two candidates for ADR-worthy decisions:

1. **Pessimistic inline-edit model** — hard to reverse (changing to optimistic later would break the polling-ownership boundary), surprising without context (a future contributor might default to optimistic), real trade-off (optimistic is faster-feeling). **Meets all three → candidate for ADR-0022.**
2. **Per-region state ownership** — follows directly from ADR-0020's smallest-divergent-subtree, so not surprising-without-context in the same way. Probably a restatement, not a new ADR.

Will offer ADR-0022 (pessimistic inline-edit) to the user before closing.
