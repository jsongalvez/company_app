# Desktop prototype: skeuomorph-desk

Wooden desk skeuomorph. Walnut plank backdrop, oxblood leather desk pad, cream
ledger pages with page-stack edges, brass nameplates and push-buttons, red
rubber stamps, serif ledger type. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_SKEUOMORPH_DESK=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any name/word works, `Press the signet` opens the branch drawer.
- [ ] ONBOARDING: `New hand?` card shows the locked card (empty capability bundle note).
- [ ] Branch select: sit at Mahogany HQ, Cedar Tour, or Teak Mission, then open the ledger.
- [ ] Desk (home): clock in/out; ask relief (broadcast); invite help; relief slate with grant/cut.
- [ ] Ledger (sessions): filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule enforced (walk-ins refuse NO_SHOW/CANCELLED with a filed note); open file; void with reason; unvoid; complete; enter walk-in.
- [ ] Files (clients): global cards; at-most-one-PENDING note; anonymize/break-seal toggle.
- [ ] Branch-day ribbon: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; lever on the Study tab.
- [ ] Till (finance): SESSION + PRODUCT drafts; filing seals a snapshot folio; undo within 48h with reason; commission-split note.
- [ ] Staff (team): users and roles incl. ONBOARDING locked row; role glance card.
- [ ] Post (mailbox): NEW stamp, open & file, mark-all-read, relief items name branch and day.
- [ ] Day book (audit): clock / seat / void / unvoid / complete / refuse / file / undo / walk-in / relief entries with reasons.
- [ ] Study (profile): branch-day lever, clock out, log out.

## Theme notes

- Palette: walnut `#3E2A1C` planks, leather `#6B2D26` pad, paper `#F5EBD0` pages with `#D9C491` stack edge, brass `#C9A227` plates/buttons, stamp red `#B3271E`, felt green `#1E4D3B` for OK stamps, sepia ink `#2A1E14`.
- Shape: rounded pages (10-18dp) over square-ish folder tabs; stamps tilted -4deg with 2dp ink borders; brass buttons with a 3dp lower lip for a pressed feel.
- Type: serif everywhere, big ledger headlines (34-64sp), small-caps section books (`THE LEDGER`), bold stamped labels.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/skeuomorph-desk/*.desktop.kt` (package `...proto.skeuomorphdesk`), fake `SkeuomorphDeskFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
