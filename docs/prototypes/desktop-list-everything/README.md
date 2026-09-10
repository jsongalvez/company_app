# Desktop List-Everything Prototype (fake data, branch `prototype/desktop-list-everything`)

Universal outliner: the whole branch reads as one nested outline — every flow is a
collapsible node, every record a bullet leaf. Part of map #755, ticket #854.

## Run

```bash
COMPANYAPP_PROTO_LIST_EVERYTHING=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `ListEverythingFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/list-everything/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.
(Base master has no DashboardPrototype gate, so there was none to preserve.)

## Flow checklist

1. **Outline chrome** — header shows identity breadcrumb (user · branch · day), a
   filter box (forces nodes open, filters leaves), Expand all / Collapse all, Exit.
2. **Onboarding** — kiosk finds the ONBOARDING user locked with zero capabilities;
   "Grant Practitioner" simulates the MANAGE_USERS grant, noted in the outline.
3. **Login** — fake five-person roster (ONBOARDING → ACCOUNTANT), tap to sign in.
4. **Branches** — QC Central (Clinic), Laguna Tour Stop 3 (Provincial tour),
   Tondo Medical Mission (Medical mission); switching re-scopes home and the strip.
5. **Home** — clock in/out per user, relief invites (accept/decline), cover asks
   (grant + withdraw/deny), all as nested nodes with pending counts.
6. **Sessions** — grouped PENDING / COMPLETED / NO_SHOW / CANCELLED, per-session
   node with Complete / No-show / Cancel, void… (reason required) / unvoid, plus a
   walk-in booking leaf. Walk-ins refuse NO_SHOW and CANCELLED with the house-rule note.
7. **Clients** — global list with gender + age + pending count (at-most-one-PENDING
   note), anonymize/reveal per client.
8. **Branch day** — node plus the full-width OPEN / PAST / REMITTED strip with the
   04:00 Asia/Manila boundary note; Advance cycles the state.
9. **Finance** — SESSION and PRODUCT remittance nodes: draft → submit (seals immutable
   snapshot id) → undo… (reason required, 48h note). Commission split node with the
   pooled-per-branch-day note and per-staff mark-paid/reopen.
10. **Notifications** — read/unread bullets, per-notice toggle, mark-all-read leaf.
11. **Audit log** — every mutation prepends who/action/target/reason; live-filtered
    by the header box like everything else.
12. **Profile** — current user, clock toggle, logout (returns to the Login outline).

## Theme notes

Index-card outliner on warm paper: every section a bordered level-0 card with a
violet caret, children hanging off a 22dp-per-level indent, status as pastel pills,
code-face notes for house rules. Scraps the Linear design to test whether a single
collapse-expand-everything tree (plus filter) navigates faster than a screen-per-flow
rail. Screenshots: run it on the target display and capture there (no checked-in
binaries in this branch).
