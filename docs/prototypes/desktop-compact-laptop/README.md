# Desktop Compact-Laptop Prototype (fake data, branch `prototype/desktop-compact-laptop`)

Dense 1280px-optimized workbench for single-branch small teams: graphite top bar, day
banner, 148dp nav rail, scrolling work pane, persistent 252dp context rail. Part of map
#755, ticket #778.

## Run

```bash
COMPANYAPP_PROTO_COMPACT_LAPTOP=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `CompactLaptopFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/compact-laptop/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding** — ONBOARDING account has the empty role bundle (locked). "Preview locked"
   signs in as R. Nuevo; "Grant Practitioner role" simulates the MANAGE_USERS grant.
2. **Login** — fake directory of five users (ONBOARDING → ACCOUNTANT). Tap to sign in.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch; clock-in-here shortcut.
4. **Home** — clock-in (home) / clock-in as relief (view-only start) / clock-out, relief
   invites (accept/decline), relief requests (grant/deny, withdraw own). Relief expires
   04:00 Manila next day; pay from the relief branch drawer.
5. **Sessions** — status filter chips, tap a row to expand: PENDING → COMPLETED /
   NO_SHOW / CANCELLED, void/unvoid with required reason, walk-in booking form. Walk-in
   sessions refuse NO_SHOW and CANCELLED with the rule note.
6. **Clients** — global list, per-client PENDING count (at-most-one-PENDING rule note),
   full/masked toggle, per-client Anonymize writes audit (PII nullified, gender+age kept).
7. **Finance** — SESSION and PRODUCT flows: draft → submit (seals immutable snapshot) →
   undo-with-reason (48h window note). Commission split card (pooled per branch day,
   equal split over staff clocked in at sold_at).
8. **Team** — users by home branch with role bundles; MANAGER-superset note.
9. **Mailbox** — read/unread notifications, mark read / mark all read.
10. **Audit** — every prototype mutation prepends an entry with who/action/target/reason.
11. **Profile** — current user, role bundle, clock-out, logout (returns to login).
12. **Branch-day band** — full-width OPEN (green) / PAST (amber) / REMITTED (teal) strip
    under the bar, with tap-to-switch days plus the 04:00 Asia/Manila boundary note.
13. **Context rail** — always-visible open/done/draft counts, duty card, needs-you card.

## Theme notes

Density-first workbench on purpose: slate ledger canvas (#E9EDF2), graphite bar, white
6dp cards, 11sp body with 10sp uppercase micro-labels — this variant scraps the Linear
design to test whether a persistent tri-pane (rail + work + context) lets a small laptop
cover a whole branch day without scroll marathons. Screenshots: run it on the target
display and capture there (no checked-in binaries in this branch).
