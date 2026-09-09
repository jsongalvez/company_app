# Desktop prototype: warm-care

Warm human care. Soft rounded 16dp cards, warm neutrals (cream, sand, terracotta, sage),
mission-friendly copy, big friendly empty states. Fake data only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_WARM_CARE=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Log in warmly` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked mat (empty capability bundle note).
- [ ] Branch select: pick Sunrise Clinic, Harbor Tour, or Lingap Mission.
- [ ] Home: clock in/out at the home branch; ask for relief / invite help; relief board lists requests and invites.
- [ ] Sessions: filter PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; details expand; void with reason; unvoid.
- [ ] Clients: global list; at-most-one-PENDING note; anonymized row toggle.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION (net income) and PRODUCT (price x quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; role glance card.
- [ ] Mailbox: read/unread toggles, mark-all-read, relief items name branch and day.
- [ ] Audit: void / unvoid / submit / undo append entries with reasons.
- [ ] Profile: clock out, log out, reset demo data.

## Theme notes

- Palette: cream `#FDF6EE` background, sand `#F6EBDD` containers, terracotta `#B4552D` primary, sage `#6B7F59` secondary, honey `#D9A441` tertiary, espresso `#3E2F23` text.
- Shape: 16dp everywhere (cards, buttons, fields, chips) - nothing sharp, nothing shadowy.
- Copy: neighbor-first language ("Good morning", "Have a gentle shift", "Mailbox bliss").
- Empty states: big `( ^_^ )` card with a kind sentence and an optional action.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/warm-care/*.desktop.kt` (package `...proto.warmcare`), fake `WarmCareFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
