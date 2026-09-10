# Desktop prototype: blueprint-draft

Architect blueprint. White-on-blue linework, measurement labels, corner ticks,
dimension bars, and a rotated draft stamp. Scrap the Linear look — every card is
a drawing sheet with sheet numbers, scales, and dashed revision dividers.
Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_BLUEPRINT_DRAFT=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: `▸ INK LOGIN` goes to table select.
- [ ] ONBOARDING: `ONBOARDING SHEET` shows the locked sheet (empty capability bundle note).
- [ ] Branch select: four drafting tables with sheet numbers; click `▸ DRAFT HERE` enters the shell on that table.
- [ ] HOME: clock in/out; relief board with CLAIM / RELEASE; file a relief request; send a relief invite with name + note.
- [ ] Sessions: ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/CANCELLED targets on walk-ins); jump status; cloud out (void) with required reason; erase cloud (unvoid); ink a PENDING walk-in.
- [ ] Clients: global file; at-most-one-PENDING note with live PEND n/1 tags; anonymized view toggle (codes only, keeps gender and age fragment).
- [ ] Branch-day banner: title block with OPEN / PAST / REMITTED plus the 04:00 Asia/Manila datum note; SWAP TABLE returns to select; flip states from Profile.
- [ ] Finance: SESSION and PRODUCT sheets; SUBMIT + SNAP seals a snapshot; UNDO 48H with reason returns to draft; commission split note (60/40 practitioner/house, settled at snapshot).
- [ ] Team: roster and roles incl. ONBOARDING empty-bundle card with one-tap countersign to Practitioner; tray guide blurbs.
- [ ] Mailbox: read/unread sheet cards, tap to flip, all-read; unread count rides the index rail.
- [ ] Audit: revision history of clock, relief, status, cloud, seal, undo entries with reasons.
- [ ] Profile: clock in/out, log out, flip branch-day, swap table, reset drafting table.

## Theme notes

- Palette: blueprint blue `#0E3A8A`, deep `#0A2A66`, sheet `#16449B`, line white `#FFFFFF`, dim `#C9DAFF`, faint `#8FA9DC`, stamp `#FFD84D`, good `#9DF0B6`, bad `#FF9D9D`.
- Shape: 2dp sharp sheets, 1.5-2dp white outlines, corner ticks, dashed dividers, dimension bars with tick marks, rotated stamp at -4 degrees.
- Copy: drafting language ("Ink login", "Pick a table", "Cloud out", "Seal", "Countersign", "Datum", "Sheet", "Rev P1").
- Layout: grid backdrop with border frame, dark index rail left (228dp), title-block banner on top, scrolling sheets right.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/blueprint-draft/*.desktop.kt` (package `...proto.blueprintdraft`), fake `BlueprintDraftFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
