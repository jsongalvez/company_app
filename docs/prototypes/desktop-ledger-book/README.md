# Desktop Ledger-Book Prototype (fake data, branch `prototype/desktop-ledger-book`)

Bound ledger book for the operating day. Part of map #755, ticket #835.

## Run

```bash
COMPANYAPP_PROTO_LEDGER_BOOK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `LedgerBookFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/ledger-book/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding (Preface)** — locked ONBOARDING hand (0 capabilities). "Attempt the Sessions
   chapter" is refused with a writ; "Inscribe Practitioner" simulates the MANAGE_USERS grant.
2. **Login (Ch. I)** — false directory of five hands (ONBOARDING → ACCOUNTANT). Sign to continue.
3. **Branches (Ch. II)** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). One house kept at a time.
4. **Counter (Ch. III)** — duty slate with pen-down/pen-up, relief invites (accept/decline),
   relief requests (cover, strike own).
5. **Day folio (Ch. IV)** — selected branch-day entries with entry/done/await counts plus the
   completed-peso sum, shortcuts to sessions and remittance.
6. **Sessions (Ch. V)** — standing filter tabs, per-entry amend drawer: PENDING → COMPLETED /
   NO_SHOW / CANCELLED, void/unvoid with required writ, walk-in margin form. Walk-in
   entries refuse NO_SHOW and CANCELLED with the rule note.
7. **Clients (Ch. VI)** — global register, per-hand PENDING count (at-most-one-PENDING rule note,
   over-rule flag), veiled view masks faces; veil/unveil writes audit.
8. **Remittance (Ch. VII)** — SESSION and PRODUCT folios as stamped pages:
   draft → submit (seals immutable folio with LB number) → undo-with-writ
   (48h window note). Commission apportion note
   (pooled per branch day, even split over pen-down hands at sold_at).
9. **Team (Ch. VIII)** — roster by home house with standing notes, plus role notes.
10. **Mailbox (Ch. IX)** — read/unread pigeon-holes, read singly / read all.
11. **Audit (Ch. X)** — every prototype mutation prepends hand/deed/folio/writ.
12. **Profile (Ch. XI)** — current hand, pen-up, depart (returns to login).
13. **Stamp banner** — full-width OPEN / PAST / REMITTED stamp under the ribbon,
    always naming the selected folio plus the 04:00 Asia/Manila boundary note.

## Theme notes

Bound-book voice on purpose: aged cream folio (#FBF3DF), leather spine rail (#4A3226),
sepia fountain-pen serif for every heading and figure, blue ruled lines between entries,
a red margin hairline under the stamp banner, and double-ring rubber stamps for
branch-day state instead of the Linear dark canvas — this variant scraps the Linear
design to test whether a handwritten chapter book the staff could shelve above the till
reads warmer at close-out than a dashboard. Remittance folios are first-class stamped
pages with centered folio headers and LB numbers so the finance close-out feels like
sealing a page, not filling a form. The ribbon of day markers stays pinned above every
chapter with per-day peso sums so the money context is never lost.
Screenshots: run it on the target display and capture there (no checked-in binaries in
this branch).
