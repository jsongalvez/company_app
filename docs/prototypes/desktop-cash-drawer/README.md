# Desktop prototype: cash-drawer (ref #785)

Night counting room: dark walnut counter, kraft-paper count sheets with ink
figures, brass denomination straps, red/amber variance flags. The hero is the
drawer count — expected vs counted per strap, flags per line and on the drawer
total, then a seal-and-handoff into remittance. Deliberately distinct from
Linear and all sibling variants.

## Run

```bash
COMPANYAPP_PROTO_CASH_DRAWER=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/cashdrawer/` local `CashDrawerRepo`). No ApiClient,
no Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Counting-room entry → OPEN THE DRAWER (net link DOWN note)
- [ ] Login as ana (Practitioner) / ben (Coordinator) / cara (MANAGER) / dan (Accountant), filter narrows list
- [ ] Login as eli (ONBOARDING) → locked sheet, empty-bundle note
- [ ] Branch select: MAKATI CLINIC / BGC CLINIC / CEBU-TOUR PROVINCIAL_TOUR / TONDO-MISSION MEDICAL_MISSION
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note + per-state edit rule
- [ ] Count: 11 denomination straps (₱1,000 down to 25¢), expected from SESSION takings, +/− counted per strap
- [ ] Count: per-strap MATCH/SHORT/OVER flags + drawer-total variance; seal requires every strap counted
- [ ] Count: SEAL → REMITTANCE HANDOFF writes a SESSION snapshot into FINANCE; receipt shows commission note
- [ ] Home: clock in/out; relief duty list; broadcast request (one-live-per-date note) + withdraw; invites accept/decline
- [ ] Sessions: per-branch list, status stepper (Complete / No-show / Cancel)
- [ ] Walk-in session: only COMPLETE offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record
- [ ] + LOG SESSION writes a PENDING entry (walk-in toggle, type auto-note)
- [ ] Clients: global list, at-most-one-PENDING note, anonymized-view toggle (gender+age kept)
- [ ] Finance: SESSION + PRODUCT spool/submit → sealed snapshot; Undo within 48h, 80h BGC sealed note; commission split note
- [ ] Team: roles/capabilities, MANAGER superset note, ONBOARDING grant/deactivate (MANAGER view)
- [ ] Mail: read/unread, mark-all-read, relief events name branch + day
- [ ] Audit: actions append entries, newest first
- [ ] Profile: role bundle, logout, clock-out + logout, exit

## Theme notes

Counter `#171009`, rail `#20150B`, paper `#F5E9CF`, ink `#2A1F10`,
brass `#C9972B`, copper `#D97B3F`, ok `#2E7D32`, short `#B3362A`,
over `#E6A100`. Figures in monospace tabular; prose in default sans.
Variance carried by MATCH/SHORT/OVER flag chips per strap and on the total.
Screens scroll; 1280x800 minimum.
