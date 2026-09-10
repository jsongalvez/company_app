# Desktop prototype: chalkboard

Chalkboard wall — dark slate, chalk typography, smudge dividers, class-session
warmth. Homeroom copy throughout (periods, registers, pigeonholes, chalk tray).
Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_CHALKBOARD=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Chalk me in` goes to branch select.
- [ ] ONBOARDING: `Peek at the ONBOARDING slate` shows the locked after-class slate (empty capability bundle note).
- [ ] Branch select: pick Homeroom Clinic, Field-Trip Tour, or Open-Day Mission.
- [ ] Home: chalk in/out at the home branch; ask for relief / offer help; relief roster lists requests, invites and duty.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; details expand; void with reason; unvoid; complete; add fresh walk-in.
- [ ] Clients: global pupil cards; at-most-one-PENDING note; anonymize/reveal toggle.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION tray and PRODUCT tray drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; role glance card.
- [ ] Mailbox: unread count, read buttons, mark-all-read, relief items name branch and day.
- [ ] Audit: void / unvoid / complete / submit / undo / walk-in / clock events append entries with reasons.
- [ ] Profile: Branch Day remote control, chalk in/out, reset demo, logout.

## Theme notes

- Palette: slate deep `#1A222C` background, slate `#232D38` rail, board `#2B3644` cards, tray `#314052` raised, chalk white `#F4F1E8` text, chalk yellow `#F2D06B` primary, chalk mint/pink/blue/orange status pastels.
- Shape: small radii (4-12dp) — chalk trays, not candy cards.
- Copy: classroom language ("Take your seat", "Period 1", "Pigeonholes", "Chalked into the ledger", "Class dismissed").
- Dividers: smudge rules — a full chalk line plus a shorter faded offset line.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/chalkboard/*.desktop.kt` (package `...proto.chalkboard`), fake `ChalkboardFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
