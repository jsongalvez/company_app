# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 26

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 26 resolved the HITL prototype **#106** ("Prototype the User Management screen UX (deactivate, slot ordering)") — `wayfinder:prototype`, resolved by grilling with falsification-first against live code. Resolution comment: https://github.com/jsongalvez/company_app/issues/106#issuecomment-5231080034. Ticket closed; map #89 updated (Decisions-so-far #106 entry, table row #106 → closed, 4 new rows #132–#135, relief-invite fog placement settled, closing paragraph). Asset `docs/prototypes/0106-user-management-outline.md` on throwaway branch `prototype/0106-user-management` (commit `3ec6be6`, pushed doc-only, pre-commit passed). ADR-0023 written; CONTEXT.md gained Role/Deactivate/Reactivate terms. **All 7 HITL prototypes (#99–#106) are now closed — the map is 100% AFK: 7 unblocked backend tasks (#128–#131 from #105, #132–#134 from #106) + 1 blocked build (#135).**

**Next session pick:** any of the **7 unblocked AFK backend tasks** (no grilling remains). All independent, all per `backend/AGENTS.md` + /code-review rounds 1+2:

- **#132 Grant path: role→capability view derivation** — the meatiest + highest-leverage: `active_user_capabilities` view union + V15 role seed + V2 header amendment + ADR exists; fixes the whole GLOBAL dead-grant class (MANAGE_USERS, ASSIGN_COMPENSATION, ASSIGN_DELEGATE, GLOBAL VIEW_BRANCH_DATA) — unblocks User Management AND the Finance/Reports drawer class. Design note inside: BRANCH-scope derivation impossible (role_capability has no context column); ACCOUNTANT all-branches intent coordinates with #131's window semantics.
- **#133 User management backend** — GET /api/users list (displayName/username/status/deactivatedAt/assignments+slots) + PATCH reactivate + `deactivated_at` migration + self-deactivate guard.
- **#134 Gate hardening** — service-level MANAGE_USERS checks on swapSlots + assignment DELETE (the F3 security hole; authz regression tests proving the leak first).
- **#128/#129/#130/#131** — the #105-graduated Reports/Finance backend set (public branch-type exports, date-range rollup, paged daily-summaries feed, Reports access scope).
- **#135 Build — User Management screen** — blocked on #132+#133+#134; its spec is the #106 outline D2–D5 (wire the existing unused `UserViewModel`; drawer visibility waits on #94-grad caps wiring).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #106 entry, Not-yet-specified, Out-of-scope, frontier table)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#106 resolution comment: https://github.com/jsongalvez/company_app/issues/106#issuecomment-5231080034**
- **Prototype outline: `docs/prototypes/0106-user-management-outline.md`** (on branch `prototype/0106-user-management`) — D1–D5 + persona matrix + out-of-scope + graduated ticket specs
- The 4 graduated tickets: #132 / #133 / #134 / #135 (bodies carry their full scopes)
- **ADR-0023** (`docs/adr/0023-global-capabilities-derive-from-roles.md`) — the grant-path decision
- Prior handoff: `docs/agents/wayfinder-105-handoff.md` (this session's parent)

## Session outcome

**#106 (User Management prototype) — resolved + closed (HITL).**

- **Falsification first (6 findings, most premises wrong)**: (F1) **no user-list endpoint exists** — `UserRoutes.kt:24` registers only the deactivate PATCH; closest = per-branch `GET /api/branches/{branchId}/assignments` (active-only, nameless, no status); (F2) **`MANAGE_USERS` GLOBAL granted nowhere in production** — `role_capability` seeds (SUPERUSER/OWNER/MANAGER, V2:55-98) doc-only per V2 header; runtime checks `active_user_capabilities`; no production code writes GLOBAL rows; only `DevSeeder` — **root cause: no production path assigns roles OR capabilities at all** (pre-launch; no `app_user`/`user_role` rows outside tests) — same class as ASSIGN_COMPENSATION GLOBAL (#104 F2) and GLOBAL VIEW_BRANCH_DATA (#105 F1); (F3) **slot-swap + assignment-delete effectively UNGATED** (the #114 exact-path lesson, 3rd occurrence) — Javalin 7 segment-exact filters: 4-segment `before("/api/branches/{branchId}/slots")` doesn't match `/slots/swap`; `swapSlots` has zero service checks — any authenticated user can swap any branch's slots; slot PATCH protected only by service GLOBAL-or-self check (self-edit = BR:67 intent); (F4) **deactivation one-way** — status flip only, no reactivate endpoint, no `deactivated_at`, no self-deactivate guard, no cascade (deactivated users stay in assignment lists); (F5) **no UNIQUE on slot** — duplicates by design (BR:67); swap = `(userIdA, userIdB)`, both actively assigned; (F6) frontend: `Route.UserManagement` + placeholder in both NavHosts (no route gate), drawer item `visible=false` until #94-grad caps wiring, unused `UserViewModel` already wired to the deactivate PATCH.
- **D1 — THE GRANT MECHANISM (user chose to decide in-session)**: **role→capability view derivation** — `active_user_capabilities` = union of direct `user_capability` rows + role-derived (`user_role`→`role_capability`→`capability`); runtime still checks ONLY the view (V2 rule preserved, view computes the union); seed V15 assigns roles to ops users; GLOBAL-scoped codes derive, BRANCH grants stay direct (role_capability has no context column); fixes the whole dead-grant class incl. ASSIGN_COMPENSATION. Chosen over materialization-on-login and surgical seeding. **ADR-0023**.
- **D2–D5**: flat all-users list (displayName, username, status badge + "deactivated X ago", assigned branches w/ slots), client-side search, no pagination (YAGNI), expand row → slots + deactivate/reactivate, deactivated dimmed + slots disabled; deactivate = confirmation dialog (login blocked immediately — JWT killed, capabilities gone, records+assignments kept) → existing PATCH; reactivate = direct action → **new symmetric PATCH reactivate** + `deactivated_at` column + **self-deactivate guard**; slots = branch dropdown from `GET /api/branches` (usable picker here — holder owns GLOBAL MANAGE_USERS, unlike #105 D3), **desktop drag/drop or arrows → pairwise swap** (existing POST /slots/swap), **mobile tap-to-edit → PATCH slot** (existing), manual input fallback, duplicates tolerated, 403/404/409 → inline error + reload (ADR-0022); single route pushed both platforms (#95), drawer item code-only MANAGE_USERS gate (#99 D7).
- **Out of scope (user-confirmed)**: user creation (no endpoint — pre-launch SQL seeds), role/capability-assignment UI, assignment removal (endpoint stays SQL territory; gate fixed in #134, not exposed as UI), non-admin self-slot-edit (BR:67 — fog), relief-invite (**placement settled: BranchSelect territory** — #105 fog closed).
- **Graduated 4 sub-issues of #89**: **#132** grant path (view union + V15 roles + ADR; unblocked), **#133** user backend (list + reactivate + deactivated_at + self-guard; unblocked), **#134** gate hardening (swap + assignment-delete service checks + authz regression tests; unblocked), **#135** Build — User Management screen D2–D5 (blocked on #132+#133+#134; wiring verified: 3 open blockers).
- New fog: user-create flow must assign roles; role/capability-assignment UI; non-admin self-slot-edit affordance (BR:67).

## Key decisions (recorded in full on the resolution comment + outline + ADR-0023)

1. **Grant path = role→capability view derivation** — the whole GLOBAL dead-grant class fixed at the root; ADR-0023.
2. **List = flat all-users + client-side search + expandable rows** — no pagination; drives #133's payload.
3. **Deactivate/reactivate = status toggle** — symmetric endpoints, `deactivated_at`, self-deactivate guard; deactivation stays non-cascading.
4. **Slots = swap for gestures, PATCH for direct edit** — desktop drag/drop + arrows → existing swap endpoint; mobile tap-to-edit → existing slot PATCH; duplicates tolerated.
5. **Branch switcher = GET /api/branches dropdown** — usable here because this screen's holder has GLOBAL MANAGE_USERS.
6. **Relief-invite placement settled** — BranchSelect territory, NOT User Management (closes the #105 fog).
7. **Out-of-scope confirmed**: user creation, role-assignment UI, assignment removal (all "later" per user — the D1 machinery is their foundation).

## Patterns + learnings (cumulative across sessions)

- **The dead-grant class is one root cause**: every "GLOBAL cap 403s for everyone" finding (#104 F2 ASSIGN_COMPENSATION, #105 F1 VIEW_BRANCH_DATA, #106 F2 MANAGE_USERS) traces to the same hole — no production path assigns roles or capabilities. #132 fixes all of them at once. Future sessions: when a screen's gate looks persona-dead, check the grant path, not just the gate code.
- **Javalin 7 before-filters match segment-exact** — the #114 lesson recurred a third time (slots filter vs `/slots/swap`). When a before-filter guards a path family, VERIFY each sub-path actually matches (or put the check in the service — #134 mandates service-level as the reliable location).
- **The #114 exact-path lesson is a verification step, not just a fix** — every authz falsification pass must enumerate the routes a filter does NOT cover.
- **BR:67 self-service intent is law**: slot PATCH self-edit is a feature; #134 keeps the swap self-service path (caller ∈ swapped pair).
- **User corrections beat my synthesis** (again): user added search to the flat list unprompted; user's "we'll add creation/roles/removal later" confirmed the scope boundary naturally. Grilling round 2 had two stalls — one re-pitch (mechanism explanation, wait-what style) and one re-scope — both resolved in one pass after re-framing.
- **Wiring gotcha this session**: the dependencies endpoint 404s with the issue's DB id in the PATH — the path takes the issue **number**, the body takes `issue_id` (DB id) or it 422s. Tracker doc's "`/issues/<child>/`" means number.
- Prior-session patterns unchanged: falsification-first against live code, /prototype + /grilling + /domain-modeling for HITL, outline asset on `prototype/0106-*` branch (pushed doc-only, pre-commit passed), resolution comment → close → map Decisions-so-far + table + closing paragraph, create-then-wire (sub-issue via `POST .../issues/89/sub_issues -F sub_issue_id=<db-id>`; verify with the sub-issues query).
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals for `AppNavHost`, `ClientResultList`, `ClientDetailLayout`, `AuditLogEntryList`, `RemittanceRowList`) — any future iOS work (or the pre-push gate's multi-target compile) hits this first.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100` + dependency summaries (`blocked_by` = open blockers only):

| # | Title | Type | Status |
|---|-------|------|--------|
| 128 | Public branch-type exports: drop VIEW_BRANCH_DATA gate on provincial/medical-mission (graduated from #105) | task | 🔓 unblocked |
| 129 | Add date-range export endpoint (graduated from #105) | task | 🔓 unblocked |
| 130 | Add paged daily-summaries endpoint for the Reports feed (graduated from #105) | task | 🔓 unblocked |
| 131 | Fix Reports access scope: GLOBAL VIEW_BRANCH_DATA = all branches + accessible-branches endpoint (graduated from #105) | task | 🔓 unblocked |
| 132 | Grant path: role→capability view derivation for GLOBAL capabilities (graduated from #106) | task | 🔓 unblocked |
| 133 | User management backend: user list + reactivate + deactivated_at + self-guard (graduated from #106) | task | 🔓 unblocked |
| 134 | Fix ungated slot-swap + assignment-delete (graduated from #106) | task | 🔓 unblocked |
| 135 | Build — User Management screen (graduated from #106) | task | 🔒 blocked by #132+#133+#134 |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #106 closed this session. **All HITL prototypes done — no grilling tickets remain on the map.**)

## Recommended next pick

- **#132 (Grant path)** — the highest-leverage AFK task: the whole GLOBAL dead-grant class in one view rewrite + seed + ADR-0023 already written. Note the open design point in its body (BRANCH-scope derivation impossible — GLOBAL-scoped codes only; ACCOUNTANT all-branches intent coordinates with #131's window semantics). Coordinate order with #131 if both proceed — they touch adjacent semantics (view grants vs read window) but are independent code.
- **#133 / #134** — both small, independent, no blockers; either is a clean session. #134 first if you want the security hole closed earliest.
- **#128–#131** — the Reports/Finance backend set; #131 recommended first (unblocks the whole Reports read surface), #128 smallest.
- **#135 (Build — User Management)** — only when #132+#133+#134 are closed; spec = outline D2–D5.
- **Merged Finance & Reports build ticket** — ungraduated; opens when #128–#131 land + the #117 expense-GET-includes-soft-deleted question is decided. Its spec = #105's outline (D1–D7) + #101's outline (edit-mode D1–D8) + #117 read-backs.
- **Candidate backend fog (small AFK hardening tickets)**: the cross-draft line-session raw 500 (#120 fog — `idx_remittance_line_session` insertIgnore→`.single()` NoSuchElement) and the `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK flow (#128–#134): /implement per `backend/AGENTS.md` (falsification-first against the ticket's body — several tickets encode design decisions, e.g. #132's context-mapping note, #134's service-level enforcement mandate), quality gate, /code-review rounds 1+2, k6 deferral precedent (#98/#115).
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update BOTH the table row AND the closing paragraph).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Table: #106 → ✅ closed; 4 new rows #132–#135 (3 unblocked, #135 blocked by #132+#133+#134).
- Decisions-so-far: new #106 entry (grant-mechanism headline, 6 falsification findings, D1–D5, 4 graduations, out-of-scope, resolution link).
- Not-yet-specified: relief-invite fog placement **settled** (BranchSelect territory); + user-create/role-assignment flow fog, + non-admin self-slot-edit fog.
- Closing paragraph: all 7 HITL prototypes closed — map is 100% AFK; frontier = #128–#134 unblocked + #135 blocked; merged Finance & Reports build still ungraduated.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for the AFK backend tasks #128–#134 (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
