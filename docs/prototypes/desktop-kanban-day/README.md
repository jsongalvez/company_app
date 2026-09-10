# Desktop prototype: kanban-day

Branch day kanban. The whole Branch Day is a corkboard wall: PENDING, COMPLETED,
NO_SHOW and CANCELLED ride side-by-side lanes as sticky notes, and pulling a note
lane-to-lane slides it home with drag-feel transitions. Kraft paper, masking-tape
headers, pin dots, monospace stencil labels. Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_KANBAN_DAY=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Pull my board` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked lane (empty Capability bundle note).
- [ ] Branch select: pick Sunrise Clinic, Harbor Provincial Tour, or Lingap Mission.
- [ ] Home: clock in/out; board pulse lane counts with OVER WIP flag; relief duty grant, relief request broadcast, relief invite send.
- [ ] Board: four status lanes with counts and a PENDING WIP limit of 4 (seeded at 5, so OVER WIP shows); branch filter pills; pin-a-session creator; tap a note to expand, `< Pull` / `Push >` slides it with animated transitions; walk-in notes hide NO_SHOW/CANCELLED arrows with the rule note; void with required reason; unvoid.
- [ ] Clients: global list; at-most-one-PENDING note; anonymized view toggle plus per-card anonymize (keeps gender and age).
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION (net income) and PRODUCT (price x quantity) drafts; submit seals a snapshot; undo within 48h with reason; one pre-seeded locked snapshot older than 48h; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked card with one-tap Practitioner grant; role glance blurbs.
- [ ] Mailbox: read/unread toggles, mark-all-read, relief items name branch and day; unread count rides the MAIL tab.
- [ ] Audit: clock, relief, pulls, void/unvoid, submit/undo append tape cards with reasons.
- [ ] Profile: clock out, log out, flip branch day, reset demo data.

## Theme notes

- Palette: cork `#B98A5E`/`#9A6F45` wall, cream lane paper `#F7F0DC`, masking-tape `#E8DCC0` headers; sticky notes per status (amber PENDING, mint COMPLETED, lilac NO_SHOW, rose CANCELLED); dark-rail `#4A3826` top bar with amber DAY BOARD plaque.
- Shape: 3-5dp squared notes with 1dp ink borders and red pin dots; tape strips over lane headers; pill filter chips.
- Copy: wall language ("Pull my board", "Pin a session", "OVER WIP", "the wall slides them home").
- Layout: four equal lanes in one row, each a keyed LazyColumn with `animateItem()` so pulled notes visibly slide; detail expands in place with `animateContentSize()`.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/kanban-day/*.desktop.kt` (package `...proto.kanday`), fake `KanbanDayFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
