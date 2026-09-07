#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/database/check-test-cleanliness.sh (map #533 #569).
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/database" && pwd)/check-test-cleanliness.sh" "$@"
