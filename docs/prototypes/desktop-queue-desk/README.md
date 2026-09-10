# Desktop Queue-Desk Prototype (fake data, branch `prototype/desktop-queue-desk`)

The coordinator's sorting office: triage inbox, claim-next, my tray, done pile. Part of map #755, ticket #822.

## Run

```bash
COMPANYAPP_PROTO_QUEUE_DESK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `QueueDeskFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/queue-desk/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Sign in** — fake directory (Coordinator, Practitioner, MANAGER, Accountant, ONBOARDING). ONBOARDING opens the locked demo instead of the belt.
2. **ONBOARDING locked** — 0 capabilities, every drawer shut; "Simulate MANAGE_USERS grant" continues as Practitioner.
3. **Branch** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR), Tondo Medical Mission (MEDICAL_MISSION) with live waiting counts.
4. **Counter (home)** — clock in/out, inbox/tray/done/unread stats, claim-next shortcut, relief invites (Accept/Decline), relief requests (Grant/Deny own Withdraw), ask-another-branch request.
5. **Triage inbox** — unclaimed PENDING slips oldest-first, claim-next takes the top slip, new walk-in ticket slip stamps a PENDING session.
6. **My tray** — PENDING slips held by you: Complete, No-show, Cancel, or Release back to triage. Walk-in rule note shown.
7. **Done pile** — COMPLETED / NO_SHOW / CANCELLED slips with void/unvoid (reason required, slip stays visible).
8. **Client index** — global list with search, PENDING badge, at-most-one-PENDING open-session block note, anonymized masked view with lift/mask cover (gender + age kept).
9. **Vault (finance)** — SESSION and PRODUCT draft drawers (−/+500, free amount), submit seals an immutable snapshot (SN-*), undo-with-reason 48h note, commission split rule ledger.
10. **Hands (team)** — role headcount board; ONBOARDING row stays a locked grant note.
11. **Pigeonhole (notifications)** — read/unread mailbox, tap to file one, file-all button; unread count badges the rail.
12. **Daybook (audit)** — every prototype mutation listed with actor/action/record/reason.
13. **Profile** — current user, clock in/out, log out (returns to sign-in), close.
14. **Day banner** — full-width OPEN / PAST / REMITTED switcher with the 04:00 Asia/Manila boundary note, always visible under the window title.

## Theme notes

Sorting-office work queue: near-black conveyor belt (`#17191D` → `#23262B`) with a kraft ticket-paper (`#FBF6E9`) work surface, rubber-stamp blue claim accent (`#1D5BBF`), stamp red/green/amber status inks, monospace stamper labels, per-status slip tints (pending yellow, completed green, no-show lavender, cancelled rose). Left rail groups SORTING / WORK QUEUE / HOUSE — this variant scraps the Linear design for a coordinator triage belt.
Screenshots: run it and judge — no checked-in images in the prototype branch.
