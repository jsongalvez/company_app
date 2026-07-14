#!/bin/bash
# Usage: bash scripts/check-baselines.sh [jmh-log-file]
# Compares the latest JMH output against baseline scores in backend/jmh-baselines.md.
# Exits with code 1 if any score dropped >20% from baseline.
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

JMH_LOG="${1:-/tmp/company-app-jmh.log}"
BASELINE="$ROOT_DIR/backend/jmh-baselines.md"

if [ ! -f "$JMH_LOG" ]; then
    echo "ERROR: JMH log file not found: $JMH_LOG"
    echo "Run JMH first: ./gradlew :backend:jmh 2>&1 | tee /tmp/company-app-jmh.log"
    exit 1
fi

if [ ! -f "$BASELINE" ]; then
    echo "WARNING: No baseline file at $BASELINE — skipping comparison."
    exit 0
fi

echo "=== JMH Baseline Comparison ==="
echo "Baseline: $BASELINE"
echo "Results:  $JMH_LOG"
echo ""

FAILURES=0

# Extract benchmark results from the JMH output (format: benchmark_name thrpt 5 SCORE ± ERROR Units)
# Example line: "SessionTypeBenchmark.computeMedicalMission   thrpt    5  3655579314.012 ± 126351230.212  ops/s"
parse_jmh() {
    # Parse the JMH results table from the log
    awk '/^Benchmark[[:space:]]+Mode/{found=1; next} found && /^[[:alnum:]_]+\.[[:alnum:]_]+[[:space:]]+thrpt/{print $1, $4}' "$JMH_LOG" | \
        while read -r name score; do
            score="${score//,/}"
            echo "$name $score"
        done
}

# Extract baseline scores from the markdown table
# Expected format: "| `BenchmarkName` | 1,234,567,890 | ... |"
parse_baseline() {
    grep -oE '^\| `[^`]+` \| [0-9,]+ ' "$BASELINE" | \
        sed 's/| `//;s/` | / /;s/,//g' | \
        awk '{print $1, $2}'
}

# Build baseline lookup
declare -A BASELINES
while IFS=' ' read -r name score; do
    BASELINES["$name"]="$score"
done < <(parse_baseline)

# Compare each JMH result against baseline
while IFS=' ' read -r name current_score; do
    expected="${BASELINES[$name]:-}"
    if [ -z "$expected" ]; then
        echo "  NEW  $name: $current_score (no baseline yet)"
        continue
    fi

    # Calculate percentage difference (current / baseline)
    pct=$(awk "BEGIN {printf \"%.1f\", ($current_score / $expected) * 100}")
    diff_pct=$(awk "BEGIN {printf \"%.1f\", 100 - $pct}")
    sign=""

    # If diff_pct is negative, it's an improvement
    if awk "BEGIN {exit !($diff_pct > 20)}" >/dev/null 2>&1; then
        sign="!"
        ((FAILURES++)) || true
    fi

    printf "  %-50s %12s  (%s%% of baseline) %s\n" "$name" "$current_score" "$pct" "$sign"
done < <(parse_jmh)

echo ""
if [ "$FAILURES" -gt 0 ]; then
    echo "FAILED: $FAILURES benchmark(s) dropped >20% from baseline."
    echo "Investigate with JFR before pushing. If the change is intentional, update backend/jmh-baselines.md."
    exit 1
else
    echo "OK: All JMH scores within 20% of baseline."
fi
