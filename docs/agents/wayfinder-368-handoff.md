# Handoff - Map #310, Session 368

## Authority

- Map #310 remains continuation authority.
- `ralph/company-app-full-build` is integration branch for the 600+ commit build and is the branch to push next.
- `ralph/wayfinder-312` is separate ticket branch for #312 and must not be used as integration branch.
- Claimed child: [Build: make Wayfinder AFK branch and CI workflow enforceable](https://github.com/jsongalvez/company_app/issues/312).

## Work completed

- Integration branch: `ralph/company-app-full-build`, 748 commits ahead of `master`; local branch is 8 commits ahead of `origin/ralph/company-app-full-build`.
- Ticket branch: `ralph/wayfinder-312`, PR https://github.com/jsongalvez/company_app/pull/313.
- Ticket branch commits: `e5b0dcd0`, `85d1b2eb`, `0a1d440e`, `f32decb8`.
- Added fail-closed `scripts/wayfinder-ci.sh` for branch, PR, CI, and resolution checks.
- Wired branch preparation and ownership validation into `scripts/wayfinder-loop.sh`.
- Added deterministic fixture test: `scripts/wayfinder-afk-test.sh`.
- Added gates ledger and AFK instructions in `CONTRIBUTING.md`.

## Verification

- `bash scripts/wayfinder-afk-test.sh`: PASS.
- `bash -n scripts/wayfinder-loop.sh scripts/wayfinder-ci.sh scripts/wayfinder-afk-test.sh`: PASS.
- `git diff --check`: PASS.
- PR #313 CI: BLOCKED. OpenAPI references missing `scripts/check-openapi-spec.sh`; quality excludes missing `:backend:publishOpenApiSpec`; k6 startup cannot find CI `.env`.
- Child #312 remains open. Do not resolve until PR required checks are green.

## Next action

- Do not push `ralph/wayfinder-312` as integration branch.
- First inspect and push `ralph/company-app-full-build`: `git switch ralph/company-app-full-build && git push origin ralph/company-app-full-build`.
- Do not run old loop against integration branch until runtime-log fix `f32decb8` is ported; old loop can auto-commit tracked runtime logs repeatedly.
- Repair baseline CI integration blockers on PR #313 or update branch from a master containing those required artifacts.
- Re-run `scripts/wayfinder-ci.sh wait-ci https://github.com/jsongalvez/company_app/pull/313` only from ticket branch.
- Only after green checks run `scripts/wayfinder-ci.sh resolve 312 https://github.com/jsongalvez/company_app/pull/313`, update Map #310, and write successor handoff.

**Status:** Integration branch handoff corrected; #312 implementation exists separately and remains blocked by failing CI.
