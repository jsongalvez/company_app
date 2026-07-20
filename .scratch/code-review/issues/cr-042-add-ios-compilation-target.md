# CR-042: Add iOS compilation target to pre-commit composeApp check

**Source:** CR-036 audit follow-up (P3)

## What to build

Add the iOS compilation target to `.githooks/pre-commit` step 2b (composeApp compilation). Currently only `compileKotlinDesktop` and `compileDebugKotlinAndroid` are checked. Add the equivalent iOS task so that iOS-only breakage is caught before commit.

## Acceptance criteria

- [ ] `.githooks/pre-commit` step 2b includes the iOS Kotlin compilation task (e.g. `./gradlew :composeApp:compileKotlinIosArm64 --no-daemon`)
- [ ] The correct iOS task name is verified against the composeApp module's available tasks
- [ ] If iOS compilation fails, commit is blocked
- [ ] Time impact of adding iOS compilation is acceptable (expected: minimal — configuration cache should handle it)

## Blocked by

None — can start immediately.

## Status: ready-for-agent
