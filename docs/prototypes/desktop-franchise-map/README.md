# Desktop prototype: franchise-map

Franchise territory atlas. Four branch pins on a schematic parchment survey map
(three territory washes, dashed survey roads, status-color pins); pick a pin in
the roster or on the map, then drill to its branch day. Deep pine rails, brass
accents, serif display type. Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_FRANCHISE_MAP=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: `Open the territory map` goes to pin select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked card (empty capability bundle note).
- [ ] Branch select: roster cards or clickable map pins; `Drill to ...` opens the app shell on that pin.
- [ ] MAP home: survey map with territory washes, roads, legend, compass tag; side pin list; drill button.
- [ ] DAY drill: clock in/out at the drilled branch; relief board with grant/fold; broadcast a relief request; send a relief invite with a note; session mix preview.
- [ ] Sessions: pin picker plus ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/CANCELLED buttons on walk-ins); status jumps; void with required reason; unvoid; book a PENDING session.
- [ ] Clients: global list; at-most-one-PENDING note; anonymized view toggle plus per-card anonymize (keeps gender and age).
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION and PRODUCT drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked card with one-tap Practitioner grant; role glance blurbs.
- [ ] Mailbox: read/unread toggles, mark-all-read; unread count rides the MAIL tab.
- [ ] Audit: clock, relief, void/unvoid, submit/undo append entries with reasons.
- [ ] Profile: clock in/out, log out, flip branch day, reset demo data.

## Theme notes

- Palette: parchment `#F4ECDA` map, deep pine `#16211A` rails, brass `#A87B1F` accents; day pins green/slate/gold; territory washes sage `#DCE5CF`, mist `#D2E2E4`, sand `#EAD9B8`.
- Shape: 20dp survey card, round status pins with initial letters and name tags, pill chips per status.
- Copy: survey language ("Franchise atlas", "Territory pins", "Drill", "Survey log", "Remittance chest").
- Layout: roster rail left, clickable Canvas map right; app shell keeps a slim tab rail with the day banner under it.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/franchise-map/*.desktop.kt` (package `...proto.franchisemap`), fake `FranchiseMapFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
