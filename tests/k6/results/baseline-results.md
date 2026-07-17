# k6 Baseline Results

Last updated: 2026-07-14

**How to use:** Run k6 and paste the summary output here. This file is the human-readable
record of what "normal" looks like for each endpoint.

**How to update:** Re-run the baseline after intentionally adding new functionality to an
endpoint. Do NOT update if a threshold failed due to an unintentional regression.

```bash
TEST_USERNAME=owner TEST_PASSWORD=pass \
  k6 run tests/k6/baseline.js --summary-export=tests/k6/results/latest.json
```

---

## Current thresholds (from baseline.js)

| Metric | Threshold |
|---|---|
| `branches_latency` | p95 < 500ms |
| `clients_search_latency` | p95 < 1000ms |
| `sessions_latency` | p95 < 1000ms |
| `errors` | rate < 5% |

## Notes

- Thresholds are intentionally generous during early development.
- Tighten thresholds after the app stabilizes in production with real traffic patterns.
- A threshold violation with stable JMH scores indicates a DB or networking bottleneck — profile with JFR.
