# Handoff - Architecture Map #180, Ticket #217

## Session outcome

- Loaded latest handoff, Map #180, `/wayfinder`, `/codebase-design`, project-local
  `.opencode/skills/improve-codebase-architecture`, and every Map #180 Context Pointer.
- Applied Map #180 no-question policy. Claimed Map #180 before focused C-01..C-14 audit.
- Confirmed no open Map #180 child at session start. Ran four bounded read-only lanes over
  shared capability enums, cleanup policy, Compose duplication, and coverage/overlap.
- Created, linked, and claimed child #217: [Build: use shared capability enums in backend persistence](https://github.com/jsongalvez/company_app/issues/217).
- Removed duplicate backend `CapabilityContextType` and `CapabilitySourceType` declarations.
  Backend persistence, authorization, scheduler, grants, seeders, and tests now use shared
  serializable enums directly. Removed repository name-based enum conversion.
- Retained PostgreSQL `customEnumeration`/`PGobject` bindings, migrations, capability-view
  behavior, scheduler ownership, and wire values. Renamed table model file to match its sole
  declaration after dead `UserCapability` removal.
- Updated canonical `docs/agents/architecture-audit-180.md` with Session 249 candidate
  dossiers, dispositions, audit-of-audit passes, and priority. Added shared-enum ownership
  lesson to `docs/agents/architecture-lessons.md`.
- Cleanup-script discovery policy and Android/iOS mobile navigation/session-list duplication
  remain deferred P2 candidates; no other candidate displaced shared enum ownership.

## Verification

- Repository-wide Kotlin search: zero backend duplicate capability enum declarations or
  repository-model enum imports; shared definitions remain sole declarations.
- Targeted capability grant-path and scheduler Postgres tests: PASS.
- `./gradlew :shared:compileKotlinJvm :backend:compileKotlin :backend:detekt :backend:ktlintCheck`: PASS after filename/import repair.
- `./gradlew :backend:detekt :backend:ktlintCheck :backend:test`: PASS.
- Pre-commit: formatting, backend quality, OpenAPI contract, cleanliness, shared compile,
  and Postgres connectivity: PASS.
- Pre-push: OpenAPI contract, Compose Android/Desktop compilation, backend distribution,
  health check, k6 baseline with 0% errors, and final test-database cleanup: PASS.
- `git diff --check`: PASS.
- No production data touched. Test database cleaned.

## Tracker and remote

- #217 resolution comment posted and issue closed.
- Map #180 updated with #217 context pointer and Session 249 checkpoint comment.
- Commit `9ec90f6` pushed to `origin/ralph/company-app-full-build`.
- Worktree was clean before this handoff was written.

## How to drive next session

1. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`,
   and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
2. Apply no-question policy unconditionally. Never invoke `question`; defer human decisions
   as labeled tracker issues.
3. Claim Map #180 before the next focused architecture audit.
4. Query Map #180 children/frontier. #217 is closed; all current children should be checked
   before selecting the first open, unblocked, unassigned child.
5. Run focused C-01..C-14 audit. Prioritize test-database cleanup-policy centralization and
   Compose duplication only if evidence shows material drift or invalid ownership; retain
   R15 and other documented fog without guessing.
6. Create and claim at most one child before implementation; resolve at most one active ticket.
7. Finish tracker, validation, commit, and push work before writing the next numbered handoff.
   After writing that handoff, stop immediately.
