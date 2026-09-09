# Desktop Checkin-Kiosk Prototype (fake data, branch `prototype/desktop-checkin-kiosk`)

Self check-in lobby for guests. Part of map #755, ticket #782.

## Run

```bash
COMPANYAPP_PROTO_CHECKIN_KIOSK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `CheckinFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/checkin-kiosk/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Welcome** — locked ONBOARDING account (0 capabilities). "Try the lobby" is
   blocked; "Grant Practitioner" simulates the MANAGE_USERS grant and continues.
2. **Sign in** — fake directory of five users (ONBOARDING → ACCOUNTANT). ONBOARDING
   stays unselectable for entry; everyone else opens the lobby clocked in.
3. **Branch** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch, then enter the lobby.
4. **Lobby** — in-line / done-today / ticket numerals, clock in/out, relief invites
   (Yes/No), relief requests (Allow/Deny, pull back own, ask another branch),
   shortcuts to arrival and the till.
5. **Arrive (3 taps)** — tap 1 who's here (returning guest or typed label),
   tap 2 care menu pick, tap 3 check in now. Issues a queue ticket (A-044…),
   creates a PENDING walk-in session + client + audit + bell note.
6. **Queue (sessions)** — status filter chips, per-session Manage drawer:
   PENDING → COMPLETED / NO_SHOW / CANCELLED, void/unvoid with required reason.
   Walk-in sessions refuse NO_SHOW and CANCELLED with the rule note.
7. **Care** — the four-item care menu with prices, plus today's waiting count.
8. **Guests (clients)** — global list, per-guest open-session count
   (at-most-one-PENDING rule note), private/reveal anonymized view
   (gender + age kept).
9. **Till (finance)** — SESSION and PRODUCT flows: draft −/+500 → submit (seals
   immutable snapshot CK-*) → undo-with-reason (48h window note). Commission
   split rule card (pooled per branch day, even split over clocked-in hands,
   relief paid from this drawer).
10. **Crew (team)** — users in branch-slot order with role bundles,
    ONBOARDING locked row, YOU marker.
11. **Bell (notifications)** — read/unread mailbox, hush per item or hush all.
12. **Ledger (audit)** — every prototype mutation prepends who/action/target/reason.
13. **Me (profile)** — current user, clock in/out, sign out (returns to sign in).
14. **Branch-day band** — full-width OPEN (mint) / PAST (amber) / REMITTED (sky)
    switcher with the 04:00 Asia/Manila boundary note, always visible.

## Theme notes

Checkin-kiosk night lobby: deep pine `#0B2420` canvas, panel `#12332D`,
cream `#FFF6E3` ticket stubs with perforation row, marigold `#FFB627` CTA,
64dp primary buttons, 56dp tab bar, 52sp hero / 30sp titles / 18sp body.
Top day band + bottom tab bar (no side rail) — this variant scraps the Linear
design to test whether a guest-facing 3-tap arrival with a printed-style queue
ticket beats a staff dashboard for walk-ins. Screenshots: run it on the
target display and capture there (no checked-in binaries in this branch).
