# Desktop prototype: branch-compare

Owner multi-branch side-by-side desk. Three branch columns (day state, gross vs
target, staffing) on one ledger board; tap a column to dive into single-branch
detail. Ink-navy desk, paper panels, one strong color per branch. Fake data
only - no network calls.

## Run

```bash
COMPANYAPP_PROTO_BRANCH_COMPARE=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Open compare desk` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked card (empty capability bundle note).
- [ ] Branch select: pick Sunrise Clinic, Harbor Provincial Tour, or Lingap Medical Mission.
- [ ] COMPARE home: three columns show day ribbon, gross vs target bar, on-shift and pending chips; tap a column to dive.
- [ ] Dive: clock in/out at the dive branch; relief list with grant/fold; broadcast a relief request; send a relief invite with a note; session mix preview.
- [ ] Sessions: branch picker plus All / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/CANCELLED buttons on walk-ins); status jumps; void with required reason; unvoid; book a PENDING session.
- [ ] Clients: global list; at-most-one-PENDING note; anonymized view toggle plus per-card anonymize (keeps gender and age).
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION (gross-derived) and PRODUCT drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked card with one-tap Practitioner grant; role glance blurbs.
- [ ] Mailbox: read/unread toggles, mark-all-read; unread count rides the MAIL tab.
- [ ] Audit: clock, relief, void/unvoid, submit/undo append entries with reasons.
- [ ] Profile: clock in/out, log out, flip dive-branch day, reset demo data.

## Theme notes

- Palette: ink navy `#141B2E` desk, deep ink `#0D1322` rails, paper `#F6F2E9` panels; branch colors Sunrise amber `#E09A1F`, Harbor teal `#14908A`, Lingap plum `#8A4E9B`; day ribbons green/slate/gold.
- Shape: 16dp paper panels with a 4-6dp branch-color top rule; pill chips per status; gross progress bars per column.
- Copy: ledger language ("Side-by-side today", "Dive", "Seal snapshot").
- Layout: three-column compare row on top, single-column dive stack below; detail tabs stay single-column scrolled.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/branch-compare/*.desktop.kt` (package `...proto.branchcompare`), fake `BranchCompareFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
