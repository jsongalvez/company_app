## Verified facts

- `tests/k6/**` is the runtime source for HTTP load-test metrics, thresholds, and suites.
- `.githooks/pre-push` runs local k6 validation, but no GitHub workflow path filter includes `tests/k6/**`.
- Existing quality, OpenAPI, and JMH workflows do not execute k6.

## Decision needed

Should CI execute k6 when `tests/k6/**` changes, or should CI only trigger a documented validation job without running k6?

## Blocker

Adding a path trigger without an execution policy creates a false coverage claim. Running k6 requires disposable PostgreSQL, app startup, credentials, cleanup, and threshold ownership decisions.

## Smallest safe next action

Choose CI execution policy, then add the narrow path trigger/job and deterministic workflow coverage.
