# Desktop Week-Review Prototype (fake data, branch `prototype/desktop-week-review`)

Friday week-review broadsheet: the desk closes the week like an editor closes an
edition. Part of map #755, ticket #850.

## Run

```bash
COMPANYAPP_PROTO_WEEK_REVIEW=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `WeekReviewFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/week-review/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.
(Base master has no DashboardPrototype gate, so there was none to preserve.)

## Flow checklist

1. **Onboarding** — kiosk signs in as ONBOARDING (locked, zero capabilities). Sessions
   show the locked note; "Grant Practitioner role" simulates the MANAGE_USERS grant.
2. **Login** — fake roster of five (ONBOARDING → ACCOUNTANT). Tap to sign in.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch the reviewed branch.
4. **Front Page (home)** — Mon–Fri done/total strip, week's till, pay-envelope tally,
   wins column (COMPLETED, unvoided) vs misses column (NO_SHOW / CANCELLED / voided),
   clock-in/out, relief invites (accept/decline) and cover asks (grant/withdraw/deny),
   seeds preview.
5. **Sessions** — status filter chips, per-session Manage (PENDING → COMPLETED /
   NO_SHOW / CANCELLED), void/unvoid with required reason, Friday walk-in booking.
   Walk-in sessions refuse NO_SHOW and CANCELLED with the house-rule note.
6. **Clients** — global list, per-client PENDING count (at-most-one-PENDING rule note),
   anonymize/reveal masks PII while keeping gender + age.
7. **Finance** — SESSION and PRODUCT flows: draft → submit (seals immutable snapshot) →
   undo-with-reason (48h window note). Commission split rule card
   (pooled per branch day, split over clocked-in staff at sold_at) plus pay envelopes
   with mark-paid/reopen.
8. **Seeds** — next-week packets grouped by day, tap to plant/reopen, pack-a-seed dialog.
9. **Team** — users by role with capability bundles, Manager-only grant control,
   cover asks with raise/grant/withdraw.
10. **Mailbox** — read/unread notices, mark read/unread, mark all read.
11. **Audit Log** — every prototype mutation prepends who/action/target/reason.
12. **Profile** — current user, clock-out, logout (returns to login).
13. **Branch-day band** — full-width OPEN / PAST / REMITTED switcher with the 04:00
    Asia/Manila boundary note, always visible under the masthead.

## Theme notes

Editorial Friday broadsheet: cream paper, ink double-rule masthead, oxblood section
flags, rubber-stamp status chips, moss-green wins vs amber misses, gold pay envelopes.
Scraps the Linear design to test whether a ritual top-to-bottom read (week → wins →
misses → payouts → seeds) closes the week better than a dense ops table. Screenshots:
run it on the target display and capture there (no checked-in binaries in this branch).
