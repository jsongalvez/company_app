# Desktop prototype: swiss-grid

Swiss grid purist. Strict 12-column ruler, Helvetica-voice sans, black rules
and whitespace only, red used once per screen. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_SWISS_GRID=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Onboarding (00): ONBOARDING locked mat, empty capability bundle note, continue to login.
- [ ] Login (01): any email works, `Enter the grid` goes to branch select.
- [ ] Branch select (02): Ermita Clinic (CLINIC / OPEN), Tagaytay Tour (PROVINCIAL_TOUR / PAST), Payatas Mission (MEDICAL_MISSION / REMITTED).
- [ ] Home (03): big pending numeral + completed total + clock state; clock in/out; relief invites accept/decline; broadcast relief request form; today strip; 04:00 Manila boundary note.
- [ ] Sessions (04): All/PENDING/COMPLETED/NO_SHOW/CANCELLED filter tabs; per-row status tap; walk-in rows hide NO_SHOW/CANCELLED with rule note; void/unvoid with reason; add walk-in.
- [ ] Clients (05): global list; at-most-one-PENDING note; veiled codes by default, tap to reveal; anonymize keeps gender + age.
- [ ] Branch-day banner: black band with OPEN / PAST / REMITTED copy plus 04:00 Asia/Manila note on every screen.
- [ ] Finance (06): SESSION (net) and PRODUCT (price × qty) drafts; submit freezes immutable snapshot; undo within 48h with reason; commission-split pool note.
- [ ] Team (07): Practitioner / Coordinator / MANAGER / Accountant / ONBOARDING locked row; capability bundle notes.
- [ ] Mailbox (08): read/unread dots, tap toggles, mark-all-read; relief events name branch + day.
- [ ] Audit (09): void / unvoid / submit / undo / clock / relief rows with reasons.
- [ ] Profile (10): clock in/out, move the day (OPEN/PAST/REMITTED demo control), reset demo, log out, exit.

## Theme notes

- Palette: paper `#FFFFFF`, wash `#F4F4F2`, ink `#111111`, grey `#6E6E6E`, hairline `#E1E1E1`, black `#000000` rules, red `#E30613` accent sparingly.
- Shape: no cards, no shadows, no rounded corners. Black 3dp top rules, 1dp hairlines, whitespace separation.
- Type: system sans throughout; 11sp uppercase micro-labels with tracking; 30sp bold headlines; 44sp bold numerals for counts.
- Grid: 12-column ruler under the masthead (01 in red); two-column 7/5 splits on wide screens; numbered sections (3.1, 4.9, 6.3…).
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/swiss-grid/*.desktop.kt` (package `...proto.swissgrid`), fake `SgFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
