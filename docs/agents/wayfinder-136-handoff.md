# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 35

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 35 resolved the AFK task **#136** ("Guard client anonymization against PENDING sessions (graduated from #99 fog)") — `wayfinder:task`, driven by the agent alone (the map remains 100% AFK). Ticket created this session (first fog graduation since the #106 set completed), resolved, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/136#issuecomment-5236407434. Commit `2d28680` on `ralph/company-app-full-build` (7 files, +160/−18, not pushed; pre-commit gate passed: ktlint/detekt/752 tests/cleanliness/shared-compile). Map #89 updated (Decisions-so-far #136 entry, anonymize fog line removed, frontier paragraph rewritten).

**Next-session state:** **0 unblocked tickets** — unchanged (the new ticket was created AND closed in-session).

## Session outcome

**#136 (Guard client anonymization against PENDING sessions) — created + resolved + closed (AFK).**

- **Created the ticket** (user pick: "AFK hardening ticket" over the HITL-ish merged Finance & Reports build / #94-grad build / #117-alone). Sharpest candidate fog: the #99 anonymize line, explicitly marked "candidate for a backend hardening ticket or an ADR note" in the map body. Wired as sub-issue of map #89, claimed, then the usual AFK flow.
- **Shipped**: `ClientService.anonymize` now throws `ConflictException` (409) "Client has an active PENDING session; complete or cancel it before anonymizing" when the client has a PENDING session. Guard runs **inside the anonymize transaction** (new `guardFn` callback on `ClientRepository.anonymize` — the `auditFn` shape), so a failed anonymize leaves PII + `deleted_at` untouched and writes zero audit rows (test-locked). The guard takes the same client-row `FOR UPDATE` lock as session-create first (`acquireClientLock` + `hasActivePendingSessionInTransaction`, both promoted private→top-level in SessionRepository.kt, mirroring the `findSessionByIdInTransaction` top-level precedent).
- **Reverse gap closed too**: `SessionRepository.create` now 409s "Cannot create a session for an anonymized client" when the locked client row has `deletedAt` set — so the husk+PENDING state is unreachable from either ordering (create-first: anonymize 409s; anonymize-first: create 409s). This was a round-2 HARD finding.
- **Round-2 review caught a pre-existing lazy-lock bug class**: Exposed `Query.forUpdate()` only sets a flag — without a terminal op (`singleOrNull()`/`toList()`/…) no `SELECT ... FOR UPDATE` ever executes. `SessionRepository.acquireClientLock` (pre-existing since CR-018) AND `ProductSaleRepository.acquireInventoryLock` (inventory TOCTOU, same commit class) both discarded their queries — silent no-ops. Both materialized via `.singleOrNull()`, matching the working `RemittanceRepository`/`ReliefAccessRepository` precedents (verified against exposed-core 1.3.1 jar sources by the round-2 reviewer). The anonymize guard therefore actually serializes now.
- **Key decisions**: (1) BLOCK not auto-cancel — anonymization is irreversible, operator must deliberately complete/cancel the session first; zero session mutation in the flow (re-verified round 2); (2) 409-not-400 — resource exists, state conflicts (the `hasActivePendingSession` create-side precedent); (3) 404-before-409 — existence first, then state; (4) husk-with-PENDING now 409s (was 404 — surfaces the stale linkage, more informative; husk-without-PENDING still 404s, pinned by the existing test); (5) bundled same-class fix: `ProductSaleRepository.acquireInventoryLock` materialized (documented in resolution).
- **Tests** +6 (suite 746→752): service — anonymize 409 keeps PII + `deletedAt` null + audit count unchanged (create's 1 row only); COMPLETED-only history succeeds; NO_SHOW+CANCELLED history succeeds; session-create-on-anonymized 409s; route — 409 through HTTP keeps PII + `deletedAt` null, 204 on the clean path. **No concurrency-interleave test** (flaky at this suite's shape) — the deterministic create-side guard test pins the invariant instead; accepted tradeoff, noted in resolution.
- **Review**: /code-review rounds 1+2. r1 — Standards: 0 HARD, 2 SOFT (audit-invariant test; NITs: deletedAt assertion on route test) + Spec: 2 SOFT (check+write atomicity — wanted the client lock; NO_SHOW/CANCELLED coverage), all fixed. r2 (fix delta vs `tmp/review-r1` commit) — **2 HARD**: the lock was a lazy no-op (above), and the create-side deletedAt guard was missing — both fixed + NIT (stale comment). No third round; the round-2 reviewers' proposed fixes were applied verbatim. **Round-1→2 lesson: verify a "lock" actually executes SQL — Exposed queries are lazy; the suite's green 746 didn't catch it.**
- **k6 deferred** (#98/#115 precedent).

## Patterns + learnings (cumulative across sessions)

- Session-35 additions:
  - **Exposed `forUpdate` laziness trap** — `Query.forUpdate()` is a flag-setter; execution happens on a terminal op. A discarded `.forUpdate()` chain = a silently dead lock. The working pattern is `.forUpdate(...).singleOrNull()` (RemittanceRepository.kt:301-306). **Check any existing "lock" helper for a terminal op before relying on it** — two pre-existing dead locks were found this way.
  - **Cross-repo reuse via top-level in-transaction functions** — the established pattern in SessionRepository.kt (`findSessionByIdInTransaction`, `toSession`): promote private in-transaction helpers to top-level public for cross-repository calls instead of duplicating queries. Used for `hasActivePendingSessionInTransaction` + `acquireClientLock`.
  - **Guard-callback in repo transactions** — `guardFn: () -> Unit = {}` param (like the existing `auditFn`) runs the domain check inside the write's transaction → check+write atomic on one connection; 404-before-409 preserved by resolving existence in the service first.
  - **Tmp-branch review-point trick** — for round-2 fix-delta reviews without committing the feature: snapshot the round-1 tree (reverse-apply fixes), commit to a throwaway branch (`tmp/review-r1`), restore the fixed tree, then `git diff tmp/review-r1` IS the fix delta. Reset --soft + delete branch afterwards for a single clean feature commit.
- Prior-session patterns unchanged: AFK flow = /implement per the module AGENTS.md, MockEngine commonTest, quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred, resolution comment → close → map Decisions-so-far + frontier paragraph + fog cleanup + handoff.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V18 taken; next is **V19** (`ls backend/src/main/resources/db/migration/` before picking).
- **PG enum same-transaction rule** (from #132): `ALTER TYPE ... ADD VALUE` + use in one migration = 55P04; split into two migrations.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100`:

**0 open children.** All graduated tickets in the #105 and #106 sets are closed; #136 (created+closed this session) cleared the last graduated fog line.

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next picks

- **Merged Finance & Reports build** (from #101 + #105) — the big remaining build; its data surface is fully built (feed #130, exports #128/#129/#131, accessible-branches picker #131). **The only gate is #117** — the expense-GET-includes-soft-deleted question (dimmed-deleted rows need a payload change per #101 D6). Graduating it starts with deciding #117 — **HITL-ish (a UX decision; the map is 100% AFK so far — first HITL decision session; flag to the user)**.
- **Remaining candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog). Both are map-fog candidates like #136 was; the #136 pattern (ticket with falsification context + decision in the body, AFK) worked well.
- **New session-35 fog candidates**: (a) the create-side guard means `SessionRepository.create`'s idempotent-retry path (`findById` before the lock) still lets a retry return an existing session for an anonymized husk if one pre-exists — pre-existing husk+PENDING rows from before the guard can't be created anymore but old ones aren't cleaned (worth a data-cleanup thought only if real data exists — pre-launch, probably moot); (b) no concurrency-interleave test pins the lock serialization (accepted tradeoff).
- **#94-grad Build — login + capabilities fetch + BranchSelect + SessionState writer integration** — ungraduated; its land populates `SessionState.capabilities`/`currentUser`, which activates #135's drawer item + route gate (and every other code-only-gated drawer item). Check whether a #94-grad ticket already exists (#94 itself is closed as a prototype).
- **Non-admin self-slot-edit (BR:67)** + **user-create flow (must assign roles)** + **relief-invite flow** — placement settled (BranchSelect territory / #106 fog), all post-launch-fog.

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill (`.agents/skills/wayfinder/SKILL.md`), tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-36 has NO unblocked ticket — pick from "Recommended next picks": either graduate the merged Finance & Reports build (decide #117 first — likely HITL grilling), graduate another backend hardening fog (AFK, #136 pattern), or graduate #94-grad. If graduating a fog: `gh issue create` → sub-issue link → wire blocking → claim, then the usual AFK flow.
3. Claim BEFORE work: `gh issue edit <N> --add-assignee @me` — verify no concurrent sessions (sub-issues assignees empty).
4. AFK flow: /implement per module AGENTS.md (backend or composeApp) + /code-review rounds 1+2 (parallel Standards + Spec; round 2 on the fix delta; use the tmp-branch review-point trick if the feature is uncommitted).
5. Post the answer as a **resolution comment**, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND clear graduated fog lines).
6. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
7. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #136 entry (guard + create-side guard + lazy-lock bug class + key decisions + tests 746→752 + review outcome + resolution link; "#99 fog line resolved").
- Not-yet-specified: **anonymize-with-PENDING-session fog line removed** (resolved — lives only as its #136 ticket). All other fog unchanged.
- Frontier paragraph: rewritten with the #136 session outcome first (created+closed, 2 review rounds, lazy-lock finding), rest reworded "closed previous session", 0 unblocked held.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket or decide #117 first — 0 unblocked).
- **`/grilling` + `/domain-modeling`** — if the merged Finance & Reports build graduates this next: the #117 expense-GET-soft-deleted decision is HITL.
- **`/implement` + `/code-review`** — for either an AFK hardening ticket or the merged build (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
