# Desktop prototype: forest-calm (ref #807)

Forest floor — quiet nature operations on warm paper. Moss greens, bark
neutrals, dotted-trail dividers, serif grove names. Login as a walker, pick a
grove, then work a rounded rail: clearing, sessions, clients, finance,
keepers, nest, rings, profile. Deliberately distinct from Linear and all
sibling variants — soft, organic, daylight.

## Run

```bash
COMPANYAPP_PROTO_FOREST_CALM=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Fake data only (`proto/forest-calm/` local `ForestCalmRepo`). No ApiClient,
no Ktor, no backend, no network. Window opens at 1280x800, full-screen capable.

## Flow checklist

- [ ] Login as ana (Practitioner) / ben (Coordinator) / cara (MANAGER) / dan (Accountant)
- [ ] Login as eli (ONBOARDING) → trailhead hold, empty-bundle note
- [ ] Branch select: Makati Grove / BGC Clearing / Cebu Trail / Tondo Understory
- [ ] Branch-day banner OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary note + per-state edit rule
- [ ] Home: clock in/out; relief duty list; broadcast request (one-live-per-date note) + withdraw; invites accept/decline
- [ ] Sessions: per-branch list, status stepper (Complete / No-show / Cancel)
- [ ] Walk-in session: only COMPLETE offered; NO_SHOW/CANCELLED rule note shown
- [ ] Void with required reason; unvoid restores record
- [ ] + LOG SESSION writes a PENDING entry (walk-in toggle)
- [ ] Clients: global list, at-most-one-PENDING note, anonymized-view toggle (gender+age kept)
- [ ] Finance: SESSION + PRODUCT draft/submit → sealed snapshot; Undo within 48h, 72h+ petrified note; commission split note
- [ ] Team: roles/capabilities, MANAGER superset note, ONBOARDING grant affordance (MANAGER view)
- [ ] Mail: read/unread, mark-all-read, relief events name branch + day
- [ ] Audit: actions append entries, newest first
- [ ] Profile: role bundle, logout, clock-out + logout, leave forest

## Theme notes

Paper `#F3EFE2`, card `#FAF7EC`, moss `#3E5C3A`, deep moss `#2A4227`,
fern `#6B8F5E`, bark `#4A3B2C`, barkwash `#7A6A53`, ambercap `#B07A2A`,
berry `#9C4A3C`. Serif display, sans body. Status pills carry state color
(PENDING amber, COMPLETED green, NO_SHOW barkwash, CANCELLED berry, sealed
moss). Trail dividers are dotted pebble rows. Screens scroll; 1280x800 minimum.
