# Desktop prototype: coach-marks

Guided coach marks — a first-run overlay coach on top of a real night-ink dashboard.
Amber beacons spotlight each region in turn, a persistent banner tracks the current
step, and a Tour tab holds the progress checklist. Skipping is always safe: ending
early keeps the app fully usable and the checklist keeps your place. Fake data only;
no backend, no network calls.

## Run

```bash
COMPANYAPP_PROTO_COACH_MARKS=true ./gradlew :composeApp:run
```

Compile check (targeted):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 and can go full-screen. Without the env var the app
boots the real `App()` unchanged.

## Flow checklist

- [ ] Sign in as any profile on the login screen (fake users only)
- [ ] Onboarding profile shows the locked screen (empty capability bundle) and signs back out
- [ ] Tour auto-starts on first entry to the dashboard (guide dialog over step 1)
- [ ] Branch select: home branch opens fully, other branches open as view-only relief duty
- [ ] Home: clock in (advances the tour), relief request → simulate branch grant → edit access, accept/decline invites, withdraw own request
- [ ] Branch-day banner: switch Open / Past / Remitted days; note the 04:00 Asia/Manila boundary text
- [ ] Guide banner: Guide reopens the step dialog, Skip step moves on, End tour keeps progress
- [ ] Sessions: filter by status, open detail, Pending → Completed / No-show / Cancelled
- [ ] Walk-in session: No-show and Cancelled are disabled, rule note shown
- [ ] Void with reason → excluded from totals but visible; unvoid restores it
- [ ] New session dialog: blocked when the client already holds a Pending session
- [ ] Clients: global list with search, at-most-one-Pending note, anonymized record keeps gender + age only
- [ ] Finance Session flow: draft → submit (snapshot frozen) → undo within 48h with reason
- [ ] Finance Product flow: quantity steppers move the pool total; commission split divides it over checked-in staff; overrides are audited
- [ ] Team: people with roles, home branches and slots; role bundle cards
- [ ] Mailbox: unread dots, tap to mark read, relief messages tap through to the branch day
- [ ] Audit log: every action above prepends an entry, newest first — including tour start/end
- [ ] Tour tab: per-step Done / Skipped / Now / Todo states, resume, restart, don't-auto-start toggle
- [ ] Profile: clock out + sign out return to login; tour controls duplicated here

## Theme notes

- Night-ink canvas `#0F1420`, panels `#1A2233` / `#222C42`, hairline `#2E3A55`
- Coach system in signal amber `#FFC53D` (beacons, banner, spotlight borders) with warm bubble `#FFF6E0` dialogs
- Status chips green / amber / red / blue; system font stack; 14px card radius
- Spotlight pattern: when a step is current, its card gains an amber border plus a numbered beacon row

## Files

- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/coach-marks/CoachMarksFake.desktop.kt` — fake domain + seed store + tour state machine
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/coach-marks/CoachMarksTheme.desktop.kt` — tokens + primitives
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/coach-marks/CoachMarksTour.desktop.kt` — banner, guide dialog, checklist
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/coach-marks/CoachMarksApp.desktop.kt` — screens + flows
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/Main.kt` — additive env gate only

Package note: the folder keeps the spec'd `coach-marks` slug, but Kotlin
packages cannot contain hyphens, so the code lives in `proto.coachmarks`.
