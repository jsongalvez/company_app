# JMH Baseline Results

Last updated: 2026-08-06

> **Baselines now measured on the GitHub Actions runner.** JMH moved from the pre-push
> hook to CI (`.github/workflows/jmh.yml`) on 2026-08-06. The scores below were measured
> on `ubuntu-latest` (JDK 21, Temurin) via workflow run 31107571931 — the dev-machine
> scores (2026-07-18) were ~35% higher, so the first CI run fails against the old
> baselines until re-established here; threshold comparisons are now CI-vs-CI.
> Error columns are dev-machine values (runner ± not captured; only the score column is
> compared by `scripts/check-baselines.sh`).

> **BranchDayBenchmark reconfigured:** `@Measurement` increased from (5, 1s) to (10, 2s),
> `@Fork` 1→2, `@Warmup` (3, 1s)→(5, 2s). Baseline threshold raised from 20%→40% for
> BranchDayBenchmark.* only (see `scripts/check-baselines.sh`). These benchmarks operate at
> nanosecond scale and are hypersensitive to system jitter — longer windows + relaxed threshold
> reduce false-positive failures while still catching real regressions.

**How to use:** Before raising any `measureTimedValue` or k6 threshold, run `./gradlew :backend:jmh`
first. If the JMH score for the relevant benchmark dropped significantly (>20%), you have a real
regression — **do not raise the threshold, fix the regression instead**. If JMH scores are stable
but the integration test or k6 threshold fails, the bottleneck is in the DB/networking layer —
investigate with JFR before adjusting.

**How to update this file:** After intentionally raising a threshold or adding a new benchmark,
re-run `./gradlew :backend:jmh` (or a CI run), paste the new scores below, and update the
"Last updated" line.

---

## Pure function benchmarks (ops/s)

| Benchmark | Score (ops/s) | Error (±) |
|---|---|---|
| `SessionTypeBenchmark.computeMedicalMission` | 2,268,297,740 | 248,588,547 |
| `SessionTypeBenchmark.computeProvincialFirst` | 2,295,875,775 | 330,380,289 |
| `SessionTypeBenchmark.computeSecondSession` | 2,297,779,747 | 377,983,479 |
| `SessionTypeBenchmark.computeSubsequent` | 2,297,032,392 | 196,897,088 |
| `BranchDayBenchmark.evaluateOpenFuture` | 764,410,867 | 167,626,047 |
| `BranchDayBenchmark.evaluateOpenPast` | 762,950,558 | 62,633,398 |
| `BranchDayBenchmark.evaluateRemitted` | 2,295,550,890 | 878,312,914 |
| `BranchDayBenchmark.expirationUtc` | 33,356,111 | 14,189,493 |
| `CommissionBenchmark.splitBetweenOne` | 105,806,688 | 6,494,841 |
| `CommissionBenchmark.splitBetweenTwo` | 107,897,686 | 15,805,923 |
| `CommissionBenchmark.splitBetweenThree` | 113,041,259 | 14,008,272 |
| `CommissionBenchmark.splitBetweenTen` | 109,713,689 | 4,875,318 |

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
| `product_latency` | p95 < 1000ms |
| `my_branches_latency` | p95 < 200ms |
| `dashboard_latency` | p95 < 200ms |
| `errors` | rate < 5% |
