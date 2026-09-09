# Desktop prototype: terminal-green (ref #768)

Phosphor-CRT retro console: near-black tube, phosphor-green monospace glyphs,
amber section headers, hairline scanline rules. Boot splash, `$ login` picker,
branch select, then a rail-driven shell. Deliberately distinct from Linear and
all sibling variants — keyboard-first copy, bracket tags, `>_ command` verbs.

## Run

```bash
COMPANYAPP_PROTO_TERMINAL_GREEN=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/terminal-green/` local `TerminalGreenRepo`). No ApiClient,
no Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Boot splash → CONNECT TTY0 (net link DOWN note)
- [ ] Login as ana (Practitioner) / ben (Coordinator) / cara (MANAGER) / dan (Accountant), filter box narrows list
- [ ] Login as eli (ONBOARDING) → ACCESS DENIED, empty-bundle note
- [ ] Branch select: MAKATI CLINIC / BGC CLINIC / CEBU-TOUR PROVINCIAL_TOUR / TONDO-MISSION MEDICAL_MISSION
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note + per-state edit rule
- [ ] Home: clock in/out; relief duty list; broadcast request (one-live-per-date note) + withdraw; invites accept/decline
- [ ] Sessions: per-branch list, status stepper (Complete / No-show / Cancel)
- [ ] Walk-in session: only COMPLETE offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record
- [ ] + LOG SESSION writes a PENDING entry (walk-in toggle, type auto-note)
- [ ] Clients: global list, at-most-one-PENDING note, anonymized-view toggle (gender+age kept)
- [ ] Finance: SESSION + PRODUCT spool/submit → sealed snapshot; Undo within 48h, 72h+ permanent note; commission split note
- [ ] Team: roles/capabilities, MANAGER superset note, ONBOARDING grant/deactivate (MANAGER view)
- [ ] Mail: read/unread, mark-all-read, relief events name branch + day
- [ ] Audit: actions append entries, newest first
- [ ] Profile: role bundle, logout, clock-out + logout, exit console

## Theme notes

Tube `#060A06`, panel `#0B120B`, edge `#1E3A24`, phosphor `#33FF66`,
dim `#1E9E44`, amber `#FFB000`, red `#FF5555`, cyan `#55FFFF`.
Everything `FontFamily.Monospace`. Status carried by `[BRACKET]` tags with
per-state color (PENDING amber, COMPLETED green, NO_SHOW cyan, CANCELLED muted).
Screens scroll; 1280x800 minimum.
