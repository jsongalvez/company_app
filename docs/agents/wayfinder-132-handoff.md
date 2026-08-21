# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 27

## What this is

A wayfinder session on map **#89** ("Frontend rebuild — from scratch to fully-integrated UI"). Session 27 resolved the AFK task **#132** ("Grant path: role→capability view derivation for GLOBAL capabilities") — `wayfinder:task`, driven by the agent alone (no grilling — the map is 100% AFK). Resolution comment: https://github.com/jsongalvez/company_app/issues/132#issuecomment-5231258458. Ticket closed; map #89 updated (Decisions-so-far #132 entry, table row #132 → closed, #135 blocker text → #133+#134, fog line corrected to mechanism-only, closing paragraph). Commit `9bc08d9` on `ralph/company-app-full-build` (7 files, +601/−5, not pushed — k6 deferred per #98/#115 precedent; pre-commit gate passed).

**Next session pick:** any of the **6 unblocked AFK backend tasks** (all independent, all per `backend/AGENTS.md` + /code-review rounds 1+2):

- **#133 User management backend** — GET /api/users list (displayName/username/status/deactivatedAt/assignments+slots) + PATCH reactivate + `deactivated_at` migration (**use V17** — V15/V16 taken by #132; V14 never existed, verified across all branches) + self-deactivate guard. Note: #132's derivation means MANAGE_USERS is now derivable from roles, but with no production seed there are still no role-derived grants outside dev/test — don't let a dead-grant assumption creep into #133's tests (they use direct grants via `DatabaseTestHelper`).
- **#134 Gate hardening** — service-level MANAGE_USERS checks on swapSlots + assignment DELETE (the F3 security hole; authz regression tests proving the leak first). The #114 exact-path lesson, 3rd occurrence — route filters must be verified per-sub-path; service-level checks are the reliable location.
- **#128/#129/#130/#131** — the #105-graduated Reports/Finance backend set. **#131 coordinates with #132**: GLOBAL `VIEW_BRANCH_DATA` now has a real producer (SUPERUSER/ACCOUNTANT roles derive it — verified `AuditLogReadScope.hasGlobalView` → all branches end-to-end); the accessible-branches endpoint should treat derived GLOBAL rows like direct ones (same view, no distinction needed).
- **#135 Build — User Management screen** — blocked on #133+#134 now (native deps auto-dropped #132; verified `blocked_by: 2`); its spec is the #106 outline D2–D5 (wire the existing unused `UserViewModel`; drawer visibility waits on #94-grad caps wiring).

**Read first:**
- The map itself: https://github.com/jsongalvez/company_app/issues/89 (Destination, Notes, Decisions-so-far incl. new #132 entry, Not-yet-specified, Out-of-scope, frontier table)
- Wayfinder skill: `/mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app/.agents/skills/wayfinder/SKILL.md`
- Tracker conventions (GitHub sub-issues + native blocking): `docs/agents/issue-tracker.md` → "Wayfinding operations"
- **#132 resolution comment: https://github.com/jsongalvez/company_app/issues/132#issuecomment-5231258458**
- **ADR-0023** (`docs/adr/0023-global-capabilities-derive-from-roles.md`) — amended with the concrete whitelist + V15/V16 split + seed deviation
- Prior handoff: `docs/agents/wayfinder-106-handoff.md` (this session's parent)

## Session outcome

**#132 (Grant path) — resolved + closed (AFK).**

- **V15__add_role_capability_source.sql** — `ALTER TYPE capability_source_type ADD VALUE 'ROLE'` (view-only source type; nothing writes it to `user_capability`).
- **V16__role_derived_global_capabilities.sql** — `active_user_capabilities` rewritten as a UNION: (a) direct `user_capability` rows (unchanged, INACTIVE-exclusion + valid-window filters intact) + (b) role-derived grants (`user_role`→`role_capability`→`capability`), GLOBAL context + nil UUID, priority 5, `source_type 'ROLE'`. Runtime still checks ONLY the view — the V2 rule is preserved, the view is the single place computing the union. **Migration split forced by PG 55P04** (a stored expression cannot reference an enum value added in the same transaction; Flyway runs one txn per migration) — V15 commits the value, V16 uses it.
- **Derivation whitelist (the ticket's open design question, decided at implementation)**: `MANAGE_USERS`/`ASSIGN_DELEGATE`/`ASSIGN_COMPENSATION` → SUPERUSER/OWNER/MANAGER; `VIEW_BRANCH_DATA` → SUPERUSER/ACCOUNTANT only. COORDINATOR's ASSIGN_COMPENSATION deliberately excluded (V2 "assigned branches only" — GLOBAL derivation would over-grant; test-guarded). BRANCH/BRANCH_DAY-scoped codes (EDIT_BRANCH_DATA, EDIT_PAST_DAY, VOID_SESSION, SUBMIT_REMITTANCE, MANAGE_PRODUCTS) cannot derive — role_capability has no context column; direct grants continue (relief, delegate, future grant UI).
- **Dedup + precedence**: view `DISTINCT ON (user, capability, context)` + `priority DESC`; derived = 5 (`GrantPriorities.ROLE_DERIVED` — above raw default 0, below relief 10 / delegate 20 / direct 100). Explicit direct grant beats role-derived (test-proven).
- **Kotlin**: `CapabilitySourceType` gained `ROLE`; `GrantPriorities` gained `ROLE_DERIVED` (kept in sync with the SQL literals). No other production code changed — `ActiveUserCapabilitiesView`, `CapabilityRepository`, `CapabilityFilter` untouched.
- **Tests (18 new)**: `CapabilityGrantPathPostgresTest` (11: OWNER bundle, SUPERUSER all-branches read, ACCOUNTANT read-only, COORDINATOR over-grant guard, INACTIVE exclusion, zero-role empty, direct-grant regression, direct-beats-derived dedup, ROLE source reporting, any-context/branch-window, hasCapability perf < 500ms) + `GrantPathAuthzTest` (6: role-derived MANAGE_USERS passes the GLOBAL route gate, ACCOUNTANT/INACTIVE/zero-role rejections, /api/me/capabilities content). Suite 675 green.

## Key decisions (recorded in full on the resolution comment + ADR-0023)

1. **Whitelist = per-role, GLOBAL-context-only derivation** — management bundle (MANAGE_USERS/ASSIGN_DELEGATE/ASSIGN_COMPENSATION) for SUPERUSER/OWNER/MANAGER; all-branches read (VIEW_BRANCH_DATA) for SUPERUSER/ACCOUNTANT. COORDINATOR excluded from ASSIGN_COMPENSATION (over-grant guard).
2. **No user_role seed in the migration** — documented deviation: no production users exist at migration time (the ticket's own parenthetical); role assignment rides the user-creation path (DevSeeder already assigns OWNER for dev/k6; production create-flow = tracked #106 fog). Material consequence: role-derived grants exist only in dev/test until then.
3. **V2 header comment untouched** — Flyway checksum validation (#121 precedent); the amended scope documentation lives in the V16 header + ADR-0023.
4. **Derived priority 5 + ROLE source type** — direct grants always beat derived (dedup deterministic); ROLE never written to user_capability.
5. **V15/V16 split** — PG 55P04 same-transaction enum restriction, not a design choice; #133 must use **V17** for `deactivated_at`.

## Patterns + learnings (cumulative across sessions)

- **The dead-grant class is fixed at the root** — #132 ships the mechanism; the remaining hole is *seeding*, which needs the user-create flow (fog). When a future screen's gate looks persona-dead, the check is now: does the persona's role derive it? (whitelist in V16) or is it a BRANCH-scoped code needing a direct grant?
- **PG enum values can't be used by stored expressions in the same transaction** (SQL state 55P04) — any future `ALTER TYPE ... ADD VALUE` followed by view/index/trigger use in one migration must be split into two. Flyway runs one txn per migration.
- **Never edit applied migrations** — V2's header said "documentation only"; the amendment went into the V16 header + ADR-0023 instead (Flyway checksum precedent, 3rd occurrence after #119/#121).
- **Flyway migration numbering gap check** — V14 never existed in any branch (verified `git log --all`); V15/V16 taken. Future tickets: check `ls backend/src/main/resources/db/migration/` before picking a number.
- **Review-round traps on docs**: round-1 Standards caught ADR references to "V15" that became false after the migration split — docs reviewed for *internal consistency with the code*, not just claims. Round 2 confirmed.
- Prior-session patterns unchanged: AFK flow = /implement per backend/AGENTS.md, falsification-first (this ticket's design question — the whitelist — was falsified against live GLOBAL-capability route checks before deciding), quality gate, /code-review rounds 1+2 (round 2 on the fix delta), k6 deferred (#98/#115 precedent), resolution comment → close → map Decisions-so-far + table + fog + closing paragraph.
- **iOS compile is pre-existing-broken** (from #120 handoff): `:composeApp:compileKotlinIosSimulatorArm64` fails on HEAD (no iOS actuals) — pre-push gate excludes iOS intentionally.

## Current frontier (verified live post-session)

Per `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100` + dependency summaries (`blocked_by` = open blockers only):

| # | Title | Type | Status |
|---|-------|------|--------|
| 128 | Public branch-type exports: drop VIEW_BRANCH_DATA gate on provincial/medical-mission (graduated from #105) | task | 🔓 unblocked |
| 129 | Add date-range export endpoint (graduated from #105) | task | 🔓 unblocked |
| 130 | Add paged daily-summaries endpoint for the Reports feed (graduated from #105) | task | 🔓 unblocked |
| 131 | Fix Reports access scope: GLOBAL VIEW_BRANCH_DATA = all branches + accessible-branches endpoint (graduated from #105) | task | 🔓 unblocked |
| 133 | User management backend: user list + reactivate + deactivated_at + self-guard (graduated from #106) | task | 🔓 unblocked |
| 134 | Fix ungated slot-swap + assignment-delete (graduated from #106) | task | 🔓 unblocked |
| 135 | Build — User Management screen (graduated from #106) | task | 🔒 blocked by #133+#134 |

(#110 "Fix hardcoded-month dates…" remains open + unassigned, standalone, NOT a child of map #89. #132 closed this session. **All 7 HITL prototypes closed (#99–#106); the map is 100% AFK.**)

## Recommended next pick

- **#133 / #134** — both small, independent, no blockers; the User Management chain's backend pair. #134 first if you want the F3 security hole closed earliest; #133 feeds #135's list payload. **#133 uses V17 for `deactivated_at`** (V15/V16 taken).
- **#128–#131** — the Reports/Finance backend set; #131 recommended first (unblocks the whole Reports read surface; now coordinate-ready with #132's derived GLOBAL VIEW_BRANCH_DATA), #128 smallest.
- **#135 (Build — User Management)** — only when #133+#134 are closed; spec = outline D2–D5.
- **Merged Finance & Reports build ticket** — ungraduated; opens when #128–#131 land + the #117 expense-GET-includes-soft-deleted question is decided. Its spec = #105's outline (D1–D7) + #101's outline (edit-mode D1–D8) + #117 read-backs.
- **Candidate backend fog (small AFK hardening tickets)**: the cross-draft line-session raw 500 (#120 fog — `idx_remittance_line_session` insertIgnore→`.single()` NoSuchElement) and the `getForSession` read-path day-gate 400 (#124 fog).

## How to drive the next session (wayfinder "Work through the map")

1. `gh issue edit <N> --add-assignee @me` — claim FIRST. Verify no concurrent sessions (sub-issues assignees empty).
2. AFK flow (#128–#131, #133–#134): /implement per `backend/AGENTS.md` (falsification-first against the ticket's body — several tickets encode design decisions, e.g. #134's service-level enforcement mandate), quality gate, /code-review rounds 1+2, k6 deferral precedent (#98/#115).
3. Post the answer as a **resolution comment** on the ticket, then `gh issue close <N>`, then append a context pointer to map #89's **Decisions so far** (fetch body → modify → `gh issue edit 89 --body-file <modified>` — update BOTH the table row AND the closing paragraph; the frontier counts + blocker lists change as tickets close).
4. Graduate fog (create-then-wire: create issues, then wire blocking with `gh api --method POST repos/jsongalvez/company_app/issues/<child-NUMBER>/dependencies/blocked_by -F issue_id=<blocker-db-id>` — **path takes the issue NUMBER, body takes the integer DB id**; sub-issue link via `POST repos/jsongalvez/company_app/issues/89/sub_issues -F sub_issue_id=<db-id>`; verify new tickets appear in the sub-issues query + dependency summaries).
5. **One-ticket-per-session limit.** When done, stop and write `docs/agents/wayfinder-<N>-handoff.md` — follow this session's format.

## Map state at session-end

Map #89 body updated this session:
- Table: #132 → ✅ closed; #135's blocker text → #133+#134 (native deps auto-dropped #132 — verified `blocked_by: 2`).
- Decisions-so-far: new #132 entry (mechanism, whitelist, V15/V16 split, dedup/precedence, 2 documented deviations, V17 note for #133, resolution link).
- Not-yet-specified: user-create/role-assignment fog line corrected — "V15 role seed" → mechanism-only reality (no production seed; role assignment rides the user-creation path).
- Closing paragraph: frontier = 6 unblocked (#128–#131, #133–#134) + #135 blocked on #133+#134; #132 closed with resolution link; merged Finance & Reports build still ungraduated.

## Suggested skills for next session

- **`/wayfinder`** — the parent workflow; re-load to follow "Work through the map" steps.
- **`/implement` + `/code-review`** — for the AFK backend tasks #128–#131, #133–#134 (parallel Standards + Spec; round 2 on the fix delta).
- **`/handoff`** — when the chosen ticket is resolved and the session is near its limit, compact + write `docs/agents/wayfinder-<N>-handoff.md`.
