# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 23

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 23 resolved the HITL prototype **#103** ("Prototype the Remittance screen UX (drafts, submit, detail)") — `wayfinder:prototype`. Full flow: claimed #103 → falsification against live backend (12 findings, F1–F13) → grilling (3 rounds; re-framed questions in plain language per the #101 Q8/Q9 lesson) → prototype outline `docs/prototypes/0103-remittance-outline.md` committed `0b29c23` on `prototype/0103-remittance` (doc-only, pre-commit passed) → CONTEXT.md updated (Undo term + Snapshot 48h carve-out, commit `60bec4e` on `ralph/company-app-full-build`) → graduated **#118/#119/#120** (sub-issue wired, blocking wired) → resolution comment (https://github.com/jsongalvez/company_app/issues/103#issuecomment-5200537056) → closed #103 → map #89 updated (child table rows 118/119/120 added + 103 → ✅, Decisions-so-far entry, closing paragraph rewritten).

**Next session pick:** the frontier is now **3 HITL prototypes + 2 AFK tasks, all unblocked, all unassigned**:

| # | Title | Type |
|---|-------|------|
| 104 | Prototype the Audit Log screen UX (filterable history, before/after diff) | prototype — HITL |
| 105 | Prototype the Reports screen UX (daily, monthly, all-time exports) | prototype — HITL |
| 106 | Prototype the User Management screen UX (deactivate, slot ordering) | prototype — HITL |
| 118 | Add remittance read-back endpoints: draft list + line pickers + days list + snapshot + drift (graduated from #103) | task — **AFK** |
| 119 | Add remittance undo + draft editing endpoints (graduated from #103) | task — **AFK** |

#120 (Build — Remittance screen) is open but **blocked on #118 + #119** (blocked_by: 2 verified live).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89
- Wayfinder skill: `/home/jayson/.config/opencode/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#103 resolution comment (this session's load-bearing record): https://github.com/jsongalvez/company_app/issues/103#issuecomment-5200537056**
- **#103 prototype outline (the build spec for #120): `docs/prototypes/0103-remittance-outline.md`** (branch `prototype/0103-remittance`)
- #117 resolution comment (finance read-back precedent): https://github.com/jsongalvez/company_app/issues/117#issuecomment-5199352099
- Prior handoffs: `docs/agents/wayfinder-117-handoff.md` (session 22), `docs/agents/wayfinder-101-handoff.md` (session 21)

## Session outcome

**#103 — resolved + closed.** All four ticket questions locked (D1–D10):

1. **D1 List** — one list, tabs Drafts/Submitted/All; rows = type badge, method, date range; **Net on submitted SESSION rows** (snapshot join in list payload). Desktop dense table / mobile cards.
2. **D2 Create** — popup over list (type, method, date range default today); header editable in place while DRAFT (D9).
3. **D3 Lines** — pickers: sessions in range (client, time, amount prefilled, editable at add) + product sales in range; tick-to-include; delete-only (no amount edit).
4. **D4 Days covered** — statused day picker (OPEN/PAST/REMITTED), already-remitted greyed, removable.
5. **D5 Submit** — structural confirm (type, days, line count, **line total** from detail, method) + "days lock, amounts freeze, undo within 48h" copy; 409 → reload + banner; frozen breakdown shown on success.
6. **D6 Detail = the receipt** — pushed on both platforms; **Frozen-at-submission block** (SESSION only) styled as snapshot (hairline card, lock glyph, muted label — receipt signal is a design requirement); **lazy "Show current state" drift expander** (frozen vs current comp/expenses/net; gross never drifts — lines immutable; hidden for PRODUCT). No comparison beyond the expander.
7. **D7 Refresh** — load on entry + refresh button, no in-screen polling; branch-scoped (selectedBranchId).
8. **D8 Out of scope** — monthly summary (Reports #105 owns it); draft-discard (YAGNI).
9. **D9 Header editing** — DRAFT-only PATCH (type/method/date range), version-locked.
10. **D10 Undo** — **48h from submission, server-enforced**; reason required; → DRAFT, days unlock, snapshot deleted, audit-trailed; monthly aggregates may shift during window (accepted).

## Key falsification findings (12, all in the resolution comment)

The ticket's four questions were ALL premised on wrong backend assumptions:

1. **F1 no draft list** — `GET /api/remittances` doesn't exist (premised); only `GET /{id}`.
2. **F2 no PATCH anywhere** — no header PATCH (premised), no line PATCH. Header + line amounts immutable after create.
3. **F3 snapshot unreadable** — `RemittanceDetailResponse` has no snapshot block; snapshot exists in DB (SESSION type only — PRODUCT writes none) but nothing exposes it.
4. **F4 no pre-submit numbers** — P&L computed inside submit (SERIALIZABLE + FOR UPDATE). Confirm dialog can't preview.
5. **F5 no source lists** — sessionId/productSaleId REQUIRED per line type but no sessions-list endpoint exists anywhere (product-sale POST-only, #100 F3).
6. **F6 no branch-day list** — day picker has no source (only #116 today endpoint).
7. **F7 bare lines** — no client/product names in line responses.
8. **F8 gate VERIFIED correct** — SUBMIT_REMITTANCE @ BRANCH all routes (only ticket whose gate premise held); Coordinator/Manager/SUPERUSER.
9. **F9 monthly-summary gate split** — VIEW_BRANCH_DATA (Accountant-readable) → Reports territory, not this screen.
10. **F10 day-breakdowns drive P&L** — comp/expenses summed over breakdown days at submit; days flip REMITTED; no day-breakdown DELETE.
11. **F11 snapshot trigger-protected** — `trg_remittance_snapshot_immutable` (V1:472-484) blocks UPDATE/DELETE → undo needs migration carve-out (V6/V11 pattern).
12. **F12 no drift visibility** — Coordinator edits to REMITTED days change live numbers; nothing shows the difference.

## Key decisions (recorded in full on the resolution comment)

1. **Undo with 48h window + reason** — user-initiated requirement (Q5 grill); carves the snapshot immutability invariant (CONTEXT.md + trigger). **ADR deferred to implementation**: write `docs/adr/0023-remittance-undo-window.md` when #119 lands — the full rationale (why it meets the ADR bar, rejected alternatives incl. void-style keep-history) is captured on **#119's comment** (https://github.com/jsongalvez/company_app/issues/119#issuecomment-5200558046) so the implementing session can reconstruct it without this conversation.
2. **Drift expander resurrected** — D6 originally ruled comparison out of scope; user asked "a button to view the diff at the right place" → lazy on-click drift endpoint; gross can't drift (lines immutable after submit) so drift = comp/expenses/net only.
3. **Receipt styling is a design requirement** — Frozen block must read as a snapshot (hairline card, lock glyph, muted label), not live data.
4. **Header-edit-in-place (Q10 A)** over delete+recreate — undo brings drafts back; rebuilding after that wastes the redo.
5. **Structural submit confirm** — numbers unknowable pre-submit (F4); confirm shows type/days/lines/line-total/method + 48h-undo copy; frozen numbers appear post-submit.
6. **Monthly summary left to Reports** (gate split, F9) — the handoff's "remittance surface includes monthly summary" was corrected: it's VIEW_BRANCH_DATA = #105's data.
7. **Sessions-in-range endpoint serves two consumers** — remittance line picker + the future dashboard build (dashboard still un-graduated; #97 prototype closed, build ticket never graduated).

## Tests

None this session — prototype-only (outline doc, no code). Pre-commit gates ran on both commits (quality gate + cleanliness + shared compile + Postgres all green).

## Patterns + learnings (cumulative)

- **Falsification is the norm, verification is the exception**: #103 produced 12 findings vs #117's 1 — remittance is the least-read-back surface in the backend. Ticket Q1–Q4 premises were all wrong except the gate. Any future "prototype screen X" session: enumerate every endpoint premise against `routes/` first.
- **User's plain-language re-frame request**: round 1 questions were too technical ("method enum names", "date range defaults", "payload joins") — user asked for concrete screens ("what does this look like to the user?"). Round 2+ used scenario framing ("you submitted yesterday, spotted the type is wrong...") — all 10 decisions locked in one follow-up round. The #101 Q8/Q9 lesson is now a two-session precedent.
- **User-authored requirements are the richest material**: the 48h undo + reason came from the user ("if the coordinator made a mistake, they can undo within reason... 48 hours"); the drift expander came from "is there a way to click a button and view the diff". Ask open questions, not just options.
- **Sub-issue wiring**: `gh api ... -F sub_issue_id=<db-id>` succeeded but returned empty jq output (wrong path `.sub_issue.number`); verify via the sub_issues query, not the POST response. 422 on retry = already added (idempotent-ish error).
- **Blocking verification**: `gh api repos/.../issues/120 --jq .issue_dependencies_summary` → `blocked_by: 2` — the live gate check.
- Prior-session patterns unchanged: falsification-before-build, one-ticket-per-session, unpushed commits on `ralph/company-app-full-build`, `-F` vs `-f` wiring, route-literal consistency, per-route before-filters (#114 F1), version-column check (V6/V11) before trusting expectedVersion premises (#117 F1).

## Current frontier (verified live post-session)

Per `gh api "repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100"` — 6 open children: #104/#105/#106 (HITL prototypes, unassigned, unblocked), #118/#119 (AFK tasks, unassigned, unblocked), #120 (blocked on #118+#119).

**Standalone (NOT children of #89):** #110 ("Fix hardcoded-month dates...") still open + unassigned — Aug rollover mechanically fixed in `3a56fde`; #110 may be stale.

**Still ungraduated:** Finance screen build (graduated from #101, read-back gaps closed by #117) — the #101 D6 expense-GET-includes-soft-deleted question (dimmed rows vs live `deleted_at IS NULL` filter) must be decided by that build. Dashboard build (graduated from #97) never created — the sessions-list gap (#103 F5) is shared with it.

## Recommended next picks

- **#118 (remittance read-back endpoints, AFK)** — first in frontier order; largest AFK surface (7 pieces: list w/ snapshot join, sessions-in-range, sales-in-range, days-in-range, day-breakdown DELETE, snapshot block, drift). Falsify first per ticket body; the outline's Graduation section is the spec. Note: `GET /api/remittances?branchId=` ordering choice (createdAt DESC + id DESC per #115 lesson), read gates = SUBMIT_REMITTANCE (reads gate same as writes — AGENTS), k6 deferred.
- **#119 (undo + header PATCH, AFK)** — the migration carve-out of `trg_remittance_snapshot_immutable` is the risky piece (V6/V11 precedent; never edit applied migrations). **Write ADR-0023 alongside this build** — rationale pre-captured on #119's comment (https://github.com/jsongalvez/company_app/issues/119#issuecomment-5200558046).
- #104/#105/#106 remain equally sharp-cuttable HITL prototypes in parallel.
- Any AFK session should also consider graduating the **Finance build** or **dashboard build** if the session runs short (one-ticket-per-session doctrine — graduation is charting, not resolving).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. **AFK task** (#118/#119): falsification-before-build per ticket body + outline's Graduation section; backend/AGENTS.md conventions (per-route before-filters, parent-child scoping, audit rows, masked UUID logging, *CreateParams for 4+ params); `:backend:detekt :backend:ktlintCheck :backend:test` + shared compile + test-DB cleanliness; /code-review (parallel Standards + Spec) before commit; commit unpushed on `ralph/company-app-full-build`.
3. **HITL prototype** (#104/#105/#106): /prototype + /grilling + /domain-modeling; falsification-before-claim against live backend; plain-language scenario framing for multi-option questions.
4. Post the answer as a **resolution comment**, `gh issue close <N>`, append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>`; keep the child-tickets table + closing paragraph current).
5. Graduate fog (create-then-wire: `gh api .../issues/89/sub_issues -F sub_issue_id=<numeric .id>` — verify via the sub_issues query; blocking via `gh api .../issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, verify with `.issue_dependencies_summary`).
6. If the decision meets the ADR bar (hard to reverse + surprising + real trade-off), offer an ADR per /domain-modeling. **Deferred ADR: ADR-0023 (remittance 48h undo carve-out) is due at #119 implementation time — rationale pre-captured on #119's comment.**

**One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: row #103 → ✅ closed; rows #118 (unblocked), #119 (unblocked), #120 (🔒 blocked by #118, #119) added.
- Decisions-so-far: new entry for #103 (12 falsifications; D1–D10; Undo term + Snapshot carve-out; graduation of #118/#119/#120; audit coverage verified).
- Closing paragraph rewritten: frontier = #104/#105/#106 (HITL) + #118/#119 (AFK); #120 blocked; Finance build still ungraduated; dashboard build never graduated (shares the sessions-list gap).
- Prior fog preserved: SessionState context-model divergence, no client-create outside sale form, anonymize-with-PENDING-session gap, orphan-code cleanup, k6 deferrals, route-path literals, hardcoded-month tests, Desktop token hardening, PrimaryHover, detekt-gate strategy, DayStatus-not-shared, LocalNavHostController + poller-VM patterns, NotificationState.clear() maintenance point, pushed-route TopAppBar pattern, per-user commission-split drill-down, cross-day comp history.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/code-review`** — for the AFK build sessions (#118/#119) before commit.
- **`/prototype` + `/grilling` + `/domain-modeling`** — for #104–#106 (plain-language framing, falsification-first per screen).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
