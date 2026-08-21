# JMH Baseline Results

Last updated: 2026-08-21

> **Baselines are medians of repeated clean CI runs on the GitHub Actions runner.**
> JMH is a manual diagnostic: `.github/workflows/jmh.yml` is `workflow_dispatch`-only
> (no push/merge/PR triggers, no local hook, never a session or ticket blocker). The
> scores below are the per-benchmark medians of
> 5 clean `ubuntu-latest` (JDK 21, Temurin) runs on master, workflow runs 32449735234,
> 32456048722, 32456448282, 32457067330, 32457565962 (2026-08-21, ticket #311); benchmark
> code was identical across all five. The range column shows the observed min–max across
> those runs; only the score (median) column is compared by `scripts/check-baselines.sh`.
>
> **Runner variance is large:** shared-runner CPU allocation clusters into low/mid/high
> lots (~1.6× between extremes on the same code). Worst observed single-run dip vs median
> is −20.5% (`BranchDayBenchmark.evaluateRemitted`, covered by its 40% threshold); the
> worst non-BranchDay dip is −17.7%. The two-run reproduction rule (fail only when the
> regression reproduces on retry) plus the 20%/40% thresholds absorb this variance —
> every one of the 5 sampled runs passes against these medians. Recalibrate by dispatching
> the workflow several times and taking per-benchmark medians, not by copying one run.

> **BranchDayBenchmark reconfigured:** `@Measurement` increased from (5, 1s) to (10, 2s),
> `@Fork` 1→2, `@Warmup` (3, 1s)→(5, 2s). Baseline threshold raised from 20%→40% for
> BranchDayBenchmark.* only (see `scripts/check-baselines.sh`). These benchmarks operate at
> nanosecond scale and are hypersensitive to system jitter — longer windows + relaxed threshold
> reduce false-positive failures while still catching real regressions.

**How to use:** Before raising any `measureTimedValue` or k6 threshold, run `./gradlew :backend:jmh`
first (locally, for direction only — these CI medians are the comparison reference). If a dispatched
JMH score for the relevant
benchmark dropped significantly (>20%, >40% for BranchDayBenchmark.*), you have a real
regression — **do not raise the threshold, fix the regression instead**. If JMH scores are stable
but the integration test or k6 threshold fails, the bottleneck is in the DB/networking layer —
investigate with JFR before adjusting.

**How to update this file:** After intentionally raising a threshold or adding a new benchmark,
re-run `./gradlew :backend:jmh` (or a CI run), paste the new scores below, and update the
"Last updated" line. For a runner-baseline shift, dispatch the CI workflow several times and
recompute the medians.

---

## Pure function benchmarks (ops/s)

Median of 5 CI runs; range = observed min–max across those runs.

| Benchmark | Score (ops/s) | Range (min–max, 5 CI runs) |
|---|---|---|
| `SessionTypeBenchmark.computeMedicalMission` | 3,206,245,841 | 2,641,504,703 – 4,186,321,501 |
| `SessionTypeBenchmark.computeProvincialFirst` | 3,234,150,921 | 2,661,402,625 – 4,177,975,924 |
| `SessionTypeBenchmark.computeSecondSession` | 3,229,904,387 | 2,659,486,906 – 4,248,639,895 |
| `SessionTypeBenchmark.computeSubsequent` | 2,935,277,683 | 2,650,004,573 – 4,189,209,195 |
| `BranchDayBenchmark.evaluateOpenFuture` | 1,067,897,188 | 941,170,487 – 1,776,657,127 |
| `BranchDayBenchmark.evaluateOpenPast` | 1,099,964,102 | 954,707,663 – 1,801,880,789 |
| `BranchDayBenchmark.evaluateRemitted` | 3,316,316,977 | 2,637,613,718 – 4,173,401,051 |
| `BranchDayBenchmark.expirationUtc` | 37,884,600 | 34,071,943 – 51,983,452 |
| `CommissionBenchmark.splitBetweenOne` | 125,416,728 | 123,934,824 – 175,571,728 |
| `CommissionBenchmark.splitBetweenTwo` | 124,213,350 | 123,419,478 – 184,303,646 |
| `CommissionBenchmark.splitBetweenThree` | 135,378,106 | 129,427,114 – 196,292,322 |
| `CommissionBenchmark.splitBetweenTen` | 127,326,383 | 118,744,402 – 181,771,163 |

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
