#!/bin/bash
# Shared app-boot lifecycle module.
# Source after common.sh. Callers must set LOG_TAG (e.g. LOG_TAG=pre-push).
# Git hooks no longer source this module (map #329: hooks never start Gradle,
# the backend, or Postgres); it remains for manual k6/integration workflows.
#
#   source "$ROOT_DIR/scripts/lib/common.sh"
#   source "$ROOT_DIR/scripts/lib/start-app.sh"
#
# Provides: app_build, app_start, app_wait_ready, app_cleanup
# Uses: LOG_TAG for log prefix, port_is_listening and kill_cleanup from common.sh.

set -euo pipefail

: "${LOG_TAG:?start-app.sh requires LOG_TAG to be set before sourcing}"

# --- app_build ---
# Builds the backend distribution. Exits 1 on failure.
app_build() {
    log "$LOG_TAG" "Building backend distribution..."
    local build_output
    build_output=$(./gradlew :backend:installDist 2>&1) || {
        echo "$build_output" | tail -3
        log "$LOG_TAG" "ERROR: Backend build failed."
        exit 1
    }
    echo "$build_output" | tail -3
    log "$LOG_TAG" "Build complete."
}

# --- app_start BOOT_LOG_PATH ---
# Starts the app in the background. Sets APP_PID and waits for port binding.
# Args: $1 — path to the boot log file (required)
# Returns: exits 1 if the app crashes or fails to bind the port within 90s.
app_start() {
    local boot_log="${1:?app_start requires a boot log path}"
    local startup_timeout=90

    log "$LOG_TAG" "Starting app in background..."
    ./gradlew :backend:run > "$boot_log" 2>&1 &
    APP_PID=$!
    log "$LOG_TAG" "App started (PID $APP_PID)."

    local app_port="${APP_PORT:-3023}"
    local app_started=false

    for i in $(seq 1 "$startup_timeout"); do
        if ! kill -0 "$APP_PID" 2>/dev/null; then
            wait "$APP_PID" || true
            log "$LOG_TAG" "ERROR: App process died during startup. Last 20 lines:"
            tail -20 "$boot_log"
            exit 1
        fi
        if port_is_listening "$app_port"; then
            app_started=true
            break
        fi
        sleep 1
    done

    if [ "$app_started" = false ]; then
        log "$LOG_TAG" "ERROR: App did not start within ${startup_timeout} seconds. Last 20 lines:"
        tail -20 "$boot_log"
        kill "$APP_PID" 2>/dev/null || true
        exit 1
    fi

    log "$LOG_TAG" "App is running and listening on port $app_port."
}

# --- app_wait_ready HEALTH_LOG_PATH BOOT_LOG_PATH ---
# Polls /health until HTTP 200 (10 attempts, 2s apart).
# Args: $1 — path to the health response log file (required)
#        $2 — path to the boot log file for diagnostics (required)
# Returns: exits 1 if health check fails or the app crashes.
app_wait_ready() {
    local health_log="${1:?app_wait_ready requires a health log path}"
    local boot_log="${2:?app_wait_ready requires a boot log path}"

    local health_retries=10
    local health_delay=2
    local app_host="${APP_HOST:-localhost}"
    local app_port="${APP_PORT:-3023}"
    local health_url="http://$app_host:$app_port/health"
    local health_ok=false
    HEALTH_OK=false
    local http_status=""

    log "$LOG_TAG" "Checking /health endpoint (up to $health_retries attempts, ${health_delay}s apart)..."
    for i in $(seq 1 $health_retries); do
        if ! kill -0 "$APP_PID" 2>/dev/null; then
            log "$LOG_TAG" "ERROR: App process died during health check."
            tail -20 "$boot_log" 2>/dev/null || true
            exit 1
        fi
        http_status=$(curl -s -o "$health_log" -w "%{http_code}" "$health_url" 2>/dev/null || echo "000")
        if [ "$http_status" = "200" ]; then
            health_ok=true
            HEALTH_OK=true
            break
        fi
        log "$LOG_TAG" "  Attempt $i/$health_retries: HTTP $http_status (retrying in ${health_delay}s)..."
        sleep "$health_delay"
    done

    if [ "$health_ok" = false ]; then
        log "$LOG_TAG" "ERROR: /health returned HTTP $http_status after $health_retries attempts."
        log "$LOG_TAG" "Last response body:"
        cat "$health_log" 2>/dev/null || true
        exit 1
    fi

    log "$LOG_TAG" "Health check passed (HTTP 200)."
}

# --- app_cleanup ---
# Kills APP_PID with a grace period then SIGKILL, waits for exit.
app_cleanup() {
    if [ -n "${APP_PID:-}" ] && kill -0 "$APP_PID" 2>/dev/null; then
        log "$LOG_TAG" "Stopping app (PID $APP_PID)..."
        kill_cleanup "$APP_PID"
        log "$LOG_TAG" "App stopped."
    fi
}
