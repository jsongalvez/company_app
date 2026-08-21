# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 30

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 30 resolved the AFK task **#131** ("Fix Reports access scope: GLOBAL VIEW_BRANCH_DATA = all branches + accessible-branches endpoint") — `wayfinder:task`, driven by the agent alone (the map is 100% AFK). Resolution comment: https://github.com/jsongalvez/company_app/issues/131#issuecomment-5234865679. Ticket closed; map #89 updated (Decisions-so-far #131 entry, table row #131 → ✅ closed, #128–#130 counts in Not-yet-specified + closing paragraph, #128 context comment). Commit `9aebf00` on `ralph/company-app-full-build` (8 files, +388/−18, not pushed — k6 deferred per #98/#115 precedent; pre-commit gate passed: ktlint/detekt/715 tests/cleanliness/shared-compile).

**Session detour (before any code)**: the workspace mount was read-only (Windows fast-startup left NTFS dirty → ntfs-3g mounted ro). Fixed by user: Windows boot + `powercfg /h off` + full shutdown + `mount -a` on Linux. No repo data at risk (tree was clean). If the mount is ever ro again: check `/proc/mounts`; clean fix = Windows-side disable fast startup, NOT `ntfsfix`.

**Next session pick:** any of the **4 unblocked tickets** (all AFK):

- **#128** — smallest; premise already falsified by #131's context comment: the GLOBAL filter it drops (`before("/api/branches/export")`, 3-segment) **never fires** on the 4-segment provincial/medical-mission routes — they're already ungated (JWT-only), exactly the target state. Work = remove dead filter + authz regression tests proving public access stays JWT-gated.
- **#129** — date-range export endpoint (whole-range rollup, csv/pdf, window semantics now exist: `BranchReadScope` + `requireBranchOrGlobalCapabilityForBranchId`).
- **#130** — paged daily-summaries feed (keyset cursor per #122 precedent; window = `BranchReadScope.windowBranchIds` — the report window is now the shared helper).
- **#135 Build — User Management screen** — unblocked (`blocked_by: 0`); spec locked (#106 D2–D5, wire existing unused `UserViewModel`; drawer visibility waits on #94-grad caps wiring). `UserSummaryResponse` DTO shipped in #133. Backend surface complete + authz-tested.
- **Merged Finance & Reports build ticket** — ungraduated; opens when #128–#130 land + the #117 expense-GET-includes-soft-deleted question is decided.
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #131 entry, Not-yet-specified, Out-of-scope, frontier table)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#131 resolution comment: https://github.com/jsongalvez/company_app/issues/131#issuecomment-5234865679**
- Prior handoff: `docs/agents/wayfinder-134-handoff.md` (this session's parent)

## Session outcome

**#131 (Reports access scope — GLOBAL VIEW_BRANCH_DATA = all branches + accessible-branches endpoint) — resolved + closed (AFK).**

- **Falsification — the ticket's premise was wrong in the dangerous direction**: the export routes were NOT gated. The 4-segment `before("/api/branches/{branchId}/export")` filter never fires on the 5-segment routes (Javalin 7 exact-segment matching) — proven red-first: zero-grant caller got **404 (handler ran), not 403** on `export/daily|monthly|all-time`. **The #114 exact-path lesson, 4th occurrence, on raw CSV/PDF data export** — any authenticated user could export any branch's financial data. The two summary routes (3-seg filter = 3-seg route) WERE gated correctly; they needed only the GLOBAL widening.
- **Shipped**: `BranchReadScope` (shared window helper extracted from `AuditLogReadScope.windowBranchIds` — `null` = all branches for GLOBAL VIEW_BRANCH_DATA holders, else `CapabilityService.findBranchWindow` = distinct BRANCH grants; audit scope delegates, semantics byte-identical); `CapabilityFilter.requireBranchOrGlobalCapabilityForBranchId` (branch-scoped OR GLOBAL, 403-before-404 preserved); **ExportRoutes filter path → wildcard** `/api/branches/{branchId}/export/*` (the gate now actually runs); branch-or-global helper on all 5 read routes; **`GET /api/branches/accessible`** — picker window, no gate (zero-grant → 200 empty list, audit-log pattern; reuses shared `BranchResponse`).
- **Key decisions**: (1) **wildcard filter over per-handler checks** — the filter-path fix is what makes the gate fire; (2) **no route gate on `/accessible`** — zero-grant empty-not-403 (audit-log D9 pattern), MANAGE_USERS filter provably doesn't fire on the 3-segment path (empirically confirmed in the red run: `/accessible` reached the `{branchId}` handler, not the filter); (3) **gate ≠ window** — export/summary gates stay VIEW_BRANCH_DATA-scoped (branch-or-global) while the picker window is any-grant (D3: picker drives edit actions too); (4) sibling dead filter `before("/api/branches/export")` **documented, not fixed** — it's #128's scope (its deadness = #128's target state); (5) **404-proves-gate-pass test technique** — the services 404 on missing data after the gate, so 403-vs-404 distinguishes gate-block from gate-pass without seeding financial data.
- Tests: `ReportsReadScopeAuthzTest` — 12 route cases (zero-grant 403 on all 5 routes; branch-scoped 404-own/403-other; GLOBAL holder 404 on a **zero-grant** branch — the headline fix; picker window incl. non-view grants; MANAGE_USERS regression on `GET /api/branches`). Suite 715 green (was 703). AuditLogAuthzTest untouched + green (delegation path covered).
- **New learning — `/api/branches/accessible` was swallowed by the `{branchId}` path param** (400 "Invalid branchId") before the literal route existed; Javalin literal-over-param precedence makes the new route win once registered. Red run surfaced it.

## Patterns + learnings (cumulative across sessions)

- **Javalin 7 before-filters match SEGMENT COUNT EXACTLY, not prefix** — now proven 5× (the #114 lesson: `/slots` 4-seg vs `/slots/swap` 5-seg in #134; `/assignments` POST/GET vs DELETE in #134; `/api/branches` 2-seg vs `/api/branches/{branchId}` 3-seg — never fires; `/export` 4-seg vs 5-seg routes here; `/branches/export` 3-seg vs 4-seg provincial routes here). **Any gate on a literal path must count segments against every route it claims to cover — and a test must prove it fires.** Wildcard `/path/*` is the fix when routes are deeper than the shared prefix.
- **Falsification-first paid off again**: the ticket said "gate today: requireBranchCapabilityForBranchId on .../export/*" — the red-first suite disproved it before any code changed. Always write the authz test first and read the statuses (403 vs 404) before touching the gate.
- **404-as-gate-pass test technique**: for read routes whose handlers 404 on missing data, the authz matrix can assert 404 (gate passed) vs 403 (gate blocked) with zero data seeding. Deterministic and cheap.
- Prior-session patterns unchanged: AFK flow = /implement per backend/AGENTS.md, red-first regression tests, quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred (#98/#115 precedent), resolution comment → close → map Decisions-so-far + table + closing paragraph, handoff.
- **Read-only NTFS mount lesson**: Windows fast-startup leaves NTFS dirty → fuseblk mounts ro; `sudo mount -o remount,rw` is unsupported on ntfs-3g; plain umount fails "target is busy" (agent + gradle daemons hold cwd); lazy umount works but the fresh mount STILL goes ro while Windows hibernation data is pending. Real fix = Windows-side `powercfg /h off` + full shutdown.
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
| 135 | Build — User Management screen (graduated from #106) | task | 🔓 unblocked (verified `blocked_by: 0`) |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #131 closed this session — the whole #105-graduated set is down to #128–#130. The map is 100% AFK — all HITL prototypes closed.)

## Recommended next pick

- **#128** — smallest, and its premise is already half-done (see context comment added this session): remove the dead GLOBAL filter + JWT-stays regression tests. Beware the cross-class branch-name collision lesson (unique `(branch_type, name)`).
- **#130** — meatiest remaining backend task; `BranchReadScope.windowBranchIds` is the ready-made window; keyset cursor precedent = #122 (`(changed_at, id)` base64url opaque cursor).
- **#129** — new rollup endpoint; gates now trivially expressible via `requireBranchOrGlobalCapabilityForBranchId`.
- **#135 Build — User Management** — spec locked (#106 D2–D5), backend surface complete and authz-tested; `UserViewModel` already exists unused. Frontend build conventions per `composeApp/AGENTS.md` + /code-review rounds 1+2.
- **Merged Finance & Reports build** — ungraduated; opens after #128–#130 + #117's expense-GET-soft-deleted decision.
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK flow (#128–#130): /implement per `backend/AGENTS.md` (falsification-first against the ticket's body — #131's finding already reshaped #128), quality gate, /code-review rounds 1+2, k6 deferral precedent (#98/#115). For #135 (build): /implement per `composeApp/AGENTS.md`.
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update BOTH the table row AND the closing paragraph; the frontier counts + blocker lists change as tickets close).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Table: #131 → ✅ closed (row now `| 131 | ... | task | ✅ closed | — |`).
- Decisions-so-far: new #131 entry (falsification finding, wildcard fix, BranchReadScope + requireBranchOrGlobal helper, accessible endpoint, scoped-out list, test matrix, review outcome, #128-context note, resolution link).
- Not-yet-specified: merged-build fog text updated ("3 remaining backend tasks (#128–#130)").
- Closing paragraph: frontier = 4 unblocked (#128–#130, #135); #131 closed with commit + resolution link; whole #105 set down to #128–#130.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for the AFK backend tasks #128–#130 (parallel Standards + Spec; round 2 on the fix delta) or the #135 frontend build.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
