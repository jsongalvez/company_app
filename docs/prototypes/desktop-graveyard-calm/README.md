# desktop-graveyard-calm — Graveyard Calm (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #849.
Branch `prototype/desktop-graveyard-calm`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_GRAVEYARD_CALM=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_GRAVEYARD_CALM=true` it boots `GraveyardCalmApp()` at 1280x800.
(No `DashboardPrototype` gate exists at the base commit — nothing to preserve; the
default path is untouched.)

## Flow checklist (all clickable, fake `CalmRepo` only)

- [ ] Sign-in: pick any of 4 fake night crew (Mara / Juno / Isa / Noa) → desk
- [ ] ONBOARDING lockout: sign in as Noa Aquino → locked screen, zero capabilities, step back out
- [ ] Branch select: Ermita Night Clinic (CLINIC) / Laguna Night Van (PROVINCIAL_TOUR) / Port Area Mission Post (MEDICAL_MISSION)
- [ ] Night desk (`1`): awake/unread/handover stats, clock in/out, relief duty accept (Teo), invite/request note card, handover log composer (pins notes, audits)
- [ ] Quiet queue (`2`): status filters incl. VOIDED, click-to-select, detail pane, complete / no-show / cancel (walk-ins refuse NO_SHOW/CANCELLED — see note), void with reason dialog, unvoid, + book softly dialog (walk-in checkbox)
- [ ] Sleeping registry (`3`): global client list, `∗ PENDING` star marks live tickets, face card, veil (anonymize: name/phone nullified, gender/age kept)
- [ ] Night till (`4`): SESSION draft (completed − comps − expenses) and PRODUCT draft (add/drop lines), SEAL → immutable snapshot, undo with reason (48h rule noted)
- [ ] Commission note: 5% SESSION pool split evenly across clocked-in crew, outside remittance
- [ ] Skeleton crew (`5`): slot-ordered roster (relief drifts last), ONBOARDING row locked, role bundle notes, call-in button
- [ ] Whisper mail (`6`): notices rail, click marks read, hush-all, unread badges; audit log (newest first, every mutation appends actor + clock + action)
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher, 04:00 Asia/Manila boundary note
- [ ] Night lamp (`7`): capability list, clock in/out, blow-out + logout → back to sign-in, shortcuts card
- [ ] Keys: `1–7` jump between desk stations

## Theme notes

Scraps the Linear system on purpose: deep night ink (`#0C1016`), lamplight amber
(`#D9A441`) primary, moon-blue quiet tags, sage green for sealed/completed states.
Night-desk metaphor: sessions are a quiet queue that never rings, notifications are
whispers, the handover log is what the dawn crew reads first. Low-contrast surfaces,
monospace micro-labels, one warm lamp in a dark room. Rail + desk + status bar on one
1280x800 surface, full-screen capable.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/graveyard-calm/`
(package `com.companyb.companyapp.proto.graveyardcalm` — no hyphens in package names):

- `GraveyardCalmTheme.desktop.kt` — palette, dark scheme, typography
- `GraveyardCalmFake.desktop.kt` — fake domain + seeded `CalmRepo` state holder + mutations (each appends an audit entry)
- `GraveyardCalmChrome.desktop.kt` — section headers, cards, tags, buttons, notes
- `GraveyardCalmAuth.desktop.kt` — sign-in, ONBOARDING lock, branch select
- `GraveyardCalmHome.desktop.kt` — stats, clock, relief duty, handover log
- `GraveyardCalmSessions.desktop.kt` — rail, filters, detail, void/create dialogs
- `GraveyardCalmClients.desktop.kt` — registry, face card, veil + team roster
- `GraveyardCalmFinance.desktop.kt` — SESSION/PRODUCT drafts, submit, snapshots, commission note
- `GraveyardCalmMail.desktop.kt` — notices + audit log + profile
- `GraveyardCalmApp.desktop.kt` — shell: banner, rail, status bar, key shortcuts

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on desk open; no network imports.
