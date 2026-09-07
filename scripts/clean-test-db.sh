#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/database/clean-test-db.sh (map #533 #569).
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/database" && pwd)/clean-test-db.sh" "$@"
