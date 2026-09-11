# Desktop Inbox-Zero Prototype (fake data, branch `prototype/desktop-inbox-zero`)

Morning inbox zero: notifications-first triage — clear the inbox, the day reveals
itself. Part of map #755, ticket #856.

## Run

```bash
COMPANYAPP_PROTO_INBOX_ZERO=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `IzFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/inbox-zero/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.
(Base master has no DashboardPrototype gate, so there was none to preserve.)

## Flow checklist

1. **Login** — window opens signed out ("Good morning."). Pick a user, Sign in & open inbox.
2. **Onboarding** — sign in as Sam Rivera (ONBOARDING): fully locked stage, zero
   capabilities, with a "Grant Practitioner" action (simulates the MANAGE_USERS grant).
3. **Inbox triage** — every actionable row in one queue: clock-in, ONBOARDING grants,
   relief invites/requests, PENDING sessions at the current branch, draft remittances,
   unread notices. Triage jumps into the flow; the named button resolves in one tap
   (Snooze parks it — it stays in its home tab). Progress bar counts down to zero.
4. **Inbox zero** — last row cleared flips the stage into "Inbox zero — the day is
   yours" with the revealed day board (branch day, pending here, open drafts).
5. **Clock & relief (Home)** — clock in/out; branch select (QC Central / Laguna Tour
   Stop 3 / Tondo Medical Mission); relief invites accept/decline; cover asks
   grant/deny/withdraw plus a relief ask sender. Relief starts view-only, grant
   unlocks edits until 04:00 Manila next day.
6. **Sessions** — list with status pills; detail offers Complete / No-show / Cancel,
   void + unvoid with reason, and a jump to the client. Walk-in sessions (⚑ pill)
   refuse NO_SHOW and CANCELLED with the house-rule note. Walk-in express registers
   a global client + PENDING session on the spot.
7. **Clients** — global records with gender + age + pending count, the
   at-most-one-PENDING rule (book blocked with a note; walk-in bypasses),
   anonymize/reveal, per-client session list with jumps.
8. **Branch day** — header strip shows OPEN / PAST / REMITTED plus the 04:00
   Asia/Manila boundary; Advance cycles the state.
9. **Finance** — `Finance` tab holds SESSION + PRODUCT drafts: new draft → submit
   (seals an immutable snapshot id) → Undo with reason (48h note) → resubmit.
   Commission split card notes the pooled-per-branch-day equal-share rule with
   per-staff mark-paid/reopen.
10. **Team** — users, roles, shift state; grant Practitioner to ONBOARDING users.
11. **Notifications** — read/unread mailbox, per-notice toggle, mark-all-read.
12. **Audit log** — newest-first who/action/target/reason; every triage tap lands here.
13. **Profile** — identity, clock toggle, logout back to login.

## Theme notes

Dawn paper: warm cream paper, ink text, one sunrise-orange triage accent, sage green
reserved for the zero state. Monospace only for identity/meta strips. Scraps the Linear
design to test whether an inbox — not a dashboard — can run a whole branch morning.
Screenshots: run it on the target display and capture there (no checked-in binaries
in this branch).
