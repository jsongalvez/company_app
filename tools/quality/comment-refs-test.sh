#!/usr/bin/env bash
# Self-test for comment-refs.py: it must refuse the ids master carried at 3323cac8 and leave the
# #ticket that detekt demands on SAFETY: casts and @Suppress lines.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHECK="$ROOT/tools/quality/comment-refs.py"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
status=0

expect() { # name, file, expected rc
  local rc=0
  python3 "$CHECK" "$2" > "$T/out" 2>&1 || rc=$?
  if [ "$rc" -ne "$3" ]; then echo "comment-refs-test: $1: wanted rc=$3, got rc=$rc: $(cat "$T/out")" >&2; status=1; fi
}

git -C "$ROOT" show 3323cac8:backend/src/main/kotlin/com/companyb/companyapp/authorization/Access.kt > "$T/Access.kt"
expect "Access.kt at 3323cac8 (owed #23, user message 13675)" "$T/Access.kt" 1

cat > "$T/Ok.kt" <<'KT'
// #598: String-backed ilike column narrows to IColumnType<String>
@Suppress("UNCHECKED_CAST")
fun a(x: Any) = x as String // SAFETY: callers pass strings #467
@Suppress("TooGenericExceptionCaught") // #600 sweep must not kill the scheduler
fun b() = Unit
KT
expect "SAFETY and @Suppress tickets" "$T/Ok.kt" 0

printf '/** Locks first (#518). */\nfun c() = Unit\n' > "$T/Bad.kt"
python3 "$CHECK" --fix "$T/Bad.kt" > /dev/null
grep -q '/\*\* Locks first. \*/' "$T/Bad.kt" || { echo "comment-refs-test: --fix left: $(cat "$T/Bad.kt")" >&2; status=1; }
expect "after --fix" "$T/Bad.kt" 0
exit $status
