# desktop-dark-ops — Dark Ops Console (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #757.
Branch `prototype/desktop-dark-ops`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_DARK_OPS=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_DARK_OPS=true` it boots `DarkOpsApp()` at 1280x800.

## Flow checklist (all clickable, fake `DarkOpsRepo` only)

- [ ] Sign-in: pick any of 4 fake operators (any secret) → LOGIN
- [ ] ONBOARDING lockout: sign in as K. Dela Pena → locked screen, empty capabilities, sign out
- [ ] Branch select: Makati (CLINIC) / Cebu Tour (PROVINCIAL_TOUR) / Tondo Mission (MEDICAL_MISSION)
- [ ] Home (`1`): clock in/out, today stats, relief duty + invite accept + request card
- [ ] Sessions (`2`): status filters incl. VOIDED, j/k navigation, detail pane,
      complete / no-show / cancel (walk-ins refuse NO_SHOW/CANCELLED — see note),
      void with reason dialog, unvoid, + book session dialog
- [ ] Clients (`3`): global registry, `* PENDING` star marks the one live session,
      detail pane, anonymize (PII nullified, gender/age kept)
- [ ] Finance (`4`): SESSION draft (net = completed − comp − expenses) and PRODUCT
      draft (add lines), SUBMIT → immutable snapshot, undo with reason (48h rule noted)
- [ ] Commission note: 5% pool split equally across clocked-in crew, outside remittance
- [ ] Team (`5`): slot-ordered roster, relief sorts last, ONBOARDING row locked, role bundle notes
- [ ] Mail (`6`): notifications mailbox, enter/click marks read, drain-all, unread badges;
      audit log (newest first, every mutation appends actor + clock + action)
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher,
      04:00 Asia/Manila boundary note; covered days lock edits for non-coordinators
- [ ] Profile (`7`): capability list, clock out, logout → back to sign-in
- [ ] Keys: `^K`/`ctrl-K` command palette, `j/k` move, `enter` act, `1–7` jump, `?` overlay

## Theme notes

Scraps the Linear system on purpose: near-black void (`#0A0C10`), phosphor green
(`#3DDC84`) primary, amber/cyan/violet semantic badges, hairline borders, zero shadows.
Monospace numerals everywhere (console density); status bar always visible with
day/branch/user/pending/unread. Power-user density: rail + detail pane + audit on one
1280x800 surface, full-screen capable.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/dark-ops/`
(package `com.companyb.companyapp.proto.darkops` — underscore because hyphens are
illegal in package names and trip the repo `PackageNaming` rule):

- `DarkOpsTheme.desktop.kt` — palette, dark scheme, mono typography
- `DarkOpsFake.desktop.kt` — fake domain + seeded `DarkOpsRepo` state holder
- `DarkOpsActions.desktop.kt` — repo mutations (each appends an audit entry)
- `DarkOpsChrome.desktop.kt` — badges, panels, rows, notes, j/k key modifier
- `DarkOpsAuth.desktop.kt` — sign-in, ONBOARDING lock, branch select
- `DarkOpsHome.desktop.kt` — clock-in, today stats, relief cards
- `DarkOpsSessions.desktop.kt` — roster, filters, detail, void/create dialogs
- `DarkOpsClients.desktop.kt` — registry, detail, anonymize
- `DarkOpsFinance.desktop.kt` — SESSION/PRODUCT drafts, submit, snapshots, commission
- `DarkOpsTeam.desktop.kt` — roster, presence, role bundles
- `DarkOpsMailbox.desktop.kt` — notices + audit log
- `DarkOpsProfile.desktop.kt` — capabilities, clock-out, logout
- `DarkOpsApp.desktop.kt` — shell: banner, rail, status bar, palette, shortcuts

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on console open; no `try/catch`, no network imports.
