# Desktop Owner-Desk Prototype (fake data, branch `prototype/desktop-owner-desk`)

The owner's private desk: money, people, risk in one walnut-and-brass study. Part of map #755, ticket #818.

## Run

```bash
COMPANYAPP_PROTO_OWNER_DESK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `OwnerFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/owner-desk/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Welcome (Foyer)** — locked ONBOARDING account (0 capabilities). "Try a drawer" is
   blocked; "Grant Practitioner" simulates the MANAGE_USERS grant and continues.
2. **Sign in** — fake directory of five users (ONBOARDING → ACCOUNTANT). ONBOARDING
   stays unselectable for entry; everyone else signs the book and clocks in.
3. **Branch** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch, then take the seat.
4. **Desk (home)** — money / people / risk lane stats, clock in/out, relief invites
   (Yes/No), relief requests (Allow/Deny, pull back own, ask another branch),
   shortcut to a new folio.
5. **New folio (3 leaves)** — leaf 1 who (returning guest or inked label),
   leaf 2 care menu pick + walk-in toggle, leaf 3 pressed seal (O-044…).
   Opens a PENDING session + client + daybook + pigeonhole note.
6. **Folio book (sessions)** — status filter chips, per-folio ruling drawer:
   PENDING → COMPLETED / NO_SHOW / CANCELLED, void/unvoid with required reason.
   Walk-in folios refuse NO_SHOW and CANCELLED with the rule note.
7. **Guest index (clients)** — global list, per-guest open-folio count
   (at-most-one-PENDING rule note), masked-by-default cover lift
   (gender + age kept).
8. **Vault (finance)** — SESSION and PRODUCT drawers: draft −/+500 → submit (presses
   immutable seal BD-*) → undo-with-reason (48h window note). Commission
   split rule ledger (pooled per branch day, even split over clocked-in hands,
   relief paid from this house's vault).
9. **Hands (team)** — roles with headcount and posting, never personal detail;
   ONBOARDING row stays a locked grant note. YOU never named — the desk is the owner's.
10. **Pigeonhole (notifications)** — read/unread mailbox, file one or file all.
    Unread count badges the drawer row.
11. **Daybook (audit)** — every prototype mutation entered: who/action/target/reason.
12. **Study (profile)** — current user, clock in/out, log out (returns to sign in,
    locks the drawers).
13. **Ribbon + lanes** — full-width OPEN / PAST / REMITTED switcher with the
    04:00 Asia/Manila boundary note, plus the money / people / risk lane strip,
    always visible under the nameplate.

## Theme notes

Owner-desk walnut-and-brass study: near-black walnut `#17100A` → `#2A1D12` desk,
dark rail, engraved brass `#C9A227` nameplate, cream ledger paper `#F7F0DC` with a
brass spine and wax-seal stamps, serif headings, money green / people indigo /
risk oxblood lanes. Left drawer nav grouped Desk / Money / People / Risk / House —
this variant scraps the Linear design for a single-owner private desk.
Screenshots: run it and judge — no checked-in images in the prototype branch.
