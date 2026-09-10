# desktop-context-rail — Context Rail (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #812.
Branch `prototype/desktop-context-rail`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_CONTEXT_RAIL=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_CONTEXT_RAIL=true` it boots `ContextRailApp()` at 1280x800.

## Flow checklist (all clickable, fake `ContextRailStore` only)

- [ ] Sign-in: pick any of 5 fake operators (any secret) → LOGIN
- [ ] ONBOARDING lockout: sign in as K. Dela Pena → locked screen, empty capabilities, sign out
- [ ] Branch select: Makati Clinic (CLINIC) / Cebu Provincial Tour (PROVINCIAL_TOUR) / Tondo Medical Mission (MEDICAL_MISSION)
- [ ] Home: clock in/out toggle, today stats, relief board (DUTY / REQUEST / INVITE) — selection opens in the right rail; grant/deny requests, accept/decline invites
- [ ] Sessions: status filters incl. VOIDED, tap-to-select (list stays), rail shows detail + Complete / No-show / Cancel (walk-ins refuse NO_SHOW/CANCELLED with a note), Void with reason dialog, Unvoid, Book session dialog
- [ ] Clients: global registry, `* PENDING` marks the one live Session, rail shows detail, Anonymize (PII nullified, gender/age kept)
- [ ] Finance: SESSION draft (net = gross − deductions) and PRODUCT draft (add lines), SUBMIT → immutable snapshot, Undo with reason (48h rule noted)
- [ ] Commission note: pooled per Branch day, split equally across clocked-in crew, manual remove, outside remittance
- [ ] Team: slot-ordered roster, relief sorts last, ONBOARDING row locked, rail shows role bundle; MANAGER can grant Practitioner
- [ ] Mail: notifications mailbox, tap marks read, mark-all-read, Jump-to-day tap-through; audit log newest-first below
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher, 04:00 Asia/Manila boundary note
- [ ] Profile: capability list, clock in/out, switch Branch, logout → back to sign-in

## Theme notes

Scraps the Linear system: warm reading-room paper (`#FAF6EE`) center that never moves,
forest-ink left nav (`#1A2E1F`), and an ink-navy inspector rail (`#101828`) with amber
accents that follows selection everywhere. Burnt-orange (`#B3541E`) primaries on paper,
amber (`#E8A838`) highlights in the rail. Hairline warm borders, zero shadows.
The premise under test: detail never displaces the list — every section (Sessions,
Clients, Finance, Team, Mail, Home) renders its selection in the same right rail.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/context-rail/`
(package `com.companyb.companyapp.proto.contextrail` — hyphens are illegal in
package names, so the hyphenated directory maps to the concatenated package):

- `ContextRailTheme.desktop.kt` — paper/forest/inspector tokens, chips, cards, notes
- `ContextRailFake.desktop.kt` — fake domain + seeded `ContextRailStore`
- `ContextRailChrome.desktop.kt` — banner, nav rail, inspector frame, status bar
- `ContextRailApp.desktop.kt` — shell: login/lock/branch select + seven sections with center-list + right-inspector pairs

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on prototype start; no network imports.
