# Desktop Projector Mode Prototype (fake data, branch `prototype/desktop-projector-mode`)

Ultra-high-contrast presentation mode for team meetings: pure-black stage, paper-white
ink, signal-yellow focus color, giant black-weight type sized to read from 5 meters.
Part of map #755, ticket #777.

## Run

```bash
COMPANYAPP_PROTO_PROJECTOR_MODE=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `ProjectorFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/projector-mode/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding** — locked ONBOARDING account (0 capabilities). "Try sessions" is
   blocked; "Grant practitioner" simulates the MANAGE_USERS grant and continues.
2. **Login** — fake directory of five users (ONBOARDING → ACCOUNTANT). Tap to sign in.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch the active branch day.
4. **Stage (home)** — branch hero with giant PENDING / COMPLETED numerals,
   clock-in/out, relief invites (accept/decline), relief requests (grant/deny, own withdraw).
5. **Sessions** — status filter chips, per-session manage drawer: PENDING → COMPLETED /
   NO_SHOW / CANCELLED, void/unvoid with required reason. Walk-in sessions refuse NO_SHOW
   and CANCELLED with the rule note.
6. **Clients** — global list, per-client PENDING count (at-most-one-PENDING rule note),
   anonymize masks PII while keeping gender + age.
7. **Finance** — SESSION and PRODUCT flows: draft → submit (seals immutable snapshot) →
   undo-with-reason (48h window note). Commission split rule card
   (pooled per branch day, equal split over clocked-in staff at sold_at).
8. **Team** — users by branch slot with role bundles.
9. **Mailbox** — read/unread notifications, mark read / mark all read.
10. **Audit** — every prototype mutation prepends an entry with who/action/target/reason.
11. **Profile** — current user, clock-out, logout (returns to login).
12. **Branch-day banner** — full-width OPEN (green) / PAST (yellow) / REMITTED (white)
    switcher with the 04:00 Asia/Manila boundary note, always visible under the header.

## Theme notes

Pure-black stage with white 72sp branch titles, 96sp KPI numerals, and a single
signal-yellow focus color for active tabs, chips, and totals. One focus card at a time
with 3dp white panel edges keeps the wall readable at distance; the top tab strip
replaces a side rail to minimize chrome. No Linear tokens on purpose — this variant
scraps the Linear design to test whether a 5-meter-legible stage beats a dense desktop
table in a team meeting. Screenshots: run it on the target display and capture there
(no checked-in binaries in this branch).
