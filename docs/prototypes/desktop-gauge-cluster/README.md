# Desktop Gauge-Cluster Prototype (fake data, branch `prototype/desktop-gauge-cluster`)

Analog instrument panel for clinic days. Part of map #755, ticket #834.

## Run

```bash
COMPANYAPP_PROTO_GAUGE_CLUSTER=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `GaugeFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/gauge-cluster/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Showroom (welcome)** — three hero dials (occupancy, gross, relief cover) for the
   current branch; locked ONBOARDING note (zero capabilities until a MANAGE_USERS
   grant); "Turn the key" enters.
2. **Ignition (sign in)** — five fake drivers (ONBOARDING → ACCOUNTANT). ONBOARDING
   refuses until "Clear onboarding hold" flips the grant; everyone else signs in
   clocked in.
3. **Garage (branch select)** — QC Central (CLINIC, 24 seats, ₱48,000 target),
   Laguna Tour Stop 3 (PROVINCIAL_TOUR, 12 seats, ₱18,000 target), Tondo Medical
   Mission (MEDICAL_MISSION, 30 seats, mission — ₱0 target) with live occupancy /
   gross / PENDING reads. Tap to switch.
4. **Cluster (home)** — greeting, hero dials, PENDING/completed/unread cells, clock
   in/out, relief duty at non-home branches (view-only until edit granted, ends
   04:00 Manila), relief invites (Accept/Decline), broadcast relief requests
   (Allow/Deny), shortcuts to the sessions bay and fuel & ledger.
5. **Sessions (sessions bay)** — rows with time, client, service, status lamp,
   WALK-IN tag, void state, price, practitioner: status lamp chips filter, per-row
   status jumps, void (reason required) / unvoid. Walk-in rows refuse NO_SHOW and
   CANCELLED with the rule note (attempt is audit-logged). New walk-in form books a
   PENDING session with a client record.
6. **Registry (clients)** — global records, per-client PENDING count
   (at-most-one-PENDING rule note), veil/reveal names, anonymize (keeps gender + age).
7. **Fuel & ledger (finance)** — SESSION and PRODUCT flows: draft +/−₱500 → submit
   (seals immutable snapshot RS-*) → undo-with-reason (48h window note). Commission
   split rule card (pooled per branch day, even split over clocked-in practitioners
   at sold_at, relief paid from this drawer).
8. **Pit crew (team)** — roster in branch-slot order with role lamps, ONBOARDING
   locked row, YOU marker.
9. **Signals (notifications)** — read/unread mailbox, hush per item or hush all.
10. **Logbook (audit)** — every prototype mutation prepends who/action/target/reason.
11. **Driver (profile)** — current driver, clock in/out, log out (returns to ignition).
12. **Branch-day lamp strip** — full-width OPEN (green) / PAST (amber) / REMITTED
    (blue) switcher with the 04:00 Asia/Manila boundary note, always visible.

## Theme notes

Gauge-cluster cockpit: mahogany `#2A1A12` fascia, brass `#B08D3E` bezels, cream
`#F4E9CE` dial faces, red `#C0272D` needles with redline arcs, lamp chips (amber
pending, green completed, dark no-show, red cancelled). Left dial rail (no side
drawers, no Linear cards) — this variant scraps the Linear design to test whether
needle-at-a-glance targets beat tables for occupancy, gross and relief coverage.
Screenshots: run it on the target display and capture there (no checked-in binaries
in this branch).
