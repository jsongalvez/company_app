# Desktop Departures-Board Prototype (fake data, branch `prototype/desktop-departures-board`)

Station departures board for clinic days. Part of map #755, ticket #830.

## Run

```bash
COMPANYAPP_PROTO_DEPARTURES_BOARD=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `BoardFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/departures-board/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Ticket Hall (welcome)** — locked ONBOARDING pass (zero capabilities) explained at
   the gate; "Clear onboarding hold" simulates the MANAGE_USERS grant, then enter.
2. **Gates (sign in)** — five fake passes (ONBOARDING → ACCOUNTANT). ONBOARDING is
   unboardable until the hold is cleared; everyone else boards clocked in.
3. **Platforms (branch select)** — QC Central (CLINIC, platform 1), Laguna Tour Stop 3
   (PROVINCIAL_TOUR, platform 2), Tondo Medical Mission (MEDICAL_MISSION, platform 3)
   with live PENDING counts. Tap to switch.
4. **Concourse (home)** — greeting, clock in/out, next three departures, relief duties
   (start relief at another platform, view-only until edit granted, ends 04:00 Manila),
   relief invites (Yes/No), broadcast relief requests (Allow/Deny), shortcuts to
   departures and the ticket office.
5. **Departures (sessions)** — flip-board rows (time flap, service, practitioner,
   BOARDING/DEPARTED/NO SHOW/CANCELLED flags, DELAYED banner with flag/clear):
   status flag chips filter, per-row status jumps, Manage drawer with void (reason
   required) / unvoid. Walk-in rows refuse NO_SHOW and CANCELLED with the rule note.
   New walk-in form boards a PENDING departure with a queue ticket + passenger record.
6. **Passengers (clients)** — global manifest, per-passenger PENDING count
   (at-most-one-PENDING rule note), mask/reveal names, anonymize (keeps gender + age).
7. **Ticket Office (finance)** — SESSION and PRODUCT windows: draft −/+500 → submit
   (seals immutable snapshot RS-*) → undo-with-reason (48h window note). Commission
   split rule card (pooled per branch day, even split over clocked-in riders at sold_at,
   relief paid from this drawer).
8. **Crew (team)** — roster in branch-slot order with role flags, ONBOARDING locked row,
   YOU marker.
9. **Signals (notifications)** — read/unread announcer board, hush per item or hush all.
10. **Logbook (audit)** — every prototype mutation prepends who/action/target/reason.
11. **My Pass (profile)** — current pass, clock in/out, sign out (returns to gates).
12. **Branch-day banner** — full-width OPEN (green) / PAST (amber) / REMITTED (sky)
    switcher with the 04:00 Asia/Manila boundary note, always visible.

## Theme notes

Departures-board station: yard-black `#0C0E08` canvas, flap `#181B12` split-flap cells,
amber `#FFB300` glyphs, cream `#F2EAD8` headlines, flag chips (amber boarding, green
departed, violet no-show, red cancelled). Masthead station clock flaps + STATION
OPEN/CLOSED lamp, left platform rail (no side drawers, no Linear cards) — this variant
scraps the Linear design to test whether a rail-board metaphor makes boarding vs
delayed vs void legible across a room. Screenshots: run it on the target display and
capture there (no checked-in binaries in this branch).
