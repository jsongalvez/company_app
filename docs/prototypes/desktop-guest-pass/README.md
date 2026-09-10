# Desktop prototype: guest-pass

Relief guest pass — an outsider-lens dashboard sketch styled as a visitor hall
pass: kraft-paper stubs, rubber stamps, perforation dividers. View-only
defaults everywhere, grant moments celebrated, expiry always visible.
Fake data only; no backend, no network calls.

## Run

```bash
COMPANYAPP_PROTO_GUEST_PASS=true ./gradlew :composeApp:run
```

Compile check (targeted):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 and can go full-screen. Without the env var the app
boots the real `App()` unchanged.

## Flow checklist

- [ ] Pick any badge on the visitor desk to enter (fake users only)
- [ ] ONBOARDING badge shows the locked screen (empty capability bundle) and signs out
- [ ] Counters: home branch opens fully, relief branches open view-only
- [ ] Duty pass: clock in at home, clock in as relief (view-only), simulate grant → green celebration + expiry strip flips to EDIT GRANT
- [ ] Fake clock stepper moves expiry math; all grants die at the 04:00 Asia/Manila boundary
- [ ] Relief requests broadcast; invites accept (writes the day grant) or decline; requests withdraw
- [ ] Branch-day banner: switch OPEN / PAST / REMITTED; boundary note always shown
- [ ] Sessions: filter by status, tap a stub for detail, PENDING → COMPLETED / NO_SHOW / CANCELLED
- [ ] Walk-in session: NO_SHOW and CANCELLED disabled, rule note shown
- [ ] Void with reason → stamped VOIDED, excluded from totals; unvoid restores it
- [ ] New session dialog: blocked when the client already holds a PENDING session
- [ ] Clients: global registry with search; anonymized record keeps gender + age only
- [ ] Finance SESSION flow: draft → submit (snapshot frozen) → undo within 48h with reason
- [ ] Finance PRODUCT flow: quantity steppers move the pool total; commission split divides it over checked-in staff
- [ ] Team: people with roles, home branches and slots; role bundle cards
- [ ] Mailbox: unread stamps, tap to mark read, relief letters jump to the branch
- [ ] Audit log: every stamp above prepends an entry, newest first
- [ ] Profile: capabilities, clock out + log out return to the visitor desk

## Theme notes

- Kraft paper `#F3EAD3`, stub `#FFFDF4`, ink `#2B2118`, muted `#7A6A54`, perforation `#C0AE87`
- Rubber stamps: green granted, oxblood locked/voided, amber view-only/pending, plum branch/role, slate neutral
- Monospace badge IDs and snapshot refs; 10px stub radius; amber view-only ribbon vs green grant celebration
- Ink-black left rail with unread count badge; green/amber full-width expiry strip under the branch-day banner

## Files

- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/guest-pass/GuestPassFake.desktop.kt` — fake domain + seed store
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/guest-pass/GuestPassTheme.desktop.kt` — tokens + primitives
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/guest-pass/GuestPassScreens.desktop.kt` — screens + flows
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/guest-pass/GuestPassApp.desktop.kt` — nav rail, branch-day banner, expiry strip
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/Main.kt` — additive env gate only

Package note: the folder keeps the spec'd `guest-pass` slug, but Kotlin
packages cannot contain hyphens, so the code lives in `proto.guestpass`.
