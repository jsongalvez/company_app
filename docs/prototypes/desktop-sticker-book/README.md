# Desktop prototype: sticker-book

Sticker reward book. Every shift earns a sticker: crews finish missions, paste badges onto
branch pride pages, and seal remittance pages with star-burst seals.

## Run

```bash
COMPANYAPP_PROTO_STICKER_BOOK=true ./gradlew :composeApp:run
```

Working directory must be the repo root (Gradle `workingDir` is the root). Window opens at
1280x800 and can go full-screen. Without the env var the app boots the real `App()`.

Targets ticket #839, branched off master `23c20212db46c2e5570bf6490b7bdf12d5713a7c`.
Fake data only: `StickerBookFakeRepo` in `proto/sticker-book/`, no `ApiClient`, no Ktor,
no backend import. Desktop-only files, additive `Main.kt` gate.

## Flow checklist

- Login: any tap signs in as Mara Villanueva (MANAGER). "I am new here" opens the
  ONBOARDING locked page (ONBOARDING crew cannot clock in until a MANAGER grants the badge).
- Branch select: four pride pages (Sunbeam Strip, Coral Bay, Meadow Walk, Star Hill) with
  gross-vs-target meters, crew counts, and the Branch Day seal.
- HOME: clock in/out, Branch Day banner with OPEN/PAST/REMITTED switcher and the 04:00
  Asia/Manila boundary note, relief duty take/drop, relief request + invite forms, and four
  missions (clock-in, session COMPLETED, remittance submitted, mailbox zero) each earning a
  sticker via STICK IT.
- SESSIONS: status filter pills, per-session detail, PENDING/COMPLETED/NO_SHOW/CANCELLED
  transitions, walk-in rule note (no NO_SHOW/CANCELLED), void with reason + unvoid, walk-in
  booking as PENDING.
- CLIENTS: global list (not branch-filtered), anonymized code-only toggle, at-most-one-PENDING
  note per client.
- FINANCE: SESSION + PRODUCT pages for the current branch, SEAL (submit) writes a snapshot,
  PEEL (undo) needs a reason and stands in for the 48h undo window, plus the commission split
  note (Branch share vs crew pool).
- TEAM: role badges (Practitioner/Coordinator/MANAGER/Accountant/ONBOARDING), on-shift flags,
  GRANT PRACTITIONER moves Rina Aquino out of ONBOARDING, capability note per role.
- MAIL: mailbox letters flip read/unread on tap, READ THEM ALL clears the unread count.
- AUDIT: ribbon of every paste/peel, newest first, plus RESET DEMO BOOK.
- ALBUM + profile: earned sticker star-bursts, per-branch pride (completed sessions meter,
  sealed remittance count), profile card with CLOCK OUT and LOGOUT + CLOSE BOOK.

## Theme notes

Scrapped the Linear look for a sticker-album feel: warm paper background, cards with thick
white sticker edges and soft shadows, star-burst badges for anything earned, sunny
coral/sun/leaf/sky/grape palette, black kickers for section labels. Tabs are sticker pills
in a kraft-paper rail with an unread-count badge on MAIL.
