# Desktop Month-Close Prototype (fake data, branch `prototype/desktop-month-close`)

Month close pack for the operating month. Part of map #755, ticket #851.

## Run

```bash
COMPANYAPP_PROTO_MONTH_CLOSE=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `MonthCloseFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/month-close/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding (locked)** — ONBOARDING hand (0 capabilities). "Attempt Sessions chapter"
   is refused with a note; "Inscribe Practitioner" simulates the role grant.
2. **Login** — false directory of five hands (ONBOARDING → ACCOUNTANT). Sign to continue.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). One house kept at a time.
4. **Pack home** — close-checklist progress (x/8 sealed), clock-in/out, relief invites
   (accept/decline), relief requests (cover own strike), unremitted-days list.
5. **Sessions** — filter tabs ALL/PENDING/COMPLETED/NO_SHOW/CANCELLED, per-entry amend drawer:
   status moves, void with required reason / unvoid. Walk-in entries refuse NO_SHOW and
   CANCELLED with the rule note.
6. **Clients** — global register, per-client PENDING count (at-most-one-PENDING rule note,
   over-rule flag), anonymized veil/unveil writes audit.
7. **Finance** — SESSION and PRODUCT folios: draft → submit (seals immutable snapshot with
   MC number, both sealed marks the day REMITTED) → undo with reason (48h window note).
   Commission split note (pooled per branch day, even split over clocked-in hands at sold_at).
8. **Team** — roster by home house with role capability notes.
9. **Mailbox** — read/unread notes, read singly / read all.
10. **Audit** — every prototype mutation prepends who/action/target/reason.
11. **Profile** — current hand, clock-out, logout (returns to login).
12. **Day banner** — full-width OPEN / PAST / REMITTED stamp naming the selected day plus
    the 04:00 Asia/Manila boundary note, plus the snapshot archive list of sealed folios.

## Theme notes

Archive-box voice on purpose: warm paper ground (#F4EFE4), kraft archive cards, ink-navy
masthead, seal-red progress and submit actions, double-ring day stamps for branch-day
state instead of the Linear dark canvas — this variant scraps the Linear design to test
whether a month-end close pack (one checklist, unremitted days, pending voids, snapshot
archive on a single screen) reads faster at seal time than a per-day dashboard.
Snapshots file as kraft archive rows with MC numbers so finance close-out feels like
filing folios into a box, not filling a form.
Screenshots: run it on the target display and capture there (no checked-in binaries in
this branch).
