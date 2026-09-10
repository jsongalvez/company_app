# Desktop Search-Only Prototype (fake data, branch `prototype/desktop-search-only`)

Command search only: no nav chrome at all — one glowing box runs the branch,
recent objects sit under it, every hit opens in a stage below. Part of map #755,
ticket #855.

## Run

```bash
COMPANYAPP_PROTO_SEARCH_ONLY=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `SearchOnlyFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/search-only/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.
(Base master has no DashboardPrototype gate, so there was none to preserve.)

## Flow checklist

1. **Login** — window opens signed out ("Who is on shift?"). Type `login` or a name
   (`ann`, `joy`, `mia`…) and pick a login command, then Sign in.
2. **Onboarding** — sign in as Sam Rivera (ONBOARDING): lands on the lock stage,
   zero capabilities, with a "Grant Practitioner" action (simulates the MANAGE_USERS
   grant). Type `grant` / `unlock` anywhere to jump back to it while anyone is locked.
3. **Command box** — empty box browses the whole branch (commands + sessions +
   clients + people + branches + money). Typing filters both verbs and objects;
   ⏎ (or tap) runs the top hit. Verb chips under the bar fill the box so the
   language is discoverable.
4. **Recents** — every opened target lands in the RECENT strip (max 8); tap to reopen.
5. **Clock & relief** — `clock in` / `clock out`; the home stage holds relief invites
   (accept/decline) and cover asks (grant/deny/withdraw mine) plus a relief ask button.
6. **Branches** — `switch branch` lists QC Central (Clinic), Laguna Tour Stop 3
   (Provincial tour), Tondo Medical Mission (Medical mission); tap to re-scope.
7. **Sessions** — search `s-101`, a client name, or a status. Detail offers Complete /
   No-show / Cancel, void + unvoid with reason, and a jump to the client. Walk-in
   sessions (⚑) refuse NO_SHOW and CANCELLED with the house-rule note. `void`
   surfaces pending sessions as void/unvoid commands.
8. **Walk-in** — `book walk-in`: name a cash client and book; new names create a
   global client row on the spot.
9. **Clients** — global records with gender + age + pending count, the
   at-most-one-PENDING note, anonymize/reveal, book-session (blocked while a PENDING
   exists), and a per-client session list.
10. **Branch day** — slim strip under the chrome shows OPEN / PAST / REMITTED plus
    the 04:00 Asia/Manila boundary; `day` / `advance` jumps to branches where
    Advance cycles the state.
11. **Finance** — `remit` opens SESSION + PRODUCT drafts: new draft → submit (seals an
    immutable snapshot id) → Undo with reason (48h note). Commission split card notes
    the pooled-per-branch-day equal-share rule with per-staff mark-paid/reopen.
12. **Notifications** — `notifications`: read/unread mailbox, per-notice toggle,
    mark-all-read.
13. **Audit log** — `audit`: newest-first who/action/target/reason; the box filters it.
14. **Profile** — `profile`: identity, clock toggle, logout back to the login box.

## Theme notes

Midnight spotlight: near-black indigo abyss, violet panels, one lime-ringed command
bar, monospace everything, pills for status. Scraps the Linear design to test whether
a branch with zero navigation — search box plus recents only — stays operable at full
flow depth. Screenshots: run it on the target display and capture there (no checked-in
binaries in this branch).
