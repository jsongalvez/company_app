# Handoff - Architecture Map #180, Ticket #207

## Session outcome

- Loaded latest handoff, `/wayfinder`, `/codebase-design`, project-local `/improve-codebase-architecture`, and every Map #180 Context Pointer.
- Claimed Map #180 before work and completed one full read-only architecture audit across C-01..C-14 using four bounded lanes.
- Retired stale R28: current iOS actuals and Swift host symbol now match common expectations.
- Recorded and dispositioned R31-R34 in `docs/agents/architecture-audit-180.md`:
  - R31 scheduler notification capability provisioning: implement, P0, selected.
  - R32 malformed or empty JMH baseline output: implement, P1, retained.
  - R33 relief-access status shared wire typing: implement, P1, retained as extension of R3.
  - R34 registration uniqueness race classification: implement, P1, retained.
- Implemented #207 without role checks in scheduler code. Migration V21 extends `active_user_capabilities` with branch-scoped ROLE-derived `RECEIVE_NEXT_APPOINTMENT_ALERTS` for active Coordinator roles with active branch assignments. Added role-only scheduler integration coverage and test ownership tracking.

## Verification

- Focused `NextAppointmentSchedulerPostgresTest`: 13/13 passed.
- `./gradlew :backend:detekt :backend:ktlintCheck :backend:test` passed.
- `./gradlew :shared:compileKotlinJvm :backend:publishOpenApiSpec` passed.
- `bash scripts/check-openapi-spec.sh` passed all route, secret, and negative-drift checks.
- Test database cleanliness passed before and after tests and k6.
- Pre-commit passed formatting, detekt, ktlint, backend tests, OpenAPI contract, cleanliness, shared compile, and Postgres connectivity.
- Pre-push passed Compose desktop/Android compilation, backend distribution build, health check, k6 baseline with `0%` errors, and final cleanup.
- `git diff --check` passed before commit.

## Tracker and remote

- #207 resolution comment posted and issue closed.
- Map #180 updated with #207 context pointer and audit checkpoint; map remains OPEN and permanent.
- `adec58e` (`fix(auth): derive scheduler capability for coordinators`) contains implementation and audit report; pushed to `origin/ralph/company-app-full-build`.
- Worktree was clean before this handoff file was written.

## How to drive next session

1. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`, and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
2. Query Map #180 children/frontier. #207 is closed. R32, R33, and R34 are documented retained candidates but are not yet child tickets.
3. Claim Map #180 before the next focused/full read-only architecture audit. Do not invent an implementation ticket before audit evidence exists.
4. Re-audit current C-01..C-14 state and all retained candidates. Retire or narrow candidates whose current code disproves them. Complete evidence, falsification, verification, disposition, and ticket creation before implementation.
5. Resolve at most one ticket, finish tracker, commit, push, and validation work before writing the next numbered handoff. Stop immediately after writing it.
