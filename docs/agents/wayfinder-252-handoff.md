# Handoff - Architecture Map #180, Ticket #220

## Session outcome

- Loaded latest handoff, Map #180, `/wayfinder`, `/codebase-design`, project-local
  `.opencode/skills/improve-codebase-architecture`, `/implement`, `/code-review`,
  `/writing-for-agents`, and every applicable Map #180 Context Pointer.
- Claimed Map #180 before the focused C-01..C-14 audit. Confirmed all existing children were
  closed, created and claimed child #220: [Build: centralize scheduler capability code](https://github.com/jsongalvez/company_app/issues/220).
- Audited scheduler capability-code ownership, route-test setup, cleanup policy, and all remaining
  ownership rows with independent read-only lanes. Promoted only the duplicated Kotlin scheduler
  capability code as actionable; retained route-test setup and cleanup extraction as lower-risk
  maintenance with no current defect.
- Added `RECEIVE_NEXT_APPOINTMENT_ALERTS` to shared `CapabilityCodes`. Migrated
  `NextAppointmentScheduler` and its focused Postgres test. V5/V21 migration SQL literals remain
  database-owned and unchanged. Scheduler query, role-derived branch provisioning, branch pairing,
  and authorization behavior remain unchanged.
- Updated canonical `docs/agents/architecture-audit-180.md` with Session 252 evidence,
  dispositions, audit-of-audit passes, and priority. Added shared capability-code ownership lesson
  to `docs/agents/architecture-lessons.md`.

## Verification

- Focused shared compilation and scheduler Postgres test: PASS.
- `./gradlew :backend:detekt :backend:ktlintCheck :backend:test`: PASS.
- Pre-commit: formatting, backend quality, OpenAPI contract, cleanliness, shared compile, and
  Postgres connectivity: PASS.
- Pre-push: OpenAPI contract, Compose Android/Desktop compilation, backend distribution, health
  check, k6 baseline with 0% errors, and final test-database cleanup: PASS.
- `git diff --check`: PASS. Review found no HARD findings; one audit-history wording issue was
  corrected in follow-up commit. Test database clean. No production data touched.
- iOS compilation was not required by this Kotlin/JVM/shared constant slice; prior external
  Kotlin Native artifact failure remains recorded in wayfinder-251-handoff.md.

## Tracker and remote

- #220 resolution comment posted and issue closed.
- Map #180 updated with #220 context pointer.
- Commits `256d6e3` and `0606483` pushed to `origin/ralph/company-app-full-build`.
- Worktree was clean before this handoff was written.

## How to drive next session

1. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`,
   and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
2. Apply no-question policy unconditionally. Never invoke `question`; defer human decisions as
   labeled tracker issues.
3. Claim Map #180 before the next focused architecture audit.
4. Query Map #180 children/frontier. #220 is closed; check all current children before selecting
   the first open, unblocked, unassigned child.
5. Run focused C-01..C-14 audit. Recheck route-test setup and cleanup only for fresh material
   leverage or failure; retain R15/fog without guessing. If no actionable child remains, run the
   required full audit and follow current no-candidate policy.
6. Create and claim at most one child before implementation; resolve at most one active ticket.
7. Finish tracker, validation, commit, and push work before writing the next numbered handoff.
   After writing that handoff, stop immediately.
