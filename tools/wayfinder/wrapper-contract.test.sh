#!/usr/bin/env bash
# wrapper-contract.test.sh — proves scripts/wayfinder-* compat wrappers behave
# identically to the canonical tools/wayfinder/* implementation (map #533 #570).
# Checks exec-delegation structure plus arg/status propagation and cwd
# independence via dry invocations. No real session spawning, remote tracker
# mutation, or daemon restart.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

for name in wayfinder-loop.sh wayfinder-create-child.sh wayfinder-verify-child.sh wayfinder-park.sh wayfinder-worker.sh wayfinder-chief.sh wayfinder-workspace.sh wayfinder-scout.sh wayfinder-maintenance.sh; do
  canonical="$ROOT/tools/wayfinder/$name"
  wrapper="$ROOT/scripts/$name"
  [ -f "$canonical" ] || { bad "canonical missing: $name"; continue; }
  [ -f "$wrapper" ] || { bad "wrapper missing: $name"; continue; }
  if grep -q "tools/wayfinder/$name" "$wrapper" && grep -q 'exec.*"\$@"' "$wrapper"; then
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

# Arg/status propagation: identical exit codes for identical misuse invocations.
check_status() { # <label> <expected-code> <args...>
  local label="$1" expected="$2"; shift 2
  local canon_rc=0 wrap_rc=0
  "$ROOT/tools/wayfinder/$label" "$@" >/dev/null 2>&1 || canon_rc=$?
  "$ROOT/scripts/$label" "$@" >/dev/null 2>&1 || wrap_rc=$?
  if [ "$canon_rc" -eq "$expected" ] && [ "$wrap_rc" -eq "$expected" ]; then
    ok "$label $* propagates status $expected via both paths"
  else
    bad "$label $* status mismatch (canonical=$canon_rc wrapper=$wrap_rc, want $expected)"
  fi
}

check_status wayfinder-create-child.sh 2
check_status wayfinder-verify-child.sh 2
check_status wayfinder-park.sh 2
check_status wayfinder-worker.sh 2
check_status wayfinder-chief.sh 2
check_status wayfinder-workspace.sh 2
check_status wayfinder-scout.sh 2
check_status wayfinder-maintenance.sh 2

# Cwd independence: invoking by absolute path from /tmp still resolves the same
# repository (same lock / same bootstrap gate) instead of the caller cwd.
canon_out="$(cd /tmp && bash "$ROOT/tools/wayfinder/wayfinder-loop.sh" --bootstrap 2>&1 || true)"
wrap_out="$(cd /tmp && bash "$ROOT/scripts/wayfinder-loop.sh" --bootstrap 2>&1 || true)"
canon_norm="$(printf '%s' "$canon_out" | sed 's/^[0-9:]* //')"
wrap_norm="$(printf '%s' "$wrap_out" | sed 's/^[0-9:]* //')"
if [ -n "$canon_norm" ] && [ "$canon_norm" = "$wrap_norm" ]; then
  ok "loop wrapper resolves canonically from any cwd (same repo endpoint)"
else
  bad "loop cwd-independence check failed: canon='${canon_out:0:120}' wrap='${wrap_out:0:120}'"
fi

# Direct vs wrapper dry invocation reach the same daemon endpoint without
# spawning: both report the dry-run bootstrap completion in a sandbox.
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/repo/tools/wayfinder" "$WORK/repo/scripts" "$WORK/repo/.wayfinder/handoffs"
cp "$ROOT/tools/wayfinder/wayfinder-loop.sh" "$WORK/repo/tools/wayfinder/"
cp "$ROOT/scripts/wayfinder-loop.sh" "$WORK/repo/scripts/"
printf 'packet\n' > "$WORK/repo/.wayfinder/handoffs/wayfinder-900-handoff.md"
cat > "$WORK/repo/opencode-stub" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
chmod +x "$WORK/repo/opencode-stub"
if (cd "$WORK/repo" && WAYFINDER_DRY_RUN=1 OPENCODE_BIN="$WORK/repo/opencode-stub" bash tools/wayfinder/wayfinder-loop.sh --bootstrap wayfinder-900-handoff.md 2>&1 | grep -q "dry-run bootstrap complete"); then
  ok "canonical dry-run bootstrap reaches endpoint"
else
  bad "canonical dry-run bootstrap did not complete"
fi
rm -f "$WORK/repo/.wayfinder-loop.state"
if (cd "$WORK/repo" && WAYFINDER_DRY_RUN=1 OPENCODE_BIN="$WORK/repo/opencode-stub" bash scripts/wayfinder-loop.sh --bootstrap wayfinder-900-handoff.md 2>&1 | grep -q "dry-run bootstrap complete"); then
  ok "wrapper dry-run bootstrap reaches the same endpoint"
else
  bad "wrapper dry-run bootstrap did not complete"
fi
[ -f "$WORK/repo/.wayfinder-loop.state" ] || bad "wrapper dry-run wrote no runtime state"

echo
if [ $fail -eq 0 ]; then echo "wrapper-contract: OK"; else echo "wrapper-contract: FAILURES PRESENT"; exit 1; fi
