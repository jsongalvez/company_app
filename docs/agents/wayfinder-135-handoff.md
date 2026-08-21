# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 34

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 34 resolved the AFK task **#135** ("Build — User Management screen (graduated from #106)") — `wayfinder:task`, driven by the agent alone (the map is 100% AFK). Resolution comment: https://github.com/jsongalvez/company_app/issues/135#issuecomment-5236044868. Ticket closed; map #89 updated (Decisions-so-far #135 entry, frontier paragraph 1 unblocked → **0 unblocked**, merged-build fog line unchanged — still gated on #117). Commit `a3c80c2` on `ralph/company-app-full-build` (7 files, +1658/−6, not pushed — k6 deferred per #98/#115 precedent; pre-commit gate passed: ktlint/detekt/746 backend tests/cleanliness/shared-compile).

**Next-session state:** **0 unblocked tickets** — the #106-graduated set is now COMPLETE (#132 grant path + #133 user backend + #134 gate hardening + #135 build). Both graduated sets (#105, #106) fully closed.

## Session outcome

**#135 (Build — User Management screen) — resolved + closed (AFK).**

- **Shipped** the locked #106 outline D2–D5 with the previously-unused `UserViewModel` extended (not recreated): D2 flat user list (displayName/username/status badge/"deactivated X ago" via existing `formatRelativeTimestamp`/assigned branches w/ slots), client-side instant search (`filterUsers`), expandable rows, deactivated dimmed (`DEACTIVATED_ROW_ALPHA` shared token) + slot controls disabled; D3 deactivate = confirm dialog → existing PATCH, reactivate = direct → #133 PATCH, self-deactivate hidden on own row, 204 updates row in place; D4 branch dropdown (branchType shown, `GET /api/branches`) + slot-order manager (slot ASC, display-name tiebreak — `slotOrderForBranch`): desktop ▲/▼ arrows = pairwise `POST /slots/swap` w/ neighbor, **neighbor pair gated on both active**; mobile tap-to-edit = `PATCH slot`; manual number fallback both (`EditSlotDialog`, `parseSlotInput` = backend-400 parity); duplicate slots tolerated (BR:67); D5 single route pushed both platforms, code-only `MANAGE_USERS` route gate (#99 D7), placeholders replaced; **drawer item stays hidden until #94-grad capability wiring — documented, not hacked** (ticket note).
- **Key decisions**: (1) single data source (`GET /api/users` drives list + slot manager; #133's not-ended assignment joint keeps deactivated users' rows, dimmed+disabled per D2); (2) pessimistic-only mutations (ADR-0022) — 2xx mutates in place, any failure keeps row + inline per-action `actionErrors` keyed by the `inFlight` key; **network failures surface inline errors too** (round-1 caught the silent-path gap: the throwaway flow's Error was never rendered); no auto-reload on 4xx (#123 acknowledge precedent; Refresh button is the reload — the outline's "inline error + reload" read as ADR-0022's no-optimistic-mutation); (3) error surfaces split (round-1 double-render fix): swap errors → slot card only; slot/deactivate/reactivate errors → user row only (covers no-branch-selected slot edits); (4) shared `UserSlotOrderCard` chrome extracted — platform actuals differ only in the #95 row behavior (desktop arrows + Edit fallback, android tap-to-edit); (5) client-synthesized `deactivatedAt` on the bodyless 204 (`?: now`, preserves existing stamp — the #133 idempotent-pair mirror); (6) accepted scope-additions: Refresh button (#123 precedent), "No branch" dropdown option, "You can't deactivate your own account" caption.
- **Tests**: 20 new `UserManagementViewModelTest` commonTest (desktop suite 97 → 117): deactivate/reactivate in-place + failure + double-tap single-request guard, **403/409/400/500 axes**, network-failure inline error + in-flight clear, swap/slot request bodies contain the right ids/number (op-mapping — the ticket's "nontrivial frontend logic"), pure `slotOrderForBranch` (branch filter + slot-ASC/name-tiebreak + deactivated flag + branchless skip), `parseSlotInput`, `filterUsers`. Desktop + Android compile green, ktlint clean.
- **Review**: /code-review rounds 1+2 PASS. r1 — Standards: 1 hard (logWarn on `UiState.Error` branches, composeApp/AGENTS.md) + 2 judgement dedups (DEACTIVATED_ROW_ALPHA ×3 → shared internal const; slot-card chrome ×2 → `UserSlotOrderCard`), fixed; Spec: 1 real gap (silent network-failure path — half of the pessimistic contract), 2 looks-wrong (desktop swap offering a deactivated-neighbor pair that the backend can only reject; swap/slot error double-render), 3 harmless scope-additions accepted — fixed. r2 — fix delta clean both axes; 3 cosmetic nits fixed directly (LaunchedEffect logWarn so a sticky error doesn't re-log per recomposition; comment accuracy on the catch scope; always-rendered disabled arrows replacing a "·" placeholder — keeps the column footprint AND the disabled affordance).
- **Global-testing learnings (none new this session)**: the VM tests used the #93 handler-based MockEngine + `runTest(testScheduler)`/StandardTestDispatcher shape; only re-confirmation that test-file-private top-level `jsonRespond` naming collides across files in the same package (renamed to `jsonResponse`), and that a member extension on `MockRequestHandleScope` isn't reachable from a sibling class in the same file (must be file-top-level).

## Patterns + learnings (cumulative across sessions)

- Session-34 additions (frontend build conventions, per airflow of #135):
  - **Pessimistic mutation harness** (ADR-0022) — the AuditLog acknowledge shape generalized into a `runMutation` helper: explicit in-flight key (added pre-flight, cleared in transform/onNonSuccess/catch), per-key `actionErrors`, throwaway `UiState<Unit>` flow so mutations never clobber the list; **network failures must ALSO write the inline error in the catch** (the throwaway flow's Error is invisible to the screen — the round-1 gap; test-guarded).
  - **Pure-helper extraction for commonTest** — slot-order derivation, slot-number parsing, search filter live as top-level functions in the VM file (like `AuditLogFilters`) and get direct unit tests absent any compose-UI test framework.
  - **Shared-card + platform-row split** (`UserSlotOrderCard` + expect/actual `UserSlotOrderList`) — the #123 AuditLogEntryList pattern refined: hoist the shared card chrome into commonMain, scope the expect/actual to just the row content that differs per-platform (#95 lock: desktop arrows vs android tap-to-edit); shared responsive tokens (dim alpha) as `internal const` in commonMain.
  - **Swap-vs-PATCH op mapping** (D4): desktop = pairwise swap with the NEIGHBOR row (index math over the slot-ASC list); mobile/manual = PATCH absolute slot. Both idempotent-safe at this scale; duplicates never trigger a renumber cascade (BR:67).
- Prior-session patterns unchanged: AFK flow = /implement per the module AGENTS.md, MockEngine commonTest, quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred (#98/#115 precedent), resolution comment → close → map Decisions-so-far + frontier paragraph + handoff.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.
- **Flyway migration numbering**: V1–V18 taken; next is **V19** (`ls backend/src/main/resources/db/migration/` before picking).
- **PG enum same-transaction rule** (from #132): `ALTER TYPE ... ADD VALUE` + use in one migration = 55P04; split into two migrations.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100`:

**0 open children.** All graduated tickets in both the #105 and #106 sets are closed. 

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89.)

## Recommended next picks

- **Merged Finance & Reports build** (from #101 + #105) — the big remaining build; its data surface is fully built (feed #130, exports #128/#129/#131, accessible-branches picker #131). **The only gate is #117** — the expense-GET-includes-soft-deleted question (dimmed-deleted rows need a payload change per #101 D6). Graduating it likely starts with deciding #117, possibly a small backend ticket first. **This is the natural next wayfinder ticket** — but it's HITL-ish (a UX decision per #101/#105 D-series; the map is 100% AFK so far, so this would be the map's first HITL decision session — flag to the user).
- **Candidate backend fog (small AFK hardening tickets)**: cross-draft line-session raw 500 (#120 fog), `getForSession` read-path day-gate 400 (#124 fog), anonymize-with-PENDING-session guard (#99 fog).
- **#94-grad Build — login + capabilities fetch + BranchSelect + SessionState writer integration** — ungraduated; its land populates `SessionState.capabilities`/`currentUser`, which is what activates #135's drawer item + route gate (and every other code-only-gated drawer item). It's the map's biggest lever for making the built screens actually reachable. Check whether a #94-grad ticket already exists (#94 itself is closed as a prototype).
- **#117 itself**: the expense-GET-includes-soft-deleted question; the last gate before the merged build opens.
- **Non-admin self-slot-edit (BR:67)** + **user-create flow (must assign roles)** + **relief-invite flow** — placement settled (BranchSelect territory / #106 fog), all post-launch-fog.

## How to drive the next session (wayfinder "Work through the map")

1. Load the map (https://github.com/jsongalvez/company_app/issues/89), the wayfinder skill (`.agents/skills/wayfinder/SKILL.md`), tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations").
2. Session-35 has NO unblocked ticket — pick one of the "Recommended next picks": either graduate the merged Finance & Reports build (decide #117 first — likely HITL grilling) or graduate a candidate backend hardening fog (AFK). If choosing a backend hardening ticket: `gh issue create` → sub-issue link → wire blocking, then the usual AFK flow.
3. Claim BEFORE work: `gh issue edit <N> --add-assignee @me` — verify no concurrent sessions (sub-issues assignees empty).
4. AFK flow: /implement per module AGENTS.md (backend or composeApp) + /code-review rounds 1+2 (parallel Standards + Spec; round 2 on the fix delta).
5. Post the answer as a **resolution comment**, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update the frontier paragraph AND any fog lines as counts change).
6. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
7. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Decisions-so-far: new #135 entry (built D2–D5 summary, key decisions incl. the round-1 network-failure gap + deactivated-neighbor swap gating + single-data-source + pessimistic-mutation contract, tests 97→117, review outcome, resolution link; "#106-grad set now COMPLETE").
- Frontier paragraph: 1 unblocked → **0 unblocked** (#135 close sentence + new frontier = 0; both graduated sets fully closed; next work waits on fog graduation or the #117-gated merged build).
- Not-yet-specified: merged-build fog line untouched (still "opens once the #117 question is decided"); all other fog unchanged.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps (note: next session must pick/graduate a ticket or decide #117 first — 0 unblocked).
- **`/grilling` + `/domain-modeling`** — if the merged Finance & Reports build graduates this next: the #117 expense-GET-soft-deleted decision is HITL.
- **`/implement` + `/code-review`** — for either an AFK hardening ticket or the merged build (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
