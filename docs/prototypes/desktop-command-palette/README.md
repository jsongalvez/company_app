# Desktop prototype: command-palette

Compass — a calm dark workspace built around a Cmd-K command palette.
Spacious sidebar with grouped navigation and auto-kept recent objects,
a floating fuzzy palette with match highlighting, and keyboard-first
movement throughout. Fake data only; no backend, no network calls.

## Run

```bash
COMPANYAPP_PROTO_COMMAND_PALETTE=true ./gradlew :composeApp:run
```

Compile check (targeted):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 and can go full-screen. Without the env var the app
boots the real `App()` unchanged.

## Keyboard map

- `Ctrl/⌘+K` — open or dismiss the command palette
- `↑` / `↓` + `Enter` — move and open in the palette, `Esc` dismisses
- `Alt+1…8` — jump to Home, Sessions, Clients, Finance, Team, Mailbox, Audit, Profile
- Typing digits inside text fields is safe: navigation needs `Alt`

## Flow checklist

- [ ] Sign in as any profile on the login screen (fake users only)
- [ ] Onboarding profile shows the locked screen (empty capability bundle) and signs back out
- [ ] Branch select: home branch opens fully, other branches open as view-only relief duty
- [ ] Home: clock in, relief request → simulate branch grant → edit access, grant/decline/withdraw requests, accept/decline invites, revoke duties
- [ ] Palette: blank shows recents first, typing fuzzes screens + sessions + clients + branches with highlighted matches
- [ ] Sidebar recent objects jump back to sessions, clients, and branches
- [ ] Branch-day banner: switch day chips across Open / Past / Remitted days; note the 04:00 Asia/Manila boundary text
- [ ] Sessions: filter by status, open detail, Pending → Completed / No-show / Cancelled
- [ ] Walk-in session: No-show and Cancelled are disabled, rule note shown
- [ ] Void with reason → excluded from totals but visible; unvoid restores it
- [ ] New session dialog: blocked when the client already holds a Pending session
- [ ] Clients: global list with search, at-most-one-PENDING note, anonymized record keeps gender + age only
- [ ] Finance Session flow: draft → submit (snapshot frozen) → undo within 48h with reason
- [ ] Finance Product flow: quantity steppers move the pool total; commission split divides it over checked-in staff; overrides are audited
- [ ] Team: people with roles, home branches and slots; role bundle cards
- [ ] Mailbox: unread dots, tap to mark read, relief messages tap through to the branch day
- [ ] Audit log: every action above prepends an entry, newest first, filterable
- [ ] Profile: clock out + sign out return to login

## Theme notes

- Calm dark canvas `#161C27`, sidebar `#10151E`, panels `#1D2431`, hairline `#2C3547`
- Ink `#E9EEF4`, muted `#93A0B4`, faint `#647082`; soft sage accent `#A9CBBE`
- Status tints: gold pending, green completed/open, amber past/no-show, red cancelled, blue remitted, violet clients/branches
- 14px cards, pill chips, generous whitespace; `Ctrl/⌘K` kbd hints wherever jumping matters

## Files

- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/command-palette/CommandPaletteFake.desktop.kt` — fake domain + seed store + fuzzy scorer
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/command-palette/CommandPaletteTheme.desktop.kt` — tokens + primitives
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/command-palette/CommandPaletteApp.desktop.kt` — login, branch select, shell, palette, home
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/command-palette/CommandPaletteScreens.desktop.kt` — sessions, clients, finance, team, mailbox, audit, profile
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/Main.kt` — additive env gate only

Package note: the folder keeps the spec'd `command-palette` slug, but Kotlin
packages cannot contain hyphens, so the code lives in `proto.commandpalette`.
