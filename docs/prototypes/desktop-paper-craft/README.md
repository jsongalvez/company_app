# Desktop Paper-Craft Prototype (fake data, branch `prototype/desktop-paper-craft`)

Paper craft desk for the operating day. Part of map #755, ticket #842.

## Run

```bash
COMPANYAPP_PROTO_PAPER_CRAFT=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `PaperCraftFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/paper-craft/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Onboarding (Cover)** — locked ONBOARDING cut-out (0 strings). "Try the Sessions
   sheet" is refused with a tape note; "Paste Practitioner" simulates the MANAGE_USERS grant.
2. **Login (Sheet 1)** — paper-doll row of five faces (ONBOARDING → ACCOUNTANT). Stick on to continue.
3. **Branches (Sheet 2)** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). One desk mat unrolled at a time.
4. **Desk (Sheet 3)** — duty paper with scissors-down/scissors-up, relief invites (paste in/snip away),
   relief requests (cover, peel off own).
5. **Branch day (Sheet 4)** — selected day cut-outs with cut-out/done/queued counts plus the
   completed-peso sum, shortcuts to sessions and remittance.
6. **Sessions (Sheet 5)** — whole/status filter tabs, per-cut-out unfold drawer: PENDING → COMPLETED /
   NO_SHOW / CANCELLED, snip/glue-back with required tape note, walk-in scrap form. Walk-in
   cut-outs refuse NO_SHOW and CANCELLED with the rule note.
7. **Clients (Sheet 6)** — global register, per-face PENDING count (at-most-one-PENDING rule note,
   over-rule flag), folded view hides faces; fold/unfold writes audit.
8. **Remittance (Sheet 7)** — SESSION and PRODUCT cut-outs as taped pages:
   draft → submit (freezes pasted cut-out with PC number) → peel-back
   (48h window note). Commission layer note
   (pooled per branch day, even split over scissors-down faces at sold_at).
9. **Team (Sheet 8)** — paper-doll row by home desk with standing notes, plus role notes.
10. **Mailbox (Sheet 9)** — paper pockets read/unread, open singly / open all.
11. **Audit (Sheet 10)** — every prototype move pastes face/deed/sheet/note.
12. **Profile (Sheet 11)** — current face, scissors-up, walk away (returns to login).
13. **Pasted banner** — full-width washi banner OPEN / PAST / REMITTED under the day strip,
    always naming the selected sheet plus the 04:00 Asia/Manila boundary note.

## Theme notes

Cut-paper desk voice on purpose: kraft desk board (#E3CFA6) with a darker tray rail,
stacked white cut-paper sheets with offset paste shadows and hand-cut corners,
rotating washi-tape dividers (pink/teal/yellow/blue/green with translucent stripes)
between every section, round sticker stamps for branch-day state instead of the Linear
dark canvas — this variant scraps the Linear design to test whether a tactile
scrapbook desk the staff could physically rearrange reads friendlier at close-out
than a dashboard. Remittance cut-outs are first-class taped pages with centered
cut-out headers and PC numbers so the finance close-out feels like taping down a
collage, not filling a form. The day strip stays pinned above every sheet with
per-day peso sums so the money context is never lost.
Screenshots: run it on the target display and capture there (no checked-in binaries in
this branch).
