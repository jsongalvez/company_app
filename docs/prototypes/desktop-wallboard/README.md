# Desktop Wallboard Prototype (fake data, branch `prototype/desktop-wallboard`)

TV-mode command center for the branch day. Part of map #755, ticket #759.

## Run

```bash
COMPANYAPP_PROTO_WALLBOARD=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `WallboardFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/wallboard/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding** — locked ONBOARDING account (0 capabilities). "Try opening Sessions" is
   blocked; "Grant Practitioner role" simulates the MANAGE_USERS grant and continues.
2. **Login** — fake directory of five users (ONBOARDING → ACCOUNTANT). Tap to sign in.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch the active branch day.
4. **Wallboard (home)** — branch+date hero with huge PENDING / COMPLETED numerals,
   clock-in/out, relief invites (accept/decline), relief requests (grant/deny own withdraw).
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
12. **Branch-day band** — full-width OPEN (green) / PAST (amber) / REMITTED (cyan) switcher
    with the 04:00 Asia/Manila boundary note, always visible under the hero.

## Theme notes

Phosphor-amber-on-near-black command center: 40sp+ black-weight numerals, full-bleed day
band, hairline panel edges. No Linear tokens on purpose — this variant scraps the Linear
design to test whether a glanceable TV wallboard reads better on a clinic TV than a dense
desktop table. Screenshots: run it on the target display and capture there (no checked-in
binaries in this branch).
