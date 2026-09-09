# Desktop prototype: executive-brief (ref #770)

Owner-first morning brief: a broadsheet "morning edition" that opens on a KPI hero
(yesterday vs target), lists exception-only alerts, and drills down on demand.
Private-club theme — deep pine hero band, brass rules, cream paper — deliberately
distinct from Linear and the daybook / console / TV sibling variants.

## Run

```bash
COMPANYAPP_PROTO_EXECUTIVE_BRIEF=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/executive-brief/` local `EbRepo`). No ApiClient, no Ktor,
no backend, no network. Window opens at 1280x800, full-screen capable.

Package note: the directory keeps the ticket's `executive-brief` name while the
Kotlin package uses the valid identifier `proto.executivebrief` (Kotlin packages
need not match directories).

## Flow checklist

- [ ] Open the brief as Olivia (OWNER) — masthead, KPI hero, exceptions, branch bars
- [ ] Sign in as Ana (Practitioner) / Ben (Coordinator) / Cara (MANAGER) / Dan (Accountant)
- [ ] Sign in as Eli (ONBOARDING) → locked gate, empty capability bundle note
- [ ] Branch select: Makati CLINIC / BGC CLINIC / Cebu PROVINCIAL_TOUR / Tondo MEDICAL_MISSION
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note
- [ ] Brief hero: yesterday revenue vs target with bar; tap KPI chips to drill down
- [ ] Exceptions only: ACTION/WATCH cards with drill-down + acknowledge (dismiss logs audit)
- [ ] Home: clock in/out; relief tabs — duty (relief clock-in), requests (broadcast, one-live-per-date note), invites (accept/decline note)
- [ ] Sessions: status stepper per detail card (Completed / No-show / Cancel)
- [ ] Walk-in session: only COMPLETED offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record; voided toggle
- [ ] + Log session creates a PENDING entry
- [ ] Clients: global list, at-most-one-PENDING note + refused-duplicate tap, anonymized-view toggle
- [ ] Finance: SESSION + PRODUCT drafts, submit → sealed snapshot, Undo within 48h with reason, 72h snapshot permanent; commission split note
- [ ] Team: roles/capabilities, MANAGER-superset note, ONBOARDING grant, deactivate
- [ ] Mailbox: read/unread toggles, mark-all-read
- [ ] Audit log: actions append entries, newest first
- [ ] Profile: role bundle, branch switch, logout, clock-out, exit prototype

## Theme notes

Paper `#F4EEDF`, card `#FFFFF0`, ink `#1E1B12`, pine `#1E3A2C`, brass `#9A7414`
/ bright `#D9B44A`, alert `#A63A2B`, sage `#4F7A5B`. The pine hero band carries
yesterday-vs-target so the Owner's first glance answers "did we hit it"; brass
rules and kickers give the broadsheet posture. Screens scroll; 1280x800 minimum.
