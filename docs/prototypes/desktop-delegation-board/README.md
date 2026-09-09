# Desktop prototype: delegation-board

Mission delegation board. Triage-tag manifest aesthetic — paper background, rubber-stamp
branch-day banner, monospace dispatch codes — with roster, coverage gaps, and the
invite/accept flow front and center. Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_DELEGATION_BOARD=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Open the delegation board` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked mat (empty capability bundle note).
- [ ] Branch select: pick Lingap Medical Mission, Sunrise Clinic, or Harbor Provincial Tour.
- [ ] Board: coverage gaps with red tags, invite-help / ask-for-relief per gap, invite/accept inbox (accept-grant/decline-deny, revoke after), duty roster in branch slot order, clock in/out.
- [ ] Sessions: filter PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; open rows to complete / no-show / cancel / reopen (walk-ins block no-show/cancel); void with reason; unvoid; log a walk-in.
- [ ] Clients: global list; at-most-one-PENDING note; anonymized view toggle (gender/age kept).
- [ ] Branch-day banner: OPEN / PAST / REMITTED stamp plus the 04:00 Asia/Manila boundary note; flip states from Profile demo controls.
- [ ] Finance: SESSION draft (net of compensation+expenses) and PRODUCT draft (price x quantity, add lines); submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row with one-tap Practitioner grant; relief-after-home-slot note.
- [ ] Mailbox: read/unread toggles, mark-all-read, relief items name branch and day.
- [ ] Audit: clock/grant/void/submit/undo entries with reasons, newest first.
- [ ] Profile: switch delegate, clock out, log out, reset demo data.

## Theme notes

- Palette: manifest paper `#F4F1E6`, card `#FFFFFDF6`, ink `#22302C`, triage red `#B3372A` primary (gaps/urgent), dispatch teal `#1F5C55` secondary (actions), amber `#9A6B14` (pending), triage green `#2E7D4F` (sealed/on-duty).
- Motifs: triage tags for every status, rubber-stamp day banner, monospace dispatch codes (`MSN-014`, `#01` slots), `FIELD NOTE` rule cards quoting CONTEXT.md domain rules.
- Copy: dispatch language ("Posted", "On duty", "Inbox zero", "The mission breathes").
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/delegation-board/*.desktop.kt` (package `...proto.delegationboard`), fake `DelegationBoardFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
