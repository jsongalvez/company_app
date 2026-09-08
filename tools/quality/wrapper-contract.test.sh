#!/usr/bin/env bash
# wrapper-contract.test.sh — proves scripts/* compat wrappers behave
# identically to the canonical tools/quality|database|performance
# implementations (map #533 #569).
# Checks exec-delegation structure plus thinness, syntax, and cwd
# independence via dry invocations. No Gradle, DB, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

# wrapper path -> canonical path
pairs=(
  "scripts/validate.sh:tools/quality/validate.sh"
  "scripts/inspect.sh:tools/quality/inspect.sh"
  "scripts/setup-hooks.sh:tools/quality/setup-hooks.sh"
  "scripts/check-test-cleanliness.sh:tools/database/check-test-cleanliness.sh"
  "scripts/clean-test-db.sh:tools/database/clean-test-db.sh"
  "scripts/check-baselines.sh:tools/performance/check-baselines.sh"
  "scripts/run-k6-contract-suites.sh:tools/performance/run-k6-contract-suites.sh"
)

for pair in "${pairs[@]}"; do
  wrapper="$ROOT/${pair%%:*}"
  canonical="$ROOT/${pair##*:}"
  name="$(basename "$wrapper")"
  [ -f "$canonical" ] || { bad "canonical missing: $name"; continue; }
  [ -f "$wrapper" ] || { bad "wrapper missing: $name"; continue; }
  if grep -qF "${pair##*:}" "$wrapper" && grep -q 'exec.*"\$@"' "$wrapper"; then
    ok "$name delegates via exec with arg forwarding"
  else
    bad "$name wrapper is not an exec delegate"
  fi
  if grep -q '^trap' "$wrapper"; then
    bad "$name wrapper traps signals (breaks propagation)"
  else
    ok "$name wrapper installs no signal trap"
  fi
  # No duplicate implementation body lives in the wrapper: it must be tiny.
  lines="$(wc -l < "$wrapper")"
  if [ "$lines" -le 12 ]; then
    ok "$name wrapper stays thin ($lines lines)"
  else
    bad "$name wrapper carries duplicate logic ($lines lines)"
  fi
  bash -n "$canonical" || bad "$name canonical fails bash -n"
  bash -n "$wrapper" || bad "$name wrapper fails bash -n"
done

# Endpoints agree from any cwd: missing-log evidence exits 2 through both
# check-baselines paths without side effects.
canon_rc=0
bash "$ROOT/tools/performance/check-baselines.sh" "$ROOT/does-not-exist.log" >/dev/null 2>&1 || canon_rc=$?
wrap_rc=0
(cd /tmp && bash "$ROOT/scripts/check-baselines.sh" "$ROOT/does-not-exist.log" >/dev/null 2>&1) || wrap_rc=$?
if [ "$canon_rc" -eq 2 ] && [ "$wrap_rc" -eq 2 ]; then
  ok "check-baselines missing-log exits 2 via both paths (cwd-independent)"
else
  bad "check-baselines status mismatch (canonical=$canon_rc wrapper=$wrap_rc, want 2)"
fi

echo
if [ $fail -eq 0 ]; then echo "wrapper-contract: OK"; else echo "wrapper-contract: FAILURES PRESENT"; exit 1; fi
