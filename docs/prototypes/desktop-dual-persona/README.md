# Desktop prototype: dual-persona (ref #781)

Dual persona toggle for the desktop dashboard. One record set, two lenses:
**Practitioner** (warm treatment-room light) vs **Coordinator** (dark ops-bridge).
Flipping the masthead toggle reshapes palette, nav, home, and actions — the data never changes.

## Run

```bash
COMPANYAPP_PROTO_DUAL_PERSONA=true ./gradlew :composeApp:run
```

Window opens at 1280x800, full-screen capable. Default run (env unset) keeps the real `App()` untouched.

## Flow checklist

- [ ] Onboarding tours the two-lens idea; ONBOARDING accounts flagged locked
- [ ] Login picks a fake user (ONBOARDING row locked, unclickable)
- [ ] Branch select filters sessions per branch
- [ ] Home: clock in/out; practitioner sees My relief, coordinator sees coverage radar
- [ ] Sessions: PENDING/COMPLETED/NO_SHOW/CANCELLED chips; walk-in S-102 hides NO_SHOW with rule note; complete/no-show only on Practitioner lens; void/unvoid with reason only on Coordinator lens; "same record, other lens" preview renders the record in the opposite palette
- [ ] Clients: global registry, at-most-one-PENDING flags; Coordinator lens sees masked names + masked contacts
- [ ] Branch-day banner: OPEN/PAST/REMITTED chip, 04:00 Asia/Manila boundary note, tap Sat/Mon/Tue to switch day
- [ ] Relief: duty/invite/request kinds; practitioner accepts own invites + posts requests; coordinator assigns open network requests
- [ ] Finance: SESSION + PRODUCT drafts editable in both lenses; submit/snapshot and Undo-48h gated to Coordinator lens; commission split 70/30 note with per-session math
- [ ] Team: users/roles + capability matrix showing which lens holds each capability
- [ ] Mailbox: read/unread toggle, mark-all-read, unread badge in nav
- [ ] Audit: every action appends newest-first entries
- [ ] Profile: lens flip, clock-out, logout back to login
- [ ] Flip the persona toggle on any screen: gated nav items hide with a "needs the other lens" hint; open gated screens fall back to Home

## Theme notes

Practitioner lens is warm paper (`#FFF7EE`), terracotta accent (`#C4552D`), treatment-first language ("My shift", "My relief"). Coordinator lens is dark slate (`#0E131B`), signal-indigo accent (`#6E8BFF`), control language ("Floor control", "Coverage radar", "Relief network"). Shared primitives (`DpCard`, `DpChip`, `DpButton`, `DpNote`) take the active `DpPalette`, so new screens automatically follow the flip.

Fake data only: `DpFakeRepo` in `proto/dual-persona/`, no `ApiClient`, no Ktor, no backend import.
