# CR-037: Add health check to app boot test

**Source:** CR-036 audit follow-up (P1)

## What to build

Add a `GET /health` endpoint to the app that runs `SELECT 1` against Postgres. Then modify `.githooks/pre-commit` to curl it after port binding (instead of only checking the port is LISTEN). Transforms the app boot test from "port bound" (false confidence) into "app responds to requests and DB is reachable."

## Acceptance criteria

- [ ] Backend exposes `GET /health` (public, no auth required) that returns 200 if Postgres is reachable, 503 if not
- [ ] `.githooks/pre-commit` step 4 is updated: after confirming port LISTEN, curl `GET /health` with a retry loop (up to 10 attempts, 2s apart)
- [ ] If `/health` returns non-200 after all retries, hook fails the commit with an error message and last response body
- [ ] If `/health` returns 200, hook reports success and proceeds to cleanup

## Blocked by

None — can start immediately.

## Status: ✅ done
