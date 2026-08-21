# Handoff - Map #310, Session 369

## Authority

- Map #310 remains continuation authority.
- Claimed child: [Build: make Wayfinder AFK branch and CI workflow enforceable](https://github.com/jsongalvez/company_app/issues/312).
- Ticket branch: `ralph/wayfinder-312`, based on `master`, current commit `767ee896`.
- Integration branch: `ralph/company-app-full-build`, separate branch, current remote commit `0670adbd`, 600+ commits ahead of `master`.
- PR: https://github.com/jsongalvez/company_app/pull/313.

## Work completed

- Repaired master-compatible CI workflow dependencies without merging integration history.
- Removed premature OpenAPI workflow and unavailable k6/cleanliness gates from ticket branch quality workflow.
- Added dotenv missing-file tolerance for CI environment variables.
- Made Wayfinder CI fall back to all reported checks when no required checks are configured.
- Made Git hooks executable and added commit-msg enforcement.
- Fixed CI test date seeding to use `BranchDayService.manilaZone` instead of JVM default timezone.
- Added runtime-log ignore rules.
- Changed quality workflow to run on `master` and `ralph/**` pushes only; removed `pull_request` trigger to prevent duplicate full CI runs.
- Changed JMH to run on `master` backend pushes or manual dispatch only. It no longer runs on PR or integration pushes.

## Verification

- Local pre-commit and pre-push gates passed on commit `767ee896`.
- Full trimmed quality command passed under `TZ=UTC`.
- Earlier PR #313 checks passed: backend quality, Compose Android/Desktop, and JMH.
- Latest PR #313 quality run for `767ee896` is pending: backend quality and both Compose compile jobs.
- `bash scripts/wayfinder-ci.sh wait-ci https://github.com/jsongalvez/company_app/pull/313 30` passed before latest workflow-trigger change.
- Ticket #312 remains open. No issue resolution or successor frontier mutation performed.

## Next action

- Wait for latest push-only quality run to finish.
- Confirm `gh pr checks 313` has backend-quality and both Compose checks passing.
- Run `bash scripts/wayfinder-ci.sh wait-ci https://github.com/jsongalvez/company_app/pull/313` from `ralph/wayfinder-312`.
- Do not merge #312 before integration: its master-based CI fixes are temporary compatibility workarounds for missing integration artifacts.
- Open a dedicated PR from `ralph/company-app-full-build` to `master` and complete full review and CI.
- Merge integration PR first, then rebase/update `ralph/wayfinder-312` onto new `master`.
- Remove temporary #312 compatibility trims now supplied by integration, rerun PR #313 CI, and resolve #312 only after green checks.
- Keep #312 open until authorized continuation performs final resolution evidence and Map #310 update.
