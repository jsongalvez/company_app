# Desktop prototype: wireframe-live

Living wireframe. Grayscale boxes that fill with real fake data, numbered
annotation flags on every box. Scrap the Linear look — white paper, gray fill
blocks, black label strips, dashed dividers, rulers, and a FLAGS on/off rail
toggle. Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_WIREFRAME_LIVE=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: `▸ FILL + ENTER` goes to branch boxes.
- [ ] ONBOARDING: `ONBOARDING BOX` shows the locked box (empty capability bundle note).
- [ ] Branch select: four branch boxes with box numbers; click `▸ FILL HERE` enters the shell on that box.
- [ ] HOME: clock in/out; relief board with CLAIM / RELEASE; file a relief request; send a relief invite with name + note.
- [ ] Sessions: ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/CANCELLED targets on walk-ins); jump status; void with required reason; unvoid; book a PENDING walk-in.
- [ ] Clients: global file; at-most-one-PENDING note with live PEND n/1 tags; anonymized view toggle (codes only, keeps gender and age fragment).
- [ ] Branch-day banner: fill box with OPEN / PAST / REMITTED plus the 04:00 Asia/Manila boundary note; SWAP BOX returns to select; flip states from Profile.
- [ ] Finance: SESSION and PRODUCT boxes; SUBMIT + SNAP seals a snapshot; UNDO 48H with reason returns to draft; commission split note (60/40 practitioner/house, settled at snapshot).
- [ ] Team: roster and roles incl. ONBOARDING empty-bundle card with one-tap flag to Practitioner.
- [ ] Mailbox: read/unread box cards, tap to flip, all-read; unread count rides the index rail.
- [ ] Audit: fill history of clock, relief, status, void, seal, undo entries with reasons.
- [ ] Profile: clock in/out, log out, flip branch-day, swap box, reset boxes.

## Theme notes

- Palette: paper `#FFFFFF`, wash `#F4F4F5`, box `#E4E4E7`, fill `#D4D4D8`, faint `#A1A1AA`, mid `#52525B`, ink `#18181B`. Grayscale only — status reads from fill (black = sealed/done, gray = draft/past, white = open) plus text tags.
- Shape: 2dp sharp boxes, black label strips with box numbers, dashed dividers, ruler bars with tick marks, hatched fill blocks, black flag squares with white numbers.
- Copy: box language ("Fill here", "Box switched", "Flag-off tray", "Shelf math", "Fill lifted").
- Layout: light grid backdrop with border frame, white index rail left (232dp), day-fill banner on top, scrolling boxes right, FLAGS on/off toggle in the rail.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/wireframe-live/*.desktop.kt` (package `...proto.wireframelive`), fake `WireframeLiveFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
