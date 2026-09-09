# Desktop prototype: clinical-light

Clinical light minimal — an airy, calm, practitioner-first dashboard sketch.
White canvas, soft mint surfaces, one teal primary action per region, generous
whitespace. Fake data only; no backend, no network calls.

## Run

```bash
COMPANYAPP_PROTO_CLINICAL_LIGHT=true ./gradlew :composeApp:run
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
- [ ] Branch select: home branch opens fully, other branches open as view-only relief duty
- [ ] Home: clock in, relief request → simulate branch grant → edit access, accept/decline invites, withdraw own request
- [ ] Branch-day banner: switch Open / Past / Remitted days; note the 04:00 Asia/Manila boundary text
- [ ] Sessions: filter by status, open detail, Pending → Completed / No-show / Cancelled
- [ ] Walk-in session: No-show and Cancelled are disabled, rule note shown
- [ ] Void with reason → excluded from totals but visible; unvoid restores it
- [ ] New session dialog: blocked when the client already holds a Pending session
- [ ] Clients: global list with search, at-most-one-Pending note, anonymized record keeps gender + age only
- [ ] Finance Session flow: draft → submit (snapshot frozen) → undo within 48h with reason
- [ ] Finance Product flow: quantity steppers move the pool total; commission split divides it over checked-in staff; overrides are audited
- [ ] Team: people with roles, home branches and slots; role bundle cards
- [ ] Mailbox: unread dots, tap to mark read, relief messages tap through to the branch day
- [ ] Audit log: every action above prepends an entry, newest first
- [ ] Profile: clock out + sign out return to login

## Theme notes

- Canvas `#FFFFFF`, surface `#F6FAF9`, ink `#1B2E2C`, muted `#5D7471`, hairline `#E1ECEA`
- Primary calm teal `#0E7C6B` on soft mint `#E2F2ED`; status chips green / amber / red / blue / grey
- System font stack (Inter intent — no bundled font in this sketch); 32px page padding, 22px card padding, 14px radius, no shadows
- One primary action per region; secondary moves are quiet text buttons

## Files

- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/clinical-light/ClinicalLightFake.desktop.kt` — fake domain + seed store
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/clinical-light/ClinicalLightTheme.desktop.kt` — tokens + primitives
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/clinical-light/ClinicalLightApp.desktop.kt` — screens + flows
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/Main.kt` — additive env gate only

Package note: the folder keeps the spec'd `clinical-light` slug, but Kotlin
packages cannot contain hyphens, so the code lives in `proto.clinicallight`.
