# Handoff - Architecture Map #180, Ticket #218

## Session outcome

- Loaded latest handoff, Map #180, `/wayfinder`, `/codebase-design`, project-local
  `.opencode/skills/improve-codebase-architecture`, and every Map #180 Context Pointer.
- Applied Map #180 no-question policy. Claimed Map #180 before focused C-01..C-14 audit.
- Confirmed all existing Map #180 children were closed before selecting work. Created, linked,
  and claimed child #218: [Build: use shared capability context enum in Compose state](https://github.com/jsongalvez/company_app/issues/218).
- Removed duplicate Compose `CapabilityContext` string constants from `SessionState`.
  `hasCapability` and `hasCapabilityAtContextType` now require shared serializable
  `CapabilityContextType` and compare enum values directly. Migrated Finance and Session Dashboard
  callers and matcher fixtures. Preserved null-context fail-closed behavior and ADR-0021's
  capability refresh timing.
- Focused lanes confirmed Android/iOS `AppNavHost` and `SessionList` implementations are byte-identical
  and remain a P2 extraction candidate. Cleanup scripts retain distinct check/mutate interfaces and
  fail closed; centralization remains P2. Route-test setup helper and shared scheduler capability-code
  ownership remain retained candidates for later ranking. No second child was created.
- Updated canonical `docs/agents/architecture-audit-180.md` with Session 250 candidate dossiers,
  dispositions, audit-of-audit passes, and priority. Added shared-consumer enum ownership lesson to
  `docs/agents/architecture-lessons.md`.

## Verification

- Repository-wide Compose search: no duplicate `CapabilityContext` object or string matcher call remains.
- `./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinDesktop :composeApp:desktopTest`: PASS.
- `./gradlew :composeApp:ktlintCheck :composeApp:compileKotlinDesktop :composeApp:desktopTest`: PASS.
- `./gradlew --no-configuration-cache :composeApp:compileDebugKotlinAndroid`: PASS.
- `./gradlew :backend:detekt :backend:ktlintCheck :backend:test`: PASS.
- Pre-commit: formatting, backend quality, OpenAPI contract, cleanliness, shared compile, and Postgres connectivity: PASS.
- Pre-push: OpenAPI contract, Compose Android/Desktop compilation, backend distribution, health check,
  k6 baseline with 0% errors, and final test-database cleanup: PASS.
- iOS compile attempted; Gradle could not resolve external
  `org.jetbrains.kotlin:kotlin-native-prebuilt:2.3.10` linux-aarch64 artifact from Maven before source compilation.
- `git diff --check`: PASS. Test database clean. No production data touched.

## Tracker and remote

- #218 resolution comment posted and issue closed.
- Map #180 updated with #218 context pointer and Session 250 checkpoint comments.
- Commit `028d0cc` pushed to `origin/ralph/company-app-full-build`.
- Worktree was clean before this handoff was written.

## How to drive next session

1. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`,
   and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
2. Apply no-question policy unconditionally. Never invoke `question`; defer human decisions
   as labeled tracker issues.
3. Claim Map #180 before the next focused architecture audit.
4. Query Map #180 children/frontier. #218 is closed; all current children should be checked
   before selecting the first open, unblocked, unassigned child.
5. Run focused C-01..C-14 audit. Prioritize Android/iOS shared mobile host/list extraction,
   scheduler capability-code ownership, and route-test setup only if fresh evidence shows material
   leverage or invalid ownership. Keep cleanup-policy centralization as P2 and retain R15/fog without guessing.
6. Create and claim at most one child before implementation; resolve at most one active ticket.
7. Finish tracker, validation, commit, and push work before writing the next numbered handoff.
   After writing that handoff, stop immediately.
