#!/usr/bin/env bash
# Fails when a workflow, tool script or scripts/ file names a repo path or Gradle task that
# does not exist. Leftovers of a removed module kept breaking CI: quality.yml called
# :shared:detektMetadataCommonMain after the phone app went (3f7f0c48), and validate.sh and
# inspect.sh called :composeApp tasks that settings.gradle.kts had dropped (3a84ed39).
#
# Usage:
#   tools/quality/check-references.sh           # paths and Gradle modules, no Gradle run, seconds
#   tools/quality/check-references.sh --gradle  # also checks each named task exists (~40s)
#   tools/quality/check-references.sh --root DIR  # check another tree (used by the self-test)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
use_gradle=0
while [ $# -gt 0 ]; do
  case "$1" in
    --gradle) use_gradle=1 ;;
    --root) ROOT="$(cd "$2" && pwd)"; shift ;;
    -h|--help) sed -n 2,10p "$0"; exit 0 ;;
    *) echo "check-references: unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done
cd "$ROOT"

SELF=tools/quality/check-references.sh
TEST=tools/quality/check-references-test.sh
mapfile -t files < <(
  { ls .github/workflows/*.yml scripts/*.sh 2>/dev/null; git ls-files 'tools/*.sh' 2>/dev/null || find tools -name '*.sh'; } |
    sort -u | grep -v -x -e "$SELF" -e "$TEST" || true
)
[ ${#files[@]} -gt 0 ] || { echo "check-references: no files to scan under $ROOT" >&2; exit 2; }

status=0
fail() { echo "check-references: $1: $2" >&2; status=1; }

# --- repo paths --------------------------------------------------------------
# A path starts at a top-level directory. Build outputs, runtime files and anything with a
# variable in it are skipped: they do not exist in a checkout.
top='tools|scripts|config|web|backend|shared|detekt-rules|docs|docker|gradle|\.github'
skip='(/build/|/build$|node_modules|/dist/|/dist$|/\.gradle|/\.e2e|evidence|/tmp/|\$|\{\{|/\.\*)'
for f in "${files[@]}"; do
  while IFS= read -r p; do
    p="${p%%[.,:;)\'\"]}"
    [ -n "$p" ] || continue
    grep -qE "$skip" <<< "$p" && continue
    if [[ "$p" == *'*'* ]]; then
      d="${p%%\**}"; d="${d%/*}"
      [ -d "$d" ] || fail "$f" "directory $d of pattern $p does not exist"
    else
      [ -e "$p" ] || fail "$f" "path $p does not exist"
    fi
  done < <(grep -vE '^[[:space:]]*(#|-? *uses:)' "$f" | grep -oE "(^|[^A-Za-z0-9_./-])((${top})/[A-Za-z0-9_./*-]+)" | sed -E 's/^[^A-Za-z.]+//' | sort -u)
done

# --- Gradle modules ----------------------------------------------------------
modules=$(grep -oE 'include\("(:[A-Za-z0-9-]+)"\)' settings.gradle.kts | grep -oE ':[A-Za-z0-9-]+' | sort -u)
for f in "${files[@]}"; do
  while IFS= read -r t; do
    m=":$(cut -d: -f2 <<< "$t")"
    grep -qx -- "$m" <<< "$modules" || fail "$f" "task $t names module $m, which settings.gradle.kts does not include"
  done < <(grep -v '^[[:space:]]*#' "$f" | grep -oE '(^|[^A-Za-z0-9_:/"-]):[A-Za-z][A-Za-z0-9-]*:[A-Za-z][A-Za-z0-9]*' | sed -E 's/^[^:]*//' | sort -u)
done

# --- Gradle tasks (needs a Gradle run) ----------------------------------------
if [ $use_gradle -eq 1 ] && [ $status -eq 0 ]; then
  # Unqualified `tasks --all` lists every project's tasks in one block: root tasks as `name`,
  # module tasks as `module:name`. A task with no description is a bare name.
  listing=$(./gradlew -q tasks --all --console=plain 2>&1) ||
    { echo "check-references: could not list Gradle tasks:" >&2; echo "$listing" | tail -5 >&2; exit 2; }
  declare -A have
  while IFS= read -r line; do
    if [[ "$line" =~ ^([a-zA-Z][A-Za-z0-9:-]*)( - .*)?$ ]]; then have[":${BASH_REMATCH[1]}"]=1; fi
  done <<< "$listing"
  for f in "${files[@]}"; do
    while IFS= read -r t; do
      [ -n "${have[$t]:-}" ] || fail "$f" "Gradle has no task $t"
    done < <(grep -v '^[[:space:]]*#' "$f" | grep -oE '(^|[^A-Za-z0-9_:/"-]):[A-Za-z][A-Za-z0-9-]*:[A-Za-z][A-Za-z0-9]*' | sed -E 's/^[^:]*//' | sort -u)
    while IFS= read -r t; do
      [ -n "${have[:$t]:-}" ] || fail "$f" "Gradle has no root task $t"
    done < <(grep -v '^[[:space:]]*#' "$f" | grep -oE '\./gradlew +[a-zA-Z][A-Za-z0-9]*( |$)' | awk '{print $2}' | sort -u)
  done
fi

[ $status -eq 0 ] && echo "check-references: OK (${#files[@]} files)"
exit $status
