#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/performance/check-baselines.sh (map #533 #569).
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/performance" && pwd)/check-baselines.sh" "$@"
