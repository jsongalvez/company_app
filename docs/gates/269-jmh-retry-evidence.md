# Gates - 269: JMH retry evidence

- [x] G1: JMH retry forces fresh benchmark execution
  CHECK: grep -F -- '--rerun-tasks' .github/workflows/jmh.yml
  EXPECT: --rerun-tasks
  EVIDENCE: ./gradlew :backend:jmh --no-daemon --rerun-tasks 2>&1 | tee "$log"

- [x] G2: baseline checker distinguishes invalid benchmark evidence from regression
  CHECK: bash scripts/check-baselines-test.sh
  EXPECT: check-baselines fixtures: PASS
  EVIDENCE: check-baselines fixtures: PASS

- [x] G3: workflow fails invalid retry evidence without calling it confirmed regression
  CHECK: grep -F -- 'Benchmark evidence invalid on retry' .github/workflows/jmh.yml
  EXPECT: Benchmark evidence invalid on retry
  EVIDENCE: echo "::error title=JMH::Benchmark evidence invalid on retry; regression not evaluated."
