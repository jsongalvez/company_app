# Desktop Trading-Desk Prototype (fake data, branch `prototype/desktop-trading-desk`)

Finance trading desk for the floor: sessions quote like ticker symbols on a live
tape, gross draws as sparklines per venue, remittance settles like a trade
blotter. Part of map #755, ticket #829.

## Run

```bash
COMPANYAPP_PROTO_TRADING_DESK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `TradingDeskFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/trading-desk/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Sign in** — fake directory of six users (Coordinator → ONBOARDING). The
   ONBOARDING row opens the locked-out demo instead of the desk.
2. **ONBOARDING locked** — zero capabilities, order entry blocked; "SIMULATE
   MANAGE_USERS GRANT" flips to a real role and continues.
3. **Venue (branch select)** — QC Central (CLINIC), Laguna Tour Stop 3
   (PROVINCIAL_TOUR), Tondo Medical Mission (MEDICAL_MISSION) with open-order
   counts and booked gross per card.
4. **Ticker tape** — every session streams across the top as
   `SYMBOL ▲/▼/■ STATUS ±AMT` (green filled, red dropped, amber pending, grey void).
5. **Desk (home)** — position card (trader, role, home venue, clock in/out,
   relief view-only vs edit flag), gross panel with 7-session sparkline, all-venue
   sparklines, relief order book (invite fill/pass, request pull, shout new request).
6. **Board (sessions)** — status filter chips, per-ticket inspector:
   PENDING → COMPLETED / NO_SHOW / CANCELLED, void/unvoid with required reason.
   Walk-in tickets refuse NO_SHOW and CANCELLED with the rule note. New-ticket
   quoting adds a PENDING row.
7. **Clients** — global book, masked/reveal toggle, per-client open-ticket count
   (at-most-one-PENDING rule note), anonymized row keeps gender + age only,
   per-client ticket positions.
8. **Blotter (finance)** — SESSION leg draft −/+500 → submit (seals immutable
   snapshot TD-*); PRODUCT leg unit-price × qty steppers → submit; settled rows
   undo-with-reason (48h window note) back to draft. Commission split house-rules
   card (pooled per branch day, even split over clocked-in hands, relief paid from
   this drawer, not subject to remittance).
9. **Crew (team)** — users in branch-slot order with role badges, ONBOARDING
   locked row, YOU marker.
10. **Wire (notifications)** — read/unread mailbox, unread filter, hush-all,
    tap to flip read state; read rows kept as history.
11. **Ledger (audit)** — every prototype mutation prepends actor/action/record/reason.
12. **Terminal (profile)** — current user, clock in/out, sign out (returns to
    sign in), kill terminal (exits).
13. **Branch-day band** — full-width OPEN (green) / PAST (amber) / REMITTED (cyan)
    switcher with the 04:00 Asia/Manila boundary note, always visible.

## Theme notes

Bloomberg-pit terminal: near-black green-tinted canvas `#060907`, panel
`#0C120D`, phosphor green `#00E065` primary, amber `#FFB000` pending, red
`#FF5252` dropped, cyan `#41C7FF` sealed. All-monospace type, 2–4dp squared
corners, `///` tape separators, `▲▼■●○▸` glyphs instead of icons. Top ticker
tape + day band, left floor rail with pending/unread badges, Canvas-drawn gross
sparklines — this variant scraps the Linear design entirely.
