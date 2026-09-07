#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/quality/inspect.sh (map #533 #569).
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/quality" && pwd)/inspect.sh" "$@"
