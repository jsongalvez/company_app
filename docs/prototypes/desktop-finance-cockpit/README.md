# Desktop prototype: finance-cockpit

Coordinator-first finance cockpit — a brass-and-pine ledger built around the
P&L hero and side-by-side SESSION / PRODUCT remittance. Fake data only; no
backend, no network calls.

## Run

```bash
COMPANYAPP_PROTO_FINANCE_COCKPIT=true ./gradlew :composeApp:run
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
- [ ] P&L hero: SESSION + PRODUCT + grand total recompute as sessions void and quantities step
- [ ] Sessions: filter by status, open detail, Pending → Completed / No-show / Cancelled
- [ ] Walk-in session: No-show and Cancelled are disabled, rule note shown
- [ ] Void with reason → excluded from totals but visible; unvoid restores it
- [ ] New session dialog: blocked when the client already holds a Pending session
- [ ] Clients: global list with search, at-most-one-Pending note, anonymized record keeps gender + age only
- [ ] Finance SESSION column: draft total → submit (snapshot frozen)
- [ ] Finance PRODUCT column: quantity steppers move the pool total → submit (snapshot frozen)
- [ ] Snapshot vault: frozen totals with submitted-age + 48h undo countdown, undo with reason returns to Draft
- [ ] Commission split: pool divided over checked-in staff, manual inclusions/exclusions audited
- [ ] Team: people with roles, home branches and slots; role bundle cards
- [ ] Mailbox: unread dots, tap to mark read, relief messages tap through to the branch day
- [ ] Audit log: every action above prepends an entry, newest first
- [ ] Profile: clock out everywhere + log out return to login

## Theme notes

- Canvas ledger paper `#F7F2E6`, deep paper `#EFE6D2`, ink `#1B1A14`, muted `#6B6350`, hairline `#D9CDAE`
- Brass `#8A6D1B` / deep brass `#4A3608` / gold `#B8912C`; pine `#10382B` / deep pine `#0A261D`; emerald `#0E7C4B`
- P&L hero is an ink ticker with a 34sp grand total; SESSION ledger accented pine, PRODUCT accented brass
- Snapshot vault cards are deep pine with gold frozen totals, explicit 48h undo affordance
- Left rail is a deep-pine cockpit switch bank; cards are cream ledgers with a 6dp accent spine
- System font stack (no bundled font in this sketch); 28px page padding, 18px card padding, 14px radius, no shadows

## Files

- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/finance-cockpit/FinanceCockpitFake.desktop.kt` — fake domain + seed store
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/finance-cockpit/FinanceCockpitTheme.desktop.kt` — tokens + primitives
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/finance-cockpit/FinanceCockpitApp.desktop.kt` — shell, auth, home, sessions, clients
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/finance-cockpit/FinanceCockpitFlows.desktop.kt` — finance, team, mailbox, audit, profile
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/Main.kt` — additive env gate only

Package note: the folder keeps the spec'd `finance-cockpit` slug, but Kotlin
packages cannot contain hyphens, so the code lives in `proto.financecockpit`.
