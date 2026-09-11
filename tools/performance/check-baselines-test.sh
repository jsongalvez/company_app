#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHECKER="$ROOT_DIR/tools/performance/check-baselines.sh"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

cat > "$WORK_DIR/baseline.md" <<'EOF'
| `SessionTypeBenchmark.computeMedicalMission` | 100 |
| `SessionTypeBenchmark.computeProvincialFirst` | 100 |
EOF

run_case() {
    local name="$1"
    local expected_status="$2"
    local output="$WORK_DIR/$name.log"
    shift 2
    printf '%s\n' "$@" > "$output"

    if BASELINE="$WORK_DIR/baseline.md" bash "$CHECKER" "$output" >/dev/null 2>&1; then
        status=0
    else
        status=$?
    fi
    if [ "$status" -ne "$expected_status" ]; then
        printf 'case %s: expected status %s, got %s\n' "$name" "$expected_status" "$status" >&2
        exit 1
    fi
}

run_case empty 2
run_case truncated 2 'Benchmark                      Mode  Cnt  Score   Error  Units'
run_case missing 2 \
    'Benchmark                      Mode  Cnt  Score   Error  Units' \
    'SessionTypeBenchmark.computeMedicalMission  thrpt  5  100  1  ops/s'
run_case malformed 2 \
    'Benchmark                      Mode  Cnt  Score   Error  Units' \
    'SessionTypeBenchmark.computeMedicalMission  thrpt  5  n/a  1  ops/s' \
    'SessionTypeBenchmark.computeProvincialFirst  thrpt  5  100  1  ops/s'
run_case complete 0 \
    'Benchmark                      Mode  Cnt  Score   Error  Units' \
    'SessionTypeBenchmark.computeMedicalMission  thrpt  5  100  1  ops/s' \
    'SessionTypeBenchmark.computeProvincialFirst  thrpt  5  100  1  ops/s'
run_case regressed 1 \
    'Benchmark                      Mode  Cnt  Score   Error  Units' \
    'SessionTypeBenchmark.computeMedicalMission  thrpt  5  70  1  ops/s' \
    'SessionTypeBenchmark.computeProvincialFirst  thrpt  5  100  1  ops/s'
run_case new-benchmark 0 \
    'Benchmark                      Mode  Cnt  Score   Error  Units' \
    'SessionTypeBenchmark.computeMedicalMission  thrpt  5  100  1  ops/s' \
    'SessionTypeBenchmark.computeProvincialFirst  thrpt  5  100  1  ops/s' \
    'SessionTypeBenchmark.computeNewBenchmark  thrpt  5  100  1  ops/s'

cat > "$WORK_DIR/unparseable-baseline.md" <<'EOF'
| Benchmark | Score (ops/s) | Range |
|---|---|---|
| `SessionTypeBenchmark.computeMedicalMission` | **100** | 90 – 110 |
| `SessionTypeBenchmark.computeProvincialFirst` | **100** | 90 – 110 |
EOF

unparseable_status=0
BASELINE="$WORK_DIR/unparseable-baseline.md" bash "$CHECKER" "$WORK_DIR/complete.log" >/dev/null 2>&1 || unparseable_status=$?
if [ "$unparseable_status" -ne 2 ]; then
    printf 'case unparseable-baseline: expected status 2, got %s\n' "$unparseable_status" >&2
    exit 1
fi

missing_log_status=0
BASELINE="$WORK_DIR/baseline.md" bash "$CHECKER" "$WORK_DIR/does-not-exist.log" >/dev/null 2>&1 || missing_log_status=$?
if [ "$missing_log_status" -ne 2 ]; then
    printf 'case missing-log: expected status 2, got %s\n' "$missing_log_status" >&2
    exit 1
fi

printf 'check-baselines fixtures: PASS\n'
