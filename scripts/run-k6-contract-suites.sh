#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/performance/run-k6-contract-suites.sh (map #533 #569).
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/performance" && pwd)/run-k6-contract-suites.sh" "$@"
