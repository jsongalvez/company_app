# Desktop Text-Only Prototype (fake data, branch `prototype/desktop-text-only`)

Plaintext terminal: the whole desk as structured monospace text. No chrome, no
cards, no shadows — banner, ASCII rules, `[ bracketed commands ]`, and a numbered
menu. Part of map #755, ticket #853.

## Run

```bash
COMPANYAPP_PROTO_TEXT_ONLY=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `TextOnlyFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/text-only/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.
(Base master has no DashboardPrototype gate, so there was none to preserve.)

## Flow checklist

1. **Onboarding** — kiosk continues as Sam Rivera, ONBOARDING (locked, zero
   capabilities). Every gated screen prints the lock line while locked.
2. **Login** — fake roster of five (ONBOARDING → ACCOUNTANT). Sign in to unlock.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Switching moves the banner + branch-day.
4. **Home** — clock-in/out (locked roles cannot clock), relief invites
   (accept/decline), cover asks (raise/withdraw/grant/deny), Mon–Fri done/total
   bars, till and misses.
5. **Sessions** — status filter line, per-session manage (PENDING → COMPLETED /
   NO_SHOW / CANCELLED), void/unvoid with required reason, walk-in booking.
   Walk-in sessions refuse NO_SHOW and CANCELLED with the house-rule note.
6. **Clients** — global list with text filter, per-client PENDING count
   (at-most-one-PENDING rule note), anonymize/reveal keeps gender + age.
7. **Finance** — SESSION and PRODUCT drafts → submit (seals immutable snapshot) →
   undo-with-reason (48h window note). Commission split rule note
   (pooled per branch day, split over clocked-in staff at sold_at) plus pay
   envelopes with mark-paid/reopen.
8. **Team** — users by role with capability lines; MANAGER identity (Mia Santos)
   sees grant links, others see the locked note.
9. **Mailbox** — read/unread notices with `(*)` flags, mark read/unread, mark all.
10. **Audit Log** — every prototype mutation prepends who/action/target/reason.
11. **Profile** — current user, clock toggle, logout (returns to login).
12. **Branch-day line** — always visible under the banner: OPEN / PAST / REMITTED
    switcher with the 04:00 Asia/Manila boundary note.

## Theme notes

Plaintext terminal on near-black paper: phosphor-ink monospace, amber section heads
and flags, signal-red errors, `[ commands ]` as the only buttons, square panels,
one-pixel ASCII rules. Scraps the Linear design to test whether a zero-chrome
text UI is the fastest possible desk — and a screen-reader heaven where every
control is already a labelled sentence. Screenshots: run it on the target display
and capture there (no checked-in binaries in this branch).
