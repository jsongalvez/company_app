#!/usr/bin/env bash
# Self-test for drop-comment-at.py: it deletes exactly the block that starts at the line and refuses code lines.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOL="$ROOT/tools/quality/drop-comment-at.py"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
cat > "$T/A.kt" <<'KT'
object A {
    /**
     * Rule a test enforces.
     */
    fun a() = 1

    // Two-line rule
    // a test enforces.
    fun b() = 2
    // kept
    fun c() = 3
}
KT
cat > "$T/want.kt" <<'KT'
object A {
    fun a() = 1

    fun b() = 2
    // kept
    fun c() = 3
}
KT
python3 "$TOOL" "$T/A.kt:2" "$T/A.kt:7" 2> /dev/null
diff -u "$T/want.kt" "$T/A.kt" || { echo "drop-comment-at-test: wrong deletion" >&2; exit 1; }
cp "$T/want.kt" "$T/B.kt"
if python3 "$TOOL" "$T/B.kt:2" 2> /dev/null; then echo "drop-comment-at-test: accepted a code line" >&2; exit 1; fi
cmp -s "$T/want.kt" "$T/B.kt" || { echo "drop-comment-at-test: wrote after refusing" >&2; exit 1; }
echo "drop-comment-at-test: ok"
