# desktop-limbo-room — Limbo Room (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #824.
Branch `prototype/desktop-limbo-room`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_LIMBO_ROOM=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_LIMBO_ROOM=true` it boots `LimboRoomApp()` at 1280x800.

## Flow checklist (all clickable, fake `LrStore` only)

- [ ] Sign-in: pick any of 5 regulars at the front desk, enter any secret → TAKE A NUMBER
- [ ] ONBOARDING lockout: sign in as K. Dela Pena → the limbo room: status card, 3-stamp next steps, who-to-ask scripts, ticket L-001, sign out
- [ ] Branch select: Makati Clinic (CLINIC) / Cebu Provincial Tour (PROVINCIAL_TOUR) / Tondo Medical Mission (MEDICAL_MISSION) as arch doors
- [ ] Home lobby: PENDING/DONE/UNREAD stats, clock in/out toggle, relief DUTY / REQUEST / INVITE — grant/deny requests, accept/decline invites, take duty
- [ ] Sessions: ALL/PENDING/COMPLETED/NO_SHOW/CANCELLED filters, voided toggle, tap-to-open rows, Complete / No-show / Cancel (walk-ins refuse NO_SHOW/CANCELLED with an audit note), Void with reason dialog, Unvoid, + BOOK dialog (walk-in gets W-ticket)
- [ ] Clients: global registry, `* PENDING` marks the live session client, Anonymize (PII nulled, gender/age kept)
- [ ] Finance: SESSION counter (gross − deductions = net) and PRODUCT counter, product shelf (+/− qty, shelve new), SUBMIT → sealed snapshot, Undo with reason (48h rule noted)
- [ ] Commission note: pooled per branch day, split equally across clocked-in crew, outside remittance
- [ ] Team: slot-ordered roster, ONBOARDING row shows LIMBO (sign in as A. Villanueva, MANAGER, to grant Practitioner)
- [ ] Mail: notifications mailbox, tap marks read, ALL/UNREAD filter, mark-all-read, JUMP→DAY tap-through to the lobby; audit log newest-first on the AUDIT door
- [ ] Branch-day banner: 2026-09-07 REMITTED / 2026-09-08 PAST / 2026-09-09 OPEN switcher, 04:00 Asia/Manila boundary note
- [ ] Profile: capability range, clock in/out, switch wing, logout → back to front desk

## Theme notes

Scraps the Linear system: the whole app is a night waiting hall. Deep indigo
walls (`#1A1433`), a neon NOW SERVING board with a glowing amber number
(`#FFB224`), stamped cream-paper tickets (`#FFF6E5`), velvet-rope red dividers
(`#C2374B`), and arch-topped doors for every room. The premise under test:
onboarding lockout deserves a designed room — status, next step, who to ask —
instead of a blank denial, and the same hall serves every other role once the
grant lands. Monospace tickets + soft sans names throughout; full-screen
capable from the 1280x800 minimum.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/limbo-room/`
(package `com.companyb.companyapp.proto.limboroom` — hyphens are illegal in
package names, so the hyphenated directory maps to the concatenated package):

- `LimboRoomTheme.desktop.kt` — hall tokens, stamps, paper/hall cards, buttons, stats
- `LimboRoomFake.desktop.kt` — fake domain + seeded `LrStore`
- `LimboRoomChrome.desktop.kt` — now-serving board, day banner, door rail, ticket stub
- `LimboRoomApp.desktop.kt` — shell: front desk / limbo room / wing select + eight hall rooms

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on prototype start; no network imports.
