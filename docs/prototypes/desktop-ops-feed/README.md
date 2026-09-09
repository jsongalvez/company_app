# Desktop Ops-Feed Prototype (fake data, branch `prototype/desktop-ops-feed`)

Dispatch-style chronological event feed for the branch day. Part of map #755, ticket #790.

## Run

```bash
COMPANYAPP_PROTO_OPS_FEED=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `OpsFeedFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/opsfeed/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding** — locked ONBOARDING account (0 capabilities). "Try posting to the feed"
   is denied and logged; "Grant Practitioner role" simulates the MANAGE_USERS grant and continues.
2. **Login** — fake directory of five users (ONBOARDING → ACCOUNTANT). Tap to sign on.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to retune dispatch focus.
4. **Ops Feed (home)** — clock-in/out, relief invites (accept/decline) and requests
   (grant/deny own withdraw), a shift-note composer, then the living log: sessions, relief,
   remittance, audit, and notes as one chronological spine with per-domain filters.
5. **Sessions** — status filter chips, per-session manage drawer: PENDING → COMPLETED /
   NO_SHOW / CANCELLED, void/unvoid with required reason. Walk-in sessions refuse NO_SHOW
   and CANCELLED with the rule note, and the refusal itself is logged.
6. **Clients** — global list, per-client PENDING count (at-most-one-PENDING rule note),
   mask/reveal keeps gender + age while hiding the name.
7. **Finance** — SESSION and PRODUCT flows: draft → submit (seals immutable snapshot) →
   undo-with-reason (48h window note). Commission split rule card
   (pooled per branch day, equal split over clocked-in staff at sold_at).
8. **Team** — users by branch slot with role bundles.
9. **Mailbox** — read/unread notifications, mark read / mark all read.
10. **Audit** — every prototype mutation prepends an entry with who/action/target/reason.
11. **Profile** — current user, clock-in/out, logout (returns to login).
12. **Branch-day band** — full-width OPEN (green) / PAST (amber) / REMITTED (blue) switcher
    with the 04:00 Asia/Manila boundary note, always visible under the masthead.

## Theme notes

Dispatch-radio log on paper: cream sheet, ink masthead, monospace time rail, one colored
pip per event domain (green sessions, violet relief, ember remittance, slate audit, blue
notes). No Linear tokens on purpose — this variant scraps the Linear design to test whether
a single chronological log reads better on a busy branch floor than tabbed tables.
Screenshots: run it on the target display and capture there (no checked-in binaries in
this branch).
