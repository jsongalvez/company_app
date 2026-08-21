# Handoff — Map #310, Session 373

## Authority

- Map #310 remains continuation authority.
- Ticket #301 resolved and merged: PR https://github.com/jsongalvez/company_app/pull/328 → `master` as `f335737d`, all PR checks green (backend-quality, compose Android/Desktop, k6-baseline, openapi, new **k6-contract-suites** 5m52s) and post-merge master `k6` run success. quality/openapi/jmh correctly did not trigger on the merge push (k6-scope paths only); the full matrix ran green on the PR head.
- Map #310 Decisions-so-far carries the #301 line with merge evidence; Next priority now lists [Build: recalibrate JMH CI baselines](https://github.com/jsongalvez/company_app/issues/311).

## Work completed

- Claimed #301 (`wayfinder-verify-child.sh 310 301` passed first).
- New `.github/workflows/k6.yml`: narrow `tests/k6/**` path triggers (push to master, PR, dispatch), disposable Postgres 18 service, ephemeral credentials (no repo secrets), concurrency cancel.
- New `scripts/run-k6-contract-suites.sh`: single runner for CI + local. Forces backend onto test DB (`POSTGRES_DB=$TEST_DB_NAME`), deterministic DevSeeder fixtures via `TEST_USERNAME`/`TEST_PASSWORD`, registers ephemeral capability-less user via `/auth/register` for the authz 403 contract (password must satisfy min-length 8 → `password`), runs all five suites, failure-safe EXIT trap (setsid process-group kill → `clean-test-db.sh` → verify).
- Suite order is load-bearing: remittance-race-test runs FIRST — it requires a fresh clock-in (strict 201) while every other suite tolerates 409, and clock-in is one-active-per-user.
- All suites run even after a failure; first failure's exit code preserved (review HARD fix — original `break` skipped remaining suites).
- Gates file `docs/gates/301-k6-ci.md`: negative control fail-red 1/7 recorded before implementation; 7/7 met after. Checker 60s cap prevents full e2e as a CHECK — G7 restructured to workflow/script consistency; e2e evidence lives in CI runs + resolution comment.

## Verification

- Local full e2e green twice (five suites pass, DB verified clean after each run).
- Negative control: cleanup trap proven against intentionally failed boot (port conflict) — DB restored clean.
- Review: standard profile P1–P4 parallel lens; one HARD fixed (run-all loop), SOFTs fixed (setsid group kill, port-busy precheck refusing foreign backends on APP_PORT), SOFTs accepted with disposition (DevSeeder early-return without fixture branch — pre-existing, out of scope; register JSON escaping — CI-only fixed creds).

## Environment notes

- Root `.env` exists this machine; pre-commit ran normally. Local Postgres up via docker compose.
- Port 8080 on this dev machine is a host docker-proxy (coolify) — script defaults to APP_PORT=8180 locally; CI pins 8080 explicitly.
- k6 CLI present locally (`/usr/local/bin/k6`).

## Next action

- Next Wayfinder frontier child in map order: [Build: recalibrate JMH CI baselines](https://github.com/jsongalvez/company_app/issues/311) — claim it first, resolve exactly one child this session.
- Branch `ralph/wayfinder-301` is merged — do not reuse.
- New sessions start AFK builds from `master` at `f335737d` or later using the `ralph/wayfinder-<ticket>` branch pattern enforced since #312.
