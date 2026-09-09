# Desktop Report-Pack Prototype (fake data, branch `prototype/desktop-report-pack`)

Export-ready owner summaries: week/month packs, branch rollups, shareable sheets aesthetic. Part of map #755, ticket #787.

## Run

```bash
COMPANYAPP_PROTO_REPORT_PACK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and resizes / full-screens through normal OS controls.
No backend, no network: everything lives in `ReportPackFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/report-pack/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.
(Master has no DashboardPrototype gate at this base, so there was nothing to preserve.)

## Flow checklist

1. **Cover (onboarding)** — ONBOARDING account locked (zero capabilities); edits refuse until "Grant Practitioner role" simulates the MANAGE_USERS grant.
2. **Sign in** — fake directory of five users (ONBOARDING → ACCOUNTANT). Tap a row to sign the book.
3. **Branches** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR), Tondo Medical Mission (MEDICAL_MISSION); tap to switch, then pick a branch day.
4. **Duty (home)** — clock-in/out sheet, relief invites (accept/decline; accept sets the duty branch), relief requests (cover own/withdraw, cover others, reopen).
5. **Pack** — week/month period switch in the navy masthead; revenue / sessions / branches KPIs plus the one-paragraph owner summary; export bar (copy link, CSV, send — fake confirmations, each writes audit).
6. **Rollup** — per-branch completed revenue with share bars, plus in-scope day states.
7. **Sessions** — status filter chips, per-row manage drawer: PENDING → COMPLETED / NO_SHOW / CANCELLED, void/unvoid with required reason, walk-in entry form. Walk-ins refuse NO_SHOW and CANCELLED with the rule note.
8. **Clients** — global file, per-client PENDING count (at-most-one-PENDING rule note, over-limit flag), anonymized view masks PII; per-row mask/unmask writes audit and keeps gender + age.
9. **Finance** — SESSION and PRODUCT drafts → submit seals an immutable snapshot with RC number → undo-with-reason inside the 48h window (exhausted receipts stay sealed). Commission split rule sheet (pooled per branch day, equal split over on-duty staff at sold_at, illustrative).
10. **Team** — roster by home branch with role notes.
11. **Mailbox** — read/unread rows, mark read / mark all read.
12. **Audit** — every prototype mutation prepends a who/action/target/reason row.
13. **Profile** — current user, duty state, pack scope, clock-out, logout (returns to sign-in).
14. **Ribbon** — full-width OPEN / PAST / REMITTED banner under the masthead, always naming the selected day and branch plus the 04:00 Asia/Manila boundary note.

## Theme notes

Shareable-sheets aesthetic on purpose: white workbook sheets on a grey page, frozen header rows, grid rules, monospace tabular figures, navy owner masthead, monospace sheet tabs (00 Cover … 12 Profile) instead of a dashboard nav, and an export bar pinned over the shareable sheets — this variant scraps the Linear design to test whether owners prefer judging a week as a sendable pack rather than exploring a console. Period switching (WEEK = Thu–Sun, MONTH = full seed fortnight) re-scopes every KPI, rollup bar, and ledger on the spot so the human can feel pack granularity. Screenshots: run it on the target display and capture there (no checked-in binaries in this branch).
