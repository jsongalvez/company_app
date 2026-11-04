#!/usr/bin/env bash
# The decision log stays checkable, and the checker refuses the gaps it exists for.
set -euo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
check="python3 -I $here/check-decisions.py"
$check "$here/hotspot-decisions.tsv"

t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
head=$'id\thypothesis\tchange\tbefore\tafter\tdelta\ttests\tverdict\tnote'
expect_fail() {
    printf '%s\n%s\n' "$head" "$2" > "$t/log.tsv"
    if out=$($check "$t/log.tsv"); then echo "FAIL: accepted $1"; exit 1; fi
    grep -q "$3" <<<"$out" || { echo "FAIL: $1 printed: $out"; exit 1; }
}
expect_fail "kept with no guard" $'X1\th\tc\t10ms\t5ms\t-50%\tunit pass\tkept\tprod 5ms' "guard that failed before"
expect_fail "kept with no prod number" $'X1\th\tc\t10ms\t5ms\t-50%\tx.test fails before, passes after\tkept\t' "without a prod measurement"
expect_fail "no after number" $'X1\th\tc\t10ms\t-\t-\tx.test fails before\tkept (pending prod)\t' "before and after"
expect_fail "unknown verdict" $'X1\th\tc\t10ms\t5ms\t-\tt\tmaybe\t' "verdict must start"

printf '%s\n%s\n' "$head" $'X1\th\tc\t10ms\t5ms\t-50%\tx.test fails before, passes after\tkept (pending prod)\t' > "$t/log.tsv"
$check "$t/log.tsv" --due | grep -q "row X1: kept, prod measurement still due" || { echo "FAIL: due list"; exit 1; }
echo "decisions.test.sh: ok"
