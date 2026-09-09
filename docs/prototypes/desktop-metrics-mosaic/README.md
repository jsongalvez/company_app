# Desktop prototype: metrics-mosaic (ref #779)

Tile-mosaic dashboard: calm warm-paper default, KPI tiles with canvas
sparklines, clay exception tiles that surface what needs review, and dense
drill-downs one tap below. Deliberately distinct from Linear and all sibling
variants — no console chrome, no retro tube, just quiet tiles and numbers.

## Run

```bash
COMPANYAPP_PROTO_METRICS_MOSAIC=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/metrics-mosaic/` local `MmRepo`). No ApiClient,
no Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Login as ana (Practitioner) / ben (Coordinator) / cara (MANAGER) / dan (Accountant), filter narrows list
- [ ] Login as eli (ONBOARDING) → locked screen, empty-bundle note
- [ ] Branch select: MAKATI CLINIC / BGC CLINIC / CEBU-TOUR PROVINCIAL_TOUR / TONDO-MISSION MEDICAL_MISSION, each with 7-day sparkline
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note + per-state edit rule
- [ ] Mosaic home: Sessions / Revenue / No-show KPI tiles with sparklines; PENDING / EXCEPTIONS / UNDO WINDOW tiles drill down
- [ ] Home: clock in/out; relief duty claim; invite accept/decline; broadcast request (one-live-per-date note) + withdraw
- [ ] Sessions: per-branch list, filter, exceptions-only toggle, status stepper (Complete / No-show / Cancel)
- [ ] Walk-in session: only COMPLETE offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record
- [ ] + Log session writes a PENDING entry (booked/walk-in toggle)
- [ ] Clients: global list, at-most-one-PENDING note (+OVER LIMIT flag), anonymized-view toggle (gender+age kept)
- [ ] Finance: SESSION + PRODUCT draft/submit → sealed snapshot; Undo within 48h, 80h snapshot permanent note; commission split note
- [ ] Team: roles/capabilities, MANAGER superset note, ONBOARDING grant/deactivate (MANAGER view)
- [ ] Mail: read/unread, mark-all-read, relief events name branch + day
- [ ] Audit: actions append entries, newest first
- [ ] Profile: role bundle, logout, clock-out + logout

## Theme notes

Paper `#F7F3EC`, panel `#FFFDF8`, edge `#E3DCCC`, ink `#211D15`,
teal `#0E7C6B`, amber `#B7791F`, clay `#B3402A` (exceptions), plum `#6C4FA1`
(no-show), moss `#5C7A2E` (walk-in). Rounded 14dp tiles, soft shadow, 11sp
letterspaced section caps. Sparklines drawn on Canvas with area fill.
Screens scroll; 1280x800 minimum.
