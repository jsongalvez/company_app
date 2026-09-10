# Desktop prototype: ribbon-tabs

Office ribbon. Every capability filed under a tabbed command ribbon: a blue
title bar with quick access (branch, mail, shift), a dark tab strip, a grouped
command bar per tab, a branch-day banner, and a status bar ledger line.
Square Office chrome, grouped tools, no Linear styling. Fake data only — no
network calls.

## Run

```bash
COMPANYAPP_PROTO_RIBBON_TABS=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Sign in ▸` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING gate` shows the locked notice (empty capability bundle).
- [ ] Branch select: pick Sunrise Clinic (OPEN), Harbor Tour (PAST), or Lingap Mission (REMITTED).
- [ ] Home tab: clock in/out; relief invites accept/decline, ask-for-relief form; day in figures.
- [ ] Sessions tab: ribbon Views filter ALL / PEND / DONE / N-SHOW / CXLD; walk-in rule note (walk-ins never take NO_SHOW/CANCELLED — buttons disabled); expand rows; complete / no-show / cancel; void / unvoid with reason.
- [ ] Clients tab: global list; at-most-one-PENDING note; anonymized codes by default, veil toggle in the ribbon lifts full names.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; move the day from Profile.
- [ ] Finance tab: SESSION (net income) and PRODUCT (price × quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note (House 60 / Practitioner 40).
- [ ] Team tab: users and roles incl. ONBOARDING locked row; capability glance note.
- [ ] Mail tab: read/unread marks, tap toggles, mark-all-read in the ribbon.
- [ ] Audit tab: void / unvoid / submit / undo / clock / relief entries with reasons.
- [ ] Profile tab: clock in/out, move the day, reset demo, log out, switch branch.

## Theme notes

- Palette: Office ribbon blue `#2B579A` / dark `#1F4173`, ribbon bar `#F3F2F1`, group white, ink `#201F1E`; status stamps green / amber / red.
- Shape: square corners, 1px hairline borders, uppercase group captions, stamp outlines, zero elevation.
- Type: bold tab strip, tracked-uppercase kickers, bold card titles, small soft notes.
- Copy: Office voice ("File ▸ Open ▸ Branch", "Clipboard group", "Walk-in intake", "Remittance bench", "Danger drawer").
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/ribbon-tabs/*.desktop.kt` (package `...proto.ribbontabs`), fake `RibbonFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
