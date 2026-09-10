# Desktop Beacon-Status Prototype (fake data, branch `prototype/desktop-beacon-status`)

The harbor beacon: every Branch reduced to a single status lamp on a night-harbor chart — green, amber, red — with the full clinic running underneath. Part of map #755, ticket #837.

## Run

```bash
COMPANYAPP_PROTO_BEACON_STATUS=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `BeaconStatusFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/beacon-status/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Sign in** — fake directory (Coordinator, Practitioners, MANAGER, Accountant, ONBOARDING). ONBOARDING opens the locked demo instead of the harbor.
2. **ONBOARDING locked** — 0 capabilities, every hatch shut; "Simulate MANAGE_USERS grant" continues as staff.
3. **Berth (branch select)** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR), Tondo Medical Mission (MEDICAL_MISSION), each with its live lamp and pending count.
4. **Beacon (home)** — clock in/out, relief-duty banner on non-home Branches (view-only until a grant lands), relief invites (Accept/Decline), relief requests (Grant/Deny, own Withdraw), ask-another-Branch request, plus this berth's lamp readout.
5. **Signals (arrivals)** — unseated PENDING oldest-first, pick-a-lane then one-tap Seat, seated list; walk-in NO_SHOW/CANCELLED house-rule note shown.
6. **Ledger (sessions)** — PENDING/COMPLETED/NO_SHOW/CANCELLED with filter, new-signal stamp, detail view, Complete / No-show / Cancel (booked only), void with required reason, unvoid.
7. **Client book** — global list with search, PENDING badge, at-most-one-PENDING second-stamp refusal, masked view with lift (gender + age kept).
8. **Vault (finance)** — SESSION and PRODUCT draft drawers (+500, free amount), submit seals an immutable snapshot (SN-*), undo-with-reason 48h note, commission split rule note (lane 60 / house 40, relief even split).
9. **Crew (team)** — role headcount board; ONBOARDING row stays a LOCKED note; full directory below.
10. **Mail (notifications)** — read/unread locker, tap to file one read, file-all button; unread count badges the rail.
11. **Logbook (audit)** — every prototype mutation listed with actor/action/record/reason.
12. **Profile** — current user, clock out, log out (returns to sign-in), close.
13. **Day banner** — full-width OPEN / PAST / REMITTED switcher with the 04:00 Asia/Manila boundary note, always visible under the window title.

## Theme notes

Night harbor: deep-sea ink canvas (`#0A1628` → `#0E1E36`), brass beacon accent (`#FFC94D`), fog-white type with monospace signal labels, one glowing lamp per Branch on a left harbor-chart rail (GREEN open-clear, AMBER past/queue, RED sealed/surge — red beats amber beats green). The watch deck below the chart jumps between harbor stations. This variant scraps the Linear design for a single-light-per-branch overview.
Screenshots: run it and judge — no checked-in images in the prototype branch.
