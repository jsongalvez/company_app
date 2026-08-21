# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 29

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 29 resolved the AFK task **#134** ("Fix ungated slot-swap + assignment-delete") — `wayfinder:task`, driven by the agent alone (the map is 100% AFK). Resolution comment: https://github.com/jsongalvez/company_app/issues/134#issuecomment-5231961307. Ticket closed; map #89 updated (Decisions-so-far #134 entry, table row #134 → ✅ closed, #135 row → 🔓 unblocked, closing paragraph rewritten). Commit `f615b50` on `ralph/company-app-full-build` (5 files, +398/−41, not pushed — k6 deferred per #98/#115 precedent; pre-commit gate passed: ktlint/detekt/703 tests/cleanliness/shared-compile).

**Next session pick:** any of the **5 unblocked tickets** (all AFK backend tasks except #135 which is the frontend build; #135's spec is locked and its payload DTO shipped):

- **#128–#131** — the #105-graduated Reports/Finance backend set. **#131 recommended first** (Reports access scope — GLOBAL VIEW_BRANCH_DATA = all branches via a shared window helper + accessible-branches endpoint; coordinate-ready with #132's derived GLOBAL VIEW_BRANCH_DATA — derived rows and direct rows behave identically in the view). #128 smallest (drop gate on provincial/medical-mission exports).
- **#135 Build — User Management screen** — now unblocked (`blocked_by: 0` verified). Spec = #106 outline D2–D5 (wire the existing unused `UserViewModel`; drawer visibility waits on #94-grad caps wiring). `UserSummaryResponse` DTO shipped in #133. Backend surface complete: `GET /api/users`, `PATCH /api/users/{id}/reactivate` + existing deactivate, `POST /slots/swap` (self-service OR MANAGE_USERS), `PATCH slot`, `DELETE assignment` — all authz-tested per sub-path. Frontend build conventions per `composeApp/AGENTS.md` + /code-review rounds 1+2.
- **Merged Finance & Reports build ticket** — ungraduated; opens when #128–#131 land + the #117 expense-GET-includes-soft-deleted question is decided. Spec = #105's outline (D1–D7) + #101's outline (edit-mode D1–D8) + #117 read-backs.
- **Candidate backend fog (small AFK hardening tickets)**: the cross-draft line-session raw 500 (#120 fog — `idx_remittance_line_session` insertIgnore→`.single()` NoSuchElement) and the `getForSession` read-path day-gate 400 (#124 fog).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #134 entry, Not-yet-specified, Out-of-scope, frontier table)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#134 resolution comment: https://github.com/jsongalvez/company_app/issues/134#issuecomment-5231961307**
- Prior handoff: `docs/agents/wayfinder-133-handoff.md` (this session's parent)

## Session outcome

**#134 (Gate hardening — slot-swap + assignment-delete) — resolved + closed (AFK).**

- **Service-level gates** (the ticket's enforcement mandate — single source of truth): `create` / `remove` / `findActiveByBranch` (now `(callerId, branchId)`) require GLOBAL `MANAGE_USERS`; `swapSlots` = GLOBAL `MANAGE_USERS` **OR** caller is one of the two swapped users (BR:67 self-service, mirroring the existing slot-PATCH self-edit); `updateSlot` unchanged; self-delete denied (remove = MANAGE_USERS only); both-assignments-active requirement preserved (repo `swapSlots` NotFound); 403-before-404 ordering preserved (capability check precedes branch lookup).
- **Both misleading 4-segment before-filters removed** from `UserBranchAssignmentRoutes.kt` — the `/slots` filter matched zero routes (only `/slots/swap` exists, 5 segments); `/assignments` covered POST/GET but implied DELETE was covered. The #114 exact-path lesson, 3rd occurrence, eradicated at the root: route filters gone, service-level is the only authz surface.
- **Leak proven red-first** (the #118 F9 pattern): the new route authz suite ran against the unfixed code — exactly the 3 leak tests failed (`expected:<403> but was:<204>`: no-grant swap, no-grant delete, self-delete).
- Tests: `UserBranchAssignmentAuthzTest` (12 route cases — per-sub-path 403 for no-grant, manager success on all 5 routes, self-swap 204, self-delete 403, self-slot 204 preserved) + `UserBranchAssignmentServicePostgresTest` flipped the three "allowed at service layer" intents to Forbidden tests + new self-swap service test + `findActiveByBranch` gated tests. `MeServiceBranchesPostgresTest` fixtures moved from the (now gated) service to direct-row `insertTestAssignment` — no grant pollution. Suite 703 green (was 687).
- Private `requireManageUsers(callerId, message)` helper extracted (round-1 Standards: the 4-arg GLOBAL block was duplicated at 5 sites).

## Key decisions (recorded in full on the resolution comment + map #89)

1. **Filters removed, not corrected** — the false-safety illusion dies; per-ticket mandate "Remove or correct the misleading route filters". Service-level is now the single authz surface for this resource, per-sub-path test-proven.
2. **GET now gated same as writes** — `findActiveByBranch` gained the MANAGE_USERS service check (backend/AGENTS.md read-endpoint rule); matches prior filter behavior exactly (the 4-segment filter DID fire for GET), no legit-caller behavior change.
3. **`MeServiceBranchesPostgresTest` fixture honesty** — a test about `/me/branches` was arming its subject with GLOBAL MANAGE_USERS just to pass the new create gate; fixtures switched to direct-row `insertTestAssignment` (incl. `ended=true` for the exclusion test).
4. **Javalin-testtools mapper double-encodes String bodies — NEW LEARNING** — `client.patch(path, "{...}")` / `client.delete(path, consumer)` serializes a String body as a **quoted JSON string** (`"{\"slot\":4}"`) → server `bodyAsClass` throws `JsonDecodingException` → 500. Pass **DTO objects** (`UpdateSlotRequest(slot = 4)`), not JSON strings. Extends the #133 trap (no `patch(path, consumer)` / `delete(path, consumer)` overloads — `delete(path, null, consumer)`).

## Patterns + learnings (cumulative across sessions)

- **Test-DB pollution via untracked audit rows** — a test writing audit rows with an untracked `changedBy` kills the teardown transaction (FK violation → atomic rollback → ALL rows stay incl. the branch fixture → every subsequent test fails at `initTestData` with duplicate `(branch_type, name)`). Track `AuditLogTable.changedBy` for EVERY user who can write audits in a test class. Also: `trackOwned` the API-created row BEFORE the request, not after the assertions.
- **Cross-class branch-name collision** — `branch_branch_type_name_key` is UNIQUE on `(branch_type, name)`; two AuthzTest classes must not reuse the same branch name ("Authz Branch" was taken by #133's `UserManagementAuthzTest` → my class used "Authz Branch 134").
- **Review-round traps**: round-1 Standards caught a **bare `assert()` silent no-op** (stdlib `assert` needs `-ea`; Gradle Test has no `enableAssertions`) — the DELETE-by-manager test never verified ended-at. Also a naming clash: my `assignmentEndedAt: Boolean` vs the service test's `assignmentEndedAt: OffsetDateTime?`. The fix-delta round-2 review itself was clean; one fix (ended-assignments fixture) was initially wrong (left an active row) and only the test run caught it — the session-20 lesson holds: run tests between review rounds.
- Prior-session patterns unchanged: AFK flow = /implement per backend/AGENTS.md, falsification-first (this ticket's premises — no swap check, no remove check, filters not firing — were verified against the code before building), red-first regression tests proving the leak, quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred (#98/#115 precedent), resolution comment → close → map Decisions-so-far + table + closing paragraph.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V17 taken; **next is V18** (`ls backend/src/main/resources/db/migration/` before picking).
- **PG enum same-transaction rule** (from #132): `ALTER TYPE ... ADD VALUE` + use in one migration = 55P04; split into two migrations.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100`:

| # | Title | Type | Status |
|---|-------|------|--------|
| 128 | Public branch-type exports: drop VIEW_BRANCH_DATA gate on provincial/medical-mission (graduated from #105) | task | 🔓 unblocked |
| 129 | Add date-range export endpoint (graduated from #105) | task | 🔓 unblocked |
| 130 | Add paged daily-summaries endpoint for the Reports feed (graduated from #105) | task | 🔓 unblocked |
| 131 | Fix Reports access scope: GLOBAL VIEW_BRANCH_DATA = all branches + accessible-branches endpoint (graduated from #105) | task | 🔓 unblocked |
| 135 | Build — User Management screen (graduated from #106) | task | 🔓 unblocked (verified `blocked_by: 0`) |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #132, #133, #134 closed across the last three sessions. **The map is 100% AFK — all HITL prototypes closed.**)

## Recommended next pick

- **#131** — Reports access scope; unblocks the whole Reports read surface (GLOBAL VIEW_BRANCH_DATA window semantics for export/summary routes + accessible-branches picker endpoint). Coordinate-ready with #132's derived GLOBAL VIEW_BRANCH_DATA (SUPERUSER/ACCOUNTANT) — derived rows and direct rows are indistinguishable in `active_user_capabilities`, same view, no special-casing needed. #122's `AuditLogReadScope` is the precedent to lift.
- **#128** — smallest ticket (drop gate on two export routes, authz tests). **#129/#130** — new rollup + keyset-cursor feed endpoints.
- **#135 Build — User Management** — spec locked (#106 D2–D5), backend surface complete and authz-tested; `UserViewModel` already exists unused.
- **Merged Finance & Reports build** — ungraduated; opens after #128–#131 + #117's expense-GET-soft-deleted decision.
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK flow (#128–#131): /implement per `backend/AGENTS.md` (falsification-first against the ticket's body), quality gate, /code-review rounds 1+2, k6 deferral precedent (#98/#115). For #135 (build): /implement per `composeApp/AGENTS.md`.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update BOTH the table row AND the closing paragraph; the frontier counts + blocker lists change as tickets close).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Table: #134 → ✅ closed; #135 → 🔓 unblocked (was "blocked by #133+#134").
- Decisions-so-far: new #134 entry (gates, filter removal, red-first proof, test counts, review outcome, testtools String-body learning, #135-unblocked note, resolution link).
- Closing paragraph: frontier = 5 unblocked (#128–#131, #135); #134 closed with resolution link; merged Finance & Reports build still ungraduated.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for the AFK backend tasks #128–#131 (parallel Standards + Spec; round 2 on the fix delta) or the #135 frontend build.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
