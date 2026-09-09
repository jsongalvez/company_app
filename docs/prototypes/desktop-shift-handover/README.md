# Desktop prototype: shift-handover (ref #771)

Shift-change obsessed dashboard. Clock-out writes a handover summary, the incoming
crew sees open items, relief context carries across. Night-shift changeover board:
depot navy, signal-amber tape, OUT → IN crew columns. Fake data only.

## Run

```bash
COMPANYAPP_PROTO_SHIFT_HANDOVER=true ./gradlew :composeApp:run
```

Window opens 1280×800, full-screen capable. Unset the env var for the real app.
No network calls: `ShiftRepo` is local fake state (no ApiClient, no Ktor, no backend).

## Flow checklist

- [ ] Sign in as any user; Eli Santos (ONBOARDING) hits the lock gate
- [ ] Pick a branch; non-home branch starts relief duty (view-only)
- [ ] Changeover board: incoming handover inbox → Acknowledge; open items Ack/Done/Reopen
- [ ] Handover: write note, carry relief toggle, clock out → entry lands in handover log + mailbox
- [ ] Sessions: PENDING → COMPLETED/NO_SHOW/CANCELLED; walk-in blocks NO_SHOW/CANCELLED; void needs reason, unvoid works; book new PENDING
- [ ] Clients: global list, search, 1-PENDING chip, anonymized row keeps gender + age, register
- [ ] Finance: SESSION + PRODUCT drafts submit → snapshot; Undo within 48h needs reason; 60h-old submit is permanent; commission split pool with include/exclude
- [ ] Team: roster with capability bundles; relief request broadcast → grant; invite accept/decline/revoke
- [ ] Mailbox: unread dots, mark read; relief events name branch + day
- [ ] Audit log: every mutation newest-first
- [ ] Profile: capabilities, clock-out shortcut, switch branch, log out

## Theme notes

Scraps Linear: dark depot operations board, monospace shift tape, amber OUT→IN
arrows on every handover card. The changeover board is home — everything else is
one click down the left rail. Branch-day banner (OPEN/PAST/REMITTED + 04:00 Manila
boundary note) pins every screen.
