# Gates — #311: recalibrate JMH CI baselines

- [x] G1: jmh workflow stays push-only with manual dispatch and no pull_request trigger
  CHECK: grep -q "workflow_dispatch" .github/workflows/jmh.yml && ! grep -q "pull_request" .github/workflows/jmh.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: JMH runs in no local hook (pre-commit, pre-push)
  CHECK: ! grep -qi "jmh" .githooks/pre-commit .githooks/pre-push
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: baseline file carries CI-runner medians for all 12 benchmarks, dated 2026-08-21
  CHECK: test "$(grep -cE '^\| `[A-Za-z]+Benchmark\.' backend/jmh-baselines.md)" -eq 12 && grep -q "Last updated: 2026-08-21" backend/jmh-baselines.md && grep -q "median" backend/jmh-baselines.md
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G4: thresholds retained — 20% default, 40% BranchDayBenchmark.*
  CHECK: grep -q "THRESHOLD=20" scripts/check-baselines.sh && grep -q 'NOISY_BENCHMARKS=("BranchDayBenchmark.\*")' scripts/check-baselines.sh && grep -q "THRESHOLD=40" scripts/check-baselines.sh
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G5: upstream-failure vs regression classification preserved in workflow retry logic
  CHECK: grep -q "regression not evaluated" .github/workflows/jmh.yml && grep -q "Regression confirmed across two runs" .github/workflows/jmh.yml && grep -q "rerun-tasks" .github/workflows/jmh.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G6: agent docs state the push-only JMH contract (no pull-request claim)
  CHECK: grep -q "backend-touching pushes" AGENTS.md && ! grep -q "runs on pull requests" backend/AGENTS.md
  EXPECT: EXIT 0
  EVIDENCE: exit 0
