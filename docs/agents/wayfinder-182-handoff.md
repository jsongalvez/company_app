# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 79

## What this is

This session resolved standalone maintenance ticket #110 and hardened Bun temporary-file cleanup after the VPS accumulated ~32 GB of native `.so` extractions.

## Session outcome

- **Issue #110 is CLOSED.** Resolution: https://github.com/jsongalvez/company_app/issues/110#issuecomment-5311201512
- `MonthlyRemittanceSummaryServicePostgresTest` now uses Manila `YearMonth` for current-month fixture ranges and summary lookups; month-end dates are calculated rather than hardcoded.
- Removed redundant `assertNotNull` checks whose non-null return type and following field assertions already prove presence.
- Targeted verification passed: `./gradlew :backend:ktlintCheck :backend:test --tests 'com.companyb.companyapp.service.MonthlyRemittanceSummaryServicePostgresTest'`.
- Full `:backend:test` was attempted with extended timeout. It timed out and reported unrelated failures in `AuditLogAuthzTest`, `ReliefDayGateAuthzTest`, `ReliefInviteAuthzTest`, `ReportsReadScopeAuthzTest`, and `UserManagementAuthzTest`.
- Commits: `9f98f08` (`test(remittance): use current month dates`), `0c2fcfb` (`chore(infra): protect bun cleanup and push handoffs`).
- `scripts/tmp-bun-so-clean.sh` now deletes only `.so` files older than 10 minutes and skips files held open according to `fuser`.
- Live `/etc/systemd/system/tmp-bun-so-clean.service` points to that script. Live timer is enabled and active, running every 5 minutes. Manual run succeeded; current `.so` count is bounded by active/recent Bun sessions.
- `scripts/wayfinder-loop.sh` now tells unattended agents to push verified commits. If push is blocked, exact blocker must be recorded in handoff.
- `git status` is clean. `bash -n scripts/wayfinder-loop.sh scripts/tmp-bun-so-clean.sh` passed.

## Current frontier

**Frontier EMPTY** for Map #89. Remaining chartable fog and standalone maintenance remain documented on map #89. #110 is now closed. Open standalone ticket remains #139 (OpenAPI map).

Standing fog remains as documented in `docs/agents/wayfinder-181-handoff.md`, including hook asymmetry, mirror test projections, stale-response partitions, transport fixture shape, stateless error-message convention, update-one map-if shape, DayStatus move, #92 machinery, relief-surface trichotomy, k6 branch-scoped writes, root AGENTS iOS line, #106 user flow, request-flow UI gap, and #139 OpenAPI map.

## Infrastructure state

- Wayfinder loop remains running in tmux session `wayfinder-loop`.
- Bun cleanup timer: `tmp-bun-so-clean.timer`, enabled and active; `OnBootSec=5min`, `OnUnitActiveSec=5min`, cleanup threshold 10 minutes.
- Cleanup skips open files, so active Bun libraries remain available. Unlinking an open Linux file is normally safe, but skip guard avoids path-reopen races.
- Commits are local and not pushed. Runner prompt now requires future unattended sessions to push after verification. This session did not push because pre-commit full gate repeatedly exceeded 10 minutes; targeted test/lint passed. Pre-push also carries known VPS risks: Android SDK/iOS actual gaps, k6, and baseline checks.

## How to drive the next session

1. Read this handoff, `CONTEXT.md`, root `AGENTS.md`, and relevant module instructions before work.
2. Load `/wayfinder`; inspect map #89 at low resolution and choose first frontier item or Recommended next pick.
3. Claim any map ticket before research or edits. One ticket per session.
4. For an AFK build, create its gates file before code, run required fail-red negative control, implement, run gate checker, then phased P1-P4 loop and P5 exit.
5. Push verified commits when runner instructions require it. If a documented gate blocks push, record exact command and failure in next handoff.
6. If a human decision is required, use question tool and wait. Do not guess.
7. Finish by writing `docs/agents/wayfinder-183-handoff.md`; it is next chain completion signal. Then stop without follow-up work.

## Critical follow-ups (human)

- Decide whether to push local commits after reviewing known pre-push environment blockers.
- `shellcheck` remains uninstalled; run it on `scripts/wayfinder-loop.sh`, `scripts/tmp-bun-so-clean.sh`, and `scripts/vps-migration-wizard.sh` when convenient.
- Coolify GitHub App, ntfy topic rotation, zombie-detector live exercise, and remaining VPS wizard follow-ups remain open.

## Suggested skills for next session

- `/wayfinder` — map #89 is empty; inspect standing fog and standalone OpenAPI map #139.
- `/implement` + `docs/agents/gates.md` + `docs/agents/code-review-loop.md` — for next build.
- `/grilling` + `/domain-modeling` — if next item needs human decision.
- `/writing-for-agents` — for any AGENTS/docs/wayfinder handoff edit.
- `/code-review` — only after fixing a ticket delta; use phased loop lenses, not one generic review.
