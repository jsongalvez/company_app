# Contributing

Use a short-lived branch from `master`, named `ralph/<feature-name>` for feature or
fix work. Push commits to that branch and open a pull request targeting `master`.
Do not push directly to `master` as a substitute for review.

## Normal Merge Flow

1. Open or update the pull request early, including issue references in commit
   messages (`ref #<number>`).
2. Request review and resolve review comments before merge.
3. Wait for every applicable CI check to pass. Backend, shared, and Compose
   changes use `quality`; API contract changes use `openapi`. JMH runs on
   backend-touching pushes and merges to `master`; it is not a local hook gate.
4. Merge normally into `master`; do not rewrite history, force-push, squash, or
   drop commits from a long-lived integration branch unless policy is explicitly
   changed first.

## Gate Classification

Pre-commit provides fast local feedback: staged Kotlin formatting, changed-module
compile/static checks, and staged shell syntax checks. It does not require Postgres or
run full tests. CI owns complete test, contract, integration, target-matrix, and
test-data cleanliness checks.

Docs-only changes (`docs/**/*.md`, `.opencode/**/*.md`, `AGENTS.md`,
`CONTEXT.md`, `README*.md`, `CONTRIBUTING.md`, and `CHANGELOG.md`) skip local
pre-push code, contract, Compose, startup, and k6 gates. They still use the
pull-request workflow by default.

Mixed or gate-sensitive pushes run complete local pre-push checks, including
test-data cleanliness, OpenAPI, Compose desktop/Android compilation, and k6
against the disposable test database.

## Emergency Or AFK Work

Push to a branch, open a draft or urgent pull request, record reason and owner
in the pull request, and leave a handoff when review or follow-up is
unavailable. Do not merge with failed checks or use force-push/history
rewriting as an emergency shortcut.
