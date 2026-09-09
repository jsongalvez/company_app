# Desktop prototype: relief-network (ref #772)

Night-market pinboard for spare hands. Espresso board, kraft-paper slips,
vermilion broadcast ink, a live WIRE ticker across the top. Relief-first:
requests, invites, and grants sit on one board with one-tap grant flows —
everything else hangs off a stall rail. Deliberately distinct from Linear and
all sibling variants.

## Run

```bash
COMPANYAPP_PROTO_RELIEF_NETWORK=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/relief-network/` local `RnRepo`). No ApiClient,
no Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.
Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: pick ana (Practitioner) / ben (Coordinator) / cara (MANAGER) / dan (Accountant)
- [ ] Login as eli (ONBOARDING) → STALL CLOSED, empty-bundle note
- [ ] Branch select: MAKATI CLINIC / BGC CLINIC / CEBU-TOUR / TONDO-MISSION with day stamps
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note + per-state edit rule
- [ ] Clock strip: home clock in/out; relief clock-in view-only until a grant lands
- [ ] Board — Requests: shout a broadcast (one-live-per-date), one-tap Grant, Deny, Withdraw own
- [ ] Board — Invites: send invite (login + day), Accept/Decline as invitee, Revoke frees the grant
- [ ] Board — Grants: day holders with source (REQUEST/INVITE), drawer-pay note
- [ ] WIRE ticker recomputes from live requests
- [ ] Sessions: ALL/PENDING/COMPLETED/NO_SHOW/CANCELLED pills; Pin-it create; Open detail
- [ ] Walk-in slip: only Complete offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason dialog; VOID stamp; Unvoid restores
- [ ] Clients: global list, at-most-one-PENDING note, anonymized counter-view toggle, per-card anonymize (gender+age kept)
- [ ] Finance: SESSION + PRODUCT drafts; Submit seals snapshot; Undo ≤48h with reason; 72h+ permanent note; commission-split note
- [ ] Team: role bundles, MANAGER-superset note; MANAGER grants Practitioner to ONBOARDING, deactivate/reactivate
- [ ] Mail: read/unread stamps, mark-all-read; relief events name branch + day
- [ ] Audit: mutations append entries, newest first
- [ ] Profile: capability bundle, demo branch-day flipper, clock-out + logout, logout, exit

## Theme notes

Board `#171009`, surface `#221812`, edge `#4A3421`, paper `#F7ECD4`,
ink `#2A1D10`, vermilion `#E4572E`, teal `#2A9D8F`, marigold `#F5A524`,
violet `#9D7BEA`. Status carried by STAMP chips (PENDING marigold,
COMPLETED teal, NO_SHOW violet, CANCELLED muted, VOID red). Market copy:
stalls, shouts, wire, slips, crew. Screens scroll; 1280x800 minimum.
