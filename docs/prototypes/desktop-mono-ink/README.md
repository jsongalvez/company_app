# Desktop prototype: mono-ink

Single-ink mono. The whole company printed in one ink on paper — hierarchy
carried by weight and size only, print-pure. Broadsheet masthead, folio
numbers, section numerals, ruled tables, outline stamps. No color, no fills,
no elevation, square corners. Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_MONO_INK=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Pull the proof` goes to bureau select.
- [ ] ONBOARDING: `Read the ONBOARDING notice` shows the locked gallery mat (empty capability bundle note).
- [ ] Bureau select: pick Sunrise Clinic (OPEN), Harbor Tour (PAST), or Lingap Mission (REMITTED).
- [ ] Front page (home): clock in/out; relief invites accept/decline, ask-for-relief form; day in figures.
- [ ] Session ledger: filter ALL / PEND / DONE / N-SHOW / CXLD; walk-in rule note shown; expand rows; complete / no-show (booked only) / void with reason; unvoid with reason.
- [ ] Client directory: global list; at-most-one-PENDING note; anonymized codes by default, lift the veil for names.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; move the day from Colophon.
- [ ] Counting room: SESSION (net income) and PRODUCT (price x quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Staff box: users and roles incl. ONBOARDING locked row; capability glance note.
- [ ] Letters: read/unread marks, tap toggles, mark-all-opened.
- [ ] Press log: void / unvoid / submit / undo / clock / relief entries with reasons.
- [ ] Colophon: clock in/out, move the day, reset demo, log out, change bureau from the sidebar.

## Theme notes

- Palette: paper `#FBFAF4`, ink `#1B1812`, tints only via ink alpha (halftone hairlines). No chromatic color anywhere, including the Material3 scheme.
- Shape: square corners, 3px/1px rules, dotted leaders, outline stamps, zero elevation.
- Type: serif black headlines (uppercase), tracked-sans kickers, italic serif decks, mono figures and folio.
- Copy: press-room voice ("Pull the proof", "Today's sheet", "Counting room", "Stop-press figures").
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/mono-ink/*.desktop.kt` (package `...proto.monoink`), fake `MonoFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
