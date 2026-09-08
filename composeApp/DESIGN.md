---
version: alpha
name: Linear-design-analysis
description: "A near-black product-focused marketing canvas ..."
colors:
  primary: "#5e6ad2"
  on-primary: "#ffffff"
  primary-hover: "#828fff"
  primary-focus: "#5e69d1"
  ink: "#f7f8f8"
  ink-muted: "#d0d6e0"
  ink-subtle: "#8a8f98"
  ink-tertiary: "#62666d"
  canvas: "#010102"
  surface-1: "#0f1011"
  surface-2: "#141516"
  surface-3: "#18191a"
  surface-4: "#191a1b"
  hairline: "#23252a"
  hairline-strong: "#34343a"
  hairline-tertiary: "#3e3e44"
  inverse-canvas: "#ffffff"
  inverse-surface-1: "#f5f6f6"
  inverse-surface-2: "#f6f7f7"
  inverse-ink: "#000000"
  brand-secure: "#7a7fad"
  semantic-success: "#27a644"
  semantic-overlay: "#000000"
typography:
  display-xl:
    fontFamily: Linear Display
    fontSize: 80px
    fontWeight: 600
    lineHeight: 1.05
    letterSpacing: -3.0px
  display-lg:
    fontFamily: Linear Display
    fontSize: 56px
    fontWeight: 600
    lineHeight: 1.10
    letterSpacing: -1.8px
  display-md:
    fontFamily: Linear Display
    fontSize: 40px
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: -1.0px
  headline:
    fontFamily: Linear Display
    fontSize: 28px
    fontWeight: 600
    lineHeight: 1.20
    letterSpacing: -0.6px
  card-title:
    fontFamily: Linear Display
    fontSize: 22px
    fontWeight: 500
    lineHeight: 1.25
    letterSpacing: -0.4px
  subhead:
    fontFamily: Linear Display
    fontSize: 20px
    fontWeight: 400
    lineHeight: 1.40
    letterSpacing: -0.2px
  body-lg:
    fontFamily: Linear Text
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.50
    letterSpacing: -0.1px
  body:
    fontFamily: Linear Text
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.50
    letterSpacing: -0.05px
  body-sm:
    fontFamily: Linear Text
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.50
    letterSpacing: 0
  caption:
    fontFamily: Linear Text
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.40
    letterSpacing: 0
  button:
    fontFamily: Linear Text
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.20
    letterSpacing: 0
  eyebrow:
    fontFamily: Linear Text
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.30
    letterSpacing: 0.4px
  mono:
    fontFamily: Linear Mono
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.50
    letterSpacing: 0
rounded:
  xs: 4px
  sm: 6px
  md: 8px
  lg: 12px
  xl: 16px
  xxl: 24px
  pill: 9999px
  full: 9999px
spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 96px
---

# Operational UI contract (#670)

Authoritative over any contradictory token usage below. The shared owners live in
`composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/contract/`.

- Hierarchy: page heading 28sp/34sp (24sp/30sp compact), current object 24sp, section
  18sp; decisive numerals 32sp only. Desktop body 14sp, touch body 16sp, secondary
  labels floor 12sp (labelSmall is 12sp, not 11sp). Headings keep Inter via the Linear
  title slots — never M3-default headlineLarge.
- Actions: one filled primary per task region; secondary outlined; tertiary text.
  Busy keeps label + bounds with a reserved 18dp progress slot; duplicate submission is
  disabled. Minimum 48dp height covers touch; desktop 40dp pointer targets sit inside it.
  Destructive red only for destructive intent. Visible 2dp PrimaryHover focus ring,
  distinct from hover/selection. Icons 18–20dp with accessible names.
- Feedback: `InlineStatus` (updating/stale/failure + Retry/success, polite live region,
  never steals focus); cold load uses a bounded placeholder; background refreshes never
  replace populated regions; success is short nonmodal with no auto-navigation.
- Dialogs: max 560dp, viewport-inset on compact, scrolling body with fixed
  title/actions; initial focus = first field (safe action when destructive); Escape/Back
  cancels only when allowed; no entrance animation. Money/count columns use tabular
  figures with end alignment.
- Proven on: Login, Accept invite, Forgot password, launch splash, BranchSelect chrome,
  Mission delegates. All other surfaces adopt in their own redesign tickets.


# Adaptive shell (#671)

Supersedes ADR-0020's platform-only shell chrome (ownership/test rules stand).

- Chrome follows measured width: >=1200dp pins a 224dp task-grouped sidebar; below
  it a labeled menu trigger opens a modal drawer. No permanent 360dp rail.
- Groups: Work (Sessions, Clients, Inventory, Notifications), Finance (Finance &
  Reports, Remittance), Administration collapsed by default (Team & branches,
  Base Rates, Product Catalog, Mission delegates, Audit Log). Footer: signed-in
  display name opens Profile; explicit Clock out in the current-shift area. Only
  authorized destinations render; route identifiers unchanged.
- Compact top bar: menu trigger + parent section title + viewed branch/date.
  Pushed details keep their single screen-owned back; the shell adds no second one.
- Header shows the viewed branch + operational date (server-authoritative, never
  device midnight); the clocked-in shift appears as a labeled secondary only when
  different. Viewing a branch never implies clocking into it.
- Active destination selection is a no-op. Section working context (selected
  object, list anchor) is retained per user+branch in memory; logout/access loss
  clears it; a branch switch rekeys so old-branch rows never surface as new data.
  The shell ships the mechanism wired for Sessions; per-section tab/filter/query
  adoption lands in the feature tickets (#672 and siblings).

# Sessions workspace (#672)

- Header: viewed branch/day heading (page size per #670) + one filled New session
  (create-gated; the empty day carries it as the secondary action instead).
- One quiet summary line: session count, gross, labeled personal commission.
  Unparseable amounts read "Unavailable", never a fabricated zero; no new
  financial calculations (gross and commission reuse the dashboard payload).
- One stable toolbar: All / Pending / Completed (shared status vocabulary),
  Hide voided (off initially, entry-local), Team (badged with the actionable
  incoming-relief count when present), Refresh (disabled while refreshing).
  The filter tab persists per user+branch; a reserved band carries Updating… /
  last-updated / Could-not-update Retry without moving surrounding chrome.
- Content width >=1000dp: list (>=600dp) + detail (360–440dp). Below it the list
  opens a full-width detail and Back restores row/scroll (master stays mounted on
  desktop narrow; mobile retains selection + scroll anchor per user+branch).
  Below 600dp rows stack identity-first (client, status/type + time, price column).
- Team (attendance, slot/swap, relief requests/invites) is an overlay sheet —
  full-width on compact screens — never a third column. Close returns focus to
  Team. Populated rows stay mounted across refreshes; revocation stays a
  protected-data card, never a retry state. The edited row keeps its editor
  mounted across filter changes and poll landings.
