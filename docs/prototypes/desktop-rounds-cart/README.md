# Desktop Rounds-Cart Prototype (fake data, branch `prototype/desktop-rounds-cart`)

The doctor's rounds cart: the Practitioner pushes a steel cart down the ward — next Client first,
chart clipped at hand, drawer counted at the end. Part of map #755, ticket #831.

## Run

```bash
COMPANYAPP_PROTO_ROUNDS_CART=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `RoundsCartFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/rounds-cart/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Sign in** — fake directory (Coordinator, Practitioners, MANAGER, Accountant, ONBOARDING). ONBOARDING opens the locked demo instead of the ward.
2. **ONBOARDING locked** — 0 capabilities, cart chocked; "Simulate MANAGE_USERS grant" continues as staff.
3. **Branch** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR), Tondo Medical Mission (MEDICAL_MISSION) with live waiting counts.
4. **Route (home)** — clock in/out, big NEXT UP stop card (first PENDING in stop order) with Clip-chart + Complete, relief invites (Accept/Decline), relief requests (Grant/Deny, own Withdraw), ask-another-branch request, stop-order census with tap-to-clip and quick Done.
5. **Chart (sessions)** — chart-at-hand detail for the clipped stop, Complete / No-show / Cancel (booked only; walk-ins skip NO_SHOW/CANCELLED per house rule note), void with required reason + unvoid, status filter, full census tap-to-clip, new walk-in stop form.
6. **Clients** — global book with search, PENDING badge, at-most-one-PENDING second-booking-wait note, anonymized masked view toggle (gender + age kept).
7. **Drawer (finance)** — SESSION and PRODUCT draft drawers (−/+500, free amount), Seal submit writes immutable snapshot (SN-*), Undo within 48h with reason, commission split note (pooled per Branch Day, split equally among clocked-in Practitioners + Coordinators at sold_at).
8. **Team** — role headcount board; ONBOARDING row stays a locked note; full directory below.
9. **Pager (notifications)** — read/unread mailbox, tap to file one, file-all button; unread count badges the rail.
10. **Logbook (audit)** — every prototype mutation listed with actor/action/record/reason.
11. **Profile** — current user, clock in/out, log out (returns to sign-in), park cart (close).
12. **Day banner** — full-width OPEN / PAST / REMITTED switcher with the 04:00 Asia/Manila boundary note, always visible under the window title.

## Theme notes

Rounds cart: dark steel cart (`#1C2B2D` → `#27393C`) with a surgical-teal handle (`#0E7C7B` / `#0A5B5A`),
chart-paper work surface (`#F7F2E4`), cream chart cards, amber pending slips, teal-wash clipped chart,
monospace stop numbers in steel chips. Left rail groups WARD ROUND / CART / HOUSE — this variant scraps
the Linear design for a next-client-first route with the chart always at hand.
Screenshots: run it and judge — no checked-in images in the prototype branch.
