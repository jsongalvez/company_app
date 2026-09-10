# Desktop prototype: comic-bold

Bold comic pop. Halftone-dot header strip, speech-bubble callouts (💬), action-line
alert strips (///), thick 3dp ink borders, burst stickers, POW/BAM/ZAP copy.
Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_COMIC_BOLD=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `POW ME IN!` goes to branch select.
- [ ] ONBOARDING: `Peek at the ONBOARDING origin story` shows the benched-hero panel (empty capability bundle note).
- [ ] Branch select: pick Powerville HQ, Zap Wagon Tour, or Kapow Outpost.
- [ ] Home: clock in/out at the home branch; ask for relief / invite help; relief board lists requests, invites and duty.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule strip shown; details expand; void with reason; unvoid; complete; add fresh walk-in.
- [ ] Clients: global rogues' gallery; at-most-one-PENDING strip; mask/unmask toggle.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy with action-lines strip plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION vault and PRODUCT crate drafts; submit seals a snapshot; undo within 48h with reason; commission-split strip.
- [ ] Team: users and roles incl. ONBOARDING benched row; role guide strip.
- [ ] Mailbox: unread pile with count, READ!, read-'em-all, relief items name branch and day.
- [ ] Audit: void / unvoid / complete / submit / undo / walk-in / clock events append entries with reasons.
- [ ] Profile: Branch Day master switch, clock out, reset demo, log out.

## Theme notes

- Palette: paper `#FFFFFBEF` background, burst `#FFD400`, pow-red `#E93030`, zap-blue `#1E50FF`, kapow-green `#00A651`, purple `#7B2FF7`, orange `#FF6B00`, ink `#141414` borders and sidebar.
- Shape: sharp 2–8dp rectangles with 3dp ink borders everywhere — nothing rounded-corporate.
- Motifs: halftone dot strip (Canvas circles on burst yellow) across the top, `💬` speech-bubble prefixes on subtitles and notes, `///` action-line strips on every alert/rule banner, uppercase black-weight headlines, burst stickers for statuses.
- Copy: comic-book exclamations ("POW!", "BAM!", "ZAP!", "KRAKOOM!", "WHAM!").
- Empty states: burst-soft ink-bordered card with 💥 face and a pow-red button.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/comic-bold/*.desktop.kt` (package `...proto.comicbold`), fake `ComicBoldFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
