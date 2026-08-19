# Part of #180

## Question

Correct `backend/AGENTS.md` k6 threshold ownership guidance. Point agents to
`tests/k6/helpers.js` and named `thresholdProfiles`; retain result history in
`tests/k6/results/baseline-results.md`; distinguish JMH baselines from k6
profiles. Documentation only. Verify every current k6 consumer and remove stale
ownership claims.

## Evidence

`tests/k6/helpers.js:79-95` exports profiles and `tests/k6/baseline.js:8-10`
consumes them, while `backend/AGENTS.md:488-498,531-533,544-549` names stale
owners.

## Validation

Grep stale claims, inspect k6 imports, run relevant shell/docs checks, and
`git diff --check`.
