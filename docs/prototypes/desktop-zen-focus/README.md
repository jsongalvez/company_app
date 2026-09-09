# Desktop prototype: zen-focus

Zen single-tasking. One session on screen, everything else hidden: a narrow
paper column, serif headings, hairline rules instead of cards and badges,
calm progress words ("arrived · awaiting begin") instead of counts. Fake data
only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_ZEN_FOCUS=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Enter quietly` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked mat (empty capability bundle note).
- [ ] Branch select: pick Sunrise Clinic (OPEN), Harbor Tour (PAST), or Lingap Mission (REMITTED).
- [ ] Stillness (home): clock in; exactly one task shown with `Attend this session`; relief duty note, invites accept/decline, ask-for-relief form.
- [ ] Session focus: attending opens the single session alone; lay it down to return.
- [ ] Sessions: filter pending / completed / no-show / cancelled; walk-in rule note shown; expand rows; void with reason; unvoid with reason.
- [ ] Clients: global list; at-most-one-PENDING note; codes veiled by default, tap to reveal names.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; move the day from Profile.
- [ ] Finance: SESSION (net income) and PRODUCT (price x quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; capability glance card.
- [ ] Mailbox: read/unread dots, tap toggles, mark-all-read.
- [ ] Audit: void / unvoid / submit / undo / clock / relief entries with reasons.
- [ ] Profile: clock in/out, move the day, reset demo, log out.

## Theme notes

- Palette: paper `#F6F3EC` background, `#FDFBF6` sheets, stone `#EAE4D4` containers, moss `#5F6F52` primary, clay `#A2673F` accent, ink `#2B2620` text.
- Shape: hairline rules, 12-14dp quiet sheets, no elevation, no badges, no counts in navigation.
- Type: serif headings (`FontFamily.Serif`), small quiet meta lines, generous 640dp-centered column.
- Copy: stillness language ("The mat is clear", "Lay it down", "Nothing else is on screen").
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/zen-focus/*.desktop.kt` (package `...proto.zenfocus`), fake `ZenFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
