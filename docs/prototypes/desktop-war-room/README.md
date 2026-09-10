# Desktop War-Room Prototype (fake data, branch `prototype/desktop-war-room`)

Incident war room that shows only what bleeds. Part of map #755, ticket #836.

## Run

```bash
COMPANYAPP_PROTO_WAR_ROOM=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `WarFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/war-room/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Brief (welcome)** — locked ONBOARDING hold (zero capabilities) explained at the
   door; "Clear onboarding hold" simulates the MANAGE_USERS grant, then take a tag.
   Triage map jumps straight to voids, relief gaps, and undo windows.
2. **Access (sign in)** — five fake tags (ONBOARDING → ACCOUNTANT). ONBOARDING is
   held until the Brief clears it; everyone else tags in clocked in.
3. **Sectors (branch select)** — QC Central (CLINIC, Sector 1), Laguna Tour Stop 3
   (PROVINCIAL_TOUR, Sector 2), Tondo Medical Mission (MEDICAL_MISSION, Sector 3)
   with live LIVE/VOID counts. Tap to switch.
4. **Floor (home)** — greeting, clock in/out, relief cover at another sector
   (view-only until edit granted, ends 04:00 Manila), relief invites (Yes/No),
   relief requests (Grant/Deny), shortcuts to triage and the vault.
5. **Triage (sessions)** — kill-list rows (time, service, practitioner,
   LIVE RISK/CLEARED/NO SHOW/KILLED flags, VARIANCE banner with flag/clear):
   status filter chips, per-row status jumps, per-row void (reason required) /
   unvoid. Walk-in rows refuse NO_SHOW and CANCELLED with the rule note.
   New walk-in form boards a PENDING row with variance watch on.
6. **Dossier (clients)** — global manifest, per-client PENDING count
   (at-most-one-PENDING rule note, OVERBOOK chip when violated), mask/reveal names,
   anonymize (keeps gender + age).
7. **Vault (finance)** — SESSION and PRODUCT drawers: draft -/+500 → seal
   (freezes immutable snapshot RS-*) → undo-with-reason (48h window note).
   Commission split rule card (pooled per Branch Day, even split over clocked-in
   Practitioners at sold_at, relief paid from this vault).
8. **Roster (team)** — duty roster in branch-slot order with role chips, ONBOARDING
   locked row, YOU marker, capability note.
9. **Signals (notifications)** — read/unread incident board, hush per item or hush all.
10. **Ledger (audit)** — every prototype mutation prepends who/action/target/reason.
11. **My Tag (profile)** — current tag, clock in/out, drop tag (returns to Access).
12. **Incident strip** — full-width OPEN (green) / PAST (amber) / REMITTED (sky)
    switcher with the 04:00 Asia/Manila boundary note, always visible.

## Theme notes

War-room bunker: near-black `#0B0B0E` canvas, panel `#16151C`, siren red `#FF3B5C`
for voids, amber `#FFB020` for variances, violet `#B388FF` for relief gaps, sky
`#7CC4FF` for undo windows. Masthead counts VOIDS + GAPS, exception rail carries
live badges, triage rows lead with the kill list — this variant scraps the Linear
design to test whether an exception-only room makes voids vs variances vs relief
gaps vs undo windows triageable at a glance. Screenshots: run it on the target
display and capture there (no checked-in binaries in this branch).
