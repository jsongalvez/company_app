# Desktop prototype: mindmap-nav

Mindmap navigation. The Branch sits at the center of a living map and every
section of the company orbits it as a node — Sessions, Clients, Finance, Team,
Inbox, Ledger, You. Tap a node to zoom into its orbit; tap the center to come
home. Vines connect center to satellites; the focused vine glows. Fake data
only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_MINDMAP_NAV=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Enter the map →` goes to Branch select.
- [ ] ONBOARDING: preview link shows the locked grey node (empty capability bundle note).
- [ ] Branch select: pick Makati Branch (OPEN), Cebu Tour (PAST), or Davao Mission (REMITTED) — the pick becomes the map center.
- [ ] Map: center node shows Branch + day ring (green OPEN / amber PAST / violet REMITTED); 8 satellite nodes carry live badges (pending count, unread mail, open drafts).
- [ ] Home orbit: clock in/out; relief invites accept/decline/withdraw; broadcast a relief request; day figures.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in never-NO_SHOW/CANCELLED rule note; expand rows; complete / no-show / cancel (booked only) / reopen; void with reason; unvoid with reason; plot a new walk-in or booked Session.
- [ ] Clients: global list; at-most-one-PENDING note; veil down shows codes, lift the veil for names and notes.
- [ ] Branch-day banner: OPEN / PAST / REMITTED plus the 04:00 Asia/Manila boundary note; tap the day stamp to advance OPEN → PAST → REMITTED → OPEN.
- [ ] Finance: SESSION (net income) and PRODUCT (price × quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; capability glance note.
- [ ] Inbox: read/unread dots, tap toggles, mark-all-read.
- [ ] Ledger: void / unvoid / submit / undo / clock / relief entries with reasons, newest first.
- [ ] You: clock in/out, advance the day, reset demo, log out, switch Branch from the banner.

## Theme notes

- Palette: abyss `#12101D`, panel `#1B1830`, vine `#6F66B8`; one accent per node (leaf green, brook blue, bloom pink, honey amber, moss violet, coral, mist teal, chalk).
- Shape: giant rounded center medallion with day ring; pill satellites; glow = scale 1.12 + filled accent on focus; thin vine lines on Canvas, thick glowing line to the focused node.
- Type: bold sans headlines, mono orbit labels and figures, tracked kickers.
- Copy: orbit/constellation/galaxy voice ("Home orbit", "Session constellation", "Mailbox moons", "Your star").
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/mindmap-nav/*.desktop.kt` (package `...proto.mindmapnav`), local `MapFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
