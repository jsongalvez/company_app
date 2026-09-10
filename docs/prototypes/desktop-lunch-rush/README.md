# desktop-lunch-rush — Midday Rush Board (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #848.
Branch `prototype/desktop-lunch-rush`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_LUNCH_RUSH=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_LUNCH_RUSH=true` it boots `LunchRushApp()` at 1280x800.

## Flow checklist (all clickable, fake `LunchRushRepo` only)

- [ ] Sign-in: pick any of 4 fake crew (M. Santos / J. Reyes / A. Villanueva / K. Dela Pena) → TAKE FLOOR
- [ ] ONBOARDING lockout: sign in as K. Dela Pena → locked screen, zero capabilities, sign out
- [ ] Branch select: Makati Flagship (CLINIC) / Cebu Tour Van (PROVINCIAL_TOUR) / Tondo Mission (MEDICAL_MISSION)
- [ ] Rush board (`1`): rush meter, bottleneck FIRE flags, clock in/out, today stats, RELIEF CALL bell, relief duty accept + request card
- [ ] Tickets (`2`): status filters incl. VOIDED, click-to-select, detail pane, complete / no-show / cancel (walk-ins refuse NO_SHOW/CANCELLED — see note), void with reason dialog, unvoid, + book ticket dialog
- [ ] Regulars (`3`): global registry, `* PENDING` star marks the live ticket, detail pane, anonymize (PII nullified, gender/age kept)
- [ ] Till (`4`): SESSION draft (net = completed − comp − expenses) and PRODUCT draft (add lines), SUBMIT → immutable snapshot, undo with reason (48h rule noted)
- [ ] Surge pricing note: rush meter 5+ adds 10% walk-in Express surcharge at submit; booked regulars keep menu price
- [ ] Commission note: 5% pool split equally across clocked-in crew, outside remittance
- [ ] Crew (`5`): slot-ordered roster, relief sorts last, ONBOARDING row locked, role bundle notes, call-in button
- [ ] Pass (`6`): notifications rail, click marks read, drain-all, unread badges; audit log (newest first, every mutation appends actor + clock + action)
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher, 04:00 Asia/Manila boundary note
- [ ] My apron (`7`): capability list, clock in/out, hang apron + logout → back to sign-in
- [ ] Keys: `1–7` jump between stations, `?` toggles shortcut card

## Theme notes

Scraps the Linear system on purpose: warm hawker-stall paper (`#FFF6E8`), char red
(`#C62F2F`) primary, turmeric amber bottleneck flags, wok-black day banner, ticket-rail
cards with perforated hot edges. Kitchen-pass metaphor: sessions are order tickets,
notifications are the pass rail, relief is ringing the bell for backup hands. Rush meter
up top turns char-red at heat 5+ and flips on the surge-price flag. Dense but friendly:
rail + board + status bar on one 1280x800 surface, full-screen capable.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/lunch-rush/`
(package `com.companyb.companyapp.proto.lunchrush` — no hyphens in package names):

- `LunchRushTheme.desktop.kt` — palette, light scheme, typography
- `LunchRushFake.desktop.kt` — fake domain + seeded `LunchRushRepo` state holder
- `LunchRushActions.desktop.kt` — repo mutations (each appends an audit entry)
- `LunchRushChrome.desktop.kt` — badges, FIRE flags, ticket cards, meter, notes
- `LunchRushAuth.desktop.kt` — sign-in, ONBOARDING lock, branch select
- `LunchRushHome.desktop.kt` — rush meter, flags, clock, relief call bell, relief cards
- `LunchRushSessions.desktop.kt` — rail, filters, detail, void/create dialogs
- `LunchRushClients.desktop.kt` — registry, detail, anonymize
- `LunchRushFinance.desktop.kt` — SESSION/PRODUCT drafts, submit, snapshots, commission + surge
- `LunchRushTeam.desktop.kt` — roster, presence, role bundles
- `LunchRushMailbox.desktop.kt` — notices + audit log
- `LunchRushProfile.desktop.kt` — capabilities, clock-out, logout, shortcuts card
- `LunchRushApp.desktop.kt` — shell: banner, rail, status bar, key shortcuts

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on board open; no network imports.
