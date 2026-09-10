# Desktop Bell-Desk Prototype (fake data, branch `prototype/desktop-bell-desk`)

The front-desk bell counter: arrivals ring in, the coordinator seats each guest in three taps (arrival → lane → ring). Part of map #755, ticket #827.

## Run

```bash
COMPANYAPP_PROTO_BELL_DESK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `BellDeskFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/bell-desk/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Sign in** — fake directory (Coordinator, Practitioners, MANAGER, Accountant, ONBOARDING). ONBOARDING opens the locked demo instead of the counter.
2. **ONBOARDING locked** — 0 capabilities, every drawer shut; "Simulate MANAGE_USERS grant" continues as staff.
3. **Branch** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR), Tondo Medical Mission (MEDICAL_MISSION) with live waiting counts.
4. **Bell (home)** — clock in/out, big "Ring! New walk-in" bell button, three-tap assignment strip (arrival → lane → ring), relief invites (Accept/Decline), relief requests (Grant/Deny, own Withdraw), ask-another-branch request.
5. **Arrivals** — unseated PENDING bells oldest-first, one-tap seat to the picked lane, seated list with Complete; walk-in NO_SHOW/CANCELLED house-rule note shown.
6. **Ledger (sessions)** — PENDING/COMPLETED/NO_SHOW/CANCELLED with filter, new bell ticket stamp, detail view, Complete / No-show / Cancel (booked only), void with required reason, unvoid; slip stays visible.
7. **Client book** — global list with search, PENDING badge, at-most-one-PENDING second-ring block note, anonymized masked view with lift/mask cover (gender + age kept).
8. **Till (finance)** — SESSION and PRODUCT draft drawers (−/+500, free amount), submit seals an immutable snapshot (SN-*), undo-with-reason 48h note, commission split rule ledger (lane 60 / house 40, relief even split).
9. **Staff (team)** — role headcount board; ONBOARDING row stays a locked grant note; full directory below.
10. **Bellbox (notifications)** — read/unread mailbox, tap to file one, file-all button; unread count badges the rail.
11. **Daybook (audit)** — every prototype mutation listed with actor/action/record/reason.
12. **Profile** — current user, clock in/out, log out (returns to sign-in), close.
13. **Day banner** — full-width OPEN / PAST / REMITTED switcher with the 04:00 Asia/Manila boundary note, always visible under the window title.

## Theme notes

Lobby bell counter: deep walnut counter (`#241A13` → `#33261B`) with a brass bell accent (`#C9972B` / `#9A7215`), marble-cream work surface (`#FFF8EC`), brass-wash pending slips (`#FFE08A`), serif lobby display type with monospace bell numbers, rounded counter cards. Left rail groups FRONT DESK / WALK-IN FLOW / HOUSE — this variant scraps the Linear design for a three-tap walk-in bell flow.
Screenshots: run it and judge — no checked-in images in the prototype branch.
