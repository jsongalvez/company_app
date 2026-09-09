# Desktop Calendar-Ops Prototype (fake data, branch `prototype/desktop-calendar-ops`)

Branch week calendar for the operating week. Part of map #755, ticket #775.

## Run

```bash
COMPANYAPP_PROTO_CALENDAR_OPS=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `CalendarOpsFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/calendar-ops/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding** — locked ONBOARDING account (0 capabilities). "Try opening Sessions" is
   blocked; "Grant Practitioner role" simulates the MANAGE_USERS grant and continues.
2. **Login** — fake directory of five users (ONBOARDING → ACCOUNTANT). Tap to sign in.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch the active branch week.
4. **Week (home)** — seven day cells Mon Sep 7 – Sun Sep 13 with OPEN/PAST/REMITTED color
   bands, done/total counts, TODAY ring on Wed. Tap any cell to open that day. Clock-in/out
   plus relief invites (accept/decline), relief requests (grant/deny, withdraw own).
5. **Day** — selected branch-day detail: every session on that day with manage drawers.
6. **Sessions** — status filter chips, per-session manage drawer: PENDING → COMPLETED /
   NO_SHOW / CANCELLED, void/unvoid with required reason, walk-in booking form. Walk-in
   sessions refuse NO_SHOW and CANCELLED with the rule note.
7. **Clients** — global list, per-client PENDING count (at-most-one-PENDING rule note),
   anonymized view masks PII while keeping gender + age; per-client Anonymize writes audit.
8. **Finance** — SESSION and PRODUCT flows: draft → submit (seals immutable snapshot) →
   undo-with-reason (48h window note). Commission split rule card
   (pooled per branch day, equal split over clocked-in staff at sold_at).
9. **Team** — users by home branch with role bundles, plus role notes.
10. **Mailbox** — read/unread notifications, mark read / mark all read.
11. **Audit** — every prototype mutation prepends an entry with who/action/target/reason.
12. **Profile** — current user, clock-out, logout (returns to login).
13. **Branch-day band** — full-width OPEN (green) / PAST (amber) / REMITTED (teal) strip under
    the week, always naming the selected day plus the 04:00 Asia/Manila boundary note.

## Theme notes

Paper-planner aesthetic on purpose: cream sheet (#FBF6EA), ink type, white day cards, and
thick day-state color bands instead of the Linear dark canvas — this variant scraps the
Linear design to test whether a week-grid-first planner reads better for coordinators
planning cover across a whole branch week than a single-day console. The week strip stays
pinned above every screen so the day context is never lost. Screenshots: run it on the
target display and capture there (no checked-in binaries in this branch).
