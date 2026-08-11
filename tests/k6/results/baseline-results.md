# k6 Baseline Results

Last updated: 2026-08-12

**How to use:** Run k6 against the test database and paste the summary output here. This file
is the human-readable record of what "normal" looks like for each endpoint.

**How to run:**

1. Start the app on the test DB (Terminal 1):
   ```bash
   POSTGRES_DB=company_app_test TEST_USERNAME=owner TEST_PASSWORD=pass ./gradlew :backend:run
   ```

2. Run the baseline (Terminal 2):
   ```bash
   TEST_USERNAME=owner TEST_PASSWORD=pass \
     k6 run tests/k6/baseline.js --summary-export=tests/k6/results/latest.json
   ```

3. Clean the test DB afterwards:
   ```bash
   bash scripts/clean-test-db.sh
   ```

**How to update:** Re-run the baseline after intentionally adding new functionality to an
endpoint. Do NOT update if a threshold failed due to an unintentional regression.

---

## Current thresholds (from baseline.js)

| Metric | Threshold |
|---|---|
| `branches_latency` | p95 < 500ms |
| `clients_search_latency` | p95 < 1000ms |
| `sessions_latency` | p95 < 1000ms |
| `my_branches_latency` | p95 < 200ms |
| `dashboard_latency` | p95 < 200ms |
| `errors` | rate < 5% |

## Notes

- `my_branches_latency` baselined 2026-08-10 (#140 — BranchSelect landed its first consumer):
  3 runs on a clean test DB (owner user, no branch assignments — the empty-branch path of the
  #98 union query) measured p95 15.3 / 8.3 / 7.3 ms. Threshold tightened to 200 ms (≈13–27×
  headroom). Re-baseline when the endpoint sees real assignment data.
- `dashboard_latency` baselined 2026-08-12 (#147 — the session dashboard endpoint
  `GET /api/branches/{branchId}/dashboard/today` landed with its first frontend consumer):
  3 runs measured p95 2.73 / 2.76 / 2.47 ms (empty day, single branch — the k6 setup now
  creates a branch + clocks in so the attendance gate passes). Threshold tightened to 200 ms
  (≈72× headroom). Re-baseline when the endpoint sees real session/commission data.
- Thresholds are intentionally generous during early development.
- Tighten thresholds after the app stabilizes in production with real traffic patterns.
- A threshold violation with stable JMH scores indicates a DB or networking bottleneck — profile with JFR.
