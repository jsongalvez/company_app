# Desktop prototype: bauhaus-blocks

Bauhaus blocks. Four branch blocks on a primary-color canvas — red circles, blue
squares, yellow triangles on paper, ruled by a black grid rail. Shape-coded nav
(circle / square / triangle / half / bars / arch), hard 3dp black borders, flat
color blocks, uppercase grotesk type. Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_BAUHAUS_BLOCKS=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: `ENTER THE COMPOSITION` goes to block select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked card (empty capability bundle note).
- [ ] Branch select: four color blocks; click a block then `OPEN ...` enters the shell on that block.
- [ ] HOME: clock in/out; composition mix card; relief board with GRANT/FOLD; broadcast a relief request; send a relief invite with a note.
- [ ] Sessions: ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/CANCELLED buttons on walk-ins); status jumps; void with required reason; unvoid; book a PENDING walk-in.
- [ ] Clients: global wall; at-most-one-PENDING note; anonymized view toggle (global pill plus per-card anonymize, keeps gender and age).
- [ ] Branch-day banner: OPEN / PAST / REMITTED color blocks plus the 04:00 Asia/Manila boundary note; SWITCH BLOCK returns to select; flip states from Profile.
- [ ] Finance: SESSION and PRODUCT drafts; submit seals a snapshot; undo within 48h with reason; commission-split note (60/40 practitioner/house, settled at snapshot).
- [ ] Team: users and roles incl. ONBOARDING locked card with one-tap Practitioner grant; role glance blurbs.
- [ ] Mailbox: read/unread toggles, mark-all-read; unread count rides the MAIL tab.
- [ ] Audit: clock, relief, void/unvoid, submit/undo append entries with reasons.
- [ ] Profile: clock in/out, log out, flip branch day, reset demo data.

## Theme notes

- Palette: paper `#F5F0E6`, ink `#141414`, red `#E30613`, blue `#0057A8`, yellow `#FFD200`; cards `#FFFFF9` with 3dp black borders and 6dp accent bars.
- Shape: square corners everywhere, pill filters with black rules, geometric Canvas hero (red circle, blue square, yellow field, black arc).
- Copy: composition language ("Enter the composition", "Choose your block", "Session blocks", "Composition record").
- Layout: black shape-rail left (190dp), day banner on top, scrolling block cards right.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/bauhaus-blocks/*.desktop.kt` (package `...proto.bauhausblocks`), fake `BauhausFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
