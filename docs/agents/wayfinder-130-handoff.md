# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 33

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 33 resolved the AFK task **#130** ("Add paged daily-summaries endpoint for the Reports feed" — graduated from #105) — `wayfinder:task`, driven by the agent alone (the map is 100% AFK). Resolution comment: https://github.com/jsongalvez/company_app/issues/130#issuecomment-5235700440. Ticket closed; map #89 updated (Decisions-so-far #130 entry, fog + merged-build opening condition now "once #117's expense-GET question is decided", frontier paragraph 2→1 unblocked + new #130 close sentence, "#105-grad set now COMPLETE"). Commit `17aa367` on `ralph/company-app-full-build` (11 files, +431/−40, not pushed — k6 deferred per #98/#115 precedent; pre-commit gate passed: ktlint/detekt/746 tests/cleanliness/shared-compile).

**Next session pick:** the **1 remaining unblocked ticket** (AFK):

- **#135 Build — User Management screen** — unblocked (`blocked_by: 0`); spec locked (#106 D2–D5, wire existing unused `UserViewModel`; drawer visibility waits on #94-grad caps wiring). Backend surface complete + authz-tested (#132/#133/#134).
- **Merged Finance & Reports build ticket** — ungraduated; opens when the #117 expense-GET-includes-soft-deleted question is decided (all #105-grad backend tasks #128–#131 have now landed — the merged screen's data surface is fully built: accessible-branches picker, paged daily-summaries feed, range/daily/monthly/all-time exports, public branch-type exports).
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).
- **#117 itself** — the expense-GET-includes-soft-deleted question (dimmed-deleted rows need a payload change per #101 D6) — the merged-screen build's last gate.

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #130 entry, Not-yet-specified, Out-of-scope, frontier paragraph)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#130 resolution comment: https://github.com/jsongalvez/company_app/issues/130#issuecomment-5235700440**
- Prior handoff: `docs/agents/wayfinder-129-handoff.md` (this session's parent)

## Session outcome

**#130 (Paged daily-summaries endpoint for the Reports feed) — resolved + closed (AFK).**

- **Falsification — the premise held, with one gate nuance**: no paged/list endpoint existed (single-date `/daily-summary?date=` + #129's range fetch only). The gate nuance: the #131 wildcard `/export/*` AND the singular `/daily-summary` before-filter do NOT cover the plural `/daily-summaries` path (Javalin 7 exact-segment matching — the #114 lesson, 6th occurrence), so "same access scope as the Reports-access ticket" required a **new before-filter** — shipped with the identical `requireBranchOrGlobalCapabilityForBranchId` shape and matrix-proven.
- **Shipped**: `GET /api/branches/{branchId}/daily-summaries?cursor=&limit=` — keyset `(date, branch_day_id) DESC` over `daily_sales_summary` (every `branch_day` row; zero-activity days included), strictly-before opaque base64url cursor; **`CursorCodec.kt` = shared codec extracted from #122** (audit's `encodeCursor`/`decodeCursor` delegate to it — byte-identical format; per-type decode messages preserved via `runCatching`+`getOrElse`); `DailySummaryBrowseCursor` + `encodeDailySummaryCursor`/`decodeDailySummaryCursor`; `DailySalesSummaryService.browseDailySummaries` (limit+1 → hasMore → dropLast → nextCursor — the #122 shape verbatim; 404 "Branch not found", **200-empty feed** on day-less branch); shared DTO `DailySalesSummaryBrowseResponse(entries, nextCursor)` (audit-browse twin); `DailySalesSummary.toResponse()` mapper moved route-private → service top-level (the #122 audit mapper pattern — single source, used by single-date route + feed); `parseLimit` deduped → `RoutesUtil.parseBrowseLimit` (round-1 review finding; #122 route behavior byte-identical).
- **Key semantics**: **gate-pass proven by 200, not 404** — a feed is 200-empty for a day-less branch, unlike the single-date 404-on-missing-data; the authz matrix locks it (zero-grant 403; branch-scoped 200 on granted / 403 on other; GLOBAL 200 on a zero-grant branch). Limit default 20, max 100 (the #122 bounds). 400s: malformed cursor, limit 0 / >100 / non-numeric.
- **Tests**: +17 (suite 746, was 729). Service (6): DESC order, limit+nextCursor, full cursor walk no-dup/no-skip (5 days, limit=2, newest-first), zero-activity days as exact zero rows `0.00,...,0.0000`, empty feed, 404 unknown branch. Route (6): 400s (limit 0/101/non-numeric, malformed cursor) + 200-with-seeded-day + 200-empty-feed. Authz matrix (4): the browse gate-pass-200 section.
- **New test-tooling learning — the #133 Exposed receiver trap bit again in seeding**: `insert { it[BranchDayTable.branchId] = branchId }` with a class-property `branchId` silently emits `branch_day.branch_id` in VALUES (PG 42P01 "invalid reference to FROM-clause entry"); fix = `this@ClassName.branchId` qualification. Rule: inside `insert {}` the receiver is the TABLE — local params win, class properties lose (columns win over class properties).
- **Review**: /code-review rounds 1+2 PASS (r1: 0 hard violations both axes; 2 soft fixes applied — parseBrowseLimit dedup, cursor message preservation through the shared codec; r2: fix delta clean both axes — both reviewers verified the audit route's limit behavior stayed byte-identical). Non-blocking note (accepted, not fixed): decode exception messages stay internal — both routes wrap to `BadRequestResponse("Invalid cursor")` (pre-existing #122 behavior, unchanged).

## Patterns + learnings (cumulative across sessions)

- **NEW — the #133 Exposed receiver trap extends to test seeding with class properties**: `insert {}` lambda receiver = the TABLE; unqualified names colliding with columns resolve to COLUMNS (local params/locals win, **class properties lose**). A test class property named like a column (`branchId`) passed as `it[BranchDayTable.branchId] = branchId` emits the column itself in VALUES → PG 42P01. Fix: `this@OuterClass.property` qualification. (Production code was safe via `params.*`; tests now have the same guard documented in a KDoc-less one-line comment in `DailySalesSummaryBrowsePostgresTest`.)
- **NEW — cursor codecs are now shared**: `encodeOpaqueCursor`/`decodeOpaqueCursor` (base64url, `|`-joined parts) live in `backend/.../repository/CursorCodec.kt`; per-browse-type `encodeCursor`/`decodeCursor` wrappers preserve their own internal error messages (`runCatching { codec }.getOrElse { throw IllegalArgumentException("<type> cursor") }` — the SwallowedException-safe shape). Future paginated endpoints: add a cursor data class + wrapper pair, reuse the codec.
- **NEW — a browse feed gate-pass is proven by 200, not 404**: export/single-date routes prove gate-pass deterministically via 404 (handler ran, no data); a feed returns 200-empty — the authz matrix must assert 200 for gate-pass on feed routes (the `ReportsReadScopeAuthzTest` daily-summaries section documents this).
- **NEW — `parseBrowseLimit` is the shared limit parser** (`RoutesUtil.kt`, same package as both route objects, no import needed): default 20 / max 100, used by audit browse (#122) + daily-summaries browse. Don't re-roll private copies in future paginated routes.
- Prior-session patterns unchanged: AFK flow = /implement per backend/AGENTS.md, red-first regression tests, quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred (#98/#115 precedent), resolution comment → close → map Decisions-so-far + fog + frontier paragraph, handoff.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V17 taken; **next is V18** (`ls backend/src/main/resources/db/migration/` before picking).
- **PG enum same-transaction rule** (from #132): `ALTER TYPE ... ADD VALUE` + use in one migration = 55P04; split into two migrations.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100`:

| # | Title | Type | Status |
|---|-------|------|--------|
| 135 | Build — User Management screen (graduated from #106) | task | 🔓 unblocked (verified `blocked_by: 0`) |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #130 closed this session — **the whole #105-graduated backend set (#128–#131) is now COMPLETE**; the #106-graduated set is down to #135.)

## Recommended next pick

- **#135 Build — User Management** — the only unblocked ticket; spec locked (#106 D2–D5), backend surface complete and authz-tested (#132 grant path, #133 list/reactivate/`deactivated_at`/self-guard, #134 gate hardening — all closed); `UserViewModel` already exists unused. Frontend build conventions per `composeApp/AGENTS.md` + /code-review rounds 1+2.
- **Merged Finance & Reports build** — ungraduated; its data surface is now fully built (feed + exports + picker); the only remaining gate is the #117 expense-GET-includes-soft-deleted question (dimmed-deleted rows need a payload change per #101 D6). Graduating it needs that question decided first — it may itself be a small backend ticket before the build opens.
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK flow (#135): /implement per `composeApp/AGENTS.md` (build conventions, ApiCallHandler, ViewModel patterns) — backend is done, no backend work expected unless a 400/403 surfaces; /code-review rounds 1+2 (parallel Standards + Spec; round 2 on the fix delta).
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND the merged-build fog line as counts change).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #130 entry (falsification incl. the exact-segment gate nuance, feed semantics 200-not-404, cursor-codec extraction, parseBrowseLimit dedup, the #133 receiver-trap-in-tests learning, review outcome, resolution link).
- Not-yet-specified: merged-build fog updated ("opens once #130 lands + #117 question" → "opens once the #117 question is decided; all #105-grad backend tasks #128–#131 landed").
- Frontier paragraph: 2 → 1 unblocked (#135); #130 close sentence (commit + resolution link + one-line gist); "#105-grad set now COMPLETE (#128–#131)" replaces both stale "down to #129–#130 / #130" lines.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for the #135 frontend build (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
