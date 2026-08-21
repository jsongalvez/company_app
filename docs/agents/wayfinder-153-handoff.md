# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 50

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 50 picked the #152 handoff's top pick — the **#117-fog expense-GET soft-deleted gate** — as **#153 ("Grilling — Expense list: include soft-deleted rows (dimmed + reason) or exclude (Finance build gate, graduated from #117 fog)")**: `wayfinder:grilling` HITL decision ticket, created + wired as a child of #89 + claimed (assigned @me), resolved live in 3 grilling rounds, resolution comment posted, ticket closed. Resolution: https://github.com/jsongalvez/company_app/issues/153#issuecomment-5288004152. The map's merged Finance & Reports fog line **graduated → #154** ("Build — merged Finance & Reports screens (graduated from #101 + #105)", `wayfinder:task`, child of #89, **unclaimed**). Map #89 updated (Decisions-so-far #153 entry + child-tickets table rows #153/#154 + stale #129/#130/#135 table rows corrected to ✅ closed + merged-build fog line removed + frontier paragraph rewritten).

**Next-session state:** **1 unblocked ticket — #154 (Build — merged Finance & Reports screens)**. The last decision gate on the map (the expense-GET soft-deleted question) is now closed; the map is in full build-graduation territory.

## Session outcome

**#153 (Grilling — Expense list: include soft-deleted rows (dimmed + reason) or exclude) — created + resolved + closed (HITL).**

- **The question was fog, not a ticket** — the #117 resolution carried the fog note ("The Finance build must decide: D6's dimmed-deleted rows need a payload change"); session opened with 0 unblocked tickets. Graduated the fog line into #153, claimed, resolved live.
- **Q1 keep D6** (user rationale: *the user may want to undo the soft-deletion*): the GET includes soft-deleted rows — `ExpenseRepository.findByBranchDayId` filter flips (`deletedAt.isNull()` removed); the CR-022 exclusion test (`f8b288a`, 2026-07-16 — which ADDED the filter after #101's F4 was true) flips to inclusion; Finance renders deleted rows dimmed + "removed" tag + reason; P&L totals untouched (view already excludes).
- **Q2 `deleted_reason TEXT NULL` via V19** — written in the same `UPDATE` as `softDelete` (the DELETE route already requires reason). I recommended the audit-log lookup first; the user pushed back ("no migration hygiene necessary, we're still in development with no real users. is A really our best option?") and was right: the row already denormalizes `deleted_by`/`deleted_at` (display-state precedent), migrations are free in dev, and the GET stays single-query. Audit row remains the history record.
- **Q3 dimmed rows read-only, except Restore** (the user's undo intent: "unless the user wants to undo the deletion").
- **Q4 dedicated `POST /api/expenses/{expenseId}/restore`** — user asked why the PATCH-on-deleted 400 was locked and whether flag-PATCH would've been better; answered: the 400 was a *product* guard (#117: "can't edit history; delete+re-log is the flow"), never a REST judgment about restore (restore wasn't in the picture); flag-PATCH would mix full-replace content semantics with a state undo — the mixed-semantics class (#141/#142). 400 lock stays.
- **Q5 standard mapping**: EDIT_BRANCH_DATA record-scoped + day-state (reason iff REMITTED); 404 missing / 400 already-live / 403 no-cap via domain exceptions; **atomic** `UPDATE ... WHERE (id) AND (deletedAt IS NOT NULL)` (0 rows → 400/404) — closes the double-restore race (the #141 ownership-in-WHERE class); audited `recordUpdate`.
- **Q6 no version bump** on delete/restore (existing `softDelete` already doesn't bump) — user asked "is there even a reason we'd want a strict mutation counter?"; answered: no — its only value would be 409-ing a harmless stale draft (restore changes no content); `expectedVersion` stays stable across an undo.
- **Restore semantics**: clears `deleted_at`/`deleted_by`/`deleted_reason`; restored rows return to full edit/delete affordances; pre-delete reason stays in audit history. `ExpenseResponse.deletedReason` additive nullable.
- **k6 deferred** with the #149/#152 reason (DevSeeder GLOBAL grants can't pass the branch-scoped expense-create gate — no HTTP seeding channel for deleted rows).
- **Fog graduated**: the merged Finance & Reports build fog line → **#154** (created, unclaimed).

## Patterns + learnings (cumulative across sessions)

- **Session-50 additions**:
  - **The grilling route to a build gate**: a fog line that says "opens once X is decided" is a decision ticket waiting to be written — the question was sharp (keep-or-filter + the outline's line 92 explicitly flagged it), so graduating it took one issue create, not a charting session.
  - **Reconsider-a-recommendation discipline**: the user's Q2 push-back was correct on the merits (denormalization precedent + free migrations + single-query GET); the concession was the right outcome — my initial pick defaulted to "no migration" without pricing the query-shape cost. Same class as the #143 rejected-HARDs discipline: a recommendation is a proposal, not a lock.
  - **Lock-history matters in grilling**: Q4's answer (why the PATCH-400 lock exists) required reading #117's resolution + the service guard — the "why was this locked" question is a resolution-comment lookup, not a design question.
- Prior-session patterns unchanged: HITL flow = graduate fog → create → claim → resolve live (never answer the user's side) → resolution comment → close → map Decisions-so-far + child-tickets row + frontier paragraph → handoff. One ticket per session. Branch untouched this session (decision-only).
- **Map hygiene fix**: child-tickets table rows #129/#130/#135 were stale ("🔓 unblocked" for long-closed tickets) — corrected to ✅ closed. Truth-class record: verify table rows against `gh issue list` when editing.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD; pre-push gate excludes iOS intentionally (`.githooks/pre-push:23-30`).
- **`testDebugUnitTest` has a pre-existing 1-failure flake** (SessionBootstrapViewModelTest teardown) — not gated, not mine, recorded.
- **Flyway migration numbering**: V1–V18 taken; **V19 is next** (the #153 `deleted_reason` column).
- **Root AGENTS.md pre-push-iOS line still stale** (says iOS compiled; the hook compiles desktop+android only) — truth-class record, doc-fix candidate. Unchanged this session.
- **#152 handoff note**: the handoff called this pick "the #117 expense-GET-soft-deleted HITL gate" — #117 itself is CLOSED (the read-back build); the gate lived in its fog note. Referred to correctly as the #117-fog question.

## Current frontier (verified live post-session)

Per `gh issue list --state open`: only #89 (map), #110 (standalone hardcoded-month test fix, NOT a child), #139 (standalone OpenAPI map) open. **1 open child of #89: #154 (Build — merged Finance & Reports screens), unclaimed** — created+graduated this session. All closed-child states verified: #129/#130/#135 CLOSED (table rows corrected).

## Recommended next picks

- **#154 (Build — merged Finance & Reports screens)** — the only unblocked ticket; spec = #101 D1–D8 + #105 decisions consuming #117/#128–#131/#153 backend (the #153 expense spec homes there: GET filter flip, V19 `deleted_reason`, restore endpoint, `ExpenseResponse.deletedReason`). AFK-friendly but large (two screens); the phased review loop applies. k6 for expense GET + restore deferred (seeding channel).
- **Remaining Not-yet-specified candidates** — unchanged: keep-last-results port, user-create flow, non-admin self-slot-edit, F7, desktop token-storage, pushed-route topbar (now MORE load-bearing — desktop has two pushed routes), audit branch-name, detekt gate strategy, #110, route-path constants, Clients search-field pop-back reset, hardcoded-month test dates, the #106 grant-UI fog, the root-AGENTS.md stale pre-push-iOS line, the SessionState context-model divergence (#99 F7 — cross-cutting, deserves its own session).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-51 has **one unblocked ticket: #154** — claim it (`gh issue edit 154 --add-assignee @me` — verify no concurrent sessions) and build it.
3. AFK flow: /implement per module AGENTS.md (read `backend/AGENTS.md` + `composeApp/AGENTS.md` — Finance+Reports is a combined build), the phased loop with `git diff <last-pass-commit>` per pass, batch-fix commits, exit at one full pass with zero HARD. Register classes to watch: count-0, ownership-in-WHERE (#141 class), comment-truth (READ the hook files/AGENTS.md lines a phase cites), fix-that-didn't-land, the Exposed insert-lambda table-receiver trap (seed helpers: name params like the columns), mixed-semantics endpoints (#141/#142/#153 Q4 class).
4. Post the answer as a **resolution comment**, then `gh issue close 154`, then append a context pointer to map #89's Decisions-so-far + update the child-tickets table row + rewrite the frontier paragraph (`gh issue edit 89 --body-file <modified>`).
5. Graduate fog (create-then-wire): `gh issue create --label wayfinder:<type>` → child via `gh issue edit <n> --parent 89` (verify via `gh issue view <n> --json parent`).
6. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Child-tickets table: #153 row added (✅ closed), #154 row added (🔓 unblocked), stale #129/#130/#135 rows corrected to ✅ closed.
- Decisions-so-far: new #153 entry (full decision record: six locked decisions, the Q2 concession, the Q4 lock-history, k6 deferral, resolution link), inserted before Not-yet-specified.
- Not yet specified: the merged Finance & Reports fog line removed (graduated to #154).
- Frontier paragraph: rewritten — #153 outcome first (the last decision gate closed), #154 as the new frontier pick, remaining chartable fog listed.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session claims #154).
- **`/implement` + `docs/agents/code-review-loop.md`** — for the #154 build (the merged Finance & Reports screens; the register now carries the Exposed table-receiver trap + mixed-semantics classes).
- **`/grilling` + `/domain-modeling`** — if a fog line graduates into a decision instead (e.g. the SessionState context-model divergence, #99 F7).
