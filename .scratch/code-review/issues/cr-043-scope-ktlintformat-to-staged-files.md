# CR-043: Scope ktlintFormat to staged files only

**Source:** CR-036 audit follow-up (P3)

## What to build

Replace the pre-commit hook's project-wide `./gradlew ktlintFormat && git add -u` with a scoped format that only touches staged `.kt`/`.kts` files. The current behavior formats ALL project files then stages ALL modified tracked files — it can sweep unrelated formatting changes into a commit.

## Acceptance criteria

- [ ] `.githooks/pre-commit` step 1 is updated to only format and re-stage files that are actually staged
- [ ] Approach: `git diff --cached --name-only --diff-filter=ACM` filtered to `*.kt`/`*.kts` files, then pass those to ktlint format or use `ktlintFormat` with file-scoping (if the plugin supports it)
- [ ] Fallback: if the ktlint Gradle plugin doesn't support file-scoped formatting, document the limitation and keep the current behavior with a comment explaining the tradeoff
- [ ] After format, only the formatted staged files are re-staged (not all tracked files)

## Blocked by

None — can start immediately.

## Status: ready-for-agent
