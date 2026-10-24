#!/usr/bin/env bash
# Opt-in local mirror of CI's `quality` workflow (.github/workflows/quality.yml).
#
# Why: pushes kept going red on detekt, ktlint, -Werror compiles and deadCodeCheck.
# tools/quality/validate.sh compiles without -Werror and never runs ktlint, detekt or
# the dead-code gate on a source change, so a green local run said nothing about CI.
# This script reads the Gradle task list out of quality.yml itself, so it cannot drift
# from CI, and runs only the modules your change touches.
#
# NOT a git hook: the repo has no git hooks. CI and this script are the checks.
#
# Usage:
#   tools/check-before-push.sh            # changed since merge-base with origin/master (+ uncommitted)
#   tools/check-before-push.sh --all      # every module, as CI would
#   tools/check-before-push.sh --plan     # print what would run, run nothing
#
# Skipped locally, still covered by CI: :backend:test and :backend:verifyOpenApiContract
# (need the CI Postgres service). Their compile step IS run here, with -Werror, via
# :backend:compileTestKotlin, which is what turned the last red CI run red.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
WORKFLOW=.github/workflows/quality.yml

mode=changed
plan_only=0
for arg in "$@"; do
  case "$arg" in
    --all) mode=all ;;
    --plan) plan_only=1 ;;
    -h|--help) sed -n 2,19p "$0"; exit 0 ;;
    *) echo "check-before-push: unknown argument $arg" >&2; exit 2 ;;
  esac
done

# --- CI's Gradle task list, straight from the workflow -----------------------
# Collects every `:module:task` word in the "Run backend quality ..." run block,
# and every `./gradlew <task>` single-task step (dead-code gate).
ci_tasks() {
  awk '
    /^      - name:/ { inblock = ($0 ~ /Run backend quality/) }
    inblock && /^        run:/ { grab = 1; sub(/^        run: *>?-? */, ""); }
    inblock && grab { print }
    /^      - name:/ && !inblock { grab = 0 }
  ' "$WORKFLOW" | tr ' ' '\n' | grep -E '^:[a-zA-Z-]+:[a-zA-Z]+$' || true
  grep -E '^[[:space:]]+run: \./gradlew [a-zA-Z]+$' "$WORKFLOW" | awk '{print $3}' || true
}

ci_shell_tests() {
  grep -E '^[[:space:]]+bash tools/.*\.sh$' "$WORKFLOW" | awk '{print $2}'
}

# --- what changed ------------------------------------------------------------
changed_files() {
  local base
  base=$(git merge-base HEAD origin/master 2>/dev/null || git rev-parse HEAD~1)
  {
    git diff --name-only "$base" --
    git diff --name-only --cached --
    git ls-files --others --exclude-standard
  } | sort -u
}

all_tasks=$(ci_tasks | sort -u)
if [ -z "$all_tasks" ]; then
  echo "check-before-push: could not read Gradle tasks from $WORKFLOW (format changed?)" >&2
  exit 2
fi

want_modules=""   # space-separated: backend detekt-rules
want_deadcode=0
want_shell=0
want_all=0
if [ "$mode" = all ]; then
  want_all=1
else
  changed=$(changed_files)
  if [ -z "$changed" ]; then
    echo "check-before-push: nothing changed against origin/master; nothing to check"
    exit 0
  fi
  while IFS= read -r f; do
    case "$f" in
      build.gradle.kts|settings.gradle.kts|gradle.properties|gradle/*|gradlew|gradlew.bat|config/detekt/*)
        want_all=1 ;;
      backend/*) want_modules="$want_modules backend"; want_deadcode=1 ;;
      detekt-rules/*) want_modules="$want_modules detekt-rules"; want_deadcode=1 ;;
      config/deadcode/*) want_deadcode=1 ;;
      tools/*|scripts/*|.github/workflows/quality.yml) want_shell=1 ;;
    esac
  done <<< "$changed"
fi

if [ $want_all -eq 1 ]; then
  want_modules="backend detekt-rules"
  want_deadcode=1
  want_shell=1
fi

# --- build the Gradle plan ---------------------------------------------------
tasks=()
for m in $(printf '%s\n' $want_modules | sort -u); do
  while IFS= read -r t; do
    [ -n "$t" ] || continue
    case "$t" in
      # Need the CI Postgres service; compile-with-Werror below stands in for them.
      :backend:test|:backend:verifyOpenApiContract) continue ;;
    esac
    tasks+=("$t")
  done < <(printf '%s\n' "$all_tasks" | grep "^:$m:")
  if [ "$m" = backend ]; then
    tasks+=(:backend:compileKotlin :backend:compileTestKotlin :backend:compileDevKotlin)
  fi
done
[ $want_deadcode -eq 1 ] && tasks+=(deadCodeCheck)

echo "check-before-push: mode=$mode modules=[$(printf '%s\n' $want_modules | sort -u | tr '\n' ' ')] deadcode=$want_deadcode shell=$want_shell"
if [ ${#tasks[@]} -gt 0 ]; then
  echo "check-before-push: ./gradlew ${tasks[*]} -PwarningsAsErrors=true"
fi
if [ $plan_only -eq 1 ]; then
  [ $want_shell -eq 1 ] && ci_shell_tests | sed 's/^/check-before-push: shell test: /'
  exit 0
fi

start=$SECONDS
status=0

if [ ${#tasks[@]} -gt 0 ]; then
  # --continue: report every red check in one run instead of one per push.
  ./gradlew --continue "${tasks[@]}" -PwarningsAsErrors=true || status=1
fi

if [ $want_shell -eq 1 ]; then
  while IFS= read -r f; do
    [ -f "$f" ] && { bash -n "$f" || status=1; }
  done < <(git ls-files 'tools/*.sh' 'scripts/*.sh')
  while IFS= read -r t; do
    [ -f "$t" ] || continue
    bash "$t" >/dev/null 2>&1 || { echo "check-before-push: shell test FAILED: $t" >&2; status=1; }
  done < <(ci_shell_tests)
fi

if [ $status -eq 0 ]; then
  echo "check-before-push: OK in $((SECONDS - start))s (CI-only: :backend:test, :backend:verifyOpenApiContract)"
else
  echo "check-before-push: FAILED after $((SECONDS - start))s. Fix before pushing." >&2
fi
exit $status
