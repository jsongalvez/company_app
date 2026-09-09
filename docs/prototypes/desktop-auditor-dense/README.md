# Desktop prototype: auditor-dense

Auditor dense tables — a forensic-ledger sketch for reviewers who read numbers
first. Paper canvas, hairline grids, zebra rows, monospace tabular numerals
end-aligned, filters pinned front and center on every table, and the audit log
as a first-class tab with a fake hash chain. Fake data only; no backend, no
network calls.

## Run

```bash
COMPANYAPP_PROTO_AUDITOR_DENSE=true ./gradlew :composeApp:run
```

Compile check (targeted):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 and can go full-screen. Without the env var the app
boots the real `App()` unchanged.

## Flow checklist

- [ ] Sign in as any row on the user register (fake users only)
- [ ] Onboarding identity shows the locked screen (empty capability bundle) and signs back out
- [ ] Branch select: home branch opens fully, other branches open as view-only relief duty
- [ ] Register: clock in, outgoing request → simulate branch grant → edit access, accept/decline invites, withdraw own request, revoke accepted invite as branch
- [ ] Branch-day banner: switch 09-08 / 09-09 / 09-10 (Remitted / Past / Open); note the 04:00 Asia/Manila boundary text
- [ ] Sessions: status chips + search filter the grid, folio panel posts Pending → Completed / No-show / Cancelled
- [ ] Walk-in folio: No-show and Cancel are disabled, rule note shown
- [ ] Void with reason → ∅ wash row, excluded from the filtered Σ and draft gross; unvoid restores it
- [ ] New session dialog: blocked when the client already holds a PENDING row
- [ ] Clients: global register with search, at-most-one-PENDING column, anonymized dossier keeps gender + age only
- [ ] Finance Session flow: live draft gross → submit (snapshot frozen) → undo within 48h with reason
- [ ] Finance Product flow: quantity steppers move the pool Σ; commission split divides it over the clocked-in roster; include/exclude overrides are audited
- [ ] Team: users with roles, home branches and slots; role bundle cards
- [ ] Mailbox: unread dots, click to mark read, relief messages tap through to the branch day
- [ ] Audit log: every action above prepends a hash-chained entry, newest first; filter by action/table/actor/record
- [ ] Profile: clock out + log out return to the sign-in register

## Theme notes

- Paper `#F6F3EA`, deep `#EFEAD9`, card `#FCFBF6`, zebra `#F1ECDD`, header `#E7E0C9`, ink `#1C1B17`
- Credit green `#1E6B3A`, debit oxblood `#9B1C1C`, pending amber `#8A5A00`, info blue `#1D4E89`, void wash `#F3E4E2`
- Monospace numerals everywhere money or ids appear, end-aligned; dense ~28px rows; 3px chips; 4px cards; no shadows, hairlines only

## Files

- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/auditor-dense/AuditorDenseFake.desktop.kt` — fake domain + seed store + ledger actions
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/auditor-dense/AuditorDenseTheme.desktop.kt` — tokens + table primitives
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/auditor-dense/AuditorDenseApp.desktop.kt` — shell, sign-in, register, team, mailbox, profile
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/auditor-dense/AuditorDenseTables.desktop.kt` — sessions, clients, finance, audit log
- `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/Main.kt` — additive env gate only

Package note: the folder keeps the spec'd `auditor-dense` slug, but Kotlin
packages cannot contain hyphens, so the code lives in `proto.auditordense`.
