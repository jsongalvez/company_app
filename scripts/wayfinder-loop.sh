#!/usr/bin/env bash
# Compatibility wrapper — canonical implementation lives at
# tools/wayfinder/wayfinder-loop.sh (map #533 #570).
# Preserved for the running daemon and external launchers; new invocations
# should prefer the canonical path. Safe restart after this relocation:
# tmux new-session -d -s wayfinder-loop 'bash tools/wayfinder/wayfinder-loop.sh'
set -euo pipefail
exec "$(cd "$(dirname "$0")/../tools/wayfinder" && pwd)/wayfinder-loop.sh" "$@"
