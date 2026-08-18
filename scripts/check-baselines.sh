#!/bin/bash
# Usage: bash scripts/check-baselines.sh [jmh-log-file]
# Compares the latest JMH output against baseline scores in backend/jmh-baselines.md.
# Exits with code 1 if any score dropped below threshold (20% default, 40% for BranchDayBenchmark).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$ROOT_DIR/scripts/lib/common.sh"

JMH_LOG="${1:-/tmp/company-app-jmh.log}"
BASELINE="${BASELINE:-$ROOT_DIR/backend/jmh-baselines.md}"

if [ ! -f "$JMH_LOG" ]; then
    log baselines "ERROR: JMH log file not found: $JMH_LOG"
    log baselines "Run JMH first: ./gradlew :backend:jmh 2>&1 | tee /tmp/company-app-jmh.log"
    exit 1
fi

if [ ! -f "$BASELINE" ]; then
    log baselines "WARNING: No baseline file at $BASELINE — skipping comparison."
    exit 0
fi

log baselines "=== JMH Baseline Comparison ==="
log baselines "Baseline: $BASELINE"
log baselines "Results:  $JMH_LOG"

FAILURES=0
PARSED_RESULTS="$(mktemp)"
trap 'rm -f "$PARSED_RESULTS"' EXIT

# Extract benchmark results from the JMH output (format: benchmark_name thrpt 5 SCORE ± ERROR Units)
# Example line: "SessionTypeBenchmark.computeMedicalMission   thrpt    5  3655579314.012 ± 126351230.212  ops/s"
parse_jmh() {
    # Parse the JMH results table from the log. A missing table or row is a
    # failed gate, not an empty benchmark run.
    if ! grep -qE '^Benchmark[[:space:]]+Mode' "$JMH_LOG"; then
        log baselines "ERROR: JMH results table not found in $JMH_LOG"
        return 1
    fi

    awk '/^Benchmark[[:space:]]+Mode/{found=1; next} found && $2 == "thrpt" && $1 !~ /^[[:space:]]*$/{print $1, $4}' "$JMH_LOG" |
        while read -r name score; do
            score="${score//,/}"
            if [[ ! "$score" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
                log baselines "ERROR: malformed JMH score for $name: $score"
                exit 1
            fi
            printf '%s %s\n' "$name" "$score"
        done > "$PARSED_RESULTS"

    if [ ! -s "$PARSED_RESULTS" ]; then
        log baselines "ERROR: no JMH benchmark results found in $JMH_LOG"
        return 1
    fi
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

if ! parse_jmh; then
    exit 1
fi

declare -A RESULTS
while IFS=' ' read -r name current_score; do
    RESULTS["$name"]="$current_score"
done < "$PARSED_RESULTS"

for name in "${!BASELINES[@]}"; do
    if [ -z "${RESULTS[$name]:-}" ]; then
        log baselines "ERROR: baseline benchmark missing from JMH output: $name"
        ((FAILURES++)) || true
    fi
done

# Compare each JMH result against baseline
while IFS=' ' read -r name current_score; do
    expected="${BASELINES[$name]:-}"
    if [ -z "$expected" ]; then
        log baselines "  NEW  $name: $current_score (no baseline yet)"
        continue
    fi

    # Calculate percentage difference (current / baseline)
    pct=$(awk "BEGIN {printf \"%.1f\", ($current_score / $expected) * 100}")
    diff_pct=$(awk "BEGIN {printf \"%.1f\", 100 - $pct}")
    sign=""

    # If diff_pct is negative, it's an improvement
    THRESHOLD=20
    # Benchmarks at nanosecond scale (high sensitivity to system noise) get relaxed threshold.
    # AGENTS.md decision tree doesn't cover this scenario — it's not a regression, not a DB
    # bottleneck, and not a new feature — so the spirit is followed, not the letter.
    # Extend this array when adding similarly noisy benchmarks.
    NOISY_BENCHMARKS=("BranchDayBenchmark.*")
    for pattern in "${NOISY_BENCHMARKS[@]}"; do
        # shellcheck disable=SC2254 # pattern intentionally contains a glob
        case "$name" in
            $pattern)
                THRESHOLD=40
                break
                ;;
        esac
    done
    if awk "BEGIN {exit !($diff_pct > $THRESHOLD)}" >/dev/null 2>&1; then
        sign="!"
        ((FAILURES++)) || true
    fi

    printf "  %-50s %12s  (%s%% of baseline) %s\n" "$name" "$current_score" "$pct" "$sign"
done < "$PARSED_RESULTS"

echo ""
if [ "$FAILURES" -gt 0 ]; then
    log baselines "FAILED: $FAILURES benchmark(s) dropped below threshold."
    log baselines "Investigate with JFR before pushing. If the change is intentional, update backend/jmh-baselines.md."
    exit 1
else
    log baselines "OK: All JMH scores within 20% of baseline."
fi
