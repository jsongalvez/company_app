# Desktop prototype: guided-onboarding (ref #766)

First-run wizard obsessed: the whole dashboard is a guided first day. Eight
lantern steps in a night-sky rail — progress steps, role explainer, empty-state
coaching, sample-data reset. Night indigo + amber lantern theme, deliberately
distinct from Linear and sibling variants.

Package dir is `guided_onboarding` (underscores — Kotlin packages cannot contain
the ticket's hyphen; env var keeps the hyphen→underscore mapping below).

## Run

```bash
COMPANYAPP_PROTO_GUIDED_ONBOARDING=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/guided_onboarding/` local `GuidedRepo`). No ApiClient, no Ktor,
no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Sign in as Ana (Practitioner) / Ben (Coordinator) / Cara (MANAGER) / Dan (Accountant)
- [ ] Sign in as Eli (ONBOARDING) → locked gate with coaching card, empty capability bundle note
- [ ] Lantern 1 Welcome: journey checklist, seed inventory, sample-data reset with confirm
- [ ] Lantern 2 Roles: bundle explainer per role, you-marker, ONBOARDING lock preview, MANAGER-superset note
- [ ] Branch select: Makati CLINIC / BGC CLINIC / Cebu PROVINCIAL_TOUR / Tondo MEDICAL_MISSION + tour lines
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note
- [ ] Lantern 3 Branch & day: clock in (lights lantern, gates next step) / clock out; relief tabs — duty, requests (broadcast, one-live-per-date), invites (accept/decline, revoke note)
- [ ] Lantern 4 Sessions: PENDING → COMPLETED / NO_SHOW / CANCELLED in detail dialog; move lights lantern
- [ ] Walk-in session: only COMPLETED offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record; void-preserves-record note
- [ ] + Log session creates a PENDING entry; empty-book coaching state when branch has none
- [ ] Lantern 5 Clients: global list, at-most-one-PENDING note, anonymized-view toggle (lights lantern, keeps gender/age note)
- [ ] Lantern 6 Finance: SESSION + PRODUCT drafts, submit → sealed snapshot, Undo within 48h with reason, 72h snapshot permanent; commission split coach card
- [ ] Lantern 7 Team: roles/slots, deactivate (fake) line, relief board with accept/grant actions
- [ ] Lantern 8 Graduate: mailbox read/unread + mark-all-read + empty-state coach, audit log newest-first, graduation button, profile + logout + clock-out + exit
- [ ] Reset sample data replays the whole first day from seed

## Theme notes

Night `#14122B`, night-soft rail `#1D1A38`, parchment cards `#FBF6EA`,
lantern amber `#F5A524`, lantern-deep `#B96A00`, leaf `#2E9E6B`. Step rail shows
✓ done / numbered current / dim todo; progress bar counts lit lanterns. Coaching
cards are amber-bordered night blocks; flow content sits on parchment. Screens
scroll; 1280x800 minimum.
