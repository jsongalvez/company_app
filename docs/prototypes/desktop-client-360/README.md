# Desktop prototype: client-360 (ref #773)

Client-centric dossier: every screen starts from the client record — a stamped
360 header, one timeline spine across all branches, session history with live
transitions, an anonymize flow, and the at-most-one-PENDING guard held front
and center. Brass-and-ink index-card theme, deliberately distinct from Linear
and sibling variants.

## Run

```bash
COMPANYAPP_PROTO_CLIENT_360=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/client-360/` local `Client360Repo`). No ApiClient, no Ktor,
no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Sign in as Ana (Practitioner) / Ben (Coordinator) / Cara (MANAGER) / Dan (Accountant)
- [ ] Sign in as Eli (ONBOARDING) → locked gate, empty capability bundle note
- [ ] Branch select: Makati CLINIC / BGC CLINIC / Cebu PROVINCIAL_TOUR / Tondo MEDICAL_MISSION
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note
- [ ] Home: clock in/out; relief tabs — duty, requests (broadcast, one-live-per-date), invites (accept/decline)
- [ ] 360 Record (default dest): stamped VERIFIED/ANONYMIZED header, visit/branch/spend/booking stats
- [ ] Global record timeline: one spine across branches, newest first, seal dots per status
- [ ] At-most-one-PENDING guard: held card blocks booking while a PENDING lives; resolve jumps to Sessions
- [ ] Book follow-up session when clear → new PENDING appears on timeline and history
- [ ] Session history: PENDING → Completed / No-show / Cancel stepper taps
- [ ] Walk-in session: only COMPLETED offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with reason chips; unvoid restores the record in place
- [ ] Anonymize flow: confirm → stamped ANONYMIZED, identity masked everywhere, audit entry; MANAGER reveal override; global mask-all toggle
- [ ] Clients: global index, PENDING / anonymized filters, open-record jumps to the dossier
- [ ] Finance: SESSION + PRODUCT drafts, submit → sealed snapshot, Undo within 48h, 60h snapshot locked permanent; commission split note
- [ ] Team: roles/capabilities, MANAGER superset note, ONBOARDING grant button records approval
- [ ] Mailbox: read/unread, mark-all-read
- [ ] Audit log: actions append entries, newest first
- [ ] Profile: role bundle, logout, clock-out, exit prototype

## Theme notes

Paper `#F3ECDD`, card `#FFFDF6`, ink `#26221A`, brass `#9A6B0F` / deep `#7A5408`,
stamp red `#B33A2B`, teal `#22756B`, timeline spine `#C9A227`. The dossier reads
like a stamped index card: eyebrow labels, seal dots, a guard card in amber when
booking is held. Rail nav keeps the 360 record first; screens scroll; 1280x800 minimum.
