# Desktop Print-Ledger Prototype (fake data, branch `prototype/desktop-print-ledger`)

Receipt/report ledger for the operating day. Part of map #755, ticket #776.

## Run

```bash
COMPANYAPP_PROTO_PRINT_LEDGER=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `PrintLedgerFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/print-ledger/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding** — locked ONBOARDING account (0 capabilities). "Try opening Sessions" is
   blocked; "Grant Practitioner role" simulates the MANAGE_USERS grant and continues.
2. **Login** — fake directory of five users (ONBOARDING → ACCOUNTANT). Tap to sign the book.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch the active counter.
4. **Counter (home)** — duty slip with clock-in/out, relief invites (accept/decline),
   relief requests (cover, withdraw own).
5. **Day sheet** — selected branch-day lines with line/done/open counts plus the
   completed-PHP total, shortcuts to sessions and receipts.
6. **Sessions** — status filter chips, per-line manage drawer: PENDING → COMPLETED /
   NO_SHOW / CANCELLED, void/unvoid with required reason, walk-in entry form. Walk-in
   lines refuse NO_SHOW and CANCELLED with the rule note.
7. **Clients** — global file, per-client PENDING count (at-most-one-PENDING rule note,
   over-limit flag), anonymized view masks PII; anonymize/reveal writes audit.
8. **Receipts** — SESSION and PRODUCT flows print as first-class receipt slips:
   draft → submit (seals immutable receipt with RC number) → undo-with-reason
   (48h window note). Commission split rule slip
   (pooled per branch day, equal split over clocked-in staff at sold_at).
9. **Team** — roster by home branch with role print-outs, plus role notes.
10. **Mailbox** — read/unread pigeonholes, mark read / mark all read.
11. **Audit** — every prototype mutation prepends an entry with who/action/target/reason.
12. **Profile** — current user, clock-out, logout (returns to login).
13. **Stamp banner** — full-width OPEN / PAST / REMITTED stamp under the day ledger,
    always naming the selected day plus the 04:00 Asia/Manila boundary note.

## Theme notes

Receipt/report aesthetic on purpose: warm paper sheet (#F7F4EC), white slips with square
corners, monospace tabular numerals for every figure, dashed receipt rules between
sections, and rubber-stamp outlines for branch-day state instead of the Linear dark
canvas — this variant scraps the Linear design to test whether a print-ready ledger the
staff could pin above the till reads better at close-out than a dashboard. Remittance
receipts are first-class screens with centered receipt headers and RC numbers so the
finance close-out feels like printing, not form-filling. The day ledger strip stays
pinned above every screen with per-day PHP totals so the money context is never lost.
Screenshots: run it on the target display and capture there (no checked-in binaries in
this branch).
