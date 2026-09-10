# Desktop prototype: whiteboard (Team whiteboard)

Isolated fake-data prototype for map #755, ticket #844. No backend, no network, no shared changes.

## Run

```bash
COMPANYAPP_PROTO_WHITEBOARD=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 minimum and can go full-screen. Without the env var, the app boots the normal `App()` unchanged.

## Flow checklist (all fake data)

- [ ] Sign-in wall: tap any teammate marker card (no password). Sam Reyes is ONBOARDING and lands on the locked screen.
- [ ] ONBOARDING locked: explains the empty role bundle; back to markers. Sign in as A. Villanueva (MANAGER) and use Crew to grant Sam the Practitioner role.
- [ ] Branch pins: Makati Flagship (CLINIC), Laguna Pop-up Tour (PROVINCIAL_TOUR), Payatas Mission Day (MEDICAL_MISSION). Tapping re-pins the board.
- [ ] Board: standup counts (to-do / done / missed), clock-in home + relief view-only note, ask-branch request + simulate-grant, relief inbox accept/decline/grant/deny.
- [ ] Sessions: To-do / Done / Missed / All piles, new card create (booked or walk-in). Walk-in cards print the rule and cap the NO_SHOW / CANCELLED pens. Complete / reopen, void with reason, unvoid.
- [ ] Clients: global wall, PENDING counts, at-most-one-PENDING note, anonymize → anonymized view keeps gender + age.
- [ ] Branch-day banner: tap OPEN / PAST / REMITTED. Note prints the 04:00 Asia/Manila boundary and the Coordinator-only REMITTED rule.
- [ ] Money: SESSION + PRODUCT drafts (unconstrained, overlapping OK), submit freezes an immutable snapshot, undo needs a reason inside 48h; the 48h-elapsed checkbox demos the permanent snapshot. Commission split note names relief pay from the relief drawer.
- [ ] Crew: roles + capability lines, manager-only ONBOARDING grant demo.
- [ ] Bell: mailbox with unread magnets, mark read, mark all read.
- [ ] Ledger: audit tape, newest first, every action appends a line.
- [ ] Me: cheat-sheet counts, clock in/out, log out.

## Theme notes

Marker strokes, magnetic index cards, washi-tape panel tops on warm paper. Dark cork frame top and bottom, color-coded day banner (green OPEN / orange PAST / blue REMITTED), magnet-dot status on every card. Standup-first: counts up top, piles by status, cheat-sheet on the profile card. Nothing shared with the Linear dashboard — hand-drawn energy throughout.
