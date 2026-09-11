#!/bin/bash
# Shared shell mechanics for project scripts and git hooks (map #533 #569).
# Generic helpers only: logging, repo-root discovery, .env sourcing, port
# probing, process cleanup. Database-test policy lives in
# tools/database/lib/db-common.sh — source that instead when you need
# test_db_name / test_data_tables / test_db_psql / quote_sql_identifier.
#
# Source this file at the top of each script:
#   source "$ROOT_DIR/tools/quality/lib/shell-common.sh"
# Caller must set ROOT_DIR before sourcing, or call ensure_root_dir afterwards.
# Resolves from the caller's ROOT_DIR, so it works from any working directory.

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
# Export-preserving: variables the caller already set (e.g. an operator's
# TEST_DB_NAME override exported for a k6 run) keep the caller's value — .env
# only fills genuinely-unset vars. This matches the backend dotenv-kotlin
# precedence (process environment wins over .env), so boot and cleanup resolve
# the same database within one run.
source_env() {
    ensure_root_dir
    [ -f .env ] || return 0
    local _source_env_key _source_env_decl
    local -A _source_env_saved=()
    local -A _source_env_exported=()
    while IFS= read -r _source_env_key || [ -n "$_source_env_key" ]; do
        [ -n "$_source_env_key" ] || continue
        case "$_source_env_key" in _source_env_*) continue ;; esac
        if _source_env_decl="$(declare -p "$_source_env_key" 2>/dev/null)"; then
            _source_env_saved["$_source_env_key"]="${!_source_env_key}"
            case "$_source_env_decl" in declare\ -*x*) _source_env_exported["$_source_env_key"]=1 ;; esac
        fi
    done < <(sed -n 's/^[[:space:]]*\(export[[:space:]][[:space:]]*\)\{0,1\}\([A-Za-z_][A-Za-z0-9_]*\)[[:space:]]*=.*/\2/p' .env | sort -u)
    # shellcheck disable=SC1091
    source .env 2>/dev/null || true
    # Restore every caller-set value the file just overwrote.
    [ "${#_source_env_saved[@]}" -eq 0 ] || for _source_env_key in "${!_source_env_saved[@]}"; do
        if [ -n "${_source_env_exported[$_source_env_key]+x}" ]; then
            export "$_source_env_key=${_source_env_saved[$_source_env_key]}" || true
        else
            printf -v "$_source_env_key" '%s' "${_source_env_saved[$_source_env_key]}" || true
        fi
    done
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
