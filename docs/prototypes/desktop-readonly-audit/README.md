# desktop-readonly-audit — Glass-Box Audit (prototype, fake data)

Isolated full-screen desktop dashboard prototype for Wayfinder map #755, ticket #820.
Branch `prototype/desktop-readonly-audit`. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_READONLY_AUDIT=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Without the env var the app boots the real `App()` unchanged at 1024x768.
With `COMPANYAPP_PROTO_READONLY_AUDIT=true` it boots `ReadonlyAuditProtoApp()` at 1280x800.

## Flow checklist (all clickable, fake `ReadonlyAuditRepo` only)

- [ ] Sign-in: pinned to the audit seat (R. Villanueva, Accountant) — other roster rows show
      why sign-in is disabled; K. Dela Pena opens the ONBOARDING lockout
- [ ] ONBOARDING lockout: zero-capability screen, empty bundle note, back to sign-in
- [ ] Branch select: Makati Clinic (CLINIC) / Cebu Provincial Tour (PROVINCIAL_TOUR) /
      Tondo Medical Mission (MEDICAL_MISSION)
- [ ] Home: clock-in shown but locked (audit seats are never rostered), today stats,
      relief board (DUTY / REQUEST / INVITE) with per-row lock reasons
- [ ] Sessions: status filters, tap-to-select detail, Complete / No-show / Cancel all locked
      with reasons; walk-in rows refuse NO_SHOW/CANCELLED with the never-booked note;
      Void and Unvoid open read-only previews (reason draftable, filing disabled);
      voided s-104 shows its filed reason; Book session locked
- [ ] Clients: global registry, `* PENDING` marks live sessions, Anonymized #A-104 shows the
      PII-nullified view (gender/age kept); Anonymize locked (MANAGE_CLIENTS)
- [ ] Finance: SESSION + PRODUCT drafts with locked Submit; submitted SESSION snapshot
      (6h old, inside the 48h Undo window — Undo locked for the seat); submitted PRODUCT
      snapshot (72h old — window closed, permanent); commission split note (pooled per
      Branch day, equal split, manual override, outside remittance)
- [ ] Branch-day banner: 2026-09-08 REMITTED / 2026-09-09 PAST / 2026-09-10 OPEN switcher,
      04:00 Asia/Manila boundary note, per-day verdict in the right rail
- [ ] Team: slot-ordered roster, relief sorts last, ONBOARDING row locked; capability bundle
      detail; role grants locked (MANAGE_USERS)
- [ ] Mailbox: tap marks read (the one action this seat owns), mark-all-read, relief events
      tap through to their Branch day; read rows kept forever
- [ ] Audit Log: immutable INSERT/UPDATE trail with caller + why-lines
- [ ] Profile: capability list, clock-out/deactivate locked, Switch Branch + Logout work

## Theme notes

Scraps the Linear system: deep-ink ledger gradient canvas (`#0A1322` → `#07101D`),
frosted-glass panels (white at 5–9% alpha, hairline borders, 16dp radius), accountant-green
(`#3AD08A`) primaries, amber (`#F2B544`) lock reasons, monospace numerals for money.
The premise under test: a glass box — everything visible, every action disabled with its
reason on the spot, plus a right-hand verdict rail naming the seat, the missing capability,
and the day state for the current screen.

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/readonly-audit/`
(package `com.companyb.companyapp.proto.readonlyaudit` — hyphens are illegal in
package names, so the hyphenated directory maps to the concatenated package):

- `ReadonlyAuditTheme.desktop.kt` — glass tokens, `AuditGlassTheme`, `GlassPanel`,
  `LockedAction`, `LockReason`, chips, pills, stat strip
- `ReadonlyAuditFake.desktop.kt` — fake domain + seeded `ReadonlyAuditRepo`
- `ReadonlyAuditApp.desktop.kt` — shell: login / ONBOARDING lock / branch select /
  nav shell + branch-day banner + verdict rail
- `ReadonlyAuditScreens.desktop.kt` — Home, Sessions, Clients, Finance, Team,
  Mailbox, Audit Log, Profile sections + void/unvoid preview dialogs

`composeApp/.../Main.kt` gains an additive env gate only; default path untouched.
Logging: single `logInfo` on prototype start; no network imports.
