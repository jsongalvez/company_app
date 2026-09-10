# Desktop prototype: captain-chair (ref #825)

Branch captain chair. Shift-lead helm: crew, relief, exceptions, handover at a
glance. Deep bridge teal-black console, brass helm metal, port/starboard
lanterns, helm-spoke dividers. Fake data only.

## Run

```bash
COMPANYAPP_PROTO_CAPTAIN_CHAIR=true ./gradlew :composeApp:run
```

Window opens 1280×800, full-screen capable. Unset the env var for the real app.
No network calls: `CaptainRepo` is local fake state (no ApiClient, no Ktor, no backend).

## Flow checklist

- [ ] Sign in as any user; Eli Santos (ONBOARDING) hits the lock gate
- [ ] Pick a branch; non-home branch starts relief duty (view-only)
- [ ] Helm: four spokes — crew on watch, relief posture, exceptions + counts, standing handover with quick clock-out note
- [ ] Handover: write note, carry relief toggle, clock out → entry lands in handover log + mailbox; acknowledge entries
- [ ] Sessions: PENDING → COMPLETED/NO_SHOW/CANCELLED; walk-in blocks NO_SHOW/CANCELLED; void needs reason, unvoid works; book new PENDING; filter by client
- [ ] Clients: global list, search, 1-PENDING chip, anonymized row keeps gender + age, register
- [ ] Finance: SESSION + PRODUCT drafts submit → snapshot; Undo within 48h needs reason; 60h-old submit is permanent; commission split pool with include/exclude
- [ ] Team: roster with capability bundles; relief request broadcast → grant; invite accept/decline/revoke; send invite
- [ ] Mailbox: unread lamps, mark read; relief events name branch + day
- [ ] Audit log: every mutation newest-first
- [ ] Profile: capabilities, clock-out/in, switch branch, log out

## Theme notes

Scraps Linear: a ship's helm console, not a ticket queue. Brass helm plate on
every station head, port-red / starboard-green exception lamps, helm-wheel
divider in the rail and login gate. The helm screen is home — four spokes,
everything else one click down the left rail. Branch-day banner (OPEN/PAST/REMITTED + 04:00 Manila
boundary note) pins every screen.
