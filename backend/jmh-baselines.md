# JMH Baseline Results

Last updated: 2026-07-18 (re-run after prior pre-push showed noise-induced regression; all benchmarks within 8% of original baseline)

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
| `SessionTypeBenchmark.computeMedicalMission` | 3,580,744,380 | 248,588,547 |
| `SessionTypeBenchmark.computeProvincialFirst` | 3,578,378,851 | 330,380,289 |
| `SessionTypeBenchmark.computeSecondSession` | 3,556,602,134 | 377,983,479 |
| `SessionTypeBenchmark.computeSubsequent` | 3,582,534,671 | 196,897,088 |
| `BranchDayBenchmark.evaluateOpenFuture` | 1,237,169,133 | 167,626,047 |
| `BranchDayBenchmark.evaluateOpenPast` | 1,266,449,922 | 62,633,398 |
| `BranchDayBenchmark.evaluateRemitted` | 3,366,685,264 | 878,312,914 |
| `BranchDayBenchmark.expirationUtc` | 43,321,724 | 14,189,493 |
| `CommissionBenchmark.splitBetweenOne` | 166,833,428 | 6,494,841 |
| `CommissionBenchmark.splitBetweenTwo` | 164,045,084 | 15,805,923 |
| `CommissionBenchmark.splitBetweenThree` | 174,621,001 | 14,008,272 |
| `CommissionBenchmark.splitBetweenTen` | 165,872,487 | 4,875,318 |

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
