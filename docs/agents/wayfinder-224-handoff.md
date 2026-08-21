# Handoff - Architecture Map #180, Session 121

## What this is

Session 121 completed candidate verification for Map #180. Every retained candidate R13-R16 and R19 now has evidence, exploration, adversarial falsification, structured repeated rubric review, and explicit disposition before implementation ranking.

## Session outcome

- Loaded Map #180 through REST after `gh issue view` hit GitHub's deprecated Projects field, latest handoff, every Map Context Pointer, `/wayfinder`, `/codebase-design`, and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
- Claimed Map #180 before investigation. Audit stayed read-only for product code, tests, migrations, and behavior.
- Completed five independent candidate dossiers:
  - R19 scheduler executor lifecycle: **implement**, first.
  - R14 mandatory OpenAPI verification: **implement**, second.
  - R13 production persistence clock authority: **implement**, narrowed to persistence timestamps and rate-window locality; no universal clock abstraction.
  - R16 unused `SessionState.isLoggedIn`: **implement**, deletion-only.
  - R15 notification inserted-count truth: **defer** pending deployment topology or overlapping scheduler invocation requirements; no child created.
- Recorded dossier evidence, falsification, lifecycle transitions, structured-verifier fallback, dispositions, and implementation order in `docs/agents/architecture-audit-180.md` Session 121.
- Added durable lessons for persistence time, executable contract gates, and ownerless executors to `docs/agents/architecture-lessons.md`.
- Updated Map #180 Decisions-so-far / Not yet specified / child-ticket task list and posted Session 121 checkpoint comment.
- Created implementation children:
  - #195 `Build: own scheduler executor lifecycle`
  - #196 `Build: enforce OpenAPI verification in mandatory gates`
  - #197 `Build: unify persistence timestamp authority`
  - #198 `Build: delete unused SessionState.isLoggedIn`
- GitHub native sub-issue endpoint returned 404, so map uses fallback child task list and each child has `Part of #180`.
- No ADR was needed. No product implementation occurred in this session.

## Verification

- `git diff --check` passed.
- Pre-commit passed backend detekt, ktlint, tests, shared JVM compilation, test-data cleanliness, and Postgres connectivity.
- First push attempt exposed disposable test DB leaks: `app_user(1)`, `user_capability(9)`, `user_role(1)`. Ran `bash scripts/clean-test-db.sh`; cleanliness then passed.
- Push retry completed. Remote `origin/ralph/company-app-full-build` contains `a0fee45`.
- Final worktree is clean; `bash scripts/check-test-cleanliness.sh` passes.
- Project-local HTML report was written to `/tmp/architecture-review-121.html`.
- OpenCode2 scoring-token logprobs were unavailable. No unsupported Luna score is claimed; structured repeated rubric fallback is recorded with reduced confidence. Deterministic repository evidence remains authoritative.

## How to drive next session

1. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`, and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
2. Confirm commit `a0fee45`, remote, and worktree state.
3. Select and claim exactly one implementation child before work. First frontier is #195 `Build: own scheduler executor lifecycle`; do not work on multiple children in one session.
4. Read `backend/AGENTS.md`, `docs/business-requirements.md`, relevant scheduler/startup ADRs, and the child body before editing. Use standard risk-based review for backend lifecycle/concurrency work.
5. Implement #195 with explicit scheduler start/stop ownership, repeated-start safety, startup-failure cleanup, application shutdown behavior, and deterministic lifecycle tests. Keep `NextAppointmentScheduler.run(clock)` as work owner. Do not solve deferred R15 in #195.
6. Run targeted checks, full backend quality gate, test-data cleanliness, shared compilation, and required pre-push gates. Repair local failures and retry push.
7. Resolve #195 with a resolution comment, close it, append its named context pointer to Map #180, then write the next numbered handoff only after all commit/push/tracker work is complete. Stop immediately after writing that handoff.
