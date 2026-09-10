# Desktop prototype: arcade-cabinet

Arcade cabinet. Neon bezel chrome on near-black, insert-coin login, high-score day records.
Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_ARCADE_CABINET=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email + PIN works, `INSERT COIN + START` adds a credit and goes to cabinet select.
- [ ] ONBOARDING: `ONBOARDING demo` shows the locked mat (zero capabilities, empty bundle note).
- [ ] Branch select: pick Sunrise Clinic, Harbor Provincial Tour, or Lingap Medical Mission by hi-score card.
- [ ] Home: clock in/out at the home branch; broadcast relief request / send relief invite; relief board lists requests and invites with branch + day; hi-score income table.
- [ ] Sessions: filter PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule banner (walk-ins block NO_SHOW/CANCELLED buttons); detail expands; void with reason; unvoid.
- [ ] Clients: global roster; at-most-one-PENDING league rule note; anonymized global toggle plus per-row anonymize/restore.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION (completed income) and PRODUCT (price x quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; role glance card.
- [ ] Mailbox: read/unread toggles, mark-all-read, relief items name branch and day.
- [ ] Audit: void / unvoid / submit / undo / clock events append entries with reasons.
- [ ] Profile: save name, flip day state, clock out + eject, reset demo data.

## Theme notes

- Palette: cabinet black `#0A0A12`, panel `#12121E`, neon pink `#FF2E88`, cyan `#00E5FF`, hi-score yellow `#FFD400`, player green `#39FF6A`, boss purple `#9D4DFF`, coin gold `#FFB800`, chrome `#9AA3B2`.
- Shape: sharp 4-8dp bevels, 2px neon borders, black inset panels - a bezel, not a card deck.
- Type: monospace marquee headers (`>>` section prefixes, uppercase chips); marquee gradient banner per screen.
- Chrome rails: 26dp chrome-gradient side rails frame every screen; marquee title bar names branch, player, credits.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/arcade-cabinet/*.desktop.kt` (package `...proto.arcadecabinet`), fake `ArcadeCabinetFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
