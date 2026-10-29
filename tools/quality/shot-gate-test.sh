#!/usr/bin/env bash
# DB-free self-test for tools/quality/shot-gate.sh: a visible web change without shots fails,
# a stamp for HEAD or SHOT_SKIP passes, a stamp for another commit fails, a logic-only change passes.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
status=0
export VERIFY_OUT="$T/out"
unset SHOT_SKIP

git init -q --bare "$T/remote.git"
git init -q "$T/work"
cd "$T/work"
git config user.email t@example.com; git config user.name t
src=web/src
mkdir -p tools/quality "$src/lib"
cp "$ROOT/tools/quality/shot-gate.sh" tools/quality/
echo '<p>one</p>' > "$src/Page.svelte"
echo 'export const a = 1' > "$src/lib/a.ts"
git add -A; git commit -q -m base
git branch -M master
git remote add origin "$T/remote.git"
git push -q origin master
base=$(git rev-parse HEAD)

expect() {
  local want=$1 what=$2 rc=0
  tools/quality/shot-gate.sh >/dev/null 2>&1 || rc=$?
  if [ "$want" = pass ] && [ $rc -ne 0 ]; then echo "shot-gate-test: $what should pass" >&2; status=1; fi
  if [ "$want" = fail ] && [ $rc -eq 0 ]; then echo "shot-gate-test: $what should fail" >&2; status=1; fi
}

echo 'export const a = 2' > "$src/lib/a.ts"
git commit -qam logic
expect pass "a logic-only change"

echo '<p>two</p>' > "$src/Page.svelte"
git commit -qam visible
expect fail "a visible change with no shots"
SHOT_SKIP="copy only" expect pass "a visible change with SHOT_SKIP"

head=$(git rev-parse HEAD)
mkdir -p "$VERIFY_OUT/before-after/${head:0:8}"
printf 'base=%s\nhead=%s\npng=x.png\n' "$base" "$base" > "$VERIFY_OUT/before-after/${head:0:8}/stamp"
expect fail "a stamp whose head is another commit"
printf 'base=%s\nhead=%s\npng=x.png\n' "$base" "$head" > "$VERIFY_OUT/before-after/${head:0:8}/stamp"
expect pass "a stamp for HEAD"

[ $status -eq 0 ] && echo "shot-gate-test: OK"
exit $status
