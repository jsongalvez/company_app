# Desktop Newsroom-Desk Prototype (fake data, branch `prototype/desktop-newsroom-desk`)

Newsroom assignment desk for clinic days. Part of map #755, ticket #833.

## Run

```bash
COMPANYAPP_PROTO_NEWSROOM_DESK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `DeskFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/newsroom-desk/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Budget Meeting (welcome)** — locked ONBOARDING card (zero capabilities) explained at
   the door; "Clear onboarding hold" simulates the MANAGE_USERS grant, then join.
2. **Press Passes (sign in)** — five fake cards (ONBOARDING → ACCOUNTANT). ONBOARDING is
   held until the hold is cleared; everyone else files clocked in.
3. **Bureaus (branch select)** — QC Central (CLINIC, desk 1), Laguna Tour Stop 3
   (PROVINCIAL_TOUR, desk 2), Tondo Medical Mission (MEDICAL_MISSION, desk 3)
   with live ON BUDGET counts. Tap to switch.
4. **Assignment Desk (home)** — greeting, clock in/out, next three slugs, stringer shifts
   (start relief at another desk, view-only until edit granted, ends 04:00 Manila),
   desk invites (Yes/No), broadcast stringer requests (Allow/Deny), shortcuts to
   stories and the business office.
5. **Stories (sessions)** — budget rows (slug time, beat, reporter,
   ON BUDGET/FILED/NO SHOW/SPIKED flags, URGENT banner with mark/clear):
   status flag chips filter, per-row status jumps, spike desk drawer with spike (reason
   required) / unspike. Walk-in tips refuse NO_SHOW and CANCELLED with the rule note.
   New walk-in form slugs a PENDING story with a tip number + source record.
6. **Sources (clients)** — global source book, per-source ON BUDGET count
   (at-most-one-PENDING rule note), mask/reveal names, anonymize (keeps gender + age).
7. **Business Office (finance)** — SESSION and PRODUCT editions: draft −/+500 → lock
   (seals immutable snapshot RS-*) → undo-with-reason (48h window note). Commission
   split rule card (pooled per branch day, even split over staff clocked in at sold_at,
   stringers paid from this drawer).
8. **Masthead (team)** — roster in branch-slot order with role flags, ONBOARDING locked row,
   YOU marker.
9. **Wire (notifications)** — read/unread desk wire, spike per item or spike all.
10. **Ledger (audit)** — every prototype mutation prepends who/action/target/reason.
11. **My Press Card (profile)** — current card, clock in/out, sign out (returns to passes).
12. **Branch-day banner** — full-width OPEN (green) / PAST (red pencil) / REMITTED (blue)
    switcher with the 04:00 Asia/Manila boundary note, always visible.

## Theme notes

Newsroom day-budget: newsprint `#F4F1E8` canvas, card `#FFFFF9` panels with ink rules,
slug cells in ink with highlighter `#FFD400` type, masthead ink bar with edition line,
section labels in red pencil `#C1272D`, budget flags (blue on-budget, green filed,
violet no-show, red spiked). Left bureau rail on deep paper (no side drawers, no Linear
cards) — this variant scraps the Linear design to test whether a budget-meeting metaphor
makes pitched vs urgent vs spiked legible at deadline. Screenshots: run it on the target
display and capture there (no checked-in binaries in this branch).
