# Desktop prototype: block-builder

Toy block builder. Chunky brick cards with white stud strips snap-feel into day
layouts on a cream table, green baseplate accents, primary red/blue/yellow
bricks. Rounded 14dp bricks, deep-ink outlines, click/snap/pop copy everywhere.
Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_BLOCK_BUILDER=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: `SNAP INTO THE TOY BOX` goes to plate select.
- [ ] ONBOARDING: `Peek at the ONBOARDING brick` shows the loose-brick card (empty capability bundle note).
- [ ] Branch select: four build plates with stud headers; click a plate then `CLICK ... INTO PLACE` enters the shell on that plate.
- [ ] HOME: snap in / pop off; day-stack card; relief stack board with SNAP ON / POP OFF; stack a relief request; snap a relief invite with name + note.
- [ ] Sessions: ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/CANCELLED click targets on walk-ins); click-to-status jumps; pop off with required reason; snap back on; stack a PENDING walk-in.
- [ ] Clients: global brick pit; at-most-one-PENDING note; anonymized view toggle (global pill plus per-card anonymize, keeps gender and age).
- [ ] Branch-day banner: stud-capped OPEN / PAST / REMITTED plates plus the 04:00 Asia/Manila boundary note; SWAP PLATE returns to select; flip states from Profile.
- [ ] Finance: SESSION and PRODUCT bricks; stack seals a snapshot; pop off (undo) within 48h with reason; click-split note (60/40 practitioner/house, settled at snapshot).
- [ ] Team: minifigs and roles incl. ONBOARDING loose brick with one-tap Practitioner snap-on; tray guide blurbs.
- [ ] Mailbox: read/unread brick notes, click-all-read; unclicked count rides the MAIL tray brick.
- [ ] Audit: click log of snap-in, relief, pop-off/snap-back, stack/undo entries with reasons.
- [ ] Profile: snap in / pop off (clock out), hop out (log out), flip build-plate day, reset toy box.

## Theme notes

- Palette: cream `#FFF6E9`, ink `#23242B`, brick red `#E03131`, brick blue `#1971C2`, brick yellow `#FFC300`, baseplate green `#2F9E44`; cards white with 3dp ink outlines and colored stud headers.
- Shape: 14dp rounded bricks, stud strips (white dots on color), offset shadow as brick depth, tray-style nav with stud-capped brick buttons.
- Copy: snap/click/pop/stack language ("Snap into the toy box", "Pick your build plate", "Session bricks", "Click log").
- Layout: dark brick-tray rail left (210dp), stud-capped plate banner on top, scrolling brick cards right.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/block-builder/*.desktop.kt` (package `...proto.blockbuilder`), fake `BlockBuilderFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
