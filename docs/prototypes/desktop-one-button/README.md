# Desktop prototype: one-button

One big button. Every screen shows exactly one giant primary action — the single
next step — with everything else quiet above it, behind the button. Radical focus:
no rails of equal-weight actions, no dashboards competing for attention.
Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_ONE_BUTTON=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, giant ENTER goes to branch select.
- [ ] ONBOARDING: `Peek at the ONBOARDING door` shows the locked waiting room (empty capability bundle note); BACK returns.
- [ ] Branch select: pick Solo Clinic, Single-File Tour, or One-Room Mission — one press each.
- [ ] Home: hero is CLOCK IN / CLOCK OUT; relief board lists duty/request/invite with branch + day; log duty / ask cover / offer help.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; tap a row to expand; void with reason dialog; unvoid; complete; add walk-in; hero finishes whoever is next (or seats a walk-in when clear).
- [ ] Clients: global cards; at-most-one-PENDING note; anonymize/reveal toggle; hero adds a client.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION and PRODUCT trays; submit seals a snapshot; undo within 48h with reason; older snapshot stays SEALED; commission-split 70/30 note; hero submits the next draft (or undoes, or opens a fresh envelope).
- [ ] Team: users and roles incl. ONBOARDING locked row; role glance card; hero sends a relief invite.
- [ ] Mailbox: unread dots, read buttons; hero marks all read at once.
- [ ] Audit: void / unvoid / complete / submit / undo / walk-in / clock / summary events append entries with reasons; hero stamps a summary line.
- [ ] Profile: Branch Day remote control, clock in/out, reset demo, logout (quiet buttons; hero is LOG OUT).

## Theme notes

- Palette: near-black stage `#0B0B0D`, panel `#141417`, card `#1B1B1F`, raised `#232329`; the hero button is the only saturated element — amber `#FFB020` on dark ink. Status accents (green/red/blue) appear only as small quiet tags.
- Shape: big radii (18-36dp) — the button is a stage, cards are pebbles.
- Copy: single-focus language ("One screen. One job. One press.", "The pile.", "The receipt roll.", "Everything else lives above — the button is only the next step.").
- Frame: branch-day banner on top, quiet screen strip under it, scrolling content, exactly one 96dp-minimum hero button pinned at the bottom of every screen.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/one-button/*.desktop.kt` (package `...proto.onebutton`), fake `OneButtonFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
