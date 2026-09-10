# desktop-duotone-sea — Sea Duotone Tide Board (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #806.
Branch `prototype/desktop-duotone-sea`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_DUOTONE_SEA=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_DUOTONE_SEA=true` it boots `SeaApp()` at 1280x800.

## Flow checklist (all clickable, fake `SeaRepo` only)

- [ ] Sign-in: pick any of 4 crew on the shore gate → dive in
- [ ] ONBOARDING lockout: sign in as K. Dela Pena → badgeless hold screen, zero capabilities, back out
- [ ] Branch select: Malate Cove (CLINIC) / Cebu Crossing (PROVINCIAL_TOUR) / Tondo Tide (MEDICAL_MISSION)
- [ ] Lagoon (`1`): clock in/out, tide-by-the-numbers, relief duty grant + invite accept/decline + request
- [ ] Tide chart (`2`): status filters incl. VOIDED, swim list, detail pane,
      close / no-show / drop (walk-ins refuse NO_SHOW/CANCELLED — see note),
      void with reason dialog, unvoid, + log-a-swim dialog (one-PENDING rule enforced)
- [ ] Drift registry (`3`): global clients, `★ PENDING` marks the one live swim,
      detail pane with swim history, anonymize (PII nullified, gender/age kept)
- [ ] Harbor ledger (`4`): SESSION draft (net = closed − comp − expenses) and PRODUCT
      draft (add/remove lines), SEAL → catch snapshot, undo with reason (48h rule noted)
- [ ] Commission note: 5% pool split equally across clocked-in crew, outside remittance
- [ ] Crew (`5`): slot-ordered roster, relief sorts last, ONBOARDING row badgeless, role chips
- [ ] Signal buoy (`6`): notifications mailbox, click marks read, drain-all, NEW badges;
      harbor log (newest first, every mutation appends actor + clock + action)
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher,
      04:00 Asia/Manila boundary note; covered days lock edits for non-coordinators
- [ ] Diver badge (`7`): capability list, clock out, log out → back to the shore gate

## Theme notes

Scraps the Linear system on purpose: abyssal teal night (`#04222B`), deep-sea panels
(`#07333E`), lagoon cards (`#0C4350`), living-coral primary (`#FF6F61`) against
sea-glass teal (`#45C4B5`) — a strict two-ink duotone over warm foam ink (`#EFF8F6`).
Twin sine-wave dividers (coral crest over teal trough, drawn on Canvas) frame the
harbor shell top and bottom; pill-shaped tide chips mark swim status; rounded
sans headlines keep the coastal-clinic calm while mono tide labels carry the
harbor-log density. The footer always shows day/pending/unread.
Full-screen capable at 1280x800 minimum.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/duotone-sea/`
(package `com.companyb.companyapp.proto.duotonesea` — hyphens are
illegal in package names):

- `SeaTheme.desktop.kt` — palette, dark scheme, rounded + mono typography
- `SeaFake.desktop.kt` — fake domain + seeded `SeaRepo` state holder
- `SeaActions.desktop.kt` — repo mutations (each appends a harbor-log entry)
- `SeaChrome.desktop.kt` — wave dividers, tide chips, lagoon cards, rows, notes
- `SeaAuth.desktop.kt` — shore sign-in, ONBOARDING lock, cove select
- `SeaHome.desktop.kt` — clock-in lagoon, numbers, relief cards
- `SeaSessions.desktop.kt` — tide chart, filters, swim detail, void/log dialogs
- `SeaClients.desktop.kt` — drift registry, swim history, anonymize
- `SeaFinance.desktop.kt` — SESSION/PRODUCT drafts, sealed catches, undo, split note
- `SeaTeam.desktop.kt` — crew roster, presence, role chips
- `SeaMailbox.desktop.kt` — buoy signals + harbor log
- `SeaProfile.desktop.kt` — diver badge, capabilities, clock-out, log out
- `SeaApp.desktop.kt` — harbor shell: banner, kelp rail, footer

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on board open; no `try/catch`, no network imports.
