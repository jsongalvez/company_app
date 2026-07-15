# CR-041: Add shared module compilation + test step to pre-commit

**Source:** CR-036 audit follow-up (P2)

## What to build

Add a step to `.githooks/pre-commit` that explicitly compiles and tests the `:shared` module. Currently the shared module (serialization/DTOs) is only compiled transitively as a dependency — there is zero explicit verification. Add `./gradlew :shared:build` (or at minimum `:shared:compileKotlinJvm`) to catch shared module breakage before commit.

## Acceptance criteria

- [ ] `.githooks/pre-commit` includes a new step that runs `./gradlew :shared:build --no-daemon` (or equivalent)
- [ ] If `:shared` compilation or tests fail, commit is blocked with the Gradle error output
- [ ] Step is placed logically — after ktlint/detekt but before the composeApp compilation check (or after composeApp, whichever minimizes total latency from configuration cache reuse)
- [ ] If `:shared` has no test source set yet, the step uses `:shared:compileKotlinJvm` (or the correct target) and a comment notes that tests should be added later

## Blocked by

None — can start immediately.

## Status: ready-for-agent
