# Desktop prototype — gantt-branch (ref #815)

Branch week gantt: practitioner rows, session blocks on a time axis, one amber
day-boundary line. Full-screen fake-data dashboard the human can run and judge.
No backend, no network.

## Run

```bash
COMPANYAPP_PROTO_GANTT_BRANCH=true ./gradlew :composeApp:run
```

Window opens 1280x800, full-screen capable. Without the env var the app keeps
its existing behavior.

## Flow checklist (all clickable, fake data)

- [ ] Login: `maria.coord` signs in; `new.orb` (ONBOARDING) is locked with a note
- [ ] Home: branch week gantt (Mon–Fri tabs, practitioner rows, blocks on the 08:00–20:00 axis, amber 04:00 boundary rail), tap a block for detail, clock in/out, branch select across 4 branches, relief duty accept/decline/reopen, invite, request
- [ ] Sessions: filter ALL/PENDING/COMPLETED/NO_SHOW/CANCELLED, open detail, move status, void with reason, unvoid
- [ ] Walk-in rule note: walk-ins never take NO_SHOW/CANCELLED (buttons hidden)
- [ ] Clients: global list, search, anonymized-only toggle, at-most-one-PENDING note
- [ ] Branch-day banner: OPEN/PAST/REMITTED per branch + 04:00 Asia/Manila boundary note
- [ ] Finance: SESSION/PRODUCT tabs, draft -> submit -> snapshot, Undo within 48h (r5 at 60h shows expired)
- [ ] Commission split note on finance screen
- [ ] Team: users, roles, capabilities
- [ ] Mailbox: mark notices read, rail badge counts unread
- [ ] Audit log: every action above appends entries, newest first
- [ ] Profile: clock-out + logout returns to login, exit-prototype button

## Theme notes

Night-dispatch blueprint: ink-navy field, hairline grid dividers, amber
day-boundary rail down the gantt's left edge, signal blocks per session status
(blue PENDING, green COMPLETED, gray NO_SHOW, red CANCELLED, brown VOID),
monospace axis labels and chips against humanist body text. Distinct from Linear
and from sibling variants — nothing here is a sandstone card.

## Screenshots

Capture on judging pass (not bundled in-branch).
