# desktop-noir-detective — Midnight Noir Precinct (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #801.
Branch `prototype/desktop-noir-detective`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_NOIR_DETECTIVE=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_NOIR_DETECTIVE=true` it boots `NoirApp()` at 1280x800.

## Flow checklist (all clickable, fake `NoirRepo` only)

- [ ] Sign-in: pick any of 4 operatives on the night log → in
- [ ] ONBOARDING lockout: sign in as K. Dela Pena → badgeless screen, empty capabilities, back out
- [ ] Branch select: Malate (CLINIC) / Cebu Stakeout (PROVINCIAL_TOUR) / Tondo Outreach (MEDICAL_MISSION)
- [ ] Dossier (`1`): clock in/out, tonight-by-the-numbers, relief duty grant + invite accept/decline + request deny
- [ ] Case files (`2`): status filters incl. VOIDED, folder list, detail pane,
      close / no-show / drop (walk-ins refuse NO_SHOW/CANCELLED — see note),
      void with reason dialog, unvoid, + open-a-case dialog (one-PENDING rule enforced)
- [ ] Persons (`3`): global registry, `★ PENDING` marks the one live session,
      detail pane with session history, anonymize (PII nullified, gender/age kept)
- [ ] Ledger (`4`): SESSION draft (net = closed − comp − expenses) and PRODUCT
      draft (add/remove lines), SEAL → envelope snapshot, undo with reason (48h rule noted)
- [ ] Commission note: 5% pool split equally across clocked-in crew, outside remittance
- [ ] Squad (`5`): slot-ordered roster, relief sorts last, ONBOARDING row badgeless, role stamps
- [ ] Wire (`6`): notifications mailbox, click/Read marks read, drain-all, NEW badges;
      night log (newest first, every mutation appends actor + clock + action)
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher,
      04:00 Asia/Manila boundary note; covered days lock edits for non-coordinators
- [ ] Badge (`7`): capability list, clock out, log out → back to the night log

## Theme notes

Scraps the Linear system on purpose: rain-dark navy night (`#080D1A`), precinct panels
(`#0D1528`), desk-lamp amber (`#E8A33D`) primary, neon-sign blue (`#6EA8FF`) accents,
siren red (`#E5484D`) for void/urgent, evidence green (`#46C08A`) for closed splits.
Venetian-blind dividers (alternating 2px slats) frame the board top and bottom;
rubber-stamp chips (rotated, bordered) mark case status; serif headlines read like a
dime-novel masthead while mono dossier labels keep the evidence-wall density.
Rain glyphs (⁂) drift over the auth screens; the footer always shows day/pending/unread.
Full-screen capable at 1280x800 minimum.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/noir-detective/`
(package `com.companyb.companyapp.proto.noirdetective` — hyphens are
illegal in package names):

- `NoirTheme.desktop.kt` — palette, dark scheme, serif + mono typography
- `NoirFake.desktop.kt` — fake domain + seeded `NoirRepo` state holder
- `NoirActions.desktop.kt` — repo mutations (each appends a night-log entry)
- `NoirChrome.desktop.kt` — blinds, stamps, badges, folders, rows, notes
- `NoirAuth.desktop.kt` — night-log sign-in, ONBOARDING lock, precinct select
- `NoirHome.desktop.kt` — clock-in dossier, numbers, relief cards
- `NoirSessions.desktop.kt` — case board, filters, folder detail, void/open dialogs
- `NoirClients.desktop.kt` — persons registry, file detail, anonymize
- `NoirFinance.desktop.kt` — SESSION/PRODUCT drafts, sealed envelopes, undo, split note
- `NoirTeam.desktop.kt` — squad roster, presence, role stamps
- `NoirMailbox.desktop.kt` — wire intercepts + night log
- `NoirProfile.desktop.kt` — badge, capabilities, clock-out, log out
- `NoirApp.desktop.kt` — precinct shell: banner, evidence rail, footer

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on board open; no `try/catch`, no network imports.
