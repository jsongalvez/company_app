# Handoff - Map #310, Session 368

## Authority

- Map #310 remains continuation authority.
- Claimed child: [Build: make Wayfinder AFK branch and CI workflow enforceable](https://github.com/jsongalvez/company_app/issues/312).

## Work completed

- Branch: `ralph/wayfinder-312`, based on `master`.
- Commit: `85d1b2eb`.
- Pull request: https://github.com/jsongalvez/company_app/pull/313
- Added fail-closed `scripts/wayfinder-ci.sh` for branch, PR, CI, and resolution checks.
- Wired branch preparation and ownership validation into `scripts/wayfinder-loop.sh`.
- Added deterministic fixture test: `scripts/wayfinder-afk-test.sh`.
- Added gates ledger and AFK instructions in `CONTRIBUTING.md`.

## Verification

- `bash scripts/wayfinder-afk-test.sh`: PASS.
- `bash -n scripts/wayfinder-loop.sh scripts/wayfinder-ci.sh scripts/wayfinder-afk-test.sh`: PASS.
- `git diff --check`: PASS.
- PR CI: BLOCKED. OpenAPI references missing `scripts/check-openapi-spec.sh`; quality excludes missing `:backend:publishOpenApiSpec`; k6 startup cannot find CI `.env`.
- Child #312 remains open. Do not resolve until PR required checks are green.

## Next action

- Repair baseline CI integration blockers on PR #313 or update branch from a master containing those required artifacts.
- Re-run `scripts/wayfinder-ci.sh wait-ci https://github.com/jsongalvez/company_app/pull/313`.
- Only after green checks run `scripts/wayfinder-ci.sh resolve 312 https://github.com/jsongalvez/company_app/pull/313`, update Map #310, and write successor handoff.

**Status:** #312 implementation complete locally; tracker resolution blocked by failing CI.
