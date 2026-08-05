# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 17

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 17 resolved the HITL prototype ticket **#100** ("Prototype the Inventory screen UX") — `wayfinder:prototype`, first HITL prototype since #99. Full grilling flow: claimed #100 → falsification pass against live backend (10 findings, F1/F2 the big ones) → grilling with user (plain-language re-framing where user got lost) → outline written → committed to `prototype/0100-inventory` (commit `67e6d38`, pushed) → resolution comment posted (https://github.com/jsongalvez/company_app/issues/100#issuecomment-5193842577) → ticket closed → map #89 updated (Decisions-so-far + #100 entry, 4 fog updates, closing paragraph) → **3 backend tickets graduated + wired as sub-issues of #89: #114, #115, #116** (all AFK, unblocked — the first AFK tickets on the frontier since #113 closed).

**Next session pick:** either one of the **3 unblocked AFK backend tickets** (#114 gate-fix+payload, #115 movement-history endpoint, #116 today's-branch-day endpoint — all independent, any order) or one of the **5 remaining HITL prototypes** (#101 Finance, #103 Remittance, #104 Audit Log, #105 Reports, #106 User Management).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #100 row, Not-yet-specified incl. 4 fog changes, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#100 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/100#issuecomment-5193842577**
- Prototype asset: `docs/prototypes/0100-inventory-outline.md` (branch `prototype/0100-inventory`, commit `67e6d38`)
- New tickets: #114 (https://github.com/jsongalvez/company_app/issues/114), #115 (https://github.com/jsongalvez/company_app/issues/115), #116 (https://github.com/jsongalvez/company_app/issues/116)
- Prior handoff: `docs/agents/wayfinder-113-handoff.md`

## Session outcome

**#100 (Inventory screen prototype) — resolved + closed.** Locked D1–D11: route gate `EDIT_BRANCH_DATA` code-only + per-element split (Restock/Adjustment/Add-product = `MANAGE_PRODUCTS`; Tester/Sample/Missing/Sale = `EDIT_BRANCH_DATA`); desktop dense table / mobile cards; low-stock in-list flag; per-row action buttons; "Record sale" button → `Route.ProductSale` with mode toggle (In-session / Walk-in / Anonymous) + day picker (retroactive sales per BR:279, `EDIT_PAST_DAY`-gated); walk-in = search client → create-new-inline if missing → anonymous fallback; session screen gets "Add sale" (same form, session-bound); movement history = branch-wide log route (per-product detail cut, YAGNI); sale row shortcut; inventory payload gains price + commission. **Graduated 3 backend tickets** (the "AFK work exists again" state the map hasn't seen since #113).

## Key falsification findings (all verified against live backend code)

1. **F1 — mixed capability gates**: stock views/restock/movement = branch-scoped `MANAGE_PRODUCTS`; sale = `EDIT_BRANCH_DATA`; catalog = GLOBAL `MANAGE_PRODUCTS`. Ticket premise ("EDIT_BRANCH_DATA required") wrong.
2. **F2 — practitioner dead-end (docs vs backend contradiction)**: `business-requirements.md:34,:280` say practitioners manage inventory + deduct tester/sample; PRACTITIONER role lacks `MANAGE_PRODUCTS` → backend blocks them from ALL stock endpoints. **User confirmed docs win** → gate fix graduated to #114.
3. **F3 — no sale read-back**: `ProductSaleRoutes` is POST-only; ticket's "drill-down" has no data source → sale history deferred (fog).
4. **F4 — no movement history read-back**: movements write-only → #115.
5. **F5 — branchDayId sourcing gap**: every mutation needs today's branchDayId + inventory-card version (optimistic lock; restock exempt — server reads version itself); `SessionState` holds neither; no clean endpoint → #116.
6. **F6 — thin payload**: `BranchInventoryResponse` lacks price/commission; BR's four-column sheet (Available/Stock/Sales/Tester-Sample) NOT representable — only derived `currentStock`.
7. **F8/F9 — movement + sale validation rules mapped** (reasons, sign requirements, MISSING notes mandatory; sale mode constraints: session-linked must not have clientId/walk-in; inactive product 400; version mismatch 409).
8. **F10 — catalog is GLOBAL**; per-branch isolation applies to stock, not product definitions.

## Key decisions (recorded in full on the resolution comment + outline)

1. **D1 per-branch isolation is a stated user value** — screen renders only the selected branch's stock; no cross-branch browsing. This shaped D11 (price rides in the inventory payload so practitioners never touch the global catalog) and D4 (catalog management out of scope).
2. **D7 sale form = first client-create UI consumer** — partially absorbs the #99 F6 "no client-create anywhere" fog; needs frontend UUID generator (API wants client-supplied id) — noted for build.
3. **D7 day picker** — retroactive sales are a BR requirement (:279), not an add-on; past days gated `EDIT_PAST_DAY` (backend enforces via `BranchDayService.assertEditable`); practitioners stay today-only.
4. **D8 records a #97 gap** — session screen "Add sale" wasn't covered by the dashboard prototype; SessionDetail build must honor it.
5. **D9 movement history = branch-wide, NOT per-product** — user explicitly preferred the whole log; per-product detail route cut as YAGNI (would also be a hollow screen until #115 lands).
6. **Low-stock flags visible to all viewers** — user: "harmless" (BR:265 says "notify the Coordinator" — accepted deviation, minor).

## Patterns + learnings (cumulative across sessions)

- **User gets lost on dense multi-option questions — re-frame plainly**: two moments this session (Q2 sale entry, Q4 low-stock, Q6 row actions) where the user said "fuzzy / a bit lost / bit dense". Fix that worked: drop to a concrete picture (row-by-row walkthrough, ASCII table, "what you'd see on screen"), state the decision as ONE question with a recommendation, expand options into what-they-mean-in-practice. The falsification dump at session start was too dense — compress to the 2-3 findings that change the questions, save the rest for the outline.
- **When user says "check the docs" — the docs can contradict the backend**: F2's practitioner dead-end was exactly that. `business-requirements.md:280` ("any practitioner can deduct stock") vs backend gate. Docs won after user confirmation — the backend ticket (#114) carries the gate moves.
- **"See if we have any misalignments with the business requirements" is now an explicit user expectation** — run the locked decisions against BR before finalizing (this session: BR:279 retroactive sales + BR:288 price visibility were real catches; BR:265 low-stock coordinator-note was minor).
- **Per-branch isolation is a load-bearing product value** — user emphasized twice ("respect the isolation, this is important"). Catalog stays global-gated; inventory payload enrichment chosen over catalog access loosening for exactly this reason.
- Prior-session patterns unchanged: falsification-before-claim (HITL prototypes), facts-vs-taste split, option-framing with named costs, code-only gates (D7 pattern), pessimistic updates (ADR-0022), backend-authoritative (ADR-0007), `#93` handler-based MockEngine for future VM tests, sub-issues wiring via numeric DB id (`gh api .../issues/89/sub_issues -F sub_issue_id=<id>` — **node_id fails, must use `.id`**; verify with the sub-issues query after wiring).

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — 8 open children:

| # | Title | Type | Notes |
|---|-------|------|-------|
| 114 | Fix inventory capability gates + enrich inventory payload (graduated from #100) | task (AFK) | Backend: gate moves + `unitPrice`/`commissionAmount` in `BranchInventoryResponse`. Independent |
| 115 | Add movement history endpoint for branch inventory (graduated from #100) | task (AFK) | Backend: `GET /api/branches/{branchId}/inventory/movements?date=`; DTO exists in shared |
| 116 | Add today's-branch-day endpoint (graduated from #100) | task (AFK) | Backend: e.g. `GET /api/branches/{branchId}/today` → `{branchDayId, status}`; serves Finance/Expenses later |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked. Zero assigned.** #110 ("Fix hardcoded-month dates in MonthlyRemittanceSummaryServicePostgresTest") remains open + unassigned but NOT a child of map #89 — standalone.

## Recommended next pick

- **#114 (gate fix + payload)** — smallest of the three backend tickets, unblocks the Inventory screen build's practitioner path; #115/#116 are independent and could run in parallel by another session. All three follow the #98/#111 backend-build pattern (falsification → build → tests → k6 deferral since no frontend consumer yet).
- Otherwise **#101 (Finance)** — next HITL prototype in frontier order; same grilling pattern. **Note per screen: the #114 gate fix will land while Finance grills — re-verify gates at build time, don't trust the pre-fix state.**

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend ticket flow: /implement-style per #98/#111 pattern (falsification → build → `:backend:detekt :backend:ktlintCheck :backend:test` → /code-review parallel axes → commit on `ralph/company-app-full-build`, not pushed, per #111/#112/#113 precedent). HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes (pattern #96/#97/#99/#100/#102). Falsification-before-claim per screen.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
4. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by`; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<numeric .id>` — **verify new tickets appear in the sub-issues query**).
5. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling.

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new entry for #100 (D1–D11 + F1–F10 + graduated #114/#115/#116 + fog list; cites resolution comment URL + outline + branch).
- Not-yet-specified: **4 fog changes** — (a) NEW: sale-history read-back (F3, needs own endpoint when wanted); (b) NEW: catalog management surface (D4); (c) NEW: retroactive-day UX (can graduate with Finance); (d) UPDATED: client-create fog — partially absorbed (sale form = first consumer; SessionCreate rebuild still needs its own).
- Closing paragraph rewritten: 3 AFK backend tickets + 5 HITL prototypes; #100 gist; fog additions listed; JMH-noise warning carried forward.
- Prior fog preserved: SessionState context-model divergence (#100 D1 again chose code-only per-element gates — still the cross-cutting decision), anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, pre-BranchSelect drawer empty-state, LocalNavHostController pattern, shell-scoped poller pattern, NotificationState.clear() maintenance point, detekt-gate strategy, pushed-route TopAppBar pattern.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for #114/#115/#116 (backend builds, /implement flow per #98/#111).
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; hand untracked files to the sub-agents explicitly).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #101–#106 (feature screen prototypes; falsification-first per screen; plain-language framing for multi-option questions).
- **`/research`** — for any Ktor / Compose / Material3 API surface the prototype needs to verify.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
