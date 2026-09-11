#!/bin/bash
# BSD-portability guard for owned shell tooling (ref #902).
#
# macOS/BSD userland lacks GNU-only flags used previously here:
# `grep -P` (PCRE) and `xargs -r` (--no-run-if-empty). The pre-commit hook
# aborted on macOS (`xargs: illegal option -- r`) and the detekt-governance
# check in validate.sh failed open there (usage error swallowed by `|| true`).
# This test fails if either flag is reintroduced into the owned shell
# families. It runs on Linux via tools/quality/validate.sh `run_shell_validation`.
#
# Portable replacements: `grep -E` with `[[:space:]]` instead of `\s` and
# `[0-9]` instead of `\d`; bare `xargs -0` (callers already guard emptiness).
#
# Strictness note: full-comment lines are skipped, but any other mention of
# the banned flags trips the scan — rephrase mentions as "grep dash-P" /
# "xargs dash-r" outside full-line comments. This file itself is excluded
# from the scan (it necessarily spells the banned forms in fixtures).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SELF="$(basename "${BASH_SOURCE[0]}")"

# grep with a P in any short-flag cluster (covers -P, -vP, -EP), or the long
# PCRE spelling. Flag clusters must immediately follow the command so a
# quoted operand that merely contains the letters cannot match; the scan
# itself uses only portable grep -E.
GREP_BAN="grep[[:space:]][[:space:]]*(-[A-Za-z0-9]+[[:space:]][[:space:]]*)*-[A-Za-z0-9]*P|grep[[:space:]].*--perl-regexp"
# xargs with r in any short-flag cluster (covers -r, -0 -r, -0r), or the long spelling.
XARGS_BAN="xargs[^#]*[[:space:]]-[A-Za-z0-9]*r|xargs[^#]*--no-run-if-empty"

die() { printf 'test-shell-portability: %s\n' "$*" >&2; exit 1; }

# --- Fixture 1: the patterns catch banned samples, spare portable ones -----
banned_samples=(
  "grep -P 'foo'"
  "grep -vP 'bar'"
  "grep --perl-regexp 'baz'"
  "echo x | xargs -0 -r echo"
  "xargs -r echo"
  "xargs -0r echo"
  "xargs --no-run-if-empty echo"
)
for sample in "${banned_samples[@]}"; do
  if [[ "$sample" == *grep* ]]; then
    printf '%s\n' "$sample" | grep -Eq "$GREP_BAN" \
      || die "pattern missed banned grep sample: $sample"
  else
    printf '%s\n' "$sample" | grep -Eq "$XARGS_BAN" \
      || die "pattern missed banned xargs sample: $sample"
  fi
done

portable_samples=(
  "grep -E 'foo'"
  "grep -vE 'bar'"
  "grep -F 'fixed'"
  "echo x | xargs -0 echo"
  "printf '%s' f | xargs -0 -I{} echo {}"
)
for sample in "${portable_samples[@]}"; do
  if printf '%s\n' "$sample" | grep -Eq "$GREP_BAN|$XARGS_BAN"; then
    die "pattern falsely flagged portable sample: $sample"
  fi
done

# --- Fixture 2: no banned flag in the owned shell families ------------------
scan_dirs=(
  "$ROOT_DIR/.githooks"
  "$ROOT_DIR/tools/quality"
  "$ROOT_DIR/tools/database"
  "$ROOT_DIR/tools/performance"
)
hits=""
while IFS= read -r file; do
  case "$file" in
    */"$SELF") continue ;;
  esac
  [ -f "$file" ] || continue
  # Skip full-comment lines so rephrased explanations stay legal; any other
  # occurrence is a reintroduction.
  content="$(grep -vE '^[[:space:]]*#' "$file" || true)"
  [ -n "$content" ] || continue
  if printf '%s\n' "$content" | grep -En "$GREP_BAN|$XARGS_BAN" >/dev/null; then
    found="$(printf '%s\n' "$content" | grep -En "$GREP_BAN|$XARGS_BAN" | sed "s|^|$file:|")"
    hits="${hits:+$hits
}$found"
  fi
done < <(find "${scan_dirs[@]}" -type f -print)

if [ -n "$hits" ]; then
  printf 'test-shell-portability: GNU-only flags reintroduced:\n%s\n' "$hits" >&2
  exit 1
fi

printf 'PASS: shell tooling uses only portable grep/xargs flags\n'
