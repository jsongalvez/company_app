# desktop-command-deck — Command Deck (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #828.
Branch `prototype/desktop-command-deck`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_COMMAND_DECK=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_COMMAND_DECK=true` it boots `CommandDeckApp()` at 1280x800.

## Flow checklist (all clickable, fake `CdStore` only)

- [ ] Sign-in: pick any of 5 crew at the gangway, enter any cipher → BOARD
- [ ] ONBOARDING hold: sign in as K. Dela Pena → the observation deck: status panel, 3-burn next steps, hail scripts, signal H-001, disembark
- [ ] Vessel select: Makati Clinic (CLINIC) / Cebu Provincial Tour (PROVINCIAL_TOUR) / Tondo Medical Mission (MEDICAL_MISSION) with day-status tags
- [ ] Helm: PENDING/COMPLETED/UNREAD/VOID glow clusters, clock in/out toggle, console dampen switch, relief DUTY / REQUEST / INVITE — grant/deny requests, accept/decline invites, take duty
- [ ] Sessions: ALL/PENDING/COMPLETED/NO_SHOW/CANCELLED filters, voided toggle, tap-to-open rows, Complete / No-show / Cancel (walk-ins refuse NO_SHOW/CANCELLED with an audit note), Void with reason dialog, Unvoid, + LOG dialog (walk-in gets W-signal)
- [ ] Clients: global registry, `* PENDING` marks the mid-visit client, Anonymize (PII nulled, gender/age kept)
- [ ] Finance: SESSION reactor (gross − deductions = net) and PRODUCT reactor, cargo shelf (+/− qty, stow new), SUBMIT → sealed snapshot, Undo with reason (48h rule noted)
- [ ] Commission note: pooled per branch day, split equally across clocked-in crew, outside remittance
- [ ] Team: slot-ordered watch bill, ONBOARDING row shows HOLD (sign in as A. Villanueva, MANAGER, to grant Practitioner)
- [ ] Mail: signals mailbox, tap marks read, ALL/UNREAD filter, mark-all-read, JUMP→DAY tap-through to the helm; audit log newest-first on the AUDIT station
- [ ] Mission-clock banner: 2026-09-07 REMITTED / 2026-09-08 PAST / 2026-09-09 OPEN switcher, 04:00 Asia/Manila boundary note, restrained alert board (klaxon only for grant waits + open drafts off the live clock)
- [ ] Profile: capability range, clock in/out, switch vessel, logout → back to gangway

## Theme notes

Scraps the Linear system: the whole app is a starship bridge on night watch.
Void-black hull (`#060810`), steel deck panels (`#0E1424`), cyan console glow
(`#5AD6FF`), phosphor-green confirmations (`#5CFF9D`), amber cautions
(`#FFB224`). Klaxon red (`#FF3B5C`) is rationed by policy — it fires only for
the two genuine needs-a-human conditions (ONBOARDING crew awaiting grant,
DRAFT counters off the live clock); everything routine glows instead of
screaming. Sharp 4–8dp console corners (no arches, no paper), monospace
readouts + soft sans names throughout; full-screen capable from the 1280x800
minimum.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/command-deck/`
(package `com.companyb.companyapp.proto.commanddeck` — hyphens are illegal in
package names, so the hyphenated directory maps to the concatenated package):

- `CommandDeckTheme.desktop.kt` — void/deck tokens, glow tags, panels, buttons, clusters
- `CommandDeckFake.desktop.kt` — fake domain + seeded `CdStore` + restrained `klaxons()`
- `CommandDeckChrome.desktop.kt` — command strip, mission-clock banner, alert board, station rail, signal ticket
- `CommandDeckApp.desktop.kt` — shell: gangway / observation deck / vessel select + eight deck stations

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on prototype start; no network imports.
