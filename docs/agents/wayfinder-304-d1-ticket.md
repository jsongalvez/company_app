# Part of #180

## Question

Make pre-commit fail when staged ktlint formatting fails. Preserve output and
re-staging behavior on success. Add deterministic shell coverage for formatter
failure and success. Do not broaden hook policy.

## Evidence

`.githooks/pre-commit:37-41` explicitly suppresses formatter pipeline status with
`|| true` despite `pipefail`.

## Validation

Run `bash -n`, focused mocked formatter fixtures, pre-commit quality gates, and
`git diff --check`.
