#!/usr/bin/env bash
# The push path. Runs the Kotlin gates CI runs (tools/check-before-push.sh: ktlint, detekt,
# -Werror compiles, deadCodeCheck, shell self-tests, dead-reference check) and pushes only
# when they pass. Arguments go to `git push` unchanged.
#
#   tools/push.sh                 # git push
#   tools/push.sh origin master   # git push origin master
#
# A change that touches no Kotlin, shell or workflow file finds nothing to check and
# pushes at once. It is not a git hook (the repo has none, #329); AGENTS.md names it as
# the way to push.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
tools/check-before-push.sh
exec git push "$@"
