# desktop-mocha-pop — Mocha Candy Evenings (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #804.
Branch `prototype/desktop-mocha-pop`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_MOCHA_POP=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_MOCHA_POP=true` it boots `MochaPopProtoApp()` at 1280x800.

## Flow checklist (all clickable, fake `MochaFakeRepo` only)

- [ ] Sign-in: pick any candy-maker on the counter → in (name field matches roster)
- [ ] ONBOARDING lockout: "Taste the ONBOARDING lock" → sealed-jar screen, zero capabilities, back out
- [ ] Branch select: Mocha Evenings HQ / Caramel Tour Van / Berry Outreach Post
- [ ] Home: clock in/out, tonight-by-the-truffles counts, relief Duty/Invite/Request composer + tray list
- [ ] Sessions: status filters incl. VOIDED chip, tray list, detail pane,
      complete / no-show / cancel (walk-ins ignore NO_SHOW/CANCELLED — see note),
      void with reason dialog, unvoid, + new-truffle dialog (one-PENDING-booking rule enforced)
- [ ] Clients: global sugar registry, `★ PENDING` marks the live truffle,
      detail pane with pending count, anonymize toggle (PII masked, flavor kept)
- [ ] Finance: SESSION draft (net from completed) and PRODUCT draft (add/drop lines),
      submit → snapshot swirl, undo with reason (48h rule noted)
- [ ] Commission note: 5% candy pool split equally across clocked-in crew, outside remittance
- [ ] Team: roster cards with role truffles, ONBOARDING row flagged locked
- [ ] Mail: counter-notes mailbox, tap/Read-all marks read, NEW truffles; unread count in rail + footer
- [ ] Audit: wrapper trail, newest first, every mutation stamps actor + shift + reason
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST switcher,
      04:00 Asia/Manila boundary note; covered days flag the fake non-coordinator edit lock
- [ ] Profile: badge, capability line per role, clock-out, log out → back to sign-in

## Theme notes

Scraps the Linear system on purpose: deep-espresso night (`#1E100B`), truffle panels
(`#2A1A12` / `#332016`), candy-pink primary (`#F28BB5`), caramel secondary (`#E8A33D`),
plum (`#B388EB`) and mint (`#7BD8A6`) for states, berry (`#E5484D`) for void/urgent.
A five-flavor drip strip runs under the header; rounded truffle chips mark status;
extra-bold candy headlines sit over warm muted copy. The footer always shows
day / pending / unread / branch. Full-screen capable at 1280x800 minimum.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/mocha-pop/`
(package `com.companyb.companyapp.proto.mochapop` — hyphens are
illegal in package names):

- `MochaTheme.desktop.kt` — palette, dark scheme, rounded shapes, bold typography
- `MochaFake.desktop.kt` — fake domain + seeded `MochaFakeRepo` state holder
- `MochaScreens.desktop.kt` — truffles, cards, notes, auth, home, sessions, clients,
  finance, team, mailbox, audit, profile
- `MochaApp.desktop.kt` — candy shell: drip strip, phase router, truffle rail, footer

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: `logInfo` on login, remittance submit, and repo reset; no network imports.
