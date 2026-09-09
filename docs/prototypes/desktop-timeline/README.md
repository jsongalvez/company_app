# Desktop prototype: timeline (ref #760)

Timeline-first sessions: the day renders as a vertical rail with wax-seal status
nodes, a time gutter, and a detail popover per session. Editorial paper theme —
cream, ink, oxblood — deliberately distinct from Linear and sibling variants.

## Run

```bash
COMPANYAPP_PROTO_TIMELINE=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/timeline/` local `TimelineRepo`). No ApiClient, no Ktor,
no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Sign in as Ana (Practitioner) / Ben (Coordinator) / Cara (MANAGER) / Dan (Accountant)
- [ ] Sign in as Eli (ONBOARDING) → locked gate, empty capability bundle note
- [ ] Branch select: Makati CLINIC / BGC CLINIC / Cebu PROVINCIAL_TOUR / Tondo MEDICAL_MISSION
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note
- [ ] Home: clock in/out; relief tabs — duty, requests (broadcast, one-live-per-date), invites (accept/decline, revoke note)
- [ ] Sessions rail: time gutter + seal nodes; click a card → detail popover
- [ ] Drag-free transitions: Completed / No-show / Cancel stepper taps (no drag gestures)
- [ ] Walk-in session: only COMPLETED offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record
- [ ] + Log session creates a PENDING entry on the rail
- [ ] Clients: global list, at-most-one-PENDING note, anonymized-view toggle
- [ ] Finance: SESSION + PRODUCT drafts, submit → sealed snapshot, Undo within 48h with reason, 72h snapshot permanent; commission split note
- [ ] Team: roles/capabilities, MANAGER superset note, ONBOARDING grant/deactivate
- [ ] Mailbox: read/unread, mark-all-read
- [ ] Audit log: actions append entries, newest first
- [ ] Profile: role bundle, logout, clock-out, exit prototype

## Theme notes

Paper `#F7F2E7`, card `#FFFDF6`, ink `#211B12`, rail `#B4552D`,
oxblood `#8E2F22`, teal `#1F6F6B`. Rail dots + time gutter carry status so the
list scans like a daybook, not a table. Screens scroll; 1280x800 minimum.
