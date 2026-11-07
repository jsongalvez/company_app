#!/usr/bin/env bash
# Self-test for check-file-size.sh on a throwaway git repo.
set -euo pipefail
CHECK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check-file-size.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
cd "$tmp" && git init -q . && mkdir -p web/src/api
lines() { seq "$1" > "$2"; }
lines 10 small.kt; lines 15 big.kt; lines 50 web/src/api/schema.d.ts; lines 50 data.jsonl
git add -A
pass=0; failn=0
expect() { # expect ok|fail "name" [extra args]
  local want=$1 name=$2; shift 2
  if bash "$CHECK" --root "$tmp" --allow "$tmp/allow.txt" --max 12 "$@" >out 2>&1; then got=ok; else got=fail; fi
  if [ "$got" = "$want" ]; then pass=$((pass+1)); else failn=$((failn+1)); echo "FAIL: $name (wanted $want)"; cat out; fi
}
printf '# header\n15 big.kt\n' > allow.txt;                    expect ok   "listed file at its count; generated and data files skipped"
lines 16 big.kt;                                                expect fail "listed file grew"
lines 14 big.kt;                                                expect fail "listed file shrank but count not lowered"
printf '14 big.kt\n' > allow.txt;                               expect ok   "count lowered"
lines 13 small.kt;                                              expect fail "new file over the cap"
lines 10 small.kt; lines 12 big.kt;                             expect fail "listed file back under cap keeps a stale line"
: > allow.txt;                                                  expect ok   "line deleted"
printf '20 gone.kt\n' > allow.txt;                              expect fail "listed file not tracked"
printf '# header\n15 big.kt\n' > allow.txt; lines 14 big.kt;       expect ok   "--fix lowers a shrunk count" --fix
grep -qx '14 big.kt' allow.txt && grep -qx '# header' allow.txt || { failn=$((failn+1)); echo "FAIL: --fix did not lower the count and keep the header"; cat allow.txt; }
lines 12 big.kt;                                                expect ok   "--fix drops a file back under the cap" --fix
grep -q 'big.kt' allow.txt && { failn=$((failn+1)); echo "FAIL: --fix kept a stale line"; cat allow.txt; }
echo "check-file-size-test: $pass passed, $failn failed"
[ $failn -eq 0 ]
