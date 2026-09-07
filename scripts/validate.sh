#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/quality/validate.sh (map #533 #569).
# Preserved for documented human entrypoints; new invocations should prefer
# the canonical path.
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/quality" && pwd)/validate.sh" "$@"
