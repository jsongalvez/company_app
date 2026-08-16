#!/bin/bash
# Shared shell library for project scripts and git hooks.
# Source this file at the top of each script:
#   source "$ROOT_DIR/scripts/lib/common.sh"
# Caller must set ROOT_DIR before sourcing, or call ensure_root_dir afterwards.

# --- log TAG MSG ---
# Prints a timestamped log line with the given tag.
log() {
    local tag="$1"; shift
    echo "$(date '+%H:%M:%S') [$tag] $*"
}

# --- ensure_root_dir ---
# Sets ROOT_DIR to the git repo root and cd's there.
# Idempotent: if ROOT_DIR is already set and valid, does nothing.
ensure_root_dir() {
    if [ -z "${ROOT_DIR:-}" ] || [ ! -d "$ROOT_DIR" ]; then
        ROOT_DIR="$(git rev-parse --show-toplevel)"
    fi
    cd "$ROOT_DIR"
}

# --- source_env ---
# Sources .env from ROOT_DIR if it exists. Silent no-op when missing.
source_env() {
    ensure_root_dir
    source .env 2>/dev/null || true
}

# --- port_is_listening PORT ---
# Returns 0 if PORT is in LISTEN state (checks lsof, then ss).
port_is_listening() {
    local port="$1"
    if command -v lsof &>/dev/null; then
        lsof -i ":$port" -P 2>/dev/null | grep -q LISTEN && return 0
    elif command -v ss &>/dev/null; then
        ss -tlnp "sport = :$port" 2>/dev/null | grep -q LISTEN && return 0
    fi
    return 1
}

# --- kill_cleanup PID ---
# Gracefully stops PID: SIGTERM, wait 10s, then SIGKILL + wait.
kill_cleanup() {
    local pid="$1"
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 10); do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 1
    done
    kill -9 "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
}
