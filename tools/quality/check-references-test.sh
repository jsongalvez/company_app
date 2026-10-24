#!/usr/bin/env bash
# DB-free self-test for check-references.sh: a clean tree passes, and each kind of dead
# reference (file, glob directory, Gradle module) fails with a message naming it.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHECK="$ROOT/tools/quality/check-references.sh"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
status=0

fresh() {
  rm -rf "$T/tree"
  mkdir -p "$T/tree/.github/workflows" "$T/tree/tools" "$T/tree/backend"
  printf 'include(":backend")\n' > "$T/tree/settings.gradle.kts"
  printf '#!/usr/bin/env bash\n./gradlew :backend:detekt\n' > "$T/tree/tools/a.sh"
  printf 'on:\n  push:\n    paths:\n      - '"'"'backend/**'"'"'\njobs:\n  j:\n    steps:\n      - uses: gradle/actions/setup-gradle@v4\n      - run: bash tools/a.sh\n' > "$T/tree/.github/workflows/w.yml"
}

expect() { # name, expected rc, expected text
  local out rc=0
  out=$("$CHECK" --root "$T/tree" 2>&1) || rc=$?
  if [ "$rc" -ne "$2" ] || { [ -n "$3" ] && ! grep -q -- "$3" <<< "$out"; }; then
    echo "check-references-test: $1: wanted rc=$2 with '$3', got rc=$rc: $out" >&2
    status=1
  fi
}

fresh; expect "clean tree" 0 "OK"
fresh; echo 'bash tools/gone.sh' >> "$T/tree/tools/a.sh"; expect "missing file" 1 "tools/gone.sh"
fresh; echo "- 'docs/**'" >> "$T/tree/.github/workflows/w.yml"; expect "missing glob directory" 1 "docs"
fresh; echo './gradlew :composeApp:compileKotlinDesktop' >> "$T/tree/tools/a.sh"; expect "removed module" 1 ":composeApp"
fresh; echo '# bash tools/gone.sh' >> "$T/tree/tools/a.sh"; expect "comment is ignored" 0 "OK"

[ $status -eq 0 ] && echo "check-references-test: OK"
exit $status
