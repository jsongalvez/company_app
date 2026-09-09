# Desktop prototype: pastel-play

Pastel clinic play. Bubbly 28dp candy cards, milk-pink canvas, sticker chips,
kaomoji empty states, toy-box copy. Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_PASTEL_PLAY=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Bounce me in!` goes to branch select.
- [ ] ONBOARDING: `Peek at the ONBOARDING welcome` shows the locked toy-box mat (empty capability bundle note).
- [ ] Branch select: pick Blush Clinic, Mint Tour, or Lilac Mission.
- [ ] Home: clock in/out at the home branch; ask for relief / invite help; relief board lists requests, invites and duty.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; details expand; void with reason; unvoid; complete; add fresh walk-in.
- [ ] Clients: global sticker book; at-most-one-PENDING note; anonymize/reveal toggle.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION jar and PRODUCT shelf drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; role glance card.
- [ ] Mailbox: unread pile with count, hug-to-read, mark-all-read, relief items name branch and day.
- [ ] Audit: void / unvoid / complete / submit / undo / walk-in / clock events append entries with reasons.
- [ ] Profile: Branch Day remote control, clock out, reset demo, log out.

## Theme notes

- Palette: milk `#FFF6FA` background, cotton `#FBE9F1` containers, candy `#F0508C` primary, mint `#1FA97C` secondary, sky `#4A9DFF` tertiary, lilac `#8B7CF6` + lemon `#E0A400` + peach `#FF9D7A` accents, plum ink `#43283C` text.
- Shape: bubbly 20-32dp radii everywhere (stickers 20dp, cards 28dp, empty states 32dp) - nothing sharp, nothing corporate.
- Copy: playground language ("Bounce me in!", "Sticker earned", "Hug (read)", "Mailbox zero, high five!").
- Empty states: big kaomoji card on lilac with a kind sentence and an optional candy button.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/pastel-play/*.desktop.kt` (package `...proto.pastelplay`), fake `PastelPlayFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
