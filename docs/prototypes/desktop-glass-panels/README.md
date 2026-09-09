# Desktop prototype: glass-panels

Frosted translucent layers floating over a deep-sea gradient. Every pane is a
sheet of glass — soft white translucency, glowing orbs behind, gradient CTA
buttons, pill nav rail. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_GLASS_PANELS=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any input works, `Drift in →` goes to branch select.
- [ ] ONBOARDING: `View onboarding pane` shows the locked pane (empty capability bundle note).
- [ ] Branch select: pick Lumen Flagship, Drift Provincial Tour, or Halo Medical Mission.
- [ ] Home: clock in/out at the home branch; invite help; broadcast a relief request; relief board with grant/deny.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule noted (no NO_SHOW/CANCELLED on walk-ins); open file; void with reason; unvoid; complete; add walk-in.
- [ ] Clients: global file; at-most-one-PENDING note; anonymize/reveal toggle.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION + PRODUCT drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row.
- [ ] Mailbox: unread count, read, mark-all-read.
- [ ] Audit: clock / void / unvoid / complete / submit / undo / walk-in / relief events append entries with reasons.
- [ ] Profile: Branch Day remote control, clock out, log out.

## Theme notes

- Palette: abyss `#0D0B26` base with indigo `#232055` → teal depth gradient; violet `#7C6CF8`, aqua `#67E8F9`, mint `#6EE7B7`, peach `#FDBA74`, rose `#FDA4AF` accents; frost text `#F4F2FF`.
- Glass: white 8% fills, white 14% emphasis, white 22% strokes, 14-24dp radii, 50dp pills. Depth comes from layered translucency over four radial-glow orbs, not shadows.
- Type: extra-light oversized display lines (“Step through / the glass.”), bold 11sp tracked-out eyebrow labels, sentence-case human copy.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/glass-panels/*.desktop.kt` (package `...proto.glasspanels`), fake `GlassPanelsFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
