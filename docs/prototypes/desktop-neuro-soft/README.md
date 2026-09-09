# Desktop prototype: neuro-soft

Soft neumorphic clinic calm. Pastel extruded panels on a misty gray-lavender
canvas, dual-tone soft shadows, tactile pill toggles, pressed-in inset notes.
Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_NEURO_SOFT=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Press to enter` goes to branch select; tactile stay-signed-in toggle is flippable.
- [ ] ONBOARDING: `Peek at the ONBOARDING cushion` shows the locked mat (empty capability bundle note).
- [ ] Branch select: pick Cloud Clinic (CLINIC), Drift Tour (PROVINCIAL_TOUR), or Halo Mission (MEDICAL_MISSION).
- [ ] Home: tactile clock-in toggle + Clock in/out buttons at the home branch; ask for relief / invite help; relief cushions list requests, invites and duty.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; details expand; void with reason; unvoid; complete; add fresh walk-in.
- [ ] Clients: global cushion book; at-most-one-PENDING note; anonymize/reveal toggle.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION press and PRODUCT layer drafts; submit seals a snapshot; undo within 48h with reason; commission-split pool note.
- [ ] Team: users and roles incl. ONBOARDING locked row; role glance inset.
- [ ] Mailbox: unread count chip, press-to-read, mark-all-read, relief items name branch and day.
- [ ] Audit: void / unvoid / complete / submit / undo / walk-in / clock events append entries with reasons.
- [ ] Profile: Branch Day press control, clock out, reset demo, log out.

## Theme notes

- Palette: mist `#E6E9F0` canvas, raised `#EBEEF4` extrusion, pressed `#DDE2EB` insets, line `#D1D8E4`, ink `#4A5568`, muted `#8A94A8`; accents periwinkle `#6E8FB2`, mint `#7FB69E`, peach `#D9A58B`, lilac `#9A93C9`, butter `#C9A86A`, rose `#B87E8F`.
- Depth: raised cards carry 6–10dp soft shadows with a light hairline highlight; inset notes sit pressed with a thin line border; chips float at 4dp.
- Shape: calm 16–32dp radii everywhere (chips 16dp, toggles 20dp, panels 24dp) — nothing sharp, nothing corporate.
- Tactile: pill toggles for clock-in, stay-signed-in, and calm-hum; pressed state sinks into mint, flat state rests in pressed gray.
- Copy: cushion language ("Press softly", "Flat and tidy", "Soft hour", "Pressed and sealed").
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/neuro-soft/*.desktop.kt` (package `...proto.neurosoft`), fake `NeuroSoftFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
