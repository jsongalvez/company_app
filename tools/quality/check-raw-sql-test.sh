#!/usr/bin/env bash
# Self-test for check-raw-sql.sh on a throwaway git repo.
set -euo pipefail
CHECK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check-raw-sql.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
main=backend/src/main/kotlin; pkg=$main/a; tests=backend/src/test/kotlin
cd "$tmp" && git init -q . && mkdir -p "$pkg" "$tests"
echo 'val x = Table.selectAll()' > $pkg/Typed.kt
echo 'transaction { exec("SELECT 1") }' > $pkg/Health.kt
echo 'exec("DELETE FROM t")' > "$tests/Tst.kt"
git add -A
pass=0; failn=0
expect() { # expect ok|fail "name"
  local want=$1 name=$2
  git add -A
  if bash "$CHECK" --root "$tmp" --allow "$tmp/allow.txt" >out 2>&1; then got=ok; else got=fail; fi
  if [ "$got" = "$want" ]; then pass=$((pass+1)); else failn=$((failn+1)); echo "FAIL: $name (wanted $want)"; cat out; fi
}
printf '# h\n%s 1 probe\n' "$pkg/Health.kt" > allow.txt; expect ok "listed call; test code skipped"
echo 'TransactionManager.current().exec(sql, args)' > $pkg/Facts.kt;  expect fail "new raw SQL in main code"
rm $pkg/Facts.kt
echo 'fun f() = exec("SELECT 2")' >> $pkg/Health.kt;                 expect fail "listed file gained a call"
printf '%s 2 probe\n' "$pkg/Health.kt" > allow.txt;                       expect ok   "count raised in the list"
echo 'val y = 1' > $pkg/Health.kt;                                    expect fail "stale line"
echo "check-raw-sql-test: $pass passed, $failn failed"
[ $failn -eq 0 ]
