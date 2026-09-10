# Desktop prototype: three-column (ref #809)

Day-shift command board — navy rail, steel deck, white inspector. Nav,
content, and inspector stay on screen at all times: every row in the center
column pins its record into the right inspector with full detail and working
controls. Dense tabular mono readouts, LED status dots, uppercase microcaps
labels, hard 1px rules. Deliberately distinct from Linear and all sibling
variants — a pro-density ops desk, not a minimal canvas.

## Run

```bash
COMPANYAPP_PROTO_THREE_COLUMN=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/three-column/` local `ThreeColumnRepo`). No ApiClient,
no Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Login as ana (Practitioner) / ben (Coordinator) / cara (MANAGER) / dan (Accountant)
- [ ] Login as eli (ONBOARDING) → gate hold, empty-bundle note
- [ ] Branch select: Makati Command / BGC Command / Cebu Circuit / Tondo Post
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note + per-state edit rule
- [ ] Home: clock in/out; relief duties pin to inspector; broadcast request (one-live-per-date note) + withdraw; invites accept/decline
- [ ] Sessions: per-branch dockets, status stepper (Complete / No-show / Cancel)
- [ ] Walk-in docket: only COMPLETE offered; NO_SHOW/CANCELLED rule note shown in row and inspector
- [ ] Void with required reason; unvoid restores record (center + inspector both work)
- [ ] + LOG SESSION writes a PENDING docket (walk-in toggle)
- [ ] Clients: global list, at-most-one-PENDING note, anonymized-view toggle (gender+age kept)
- [ ] Finance: SESSION + PRODUCT draft/submit → sealed snapshot; Undo within 48h, 72h+ locked note; commission split note
- [ ] Team: roles/capabilities, MANAGER superset note, ONBOARDING grant affordance (MANAGER view)
- [ ] Mailbox: read/unread, mark-all-read, relief events name branch + day
- [ ] Audit: actions append entries, newest first
- [ ] Profile: role bundle, logout, clock-out + logout, leave board
- [ ] Inspector: always visible; row clicks pin session/client/remittance/notice/relief/operator detail; context tallies per screen when nothing pinned

## Theme notes

Rail `#0E1E33`, rail-hi `#1B3355`, deck `#EDF1F6`, panel `#FFFFFF`,
line `#D3DCE7`, ink `#0F1F33`, ink-soft `#5B6B82`, signal `#E4572E`,
go `#1E7F4F`, amber `#B7791F`, red `#C0392B`. Sans display, mono data.
LED dots carry state color (PENDING amber, COMPLETED green, NO_SHOW steel,
CANCELLED red, sealed green, ONBOARDING steel). Columns: 216dp navy nav,
fluid center, 296dp inspector. Screens scroll; 1280x800 minimum.
