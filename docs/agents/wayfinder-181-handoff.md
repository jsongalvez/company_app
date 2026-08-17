# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 78

## What this is

This session resolved the unifier-family naming coherence fog line. The AFK build ticket, **Build — Align in-flight guard naming**, renamed `ActionTracker.begin` to `tryBegin`, matching `InFlightGuard` and `KeepLastByKey`.

## Session outcome

- **Issue #177 is CLOSED.** Resolution: https://github.com/jsongalvez/company_app/issues/177#issuecomment-5310909002
- `ActionTracker.tryBegin` now uses same admission name as `InFlightGuard.tryBegin` and `KeepLastByKey.tryBegin`.
- Updated 9 production call sites, 7 ActionTracker test call sites, live KDoc, UserViewModel comment, and two test names.
- Behavior unchanged: synchronous per-key coalescing, retry error clearing, terminal cleanup, and stale-generation handling remain intact.
- Historical handoffs retain old names as archival session records.
- Map #89 updated: naming-coherence decision added to Decisions so far, removed from live fog/frontier wording.
- Phased review: initial P1-P5 found stale live KDoc/comment/test-name references and one comment indentation defect. Fixed. Exit P1-P4: 0 HARD. P5: no architecture or hygiene residue.
- Verification: `./gradlew :composeApp:ktlintCheck :composeApp:desktopTest` passed. `git diff --check` passed.

## Current frontier

**Frontier EMPTY** for Map #89. Remaining chartable fog and standalone maintenance remain documented on map #89. Open standalone tickets remain #139 (OpenAPI map) and #110 (hardcoded-month test dates).

Standing fog includes:

- Hook asymmetry from #176 P5.
- Mirror sync projections as test-surface only.
- Stale-response guard partition by invariant and commit target.
- Transport-pin family fixture marker.
- Stateless onError message convention.
- Update-one map-if sub-shape.
- DayStatus move, #92 Q4/Q5 machinery, relief-surface trichotomy, k6 branch-scoped writes, root AGENTS iOS pre-push line, #106 user creation/role assignment, request-flow grant/deny UI gap, and #139 OpenAPI map.

## Infrastructure state

- Wayfinder loop remains running in tmux session `wayfinder-loop`.
- Latest handoff is this file. No follow-up work started.

## How to drive the next session

1. Read this handoff, `CONTEXT.md`, root `AGENTS.md`, and relevant module instructions before work.
2. Load `/wayfinder`; inspect map #89 at low resolution and choose first frontier item or Recommended next pick.
3. Claim any map ticket before research or edits. One ticket per session.
4. For an AFK build, create its gates file before code, run required fail-red negative control, implement, run gate checker, then phased P1-P4 loop and P5 exit.
5. If a human decision is required, use question tool and wait. Do not guess.
6. Finish by writing `docs/agents/wayfinder-182-handoff.md`; it is next chain completion signal. Then stop without follow-up work.

## Critical follow-ups (human)

- Push remains deliberate: branch is local and ahead of origin.
- `shellcheck` remains uninstalled; run it on `scripts/wayfinder-loop.sh` and `scripts/vps-migration-wizard.sh` when convenient.
- Coolify GitHub App, ntfy topic rotation, zombie-detector live exercise, and remaining VPS wizard follow-ups remain open.

## Suggested skills for next session

- `/wayfinder` — map #89 is empty; inspect standing fog and standalone maintenance tickets.
- `/implement` + `docs/agents/gates.md` + `docs/agents/code-review-loop.md` — for next build.
- `/grilling` + `/domain-modeling` — if next item needs human decision.
- `/writing-for-agents` — for any AGENTS/docs/wayfinder handoff edit.
- `/code-review` — only after fixing a ticket delta; use phased loop lenses, not one generic review.
