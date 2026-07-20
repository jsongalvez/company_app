# CR-040: Wire k6 baseline into pre-push hook

**Source:** CR-036 audit follow-up (P2)

## What to build

Add a step to `.githooks/pre-push` that runs `scripts/load-test/baseline.js` via k6 against a booted app instance. The hook starts the app (same pattern as pre-commit step 4), runs k6, checks thresholds, stops the app, and blocks the push if thresholds are violated. Transforms k6 from documentation into an enforced gate.

## Acceptance criteria

- [ ] `.githooks/pre-push` starts the app (build + run in background, wait for port + `/health`)
- [ ] K6 is checked for availability; if not installed, hook prints a warning but does not block
- [ ] `k6 run scripts/load-test/baseline.js` runs against the local app with `--quiet` or output-tail-friendly flags
- [ ] If k6 exits non-zero (threshold violation), hook fails with the k6 summary output
- [ ] App is stopped and cleaned up regardless of k6 result (trap EXIT or explicit cleanup)
- [ ] `TEST_USERNAME`/`TEST_PASSWORD` are read from `.env` or environment

## Blocked by

CR-037 (health check endpoint needed for reliable app-is-ready signal)

## Status: ready-for-agent
