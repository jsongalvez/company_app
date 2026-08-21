# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 77

## What this is

This session completed the #175 successor build: **Build — Stateful stale-failure legs gate (the #165 failure-leg closure)**, issue #176. The map decision is now closed end-to-end. The session also hardened unattended-loop recovery after a model turn stops without writing its handoff, switched fresh subagent and loop sessions to GPT-5.6 Luna, and installed a daily bun temporary-file cleanup timer on the VPS.

## Session outcome

- **Issue #176 is CLOSED.** Resolution: https://github.com/jsongalvez/company_app/issues/176#issuecomment-5310835245
- `ApiCallHandler.launch` now gates generic `UiState.Error` writes on both failure legs:
  - Non-success: `onNonSuccess` still runs; generic Error write requires `stamp() == captured`.
  - Exception: `onError` still runs; generic Error write requires `stamp() == captured`.
  - Success keeps #165 stamp/fallback substitution. No failure fallback. No stateless-gate re-unification.
  - Default `{0L}` callers remain behavior-identical.
- Tests added or revised:
  - Stale HTTP response held in flight, external stamp flip, moved-on Success written, stale failure cannot overwrite it.
  - Stale exception held in flight, external stamp flip, `onError` still runs, Error write suppressed.
  - Current HTTP and exception failures still write Error.
  - Notification markRead-404 held pre-action failure cannot clobber the reload Success.
- Documentation updated:
  - `ApiCallHandler.launch` KDoc now describes all three landing legs.
  - `composeApp/AGENTS.md` describes the stateful stamp/fallback plus failure-leg gate.
- **Gate ledger added:** `docs/gates/176-stateful-stale-failure-legs.md`. Final checker result: 4/4 PASS.
- **Negative control:** both failure gates were stripped temporarily; focused desktop tests failed; gates were restored. The required pre-code gate-ledger red run was missed because the gate file was created after implementation. This is recorded in issue #176; no evidence was fabricated.
- **Phased review:** four standard passes plus P5. Exit condition: 0 HARD after triage.
  - Pass 1: stale-exception external ordering missing; trailing whitespace. Fixed in `2f6fd43`.
  - Pass 2: stale HTTP test used counter-only staleness. Fixed with deferred external ordering and gate ledger in `11b435c`.
  - Pass 3: HTTP test's moved-on state assertion strengthened in `4b2284b`.
  - Final pass: P1/P2/P4 clean. P3 Relief fallback concern rejected with code and passing `i3` evidence: action mutation writes Success before stale fallback, so `loadReceived` reissues instead of seeing Loading.
  - P5a: one ARCH graduate, hook asymmetry. P5b: no hygiene findings.
- **P5 ARCH graduate:** hook asymmetry — stateful stale failures keep caller hooks while gating generic state writes; stateless stale landings skip hooks wholesale. No current stamped caller supplies custom failure hooks. Map fog line added.
- Map #89 updated: #175 and #176 rows closed, Decisions-so-far pointer added, stale build fog closed, hook-asymmetry fog added, Frontier remains EMPTY.
- Ticket/build commits:
  - `4c2a301` — stateful stale-failure gate implementation.
  - `2f6fd43` — stale exception external ordering pin.
  - `11b435c` — stale HTTP external ordering pin + gate ledger.
  - `4b2284b` — moved-on Success assertion.
- Separate infrastructure commits:
  - `3a7bb27` — wayfinder loop disk-floor tripwire.
  - `0427e86` — immediate continuation prompt after completed assistant turn without handoff.
  - `27c2fd1` — runbook model reference switched to Luna.

## Current frontier

**Frontier EMPTY** — #175 decision and #176 build are closed. Open standalone tickets remain #139 (OpenAPI map) and #110 (hardcoded-month test dates). Map #89's next chartable pick is the unifier-family naming coherence fog line.

Standing fog now includes:

- **Hook asymmetry, graduated by #176 P5:** stateful stamped failure hooks run while generic state writes are gated; stateless stale hooks skip. Reopen on a stamped caller with a custom hook or a proposed cross-family unifier.
- **Unifier-family naming coherence, marker FIRED by #174:** `ActionTracker.begin` versus `InFlightGuard`/`KeepLastByKey.tryBegin`; documented leaning is `begin` to `tryBegin`. Small AFK decision ticket or fold into next family change.
- Mirror sync projections are test-surface only.
- Stale-response guard partition remains split by invariant and commit target.
- Transport-pin family fixture marker, stateless onError message convention, update-one map-if shape, DayStatus move, #92 Q4/Q5 machinery, relief-surface trichotomy, k6 branch-scoped writes, root AGENTS iOS pre-push line, #110, #106 user creation/role assignment, request-flow grant/deny UI gap, and #139 OpenAPI map remain as in #179.

## Infrastructure state

- **Wayfinder loop is RUNNING** in tmux session `wayfinder-loop`.
- Persisted state supervises current session `ses_ff43d6de0ffenwqXOTzJB40wMl`; no bootstrap was used, so no duplicate child was spawned.
- `scripts/wayfinder-loop.sh` now checks completed assistant messages every `WAYFINDER_TICK_SECS` (default 5s). A completed `stop`/`length`/`error` turn with no handoff, pending question, permission, or running tool receives an immediate continuation prompt. Message-id dedupe prevents prompt loops.
- The existing 540-second zombie detector remains as fallback for sessions that disappear or stall without a completed message.
- Loop disk floor defaults to 5 GiB. Low disk sends one notification per crossing and blocks new spawns until space recovers.
- `.wayfinder-loop.env` is gitignored and now has `WAYFINDER_MODEL=gpt-5.6-luna`. The loop's API session override is necessary because loop-created primary sessions do not inherit `general`/`explore` agent models.
- Global OpenCode config overrides built-in `general` and `explore` agents to `opencode-go/gpt-5.6-luna`. Service restart verified healthy.
- `/etc/systemd/system/tmp-bun-so-clean.service` + `.timer` installed and enabled. Daily, `Persistent=true`, 15-minute randomized delay. It removes `/tmp/.[0-9a-f]*.so` files older than one day. The tmpfiles glob approach was tested and removed because this systemd version ages directory contents, not matching regular files.
- Current VPS disk recovered to roughly 34 GiB free after bun `.so` cleanup. Check `/tmp`, `composeApp/build`, and `~/.gradle` if the disk guard fires.

## How to drive the next session

1. Read this handoff, `CONTEXT.md`, root `AGENTS.md`, and the relevant module instructions before work.
2. Load `/wayfinder`; inspect map #89 at low resolution and choose the first frontier item or the Recommended next pick.
3. Claim any map ticket before research or edits. One ticket per session.
4. For an AFK build, create its gates file before code, run the required fail-red negative control, implement, run the gate checker, then run the phased P1-P4 loop and P5 exit.
5. If a human decision is required, use the question tool and wait. Do not guess.
6. Finish by writing `docs/agents/wayfinder-181-handoff.md`; it is the next chain completion signal. Then stop without follow-up work.

## Critical follow-ups (human)

- Push remains deliberate: branch is local and ahead of origin; current tracked worktree is clean before this handoff is committed.
- The loop's current session was originally created with the old DeepSeek pin. New sessions after this handoff use `gpt-5.6-luna`.
- The pre-code gate-ledger step was missed for #176. Future builds must create and red-run `docs/gates/<issue>-*.md` before the first edit.
- `shellcheck` remains uninstalled; run it on `scripts/wayfinder-loop.sh` and `scripts/vps-migration-wizard.sh` when convenient.
- Coolify GitHub App, ntfy topic rotation, zombie-detector live exercise, and remaining VPS wizard follow-ups from #179 remain open.

## Suggested skills for next session

- `/wayfinder` — map #89 is empty; next chartable pick is unifier-family naming coherence.
- `/grilling` + `/domain-modeling` — if the naming ticket requires a human decision.
- `/implement` + `docs/agents/gates.md` + `docs/agents/code-review-loop.md` — for the next build.
- `/writing-for-agents` — for any AGENTS/docs/wayfinder handoff edit.
- `/code-review` — only after fixing a ticket delta; use the phased loop lenses, not a single generic review.
