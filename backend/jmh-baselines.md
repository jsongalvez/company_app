# JMH Baseline Results

Last updated: 2026-07-14 (commit range: 0f8fe65..2d817fb)

**How to use:** Before raising any `measureTimedValue` or k6 threshold, run `./gradlew :backend:jmh`
first. If the JMH score for the relevant benchmark dropped significantly (>20%), you have a real
regression — **do not raise the threshold, fix the regression instead**. If JMH scores are stable
but the integration test or k6 threshold fails, the bottleneck is in the DB/networking layer —
investigate with JFR before adjusting.

**How to update this file:** After intentionally raising a threshold or adding a new benchmark,
re-run `./gradlew :backend:jmh`, paste the new scores below, and update the "Last updated" line.

---

## Pure function benchmarks (ops/s)

| Benchmark | Score (ops/s) | Error (±) |
|---|---|---|
| `SessionTypeBenchmark.computeMedicalMission` | 3,655,579,314 | 126,351,230 |
| `SessionTypeBenchmark.computeProvincialFirst` | 3,645,516,545 | 105,420,350 |
| `SessionTypeBenchmark.computeSecondSession` | 3,625,109,850 | 339,878,410 |
| `SessionTypeBenchmark.computeSubsequent` | 3,626,806,833 | 134,529,544 |
| `BranchDayBenchmark.evaluateOpenFuture` | 548,007,352 | 331,725,067 |
| `BranchDayBenchmark.evaluateOpenPast` | 567,355,085 | 232,355,664 |
| `BranchDayBenchmark.evaluateRemitted` | 1,456,638,238 | 462,290,572 |
| `BranchDayBenchmark.expirationUtc` | 21,476,475 | 16,095,356 |
| `CommissionBenchmark.splitBetweenOne` | 166,626,501 | 11,582,068 |
| `CommissionBenchmark.splitBetweenTwo` | 163,782,287 | 10,428,396 |
| `CommissionBenchmark.splitBetweenThree` | 174,423,568 | 8,825,960 |
| `CommissionBenchmark.splitBetweenTen` | 168,134,763 | 6,146,358 |

---

## Integration test measureTimedValue thresholds

| Test | Threshold | Hot path |
|---|---|---|
| `AttendanceServicePostgresTest.clockIn` | < 5s | resolveOrCreate + hasCapability + audit |
| `SessionServicePostgresTest.create` | < 10s | computeSessionType + resolveOrCreate + hasCapability + audit |
| `CommissionServicePostgresTest.recal` | < 5s | splitCommission + recalculate + inclusive |
| `RemittanceServicePostgresTest.submit` | < 15s | SERIALIZABLE submit + snapshot + branch day transition |

---

## k6 HTTP latency thresholds

| Metric | Threshold |
|---|---|
| `branches_latency` | p95 < 500ms |
| `clients_search_latency` | p95 < 1000ms |
| `sessions_latency` | p95 < 1000ms |
| `errors` | rate < 5% |
