# Map #180 Child: Correct stale k6 threshold documentation

Part of #180.

## Task

Make k6 threshold documentation match shipped runtime metric and profile ownership.
`tests/k6/results/baseline-results.md` and `backend/jmh-baselines.md` list nonexistent
`sessions_latency`, while runtime defines `session_latency` and the baseline profile measures
`branches_latency`, `clients_search_latency`, `product_latency`, `my_branches_latency`,
`dashboard_latency`, and `errors`.

## Scope

- Correct the two stale threshold tables from `tests/k6/helpers.js` and `tests/k6/baseline.js`.
- Preserve runtime thresholds and k6 behavior; docs-only change.
- Add or update deterministic documentation/source consistency validation if an existing check
  covers these tables.
- No ADR needed; this corrects documentation to existing ownership.

## Validation

- Repository search finds no `sessions_latency` claim in threshold documentation.
- Documented baseline metrics and thresholds exactly match `thresholdProfiles.baseline`.
- Existing k6 inspection and documentation-only gate classification pass.
