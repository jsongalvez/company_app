# Desktop prototype — desert-sand (ref #808)

Sun-baked sandstone, adobe cards, high-noon clarity. Full-screen fake-data
dashboard the human can run and judge. No backend, no network.

## Run

```bash
COMPANYAPP_PROTO_DESERT_SAND=true ./gradlew :composeApp:run
```

Window opens 1280x800, full-screen capable. Without the env var the app keeps
its existing behavior.

## Flow checklist (all clickable, fake data)

- [ ] Login: `maria.coord` signs in; `new.orb` (ONBOARDING) is locked with a note
- [ ] Home: clock in/out, branch select across 4 branches, relief duty accept/decline/reopen, invite, request
- [ ] Sessions: filter ALL/PENDING/COMPLETED/NO_SHOW/CANCELLED, open detail, move status, void with reason, unvoid
- [ ] Walk-in rule note: walk-ins never take NO_SHOW/CANCELLED (buttons hidden)
- [ ] Clients: global list, search, anonymized-only toggle, at-most-one-PENDING note
- [ ] Branch-day banner: OPEN/PAST/REMITTED per branch + 04:00 Asia/Manila boundary note
- [ ] Finance: SESSION/PRODUCT tabs, draft -> submit -> snapshot, Undo within 48h (r5 at 60h shows expired)
- [ ] Commission split note on finance screen
- [ ] Team: users, roles, capabilities
- [ ] Mailbox: mark notices read, rail badge counts unread
- [ ] Audit log: every action above appends entries, newest first
- [ ] Profile: clock-out + logout returns to login

## Theme notes

Dune gradient backdrop, ink nav rail with sun-gold masthead, adobe cards with
scorched top edges, turquoise jewelry accents for branch-day/finance states,
palm green for good states, high-noon ink for text. Distinct from Linear and
from sibling variants.

## Screenshots

Capture on judging pass (not bundled in-branch).
