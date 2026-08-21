# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 18

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 18 resolved the AFK backend task **#114** ("Fix inventory capability gates + enrich inventory payload") — `wayfinder:task`, graduated from #100, the first of the three backend tickets (#114/#115/#116). Full flow: claimed #114 → falsification against live backend (found the ticket's premise WRONG — the 5-segment sub-routes were completely ungated, not MANAGE_PRODUCTS-gated) → build (per-route before-filters + payload enrichment) → `:backend:detekt :backend:ktlintCheck :backend:test` green → /code-review (parallel Standards + Spec; both PASS after 3 spec fixes + 1 standards fix) → committed `46fdf15` on `ralph/company-app-full-build` (**not pushed**, #111/#112/#113 precedent) → resolution comment (https://github.com/jsongalvez/company_app/issues/114#issuecomment-5194513806) → ticket closed → map #89 updated (Decisions-so-far + #114 entry, closing paragraph rewritten).

**Next session pick:** either one of the **2 remaining unblocked AFK backend tickets** (#115 movement-history endpoint, #116 today's-branch-day endpoint — independent, any order) or one of the **5 remaining HITL prototypes** (#101 Finance, #103 Remittance, #104 Audit Log, #105 Reports, #106 User Management).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #114 row, Not-yet-specified, Out-of-scope)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#114 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/114#issuecomment-5194513806**
- New backend state: `BranchInventoryRoutes.kt` (4 per-route before-filters), `BranchInventoryAuthzTest.kt` (17 route-level authz tests), `BranchInventoryResponse` DTO (unitPrice + commissionAmount)
- Prior handoff: `docs/agents/wayfinder-100-handoff.md` (session 17)

## Session outcome

**#114 (inventory gates + payload) — resolved + closed.** Four per-route branch-scoped before-filters replacing the single 4-segment one: GET inventory + low-stock → `EDIT_BRANCH_DATA`; movement reason-dependent (TESTER/SAMPLE/MISSING → `EDIT_BRANCH_DATA`, ADJUSTMENT → `MANAGE_PRODUCTS`); restock + ensureCard stay `MANAGE_PRODUCTS` (BR:240). Shared `parseMovementReason()` (gate + handler, single source of truth; uniform 400 for invalid reasons). `BranchInventoryResponse` gains `unitPrice` + `commissionAmount` (D11) from the existing product join. First negative-authz route tests in the repo (17 cases, `X-Test-User` header harness).

## Key falsification findings (all verified against live backend code)

1. **F1-corrected — the gates were NOT "MANAGE_PRODUCTS on stock views/restock/movement"; the 5-segment sub-routes had NO gate at all.** Javalin `before` filters match exact path patterns (only `/path/*` cascades — `backend/docs/javalin-framework.md`; codebase pattern: SessionRoutes/CompensationRoutes/ExpenseRoutes register per-sub-path filters). The single `before("/api/branches/{branchId}/inventory")` (4 segments) matched only `GET/POST .../inventory`. `GET .../low-stock`, `POST .../restock`, `POST .../movement` (5 segments) were **completely ungated** — any authenticated user could restock/deduct/adjust. The ticket's premise was wrong; the fix ADDS gates where the ticket said "move". Security-hole closure, not just a gate move.
2. **F2-verified**: service layer has zero capability checks (all HTTP-layer per CapabilityFilter design) — no double-gating to remove.
3. **F6-verified**: `BranchInventoryWithProduct` carried only `productName`; the `ProductTable` join already existed in both queries — enrichment was a row-mapping change only.
4. **F10-verified**: catalog stays GLOBAL `MANAGE_PRODUCTS` (`ProductRoutes.kt:26`) — untouched.

## Key decisions (recorded in full on the resolution comment)

1. **Per-route before-filters, not one filter per path prefix** — Javalin exact-pattern matching makes a single prefix filter a silent hole (that's literally what the old code was).
2. **Reason-dependent movement gate reads the body in the before-filter** (ProductSaleRoutes precedent — body is read twice; Javalin caches it). Invalid/unallowed reasons → uniform 400 in the gate, matching the handler's message — capability-dependent status leaks impossible.
3. **`parseMovementReason()` extracted and shared** between gate and handler — /code-review Standards finding (duplication/drift risk), fixed.
4. **D11 as String/toPlainString** matching `ProductResponse` convention; model carries `BigDecimal`, DTO serializes as String.
5. **`@Suppress("LongMethod")` on register()** (61 lines vs 60 max) — SessionRoutes precedent.
6. **Test harness identity switch via `X-Test-User` header** — mirrors RouteValidationTest's `createApp` but per-request user; no JWT dance. First negative-authz route tests in the suite.
7. **trackOwned BEFORE requests** (not after assertions) — the failed-run leak incident: rows created by a passing-midway request weren't tracked, teardown FK chain broke, rows persisted in test DB permanently. Pre-commit cleanliness check caught it; `bash scripts/clean-test-db.sh` cleared the residue.

## Patterns + learnings (cumulative across sessions)

- **Javalin `before` filters are exact-pattern matched** — `{param}` matches ONE segment; no cascade without `*`. Before writing a "parent path" filter, enumerate the actual sub-routes and register one filter per path (SessionRoutes pattern). The #100 falsification's gate map (F1) was itself wrong on this — it read the single filter as covering everything.
- **First negative-authz route tests**: harness = RouteValidationTest's `createApp` clone + `ctx.header("X-Test-User")` attribute injection. 403s come from `ForbiddenException` (CapabilityService) mapped in the harness — the route objects need no JWT plumbing.
- **Test-data leak class bug**: `trackOwned` after an assertion = untracked rows on failure. Track before the request. If the DB is ever polluted: `bash scripts/clean-test-db.sh` (pre-commit runs `scripts/check-test-cleanliness.sh`).
- Prior-session patterns unchanged: falsification-before-claim (HITL prototypes), facts-vs-taste split, option-framing with named costs, code-only gates, pessimistic updates (ADR-0022), backend-authoritative (ADR-0007), `#93` handler-based MockEngine for future VM tests, sub-issues wiring via numeric DB id, JMH-noise caution, unpushed commits on `ralph/company-app-full-build`.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues` — 7 open children:

| # | Title | Type | Notes |
|---|-------|------|-------|
| 115 | Add movement history endpoint for branch inventory (graduated from #100) | task (AFK) | Backend: `GET /api/branches/{branchId}/inventory/movements?date=` → `InventoryMovementResponse[]` (DTO exists in shared). Gate per #114 pattern: EDIT_BRANCH_DATA read? Verify — movements are TESTER/SAMPLE/MISSING/ADJUSTMENT territory |
| 116 | Add today's-branch-day endpoint (graduated from #100) | task (AFK) | Backend: e.g. `GET /api/branches/{branchId}/today` → `{branchDayId, status}`; serves Finance/Expenses later |
| 101 | Prototype the Finance screen UX (P&L, compensation, expenses) | prototype | HITL grilling |
| 103 | Prototype the Remittance screen UX (drafts, submit, detail) | prototype | HITL grilling |
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype | HITL grilling |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype | HITL grilling |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype | HITL grilling |

**Zero blocked. Zero assigned.** #110 ("Fix hardcoded-month dates in MonthlyRemittanceSummaryServicePostgresTest") remains open + unassigned but NOT a child of map #89 — standalone.

## Recommended next pick

- **#115 (movement history endpoint)** — #114's sibling; the DTO + per-route-gate pattern from #114 apply directly. **Note: #115's ticket was written before #114's falsification — its gate assumption (from #100 F1) may be stale; re-verify gates against the now-fixed `BranchInventoryRoutes.kt`.** #116 independent, could run in parallel by another session.
- Otherwise **#101 (Finance)** — next HITL prototype in frontier order; same grilling pattern. **Note per screen: #114's gate fix landed while Finance grills — re-verify gates at build time, don't trust the pre-fix state.**

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK backend ticket flow: /implement-style per #98/#111/#114 pattern (falsification → build → `:backend:detekt :backend:ktlintCheck :backend:test` → /code-review parallel axes → commit on `ralph/company-app-full-build`, not pushed). HITL prototype flow: /prototype + /grilling + /domain-modeling per map Notes (pattern #96/#97/#99/#100/#102). Falsification-before-claim per screen.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`).
4. Graduate any fog the answer makes specifiable (create-then-wire: create issues, then wire blocking with `gh api .../dependencies/blocked_by`; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<numeric .id>` — **verify new tickets appear in the sub-issues query**).
5. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling.

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new entry for #114 (falsification correction F1 — ungated sub-routes; four per-route filters; parseMovementReason; D11 payload; 17-test authz suite; /code-review PASS; k6 deferred).
- Closing paragraph rewritten: 2 AFK backend tickets + 5 HITL prototypes; #114 gist; no new fog additions (route-path literals re-flagged by /code-review — pre-existing, standing fog); JMH-noise warning carried forward.
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals (#114 added), route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, pushed-route TopAppBar pattern, sale-history read-back (F3), catalog management surface (D4), retroactive-day UX.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement`** — for #115/#116 (backend builds, /implement flow per #98/#111/#114).
- **`/code-review`** — at the end of any /implement flow (parallel Standards + Spec; hand untracked files to the sub-agents explicitly).
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #101–#106 (feature screen prototypes; falsification-first per screen; plain-language framing for multi-option questions).
- **`/research`** — for any Ktor / Compose / Material3 API surface the prototype needs to verify.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
