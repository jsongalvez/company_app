# Desktop Kiosk-Touch Prototype (fake data, branch `prototype/desktop-kiosk-touch`)

Walk-in kiosk for the front desk tablet. Part of map #755, ticket #762.

## Run

```bash
COMPANYAPP_PROTO_KIOSK_TOUCH=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `KioskFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/kiosk-touch/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding** — locked ONBOARDING account (0 capabilities). "Try opening Sessions" is
   blocked; "Grant Practitioner + enter" simulates the MANAGE_USERS grant and continues.
2. **Login** — fake directory of five users (ONBOARDING → ACCOUNTANT). Tap Start to sign in.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap Use to switch, or Continue where you are.
4. **Home** — giant pending/completed numerals, tap-to-clock-in/out, relief invites
   (Yes/No), relief requests (Allow/Deny, Pull back own), shortcuts to Sessions and Pay.
5. **Walk-in (3 taps)** — Home → "New walk-in": tap 1 service, tap 2 guest label,
   tap 3 Check in now. Creates a PENDING walk-in session + client + audit + inbox note.
6. **Sessions** — status filter chips, per-session Manage drawer: PENDING → COMPLETED /
   NO_SHOW / CANCELLED, void/unvoid with required reason. Walk-in sessions refuse NO_SHOW
   and CANCELLED with the rule note.
7. **Clients** — global list, per-client PENDING count (at-most-one-PENDING rule note),
   Mask/Reveal anonymized view (gender + age kept).
8. **Pay (finance)** — SESSION and PRODUCT flows: draft −/+500 → submit (seals immutable
   snapshot KS-*) → undo-with-reason (48h window note). Commission split rule card
   (pooled per branch day, equal split over clocked-in staff at sold_at).
9. **Team** — users by branch slot with role bundles, ONBOARDING locked row, role glance.
10. **Inbox** — read/unread notifications, Read per item, mark everything read.
11. **Log** — every prototype mutation prepends an entry with who/action/target/reason.
12. **Me** — current user, clock-out, logout (returns to sign in).
13. **Branch-day band** — full-width OPEN (green) / PAST (amber) / REMITTED (blue) switcher
    with the 04:00 Asia/Manila boundary note, always visible under the top strip.

## Theme notes

Kiosk-touch walk-in: paper-white `#FFFEFA` kiosk, ink-black chrome, safety-orange
`#E85D10` CTA, 2dp ink borders, 20dp radii, 72dp primary targets (56dp chips, 48dp links),
52sp hero / 26sp card titles / 20sp body minimum. One centered column, left giant nav rail,
no dense tables — this variant scraps the Linear design to test whether a 3-tap
front-desk tablet beats a desktop dashboard for walk-ins. Screenshots: run it on the
target display and capture there (no checked-in binaries in this branch).
