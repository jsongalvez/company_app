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
   backend-touching pushes or merges to `master` plus manual dispatch; it is not a
   local hook gate.
4. Merge normally into `master`; do not rewrite history, force-push, squash, or
   drop commits from a long-lived integration branch unless policy is explicitly
   changed first.

## Gate Classification

Git hooks are bookkeeping, not build pipelines (map #329). Pre-commit formats staged
Kotlin with the standalone ktlint CLI (warn + skip when missing) and syntax-checks
staged shell files. Pre-push runs no gates. Neither hook starts Gradle, Postgres, the
backend, k6, JMH, OpenAPI generation, or Compose compilation, and neither queries the
database. CI owns complete test, contract, integration, target-matrix, and test-data
cleanliness checks, and the active agent never polls it — failures are consumed by the
next session.

While implementing, run the smallest warm Gradle/test task that answers the current
question (`bash scripts/validate.sh` auto-selects; see `backend/AGENTS.md` "Targeted
validation"); do not rerun broad suites because commit or push is next.

## Emergency Or AFK Work

Push to a branch, open a draft or urgent pull request, record reason and owner
in the pull request, and leave a handoff when review or follow-up is
unavailable. Do not merge with failed checks or use force-push/history
rewriting as an emergency shortcut.
