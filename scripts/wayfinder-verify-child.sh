#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/wayfinder/wayfinder-verify-child.sh (map #533 #570).
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/wayfinder" && pwd)/wayfinder-verify-child.sh" "$@"
