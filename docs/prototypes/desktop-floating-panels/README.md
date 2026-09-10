# Desktop prototype: floating-panels

Floating panels. Every tool drifts as its own overlapping panel over a dimmed
canvas: a dark plum-slate backdrop, warm paper cards at staggered offsets with
drag-grip headers, ghost panels peeking behind the focused sheet, and a Zoom
toggle per panel that rings it violet and lifts it forward. Zero Linear
styling. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_FLOATING_PANELS=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Sign in ▸` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING gate` shows the locked notice (empty capability bundle).
- [ ] Branch select: pick Sunrise Clinic (OPEN), Harbor Tour (PAST), or Lingap Mission (REMITTED) from staggered panels.
- [ ] Home dock: clock in/out; relief invites accept/decline, ask-for-relief form; day in figures.
- [ ] Sessions dock: Views filter ALL / PEND / DONE / N-SHOW / CXLD; walk-in rule note (walk-ins never take NO_SHOW/CANCELLED — buttons disabled); unfold tools per card; complete / no-show / cancel; void / unvoid with reason.
- [ ] Clients dock: global list; at-most-one-PENDING note; anonymized codes by default, veil toggle in the dock lifts full names.
- [ ] Branch-day chip: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; move the day from Profile.
- [ ] Finance dock: SESSION (net income) and PRODUCT (price × quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note (House 60 / Practitioner 40).
- [ ] Team dock: floating teammate panels incl. ONBOARDING locked row; capability glance note.
- [ ] Mail dock: read/unread marks, tap toggles, mark-all-read in the dock.
- [ ] Audit dock: void / unvoid / submit / undo / clock / relief entries with reasons.
- [ ] Profile dock: clock in/out, move the day, reset demo, log out, switch branch.

## Theme notes

- Palette: canvas deep `#0F0F16` / `#16161E`, dock `#2A2B3A`, paper `#FCFBF7` / dim `#F1EEE5`, ink `#23222B`; focus violet `#7C5CBF` / deep `#5B3FA3`; status green / gold / red.
- Shape: 20dp stage rounding, 16dp panels, 12dp controls, grip-pill drag handles, ghost panels offset behind the focused sheet, staggered session/client offsets.
- Type: black panel titles, tracked-uppercase kickers, bold dock chips, small soft notes.
- Copy: drifting voice ("Choose your canvas", "Off the canvas", "Nothing floating", "Drift ▸ Pick a branch").
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/floating-panels/*.desktop.kt` (package `...proto.floatingpanels`), fake `FloatFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
