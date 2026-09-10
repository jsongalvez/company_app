# desktop-daybreak-routine — Daybreak Opener (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #847.
Branch `prototype/desktop-daybreak-routine`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_DAYBREAK_ROUTINE=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_DAYBREAK_ROUTINE=true` it boots `DaybreakApp()` at 1280x800.

## Flow checklist (all clickable, fake `DaybreakRepo` only)

- [ ] Sign-in: pick any of 4 fake keys (any unlock) → UNLOCK
- [ ] ONBOARDING lockout: sign in as K. Dela Pena → held-at-the-door screen, empty bundle, hand key back
- [ ] Branch select: Makati (CLINIC) / Cebu Tour (PROVINCIAL_TOUR) / Tondo Mission (MEDICAL_MISSION)
- [ ] Opener (`1`): 4-step ritual — [1] unlock via clock-in, [2] count drawer vs 12500 float, [3] review day, [4] welcome first client → OPEN THE DOORS; relief strip with grant
- [ ] Sessions (`2`): status filters, tap for detail, complete / no-show / cancel (walk-ins refuse NO_SHOW/CANCELLED — see note), void with reason dialog, unvoid, + book dialog
- [ ] Clients (`3`): global registry, `*` marks the one live PENDING session, detail pane, anonymize (PII nullified, gender/age kept)
- [ ] Finance (`4`): SESSION draft (net = completed − comp − expenses) and PRODUCT draft (add lines), SUBMIT → immutable snapshot, undo with reason (48h rule noted)
- [ ] Commission note: 5% pool split equally across clocked-in crew, outside remittance
- [ ] Team (`5`): slot-ordered roster, relief sorts last, ONBOARDING row locked, role bundle notes
- [ ] Mail (`6`): notifications mailbox, tap marks read, drain-all, unread badges; audit log newest-first, every mutation appends actor + clock + action
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher, 04:00 Asia/Manila boundary note; locked days gate edits for non-coordinators
- [ ] Profile (`7`): capability list, clock out, logout → back to key rack
- [ ] Window: 1280x800 minimum, full-screen capable; status bar shows day/branch/user/pending/unread

## Theme notes

Scraps the Linear system for a sunrise opener: warm paper (`#FFF6E8`), cream cards, ink-brown text, sunrise orange (`#D96C2B`) primary, sage/sky/rose semantic badges, rounded 8-10dp panels, hairline tan borders, zero shadows. The rail is a "sunrise rail" with opener progress; the top banner is a dark ink strip with the Branch Day switcher in cream below. Ritual-first, not console-dense: one vertical morning checklist instead of a power grid.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/daybreak-routine/`
(package `com.companyb.companyapp.proto.daybreakroutine` — no hyphen, hyphens are illegal in packages):

- `DaybreakTheme.desktop.kt` — palette, light scheme, dawn typography
- `DaybreakFake.desktop.kt` — fake domain + seeded `DaybreakRepo` state holder
- `DaybreakActions.desktop.kt` — repo mutations (each appends an audit entry)
- `DaybreakChrome.desktop.kt` — badges, panels, rows, notes, day banner
- `DaybreakAuth.desktop.kt` — key rack sign-in, ONBOARDING lock, branch select
- `DaybreakRitual.desktop.kt` — 4-step opener + relief strip (signature screen)
- `DaybreakSessions.desktop.kt` — roster, filters, detail, void/create dialogs
- `DaybreakClients.desktop.kt` — registry, detail, anonymize
- `DaybreakFinance.desktop.kt` — SESSION/PRODUCT drafts, submit, snapshots, commission
- `DaybreakTeam.desktop.kt` — roster, presence, role bundles
- `DaybreakMailbox.desktop.kt` — notices + audit log
- `DaybreakProfile.desktop.kt` — capabilities, clock-out, logout
- `DaybreakApp.desktop.kt` — shell: banner, rail, status bar

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on opener open; no network imports.
