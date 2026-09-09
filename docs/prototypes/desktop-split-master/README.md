# Desktop prototype: split-master (ref #765)

Split master-detail everywhere: a fixed 480dp dark-ink master list on the left,
a flexible warm-paper detail pane on the right, plus a slim destination rail.
Selection is retained per destination; master scroll states are hoisted in the
shell so leaving and coming back restores scroll. Drafting-split ledger theme —
ink, paper, signal amber — deliberately distinct from Linear and siblings.

## Run

```bash
COMPANYAPP_PROTO_SPLIT_MASTER=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/split-master/` local `SplitMasterRepo`). No ApiClient, no
Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Sign in as Ana (Practitioner) / Ben (Coordinator) / Cara (MANAGER) / Dan (Accountant)
- [ ] Sign in as Eli (ONBOARDING) → locked gate, empty capability bundle note
- [ ] Branch select: Makati CLINIC / BGC CLINIC / Cebu PROVINCIAL_TOUR / Tondo MEDICAL_MISSION
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note
- [ ] Home: clock in/out; relief board split — duty, requests (broadcast, one-live-per-date), invites (accept/decline, revoke note)
- [ ] Sessions split: 480dp master list, click a row → detail follows; selection retained, back restores scroll
- [ ] Status transitions: PENDING / COMPLETED / NO_SHOW / CANCELLED stepper taps
- [ ] Walk-in session: only COMPLETED offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record
- [ ] + Log session creates a PENDING entry and selects it
- [ ] Clients split: global list, at-most-one-PENDING note, anonymized-view toggle
- [ ] Finance split: SESSION + PRODUCT drafts, submit → sealed snapshot, Undo within 48h with reason, 72h snapshot permanent; commission split note
- [ ] Team split: roles/capabilities, MANAGER superset note, ONBOARDING grant/deactivate
- [ ] Mailbox split: read/unread, open marks read, mark-all-read
- [ ] Audit log split: actions append entries, newest first
- [ ] Profile split: role bundle, logout, clock-out, exit prototype

## Theme notes

Ink `#14181F`, ink-soft `#1E242E`, paper `#FAF6EE`, card `#FFFDF8`,
amber `#F5A524`, teal `#1F7A6D`, oxblood `#9C3324`. The amber spine marks the
selected master row and the active rail destination; status chips carry session
state in the detail pane. Every destination is a split — no full-width tables,
no popovers. 1280x800 minimum.
