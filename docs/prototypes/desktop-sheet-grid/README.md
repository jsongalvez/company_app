# desktop-sheet-grid — Sheet-Grid (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #817.
Branch `prototype/desktop-sheet-grid`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_SHEET_GRID=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_SHEET_GRID=true` it boots `SheetGridApp()` at 1280x800.

## Flow checklist (all clickable, fake `SgStore` only)

- [ ] Sign-in: pick any of 5 fake operators in the login grid, enter any secret → LOGIN
- [ ] ONBOARDING lockout: sign in as K. Dela Pena → locked screen, blank capability range, sign out
- [ ] Branch select: Makati Clinic (CLINIC) / Cebu Provincial Tour (PROVINCIAL_TOUR) / Tondo Medical Mission (MEDICAL_MISSION)
- [ ] Home: TODAY stat strip, clock in/out toggle, relief grid (DUTY / REQUEST / INVITE) — grant/deny requests, accept/decline invites and duties
- [ ] Sessions: status filters incl. VOIDED, tap-to-select rows, Complete / No-show / Cancel (walk-ins refuse NO_SHOW/CANCELLED with a note), Void with reason dialog, Unvoid, + BOOK dialog (walk-in gets W-ticket)
- [ ] Clients: global registry, `* PENDING` marks the live Session client, Anonymize (PII nulled, gender/age kept)
- [ ] Finance: SESSION draft (net = gross − deductions, ± adjusters) and PRODUCT lines (+ BALM / − LAST), SUBMIT → sealed snapshot, Undo with reason (48h rule noted)
- [ ] Commission note: pooled per Branch day, split equally across clocked-in crew, outside remittance
- [ ] Team: slot-ordered roster, ONBOARDING row locked, MANAGER grants Practitioner
- [ ] Mail: notifications mailbox, tap marks read, ALL/UNREAD filter, mark-all-read, JUMP→DAY tap-through to HOME; audit log newest-first below on AUDIT tab
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher, 04:00 Asia/Manila boundary note
- [ ] Profile: capability range, clock in/out, switch Branch, logout → back to sign-in

## Theme notes

Scraps the Linear system: everything is a spreadsheet. White workbook with 1px
grid lines (`#D3D8D3`), frozen grey header rows plus column letters (A, B, C…)
and row numbers on every tab, Excel-green formula bar (`#107C41`, `fx` glyph)
echoing the active cell, bottom sheet tabs (HOME … PROFILE) for navigation, and
a dark-green status bar (READY · context · key hints). Data cells are monospace
12sp, dense and tabular throughout. Keyboard: ↑↓ moves the active row, Enter
acts where offered. The premise under test: operators who live in ledgers can
run the whole branch day without leaving the grid.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/sheet-grid/`
(package `com.companyb.companyapp.proto.sheetgrid` — hyphens are illegal in
package names, so the hyphenated directory maps to the concatenated package):

- `SheetGridTheme.desktop.kt` — grid tokens, cells, headers, notes, buttons
- `SheetGridFake.desktop.kt` — fake domain + seeded `SgStore`
- `SheetGridChrome.desktop.kt` — banner, formula bar, sheet tabs, status bar, arrow-key modifier
- `SheetGridApp.desktop.kt` — shell: login/lock/branch select + eight workbook sheets

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on prototype start; no network imports.
