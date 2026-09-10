# Desktop prototype: pass-counter

Kitchen pass counter. Sessions are paper tickets pinned on the pass rail under
heat lamps: the pending rail fires left, the selected slip faces the line in the
middle, the completed bell and the void waste log hang right. Charcoal steel,
lamp amber, cream ticket paper. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_PASS_COUNTER=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Onboarding: stage welcome shows the ONBOARDING lock (empty capability bundle note).
- [ ] Login: pick a crew member + any 4-digit PIN; clocks in at the pass.
- [ ] Stations: Garde Manger / Grill Station / Tour Cart 3 cards with day tags; `Work ...` opens the pass.
- [ ] Pass home: pending rail with slip rows; slip detail with status calls; clock in/out; relief cover count with invite/request shortcuts; completed bell column; void waste log column.
- [ ] Tickets: ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/Cancel buttons on walk-ins); status jumps incl. re-fire; void with required reason; pull from waste; fire booked or walk-in ticket.
- [ ] Guests: global book; at-most-one-PENDING rule enforced (fire disabled while a slip is up); named/anonymized toggle plus blur-all.
- [ ] Service-day banner: OPEN / PAST / REMITTED pills plus the 04:00 Asia/Manila boundary note; flippable from banner or Profile.
- [ ] Cash-up: SESSION and PRODUCT tills; seal submits a snapshot; undo within 48h with reason (one-shot); commission-split note (60/40 SESSION, 80/20 PRODUCT).
- [ ] Crew: roles incl. ONBOARDING locked card with one-tap Practitioner grant; relief board with grant/fold; broadcast request and send invite with a note.
- [ ] Mailbox: read/unread toggles, mark-all-read; unread count rides the MAILBOX rail entry.
- [ ] Ledger: fire/bell/void/grant/submit/undo audit entries with reasons.
- [ ] Profile: expo card, clock in/out, log out, day flip, demo reset.

## Theme notes

- Palette: charcoal `#1B1512` steel `#2A2320`, heat-lamp amber `#E8A020`, ticket cream `#FFF8E8`, brass `#C9A227`, fire `#C2410C`, serve green `#2F7D4F`, waste red `#B3372F`.
- Shape: 8dp ticket slips, round filter pills, mono slip numbers (`PcSlip`) with heavy board headers.
- Copy: kitchen language ("The Pass", "Firing", "Bell", "Waste log", "Cash-up drawer", "Cover board", "Spike").
- Layout: heat-lamp banner + day band on top; steel ticket rail left; paper work area center; bell/waste dark cards right on the pass home.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/pass-counter/*.desktop.kt` (package `...proto.passcounter`), fake `PassCounterFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
