# Desktop prototype: night-shift (ref #780)

True-black OLED night mode for overnight crews: warm red-shift palette with no
blue light, a crew dimmer that scales every numeral, serif display type, and
quiet low-contrast alerts. Dusk boot, crew login, branch pick, then a
rail-driven shell. Deliberately distinct from Linear and all sibling variants.

## Run

```bash
COMPANYAPP_PROTO_NIGHT_SHIFT=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/night-shift/` local `NightShiftRepo`). No ApiClient,
no Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Dusk boot → dimmer EMBER/LOW/MED/HIGH moves the big 22:47 numeral → START THE NIGHT
- [ ] Login as ana (Practitioner) / ben (Coordinator) / cara (MANAGER) / dan (Accountant), search narrows list
- [ ] Login as eli (ONBOARDING) → locked note, empty-bundle rule
- [ ] Branch select: MAKATI CLINIC / BGC CLINIC / CEBU-TOUR PROVINCIAL_TOUR / TONDO-MISSION MEDICAL_MISSION
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note + per-state edit rule
- [ ] Home: clock in/out with dimmed numerals; relief duty take; invite accept/decline; broadcast request (one-live note) + withdraw
- [ ] Sessions: per-branch list, status stepper (Complete / No-show / Cancel)
- [ ] Walk-in session: only COMPLETE offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record
- [ ] + LOG SESSION writes a PENDING entry (walk-in toggle, type auto-note)
- [ ] Clients: global list, at-most-one-PENDING note, anonymized toggle keeps gender + age
- [ ] Finance: SESSION + PRODUCT submit → sealed snapshot; Undo within 48h, 72h+ permanent note; commission split note
- [ ] Team: roles/capabilities, MANAGER superset note, grant/deactivate (MANAGER view)
- [ ] Mail: quiet-hours dimmed alerts, ON/OFF toggle, mark-all-read, relief events name branch + day
- [ ] Audit: actions append entries, newest first
- [ ] Profile: role bundle, logout, clock-out + logout, exit

## Theme notes

True black `#000000`, panel `#0D0B09`, edge `#2E2417`, ember `#E8A33D`,
glow `#FFE9D6`, quiet rose `#D97B6C`, sage `#9DBE8C`. Serif numerals and
titles, sans body. Dimmer scales numeral alpha 0.32 → 1.0. Quiet hours render
mail as hairline alerts instead of panels. Screens scroll; 1280x800 minimum.
