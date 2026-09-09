# Desktop prototype: card-deck

Card-deck everything. Deep-felt table background, every entity a playing card with a
rank badge, horizontal swipe decks per section. Playful copy, full flows. Fake data
only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_CARD_DECK=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Deal me in` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked card (empty capability bundle note).
- [ ] Branch select: pick Sunrise Clinic, Harbor Provincial Tour, or Lingap Mission.
- [ ] Home: clock in/out per branch card; relief deck with grant/fold; broadcast a relief request; send a relief invite.
- [ ] Sessions: All / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/CANCELLED buttons on walk-ins); status jumps; void with required reason; unvoid.
- [ ] Clients: global list; at-most-one-PENDING note; anonymized view toggle plus per-card anonymize (keeps gender and age).
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION (net income) and PRODUCT (price x quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked card with one-tap Practitioner grant; role glance blurbs.
- [ ] Mailbox: read/unread toggles, mark-all-read, relief items name branch and day; unread count rides the MAIL tab.
- [ ] Audit: clock, relief, void/unvoid, submit/undo append cards with reasons.
- [ ] Profile: clock out, log out, flip branch day, reset demo data.

## Theme notes

- Palette: felt green `#1E3A3A` background, deep felt `#152929` rails, cream paper `#FFFDF4` cards with 2dp ink borders, rail amber `#D9A441` accents; suit colors per deck (coral sessions, sky clients, mint money, lilac team, amber mail, slate audit).
- Shape: 14dp playing cards with hard ink borders and zero elevation; rank badge circles; pill chips per status.
- Copy: table language ("Deal me in", "Fresh deck", "Fold", "Sit at this table").
- Layout: one horizontal LazyRow deck per section with a count plaque; detail screens stay single-column scrolled.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/card-deck/*.desktop.kt` (package `...proto.carddeck`), fake `CardDeckFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
