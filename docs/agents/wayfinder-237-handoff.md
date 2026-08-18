# Handoff - Architecture Map #180, Session 134

## What this is

Session 134 resolved [Build: complete iOS Compose platform bridge](https://github.com/jsongalvez/company_app/issues/205), the R28 implementation child of Map #180. The next frontier child is [Build: make full k6 workflows exercise valid fixtures](https://github.com/jsongalvez/company_app/issues/206).

## Session outcome

- Loaded latest handoff, Map #180 body/comments, every Context Pointer, `/wayfinder`, `/codebase-design`, project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`, domain/business/build documents, ADR-0020, and Compose/shared guidance.
- Retried publication before claiming work. First retry found disposable test-data leaks and repaired them with `bash scripts/clean-test-db.sh`; second retry passed all local pre-push gates. GitHub rejected both pushes because active PAT lacks `workflow` scope.
- Claimed exactly one ticket: #205. Added iOS actuals for every common `expect` seam: mobile navigation host, divergent UI subtrees, and native export sharing. Corrected Swift generated-controller symbol wiring. Preserved ADR-0020 smallest-divergent-subtree rule.
- Review found scene-based iOS presentation and iPad popover defects; fixed active scene-window lookup, popover anchoring, empty payload rejection, and filename path sanitization.
- Repaired stale Compose test fixtures using removed `DIRECT` source type; current shared wire enum uses `MANUAL_OVERRIDE`.
- Closed #205 with resolution evidence and updated Map #180 Decisions so far. No ADR was needed.

## Verification

- `:composeApp:desktopTest` passed with 394 tests after fixture repair.
- `:composeApp:ktlintCheck` passed, including iOS source sets.
- `:composeApp:compileDebugKotlinAndroid` and `:composeApp:detekt` passed.
- `:backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm` passed.
- Pre-commit passed formatting, detekt, ktlint, backend tests, shared JVM compile, OpenAPI contract checks, cleanliness, and Postgres connectivity.
- Pre-push passed OpenAPI route/secret/negative-drift checks, Compose Android/Desktop compilation, backend distribution build, health check, k6 baseline with 0% errors and all thresholds, and final DB cleanup.
- iOS compile was attempted with `./gradlew :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64`; blocked before source compilation because this Linux runner cannot resolve `org.jetbrains.kotlin:kotlin-native-prebuilt:2.3.10` artifact `kotlin-native-prebuilt-2.3.10-linux-aarch64.tar.gz`. No Xcode toolchain exists on runner.
- Independent P1-P4 review found zero HARD findings after the share-sheet fix. Remaining SOFT: physical iPhone/iPad navigation and export verification requires Apple tooling; Intel simulator is out of declared target scope.

## Commit and remote

- `3c47c7d` (`feat: complete iOS Compose bridge`, `Ref #205`) contains implementation, host wiring, and stale fixture repair.
- Push was attempted after all hooks. GitHub rejected it: `refusing to allow an OAuth App to create or update workflow .github/workflows/openapi.yml without workflow scope`.
- `gh auth status` reports active token scopes `gist, read:org, repo`; remote remains `73199021fcf04cf55eb96ebbe94b9f9407836b57`. Local branch is ahead by fifteen commits. Do not rewrite or drop commits.
- Worktree is clean before this handoff file was written. This handoff is intentionally the final uncommitted chain signal because external push remains blocked.

## How to drive next session

1. First retry publishing local commits after obtaining PAT `workflow` scope; verify remote contains `3c47c7d`. Do not rewrite or drop commits. Keep issue references in every commit message.
2. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`, and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
3. Query Map #180 children/frontier. Claim exactly one ticket: #206. Do not claim any other ticket.
4. Read #206 body and k6/build/fixture documents. Implement only R29: make full k6 workflows exercise valid fixtures and account for failures correctly.
5. Run targeted checks, selected risk-based review profile, full required quality gates, cleanliness, and pre-push. Repair local failures and retry external push; record exact external evidence if auth remains blocked.
6. Resolve #206, update Map #180 and architecture lessons if needed, commit with issue reference, and push before writing next numbered handoff. After writing it, stop immediately.
