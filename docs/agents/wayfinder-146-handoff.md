# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 44

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 44 ran the **final Tier-1 audit surface**: **#146 ("Audit — day gates #136–#138 (phased loop)")** — `wayfinder:task`, AFK — was created, wired as a child of #89, claimed, run through the phased code-review loop (**2 passes, 2 HARD-class findings, all fixed**), resolved, and closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/146#issuecomment-5253663177. 2 fix commits `917e7cd`→`db189c4` on `ralph/company-app-full-build` (unpushed; each pre-commit gate passed: ktlint/detekt/772 backend tests/cleanliness/shared-compile/Postgres). Map #89 updated (Decisions-so-far #146 entry; #138 entry's "session-detail GET" claim corrected; frontier paragraph rewritten with the #146 outcome + **Tier-1-complete** status).

**Next-session state:** **0 unblocked tickets** — unchanged. **TIER-1 IS COMPLETE** — all 9 surfaces audited: screens #141/#142/#143/#144, backend grants #145, day gates #146. The audit era of the map closes; what remains chartable is the frontend build chain (#97-grad Dashboard next) and the HITL gates (#117, relief-dialog).

## Session outcome

**#146 (Audit — day gates #136–#138 (phased loop)) — created + resolved + closed (AFK).**

- **Created the ticket** (the #145 handoff's recommended last surface: day-gates #136–#138 — closed as builds, never loop-reviewed as surfaces; spec = the tickets' own resolutions), wired child of #89, claimed, then the usual AFK flow. Audit surface = `git diff a1dbc52..291aa5a` (the four commits `2d28680`/`f371ded`/`5b522d4`+`291aa5a`, nothing interleaved).
- **Verdict: PASS — 2 HARD-class findings over 2 loop passes, all fixed in-ticket** (vs 13/9 in #141, 18/21 in #142, 5/5 in #143, 15/9 in #144, 2/2 in #145). The #145 backend calibration held exactly: **both findings were truth/data classes, ZERO interaction-class bugs**. Backend suite 772 green throughout; no migrations, no DTO changes, no shared/ changes, no ADR changes, no k6 (no endpoint surface change).
- **The 2 HARD-class (both caught in pass-1 TRIAGE SYNTHESIS — all four pass-1 phase agents rated them SOFT or missed them)**: (1) **[data] Content-blind idempotent retry** — the #137 round-1 lesson's own class, third occurrence: `RemittanceLineRepository` racedRetry re-read filtered on `id` only, so a client UUID reused for a DIFFERENT entity (session vs product-sale) silently returned the wrong line as "success". Fixed: re-read filtered on the type's ref column (`entityRefCondition`, extracted + also deduping `assertNotAlreadyIncluded`); content mismatch falls through to the duplicate 409. Pass-2 (`db189c4`): the helper fails closed on null entity refs (ValidationException) — the route's type↔ref validation (RemittanceRoutes:341-349) made the null path provably inert at the HTTP surface, but the repo guard now stands alone. (2) **[truth] backend/AGENTS.md:277 "Exposed `Query.forUpdate()` is not available" — FALSE as shipped since #136**: `acquireClientLock`/`acquireInventoryLock`/RemittanceRepository helpers all materialize FOR UPDATE via terminal ops; the doc line would steer a future build back into the lazy-no-op bug class. Rewritten to document the terminal-op requirement. Same batch: the AGENTS.md exception-layering sentence amended (repo-layer guards throw the same domain exceptions inside transactions — pre-existing divergence, per the #145 doc-truth discipline).
- **Cheap SOFTs shipped**: `ClientRepository.anonymize` guardFn now REQUIRED (no `= {}` default — a future repo-level caller can't silently bypass the PENDING guard + lock); the #137 rollback claim PINNED (same-draft duplicate test now asserts version AND audit-count unchanged after the 409 — the resolution claimed "zero audit rows + no version bump" but never asserted it); +1 pure test completing the `assertReadableState` 6-combo matrix (OPEN×cap=true was missing from the claimed "all status×capability combos").
- **Truth-class records (claims falsified, consequence-free — recorded, not fixed)**: (a) the #138 "session-detail GET ... fixed by the one change" claim is FALSE as phrased — no `GET /api/sessions/{id}` exists; the second affected caller is the POST create response (SessionRoutes:172, already write-gated); the one real read path IS fixed. **Map's #138 entry corrected in-session.** (b) #138's "22 write call sites" overstated (18 real + 4 doc mentions); substance (only read-path caller) verified. (c) #138's "12 functions > threshold 11" miscounted (actual 11) **BUT the `@Suppress("TooManyFunctions")` is still REQUIRED** — detekt flags 11 ≥ 11 (empirically proven: pass-1 removal attempt failed the gate, suppress restored).
- **Rejected-HARDs (false premises, evidence-proven — the discipline held)**: create-response read-gate 403 on REMITTED-today creates (inert — any caller reaching the response already passed the WRITE gate with the same capability on that day); racedRetry 409 false-positive on legitimate retries (Ktor resends the identical immutable payload, picker mints fresh UUIDs per tick); TooManyFunctions suppress "dead" (gate failure disproved).
- **Accepted SOFTs (8, reasons in the resolution)**: husk+PENDING create message choice (PENDING-first = operative conflict); racedRetry deletedAt blindness (unreachable — delete needs a line id from a successful add response, retries mint fresh UUIDs; mirrors the pre-existing top-of-function early-return); `acquireClientLock` ResultRow? leak (mild; terminal-op requirement satisfied); same-id/different-content SEQUENTIAL retry returns the other line (spec-mandated idempotency-by-id, first-write-wins); raced-window 409 message names type not entity (pathological UUID reuse; 409 honest); rollback-pin counts whole audit table (deterministic under serial test config); guardFn-required vs auditFn-default asymmetry (deliberate: guard = invariant, audit = bookkeeping); entityRefCondition naming (matches ticket phrasing).
- **Sub-agent note**: all four pass-1 phases ran cleanly (no provider errors this session — no restart chain, no prompt reconstruction needed); the pass-2 delta got the full four-phase re-run. The 2 HARD-class findings surfacing in triage rather than phases is the fresh-code variant of the #145 verification-pass lesson — cheap to catch in synthesis because the phases' own evidence pointed at the spots.

## Patterns + learnings (cumulative across sessions)

- **Session-44 additions**:
  - **The #137 lesson generalizes: ANY thrown-count-0 fallback that returns "existing" must be content-scoped, not id-scoped.** PK-swallow re-reads keyed on `id` alone silently serve a different-content row when a client UUID collides across entity types. The ref-column filter is the shape; a null-ref fail-closed guard makes the repo helper stand alone from route validation.
  - **The backend audit calibration is now double-confirmed (#145 + #146): truth/data classes dominate, interaction-class ~zero, convergence in 2 passes.** The screens' stale-state/interaction reservoir does not exist on the backend surfaces. Audit-time fresh-code blindness (both HARD-class were rated SOFT by pass-1 phases) is real — triage synthesis must re-derive from the phases' own evidence, not just tally their ratings.
  - **Doc-truth checks belong in the audit's own batch**: AGENTS.md:277's forUpdate line had been stale since #136 and would have misdirected a future build; the exception-layering sentence likewise. When the audit touches docs the code contradicts, fix the doc in-ticket (the #145 discipline applied to the module authority doc, not just ADRs).
  - **A claimed test-lock is not a test-lock**: #137's "transaction rollback leaves zero audit rows + no version bump" was structurally true but never asserted — pinning it (version + audit count before/after the 409) took two small assertions. Falsify test-lock CLAIMS the same way as behavior claims.
  - **detekt TooManyFunctions fires at count ≥ threshold** (11 functions at threshold 11 flagged; gate failure is the ground truth — don't trust a phase's "fires only at >11" re-read).
- Prior-session patterns unchanged: AFK flow = quality gate, phased loop per `docs/agents/code-review-loop.md`, resolution comment → close → map Decisions-so-far + frontier paragraph + handoff. k6 N/A (no endpoint changes). Red-first falsification N/A (audit, not build).
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (6 expect-without-actual incl. `AuditLogEntryList` — all pre-date this session; pre-push gate excludes iOS intentionally).
- **Flyway migration numbering**: V1–V18 taken; next is **V19** (`ls backend/src/main/resources/db/migration/` before picking).
- **Test-DB pollution on partial `--tests` runs**: `bash scripts/clean-test-db.sh` before AND after partial backend runs (composeApp desktop tests don't touch Postgres).

## Current frontier (verified live post-session)

Per `gh issue list --state open`: only #89 (map), #110 (standalone hardcoded-month test fix, NOT a child), #139 (standalone OpenAPI map) open. **0 open children of #89** — #146 created+closed this session. **Tier-1 audit stream COMPLETE (9/9 surfaces).**

## Recommended next picks

- **#97-grad Build — Dashboard** (prototype exists: `prototype/0097-session-dashboard`) — the natural next frontend build and the map's primary remaining AFK-able direction; the clock-out → dashboard-state-clear transition fog (#97 outline) is a decision inside it (HITL-ish — use /grilling + /domain-modeling when it graduates). The #141–#144 guard shapes + the #145/#146 backend-truth discipline both carry over.
- **Merged Finance & Reports build** (from #101 + #105) — still gated on the #117 expense-GET-includes-soft-deleted question (HITL UX decision; ungraduated in the map).
- **Relief-request dialog on BranchSelect** — #91's one-liner, needs its own grilling.
- **Remaining Not-yet-specified candidates** — unchanged from the #145 handoff (user-create flow, non-admin self-slot-edit, F7, desktop token-storage, pushed-route topbar, audit branch-name, detekt gate strategy, #110, route-path constants, Clients search-field pop-back reset, keep-last-results port, hardcoded-month test dates).

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-45 has NO unblocked ticket and the audit stream is exhausted — pick from "Recommended next picks". The Dashboard build is the map's natural next move (graduating it = a /grilling for the clock-out decision, then a `wayfinder:task` frontend build child of #89). If the user prefers a backend ticket, the only un-audited backend surface left is... none (Tier-1 complete) — remaining backend work is new builds, not audits.
3. Claim BEFORE work: `gh issue edit <n> --add-assignee @me` — verify no concurrent sessions.
4. AFK flow: red-first falsification, /implement per module AGENTS.md, the phased loop with `git diff <last-pass-commit>` per pass, batch-fix commits, exit at one full pass with zero HARD. For frontend builds: the #141–#144 phase templates in `docs/agents/code-review-loop.md`.
5. Post the answer as a **resolution comment**, then `gh issue close <n>`, then append a context pointer to map #89's Decisions-so-far (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND clear graduated fog lines).
6. Graduate fog (create-then-wire): `gh issue create --label wayfinder:task` → child via `gh issue edit <n> --parent 89` (verify via the child's `parent` field — `gh issue view <n> --json parent`).
7. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #146 entry (full shipped-scope summary + the 2 HARD-class breakdown + the content-blind/two-truth-class catch + Tier-1-complete note + resolution link), inserted at the end of the section.
- #138 entry corrected: "the session-detail GET ... fixed by the one change" → "Both the concerns GET AND the session-create POST response (the only other `getSessionConcerns` embed path; audited in #146 — no session-detail GET endpoint exists) fixed by the one change."
- Frontier paragraph: rewritten — #146 outcome first (2 passes, 2 HARD-class, finding rate, the fresh-code-blindness note), **Tier-1 audit stream COMPLETE** statement, 0 unblocked held.
- Not-yet-specified: unchanged (no new fog this session).

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket — 0 unblocked, audits exhausted).
- **`/grilling` + `/domain-modeling`** — for the #97-grad Dashboard build: the clock-out question is a decision (HITL-ish); same for the merged Finance build's #117 gate.
- **`/implement`** — for the Dashboard build (the #141–#144 guard shapes + #145/#146 backend-truth discipline carry over).
- **`docs/agents/code-review-loop.md`** — the phased loop is now proven across SIX surfaces (4 screens + 2 backend); the #146 resolution is the reference for a 2-pass backend convergence + the fresh-code-blindness triage lesson.
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
