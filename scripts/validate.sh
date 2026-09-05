#!/usr/bin/env bash
# Targeted local validation selector (map #329, ticket #331).
# Agent-invoked only — git hooks never call this. Chooses the narrowest warm
# Gradle invocation for the current change; docs-only changes skip builds.
#
# Usage:
#   scripts/validate.sh              # auto: classify changed files, run narrowest tasks
#   scripts/validate.sh <args...>    # passthrough: scripts/validate.sh :backend:test --tests '...FooTest'
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

# Anti-suppression governance (#465): new config/detekt excludes entries must
# link a ticket; baseline/lint.xml escape hatches are banned (frozen at zero).
# Excludes use `**/`-rooted globs by convention, so added glob entries are new
# excludes. Frozen grandfather lines carry their own #465 marker and pass.
check_detekt_governance() {
  local bad_excludes
  bad_excludes=$(
    git diff HEAD -U0 -- 'config/detekt/*' 2>/dev/null \
      | grep -P '^\+\s*-\s*.*\*\*' \
      | grep -vP '#\d+' || true
  )
  if [ -n "$bad_excludes" ]; then
    echo "validate: new config/detekt excludes entries must link a ticket (#<n>), ref #465:" >&2
    echo "$bad_excludes" >&2
    return 1
  fi
  local banned
  banned=$(
    {
      git ls-files
      git ls-files --others --exclude-standard
    } | grep -P '(^|/)lint\.xml$|baseline[^/]*\.xml$' | grep -vP '(^|/)build/' || true
  )
  if [ -n "$banned" ]; then
    echo "validate: baseline/lint.xml escape hatches are banned (ref #465):" >&2
    echo "$banned" >&2
    return 1
  fi
}

if [ "$#" -gt 0 ]; then
  start=$SECONDS
  ./gradlew "$@"
  echo "validate: OK in $((SECONDS - start))s"
  exit 0
fi

changed=$(
  {
    git diff --name-only HEAD -- 2>/dev/null
    git diff --name-only --cached -- 2>/dev/null
    git ls-files --others --exclude-standard -- 2>/dev/null
  } | sort -u
)
if [ -z "$changed" ]; then
  changed=$(git diff --name-only HEAD~1 -- 2>/dev/null || true)
fi
if [ -z "$changed" ]; then
  echo "validate: no changed files detected; pass gradle tasks explicitly" >&2
  exit 1
fi

backend=0 shared=0 compose=0 buildlogic=0 docs=0
tasks=()
focused=()
while IFS= read -r f; do
  case "$f" in
    *.md|docs/*|*.txt)
      docs=1
      ;;
    build.gradle.kts|settings.gradle.kts|gradle/*|config/detekt/*|build-logic/*)
      buildlogic=1
      if [[ "$f" == config/detekt/* ]]; then
        # Detekt policy changed — Detekt is now informative on every module.
        tasks+=(:backend:detekt :shared:detektJvmMain :composeApp:detektDesktopMain)
      fi
      ;;
    backend/src/test/*Test.kt)
      backend=1
      pkg=$(sed -n 's/^package \([a-zA-Z0-9_.]*\).*/\1/p' "$f" | head -1)
      [ -n "$pkg" ] && focused+=("$pkg.$(basename "$f" .kt)")
      ;;
    backend/*) backend=1 ;;
    shared/src/*Test.kt)
      shared=1
      pkg=$(sed -n 's/^package \([a-zA-Z0-9_.]*\).*/\1/p' "$f" | head -1)
      [ -n "$pkg" ] && focused+=("$pkg.$(basename "$f" .kt)")
      ;;
    shared/*) shared=1 ;;
    composeApp/src/*Test.kt)
      compose=1
      pkg=$(sed -n 's/^package \([a-zA-Z0-9_.]*\).*/\1/p' "$f" | head -1)
      [ -n "$pkg" ] && focused+=("$pkg.$(basename "$f" .kt)")
      ;;
    composeApp/*) compose=1 ;;
  esac
done <<< "$changed"

if [ $((backend + shared + compose + buildlogic)) -eq 0 ]; then
  echo "validate: docs-only change — no build validation needed"
  exit 0
fi

check_detekt_governance

if [ $buildlogic -eq 1 ]; then
  # Build logic touches every module's configuration; compile one target per module.
  tasks+=(:backend:compileKotlin :shared:compileKotlinJvm :composeApp:compileKotlinDesktop)
elif [ ${#focused[@]} -gt 0 ]; then
  # Focused --tests filters configure only Test tasks — a compile task in the same
  # invocation fails ("Unknown command-line option '--tests'"). Test tasks compile
  # their main + test source sets themselves, so they run alone here.
  [ $backend -eq 1 ] && tasks+=(:backend:test)
  [ $shared -eq 1 ] && tasks+=(:shared:jvmTest)
  [ $compose -eq 1 ] && tasks+=(:composeApp:desktopTest)
else
  if [ $backend -eq 1 ]; then
    tasks+=(:backend:compileKotlin)
  fi
  if [ $shared -eq 1 ]; then
    tasks+=(:shared:compileKotlinJvm :shared:jvmTest)
  fi
  if [ $compose -eq 1 ]; then
    tasks+=(:composeApp:compileKotlinDesktop :composeApp:desktopTest)
  fi
fi

# Focused --tests filters ride the last test task in the invocation (Gradle ORs them).
if [ ${#focused[@]} -gt 0 ]; then
  for t in "${focused[@]}"; do tasks+=(--tests "$t"); done
fi

echo "validate: ${tasks[*]}"
start=$SECONDS
./gradlew "${tasks[@]}"
echo "validate: OK in $((SECONDS - start))s"
