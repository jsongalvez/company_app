# Desktop prototype: brutalist-raw

Raw brutalist concrete. Exposed gray slabs, 3px black borders, square corners,
oversized monospace type, hazard-orange stamps, zero decoration. Fake data only —
no network calls.

## Run

```bash
COMPANYAPP_PROTO_BRUTALIST_RAW=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any input works, `STAMP IN >` goes to branch select.
- [ ] ONBOARDING: `NEW HAND? VIEW ONBOARDING SLAB >` shows the locked slab (empty capability bundle note).
- [ ] Branch select: pick CONCRETE HQ, GRAVEL TOUR, or REBAR MISSION.
- [ ] Home: clock in/out at the home branch; request relief (broadcast); invite help; relief board with grant/deny/cut.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule stamped (no NO_SHOW/CANCELLED on walk-ins); open file; void with reason; unvoid; complete; add walk-in.
- [ ] Clients: global file; at-most-one-PENDING note; anonymize/reveal toggle.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION + PRODUCT drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; role glance card.
- [ ] Mailbox: unread count stamp, read, mark-all-read, relief items name branch and day.
- [ ] Audit: clock / void / unvoid / complete / submit / undo / walk-in / relief events append entries with reasons.
- [ ] Profile: Branch Day remote control, clock out, log out.

## Theme notes

- Palette: concrete paper `#D6D3CC` background, slab `#B9B5AB` containers, white `#F4F2ED` cards, ink `#111111` borders/text/nav, hazard `#FF4D00` accents and stamps.
- Shape: 0dp everywhere — square cards, square fields, square buttons. Borders 2-5px solid black.
- Type: monospace everywhere, oversized black headlines (44-72sp), numbered sections (`03 / CLOCK-IN`), uppercase stamped copy.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/brutalist-raw/*.desktop.kt` (package `...proto.brutalistraw`), fake `BrutalistRawFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
