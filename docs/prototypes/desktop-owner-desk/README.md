# Desktop prototype: owner-desk (ref #819)

The owner's private desk: a single-owner view of money, people, and risk.
Mahogany desk, brass nameplate, ledger-cream cards, serif mastheads —
deliberately distinct from Linear and every sibling variant. Staff
particulars stay sealed: headcounts and capability bundles only, never
per-staff detail pages.

## Run

```bash
COMPANYAPP_PROTO_OWNER_DESK=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/owner-desk/` local `OwnerDeskRepo`). No ApiClient, no
Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Take the seat as Amara (Owner) or staff; Eli (ONBOARDING) → locked gate, empty capability note
- [ ] Branch gate: Flagship CLINIC / Riverside CLINIC / Highland PROVINCIAL_TOUR / Harbor MEDICAL_MISSION
- [ ] Nameplate branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note
- [ ] Desk: morning-brief money cells (takings, sessions done/open, headcount) + risk flags
- [ ] Desk: clock in/out; relief tabs — duty, invites (accept/decline), requests (one-live-per-date note)
- [ ] Sessions ledger: open a card → detail; PENDING/COMPLETED/NO_SHOW/CANCELLED steppers
- [ ] Walk-in session: only COMPLETED offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores the record
- [ ] + Enter session creates a PENDING entry in the ledger
- [ ] Sealed file (clients): global list, at-most-one-PENDING note, anonymized-view toggle
- [ ] Finance: SESSION + PRODUCT drafts, submit → sealed snapshot, Undo within 48h with reason, older snapshots permanent; commission split note
- [ ] People: headcount by role, capability bundles, MANAGER superset note, ONBOARDING grant/deactivate
- [ ] Dispatch box: read/unread, mark-all-read
- [ ] Day book (audit): actions append entries, newest first
- [ ] Nameplate (profile): role bundle, clock-out, log out, leave the desk

## Theme notes

Mahogany `#241A12`, desk edge `#3A2A1B`, ledger `#F3EBD3`, brass `#C9A227`,
felt `#1E4D3A` for money, oxblood `#7A2A22` for risk. Serif mastheads carry
the private-ledger voice; the risk rail under the morning brief keeps the
owner's eye on what needs signing. Screens scroll; 1280x800 minimum.
