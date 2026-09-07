#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/quality/local-ci.sh (map #533 #569).
# Preserved for the running daemon and external launchers; new invocations
# should prefer the canonical path.
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/quality" && pwd)/local-ci.sh" "$@"
