#!/usr/bin/env bash
# wayfinder-capacity.sh — role-aware resource limits, heavy-job coordination, and parallel-worker observability (map #697, ticket #742).
#
# Copy-on-write or worktree isolation reduces workspace/setup cost, but CPU,
# RAM, file descriptors, Gradle daemons, database processes, and API usage are
# not free. This script is the capacity owner beside the worker-control seam
# (wayfinder-worker.sh, which enforces only the hard global bound): it adds
# role-aware ceilings, an independently bounded heavyweight-job semaphore, and
# the compact operator status view over worker/review/heavy/scout state.
#
# Subcommands:
#
#   limits [--machine]
#             print the effective limits with live usage (global, maintenance,
#             bug-scout, helper, heavy). Read-only: no Herdr, no gh, no git.
#   check --role R
#             chief dispatch gate for one spawn: refuse when the shared global
#             bound or the role's narrow ceiling is full. Read-only.
#             Observable to all; the chief lanes call it before worker spawns.
#   heavy-acquire --holder WORKER --ticket N [--note TEXT]
#             take one heavyweight slot (full Gradle suites, load runs) for a
#             live worker. Refuses with a waiting-resource message when full —
#             waiting-resource is NOT semantic blocked-input: the worker stays
#             running and retries with backoff, never files needs-info for it.
#             Idempotent for the same holder. Callable by any role (workers
#             coordinate their own heavy ops); emits heavy-acquired/waiting.
#   heavy-release --holder WORKER
#             release the holder's slot. Idempotent (absent releases succeed).
#             Callable by any role; emits heavy-released. Workers release
#             before reporting done; reconcile below repairs the rest.
#   heavy-list
#             print held slots as raw TSV (holder, ticket, since, note).
#   heavy-reconcile
#             chief-only crash repair: release slots whose holder has no live
#             (spawning/running) worker row, then print the verdict. Registry-
#             only and deterministic, so a crashed worker never leaks a heavy
#             slot permanently. Never respawns and never touches Herdr.
#   heavy-status [--machine]
#             print heavy used/free plus holders. Read-only.
#   emit <event> [--worker W] [--role R] [--ticket N] [--workspace D]
#                [--detail TEXT]
#             append one structured lifecycle event (timestamp, event, worker,
#             role, ticket, workspace, detail) to the append-only events log.
#             Observable to all; chief/review/workspace/scout lanes emit
#             best-effort (a failed emit never breaks scheduling).
#   events [--lines N]
#             print the last N events (default 50). Read-only.
#   status [--machine]
#             compact operator view without entering panes: active workers,
#             blocked-input vs resource-waiting, review-pending, crashed,
#             role/heavy usage with free slots, live maintenance, scout
#             progress, integration gate, quiescence verdict, restart risk.
#             Registry + gate files only: no Herdr, no gh, no git.
#
# Capacity model (env, all configurable):
#
#   WAYFINDER_MAX_WORKERS               hard global ceiling, >= 1 (default 1 —
#                                       the sequential fallback; shared with
#                                       the worker seam, which enforces it).
#   WAYFINDER_MAX_MAINTENANCE_WORKERS   narrow maintenance ceiling, >= 1
#                                       (default 1 — red CI gets prompt
#                                       attention without an idle reservation).
#   WAYFINDER_MAX_BUG_SCOUTS            narrow scout ceiling, >= 0, 0 disables
#                                       scouting (default 1 — long-lived audit
#                                       cannot starve implementation).
#   WAYFINDER_MAX_HELPERS               narrow helper ceiling, >= 0, 0 disables
#                                       helpers (default 2).
#   WAYFINDER_MAX_HEAVY_JOBS            heavyweight-job ceiling, >= 0,
#                                       0 disables heavy ops (default 2 —
#                                       bounds concurrent Gradle suites
#                                       independently from agent count).
#
# Helper workers count toward the global bound like any worker. Ticket workers
# carry no narrow ceiling beyond the global one.
#
# Deterministic scheduling policy under constrained capacity:
#
#   1. Already-running safe work is never preempted to make room.
#   2. Urgent maintenance/CI repair goes first (explicit chief lane).
#   3. Ticket frontier fills in tracker order (priority:P0, then P1, then
#      P2/untagged; lowest number breaks ties — the chief's frontier query).
#   4. Helpers serve their parent ticket only (explicit chief spawn).
#   5. Bug-scout slices go last (explicit chief spawn, capped above).
#   6. A heavy-slot refusal is waiting-resource, never blocked-input:
#      dependency correctness still outranks utilization.
#
# Lifecycle event vocabulary (emit names; the status view reads registries,
# not the log, so a rotated/truncated log never blinds operators):
#
#   chief-recovered worker-dispatched helper-dispatched
#   maintenance-dispatched scout-slice-started scout-slice-completed
#   worker-collected worker-blocked-input worker-failed slot-refilled
#   review-accepted review-revision review-rejected review-cancelled
#   integration-completed workspace-created workspace-cleaned
#   heavy-acquired heavy-waiting heavy-released heavy-reconciled
#
# State (all under gitignored .wayfinder/, same-dir atomic rewrites):
#
#   heavy registry  holder, ticket, acquired_at, note (one row per held slot)
#   events log      timestamp, event, worker, role, ticket, workspace, detail
#
# Worker-registry statuses are owned by the worker seam + review queue; this
# script only reads them (live means spawning|running). Provider-neutral: no
# workspace paths are created or parsed here, so Git-worktree and future CoW
# providers share the same capacity semantics.
#
# Contract notes (ticket #742 acceptance):
# - A hard configurable global worker limit exists (worker seam enforces it;
#   limits/check report it here).
# - Role-aware ceilings reserve maintenance attention and cap scouts/helpers;
#   heavy concurrency is bounded independently from agent count.
# - Resource waiting stays distinct from semantic blocked state everywhere
#   (refusal text, status bands, event names).
# - Recovery releases heavy slots held by crashed workers (heavy-reconcile).
# - Do not introduce OpenCode, Rift, or Lane dependencies to satisfy this ticket.
#
# Env:
#   WAYFINDER_WORKER_REGISTRY   worker registry file (default: <repo>/.wayfinder/workers.tsv)
#   WAYFINDER_HEAVY_REGISTRY    heavy registry file (default: beside the worker registry)
#   WAYFINDER_EVENTS_LOG        events log file (default: beside the worker registry)
#   WAYFINDER_SCOUT_REGISTRY    scout state file read by status (default: <repo>/.wayfinder/scout.tsv)
#   WAYFINDER_INTEGRATION_GATE  gate file read by status (default: <repo>/.wayfinder/integration-gate)
#   WAYFINDER_ROLE              caller role; heavy-reconcile requires the chief
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DISCOVERED_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKER="$SCRIPT_DIR/wayfinder-worker.sh"
SCOUT="$SCRIPT_DIR/wayfinder-scout.sh"
REVIEW="$SCRIPT_DIR/wayfinder-review.sh"
RECOVER="$SCRIPT_DIR/wayfinder-recover.sh"

WORKER_REGISTRY="${WAYFINDER_WORKER_REGISTRY:-$DISCOVERED_REPO/.wayfinder/workers.tsv}"
STATE_DIR="$(dirname "$WORKER_REGISTRY")"
HEAVY_REGISTRY="${WAYFINDER_HEAVY_REGISTRY:-$STATE_DIR/heavy.tsv}"
EVENTS_LOG="${WAYFINDER_EVENTS_LOG:-$STATE_DIR/events.log}"
SCOUT_REGISTRY="${WAYFINDER_SCOUT_REGISTRY:-$DISCOVERED_REPO/.wayfinder/scout.tsv}"
GATE_FILE="${WAYFINDER_INTEGRATION_GATE:-$DISCOVERED_REPO/.wayfinder/integration-gate}"
CALLER_ROLE="${WAYFINDER_ROLE:-chief}"

MAX_WORKERS="${WAYFINDER_MAX_WORKERS:-1}"
MAX_MAINT="${WAYFINDER_MAX_MAINTENANCE_WORKERS:-1}"
MAX_SCOUTS="${WAYFINDER_MAX_BUG_SCOUTS:-1}"
MAX_HELPERS="${WAYFINDER_MAX_HELPERS:-2}"
MAX_HEAVY="${WAYFINDER_MAX_HEAVY_JOBS:-2}"

LEAF_ROLES="ticket maintenance bug-scout helper"
VALID_ROLES="ticket maintenance bug-scout helper"
LIVE_STATES="spawning running"

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

die() { printf 'wayfinder-capacity: %s\n' "$*" >&2; exit 1; }
usage_err() { printf 'wayfinder-capacity: %s\n' "$*" >&2; exit 2; }

require_chief() { # <op>
    case " $LEAF_ROLES " in
        *" $CALLER_ROLE "*) die "$1 requires the map chief (WAYFINDER_ROLE=$CALLER_ROLE is a leaf role — request help via the chief instead)" ;;
    esac
}

need_arg() { # <flag> <value>
    [ -n "${2:-}" ] || usage_err "$1 requires a value"
}

valid_name() { [[ "${1:-}" =~ ^[a-z][a-z0-9_-]{0,31}$ ]]; }
valid_role() { case " $VALID_ROLES " in *" $1 "*) return 0;; esac; return 1; }
valid_event() { [[ "${1:-}" =~ ^[a-z][a-z0-9_-]{0,63}$ ]]; }

validate_limits() {
    [[ "$MAX_WORKERS" =~ ^[0-9]+$ ]] && [ "$MAX_WORKERS" -ge 1 ] \
        || die "WAYFINDER_MAX_WORKERS must be >= 1 (got '$MAX_WORKERS')"
    [[ "$MAX_MAINT" =~ ^[0-9]+$ ]] && [ "$MAX_MAINT" -ge 1 ] \
        || die "WAYFINDER_MAX_MAINTENANCE_WORKERS must be >= 1 (got '$MAX_MAINT')"
    [[ "$MAX_SCOUTS" =~ ^[0-9]+$ ]] \
        || die "WAYFINDER_MAX_BUG_SCOUTS must be >= 0 (got '$MAX_SCOUTS')"
    [[ "$MAX_HELPERS" =~ ^[0-9]+$ ]] \
        || die "WAYFINDER_MAX_HELPERS must be >= 0 (got '$MAX_HELPERS')"
    [[ "$MAX_HEAVY" =~ ^[0-9]+$ ]] \
        || die "WAYFINDER_MAX_HEAVY_JOBS must be >= 0 (got '$MAX_HEAVY')"
}

# live_count <role|"all"> — worker rows in spawning|running, optionally filtered.
live_count() { # <role|all>
    [ -f "$WORKER_REGISTRY" ] || { printf '0'; return; }
    if [ "$1" = "all" ]; then
        awk -F'\t' '$6 == "spawning" || $6 == "running" { n++ } END { print n + 0 }' "$WORKER_REGISTRY"
    else
        awk -F'\t' -v r="$1" '$2 == r && ($6 == "spawning" || $6 == "running") { n++ } END { print n + 0 }' "$WORKER_REGISTRY"
    fi
}

heavy_used() {
    [ -f "$HEAVY_REGISTRY" ] || { printf '0'; return; }
    awk -F'\t' 'NF >= 2 { n++ } END { print n + 0 }' "$HEAVY_REGISTRY"
}

heavy_row() { # <holder> — print the heavy row or nothing.
    [ -f "$HEAVY_REGISTRY" ] || return 0
    awk -F'\t' -v h="$1" '$1 == h { print; exit }' "$HEAVY_REGISTRY"
}

worker_row() { # <name> — print the worker TSV row or nothing.
    [ -f "$WORKER_REGISTRY" ] || return 0
    awk -F'\t' -v n="$1" '$1 == n { print; exit }' "$WORKER_REGISTRY"
}

worker_live() { # <name> — true when the worker row is spawning|running.
    local row st
    row="$(worker_row "$1")"
    [ -n "$row" ] || return 1
    st="$(printf '%s' "$row" | cut -f6)"
    case " $LIVE_STATES " in *" $st "*) return 0;; esac
    return 1
}

with_heavy_lock() {
    if command -v flock >/dev/null 2>&1; then
        mkdir -p "$(dirname "$HEAVY_REGISTRY")"
        exec {WAYFINDER_HEAVY_LOCK_FD}>"$HEAVY_REGISTRY.lock"
        flock -w 60 "$WAYFINDER_HEAVY_LOCK_FD" \
            || die "cannot lock heavy registry: $HEAVY_REGISTRY.lock"
    fi
}

with_heavy_unlock() {
    if command -v flock >/dev/null 2>&1; then
        eval "exec $WAYFINDER_HEAVY_LOCK_FD>&-" 2>/dev/null || true
    fi
}

# emit_raw <event> <worker> <role> <ticket> <workspace> <detail> — append-only,
# never fails the caller (O_APPEND single-line writes; a lost events log never
# blinds operators because status reads registries, not the log).
emit_raw() {
    local event="$1" worker="${2:-}" role="${3:-}" ticket="${4:-}" workspace="${5:-}" detail="${6:-}"
    valid_event "$event" || return 1
    event="$(printf '%s' "$event" | tr '\t\n' '  ')"
    worker="$(printf '%s' "$worker" | tr '\t\n' '  ')"
    role="$(printf '%s' "$role" | tr '\t\n' '  ')"
    ticket="$(printf '%s' "$ticket" | tr '\t\n' '  ')"
    workspace="$(printf '%s' "$workspace" | tr '\t\n' '  ')"
    detail="$(printf '%s' "$detail" | tr '\t\n' '  ')"
    mkdir -p "$(dirname "$EVENTS_LOG")"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$(date '+%F %T')" "$event" "$worker" "$role" "$ticket" "$workspace" "$detail" >> "$EVENTS_LOG"
}

cmd_emit() {
    [ $# -ge 1 ] || usage_err "emit: event name is required"
    local event="$1"; shift
    local worker="" role="" ticket="" workspace="" detail=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --worker) need_arg "$1" "${2:-}"; worker="$2"; shift 2 ;;
            --role) need_arg "$1" "${2:-}"; role="$2"; shift 2 ;;
            --ticket) need_arg "$1" "${2:-}"; ticket="$2"; shift 2 ;;
            --workspace) need_arg "$1" "${2:-}"; workspace="$2"; shift 2 ;;
            --detail) need_arg "$1" "${2:-}"; detail="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "emit: unknown flag $1" ;;
        esac
    done
    valid_event "$event" || usage_err "emit: invalid event name '$event'"
    emit_raw "$event" "$worker" "$role" "$ticket" "$workspace" "$detail" \
        || die "emit: cannot write events log: $EVENTS_LOG"
    printf 'emitted %s\n' "$event"
}

cmd_events() {
    local lines=50
    while [ $# -gt 0 ]; do
        case "$1" in
            --lines) need_arg "$1" "${2:-}"; lines="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "events: unknown flag $1" ;;
        esac
    done
    [[ "$lines" =~ ^[0-9]+$ ]] || usage_err "events: --lines must be numeric"
    [ -f "$EVENTS_LOG" ] || { printf '(no events)\n'; return 0; }
    tail -n "$lines" "$EVENTS_LOG"
}

cmd_limits() {
    local machine=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --machine) machine=1; shift ;;
            -h|--help) usage ;;
            *) usage_err "limits: unknown flag $1" ;;
        esac
    done
    validate_limits
    local g m s h
    g="$(live_count all)"; m="$(live_count maintenance)"
    s="$(live_count bug-scout)"; h="$(live_count helper)"
    if [ -n "$machine" ]; then
        printf 'scope=global used=%s max=%s free=%s\n' "$g" "$MAX_WORKERS" "$((MAX_WORKERS - g))"
        printf 'scope=maintenance used=%s max=%s free=%s\n' "$m" "$MAX_MAINT" "$((MAX_MAINT - m))"
        printf 'scope=bug-scout used=%s max=%s free=%s\n' "$s" "$MAX_SCOUTS" "$((MAX_SCOUTS - s))"
        printf 'scope=helper used=%s max=%s free=%s\n' "$h" "$MAX_HELPERS" "$((MAX_HELPERS - h))"
        printf 'scope=heavy used=%s max=%s free=%s\n' "$(heavy_used)" "$MAX_HEAVY" "$((MAX_HEAVY - $(heavy_used)))"
        return 0
    fi
    printf 'capacity: global %s/%s busy, maintenance %s/%s, scouts %s/%s, helpers %s/%s, heavy %s/%s\n' \
        "$g" "$MAX_WORKERS" "$m" "$MAX_MAINT" "$s" "$MAX_SCOUTS" "$h" "$MAX_HELPERS" "$(heavy_used)" "$MAX_HEAVY"
}

cmd_check() {
    local role=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --role) need_arg "$1" "${2:-}"; role="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "check: unknown flag $1" ;;
        esac
    done
    valid_role "$role" || usage_err "check: --role must be one of: $VALID_ROLES"
    validate_limits
    local g
    g="$(live_count all)"
    if [ "$g" -ge "$MAX_WORKERS" ]; then
        die "check: at global capacity ($g/$MAX_WORKERS running) — refuse $role dispatch (free a slot or raise WAYFINDER_MAX_WORKERS)"
    fi
    case "$role" in
        maintenance)
            if [ "$(live_count maintenance)" -ge "$MAX_MAINT" ]; then
                die "check: at maintenance capacity ($(live_count maintenance)/$MAX_MAINT live) — refuse to exceed WAYFINDER_MAX_MAINTENANCE_WORKERS"
            fi
            ;;
        bug-scout)
            if [ "$MAX_SCOUTS" -eq 0 ]; then
                die "check: bug-scout dispatch disabled (WAYFINDER_MAX_BUG_SCOUTS=0)"
            fi
            if [ "$(live_count bug-scout)" -ge "$MAX_SCOUTS" ]; then
                die "check: at scout capacity ($(live_count bug-scout)/$MAX_SCOUTS live) — scouting cannot starve implementation (raise WAYFINDER_MAX_BUG_SCOUTS to allow more)"
            fi
            ;;
        helper)
            if [ "$MAX_HELPERS" -eq 0 ]; then
                die "check: helper dispatch disabled (WAYFINDER_MAX_HELPERS=0)"
            fi
            if [ "$(live_count helper)" -ge "$MAX_HELPERS" ]; then
                die "check: at helper capacity ($(live_count helper)/$MAX_HELPERS live) — refuse to exceed WAYFINDER_MAX_HELPERS"
            fi
            ;;
    esac
    printf 'capacity ok: role=%s global=%s/%s\n' "$role" "$g" "$MAX_WORKERS"
}

cmd_heavy_acquire() {
    local holder="" ticket="" note=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --holder) need_arg "$1" "${2:-}"; holder="$2"; shift 2 ;;
            --ticket) need_arg "$1" "${2:-}"; ticket="$2"; shift 2 ;;
            --note) need_arg "$1" "${2:-}"; note="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "heavy-acquire: unknown flag $1" ;;
        esac
    done
    valid_name "$holder" || usage_err "heavy-acquire: invalid holder worker name '${holder:-}'"
    [[ "$ticket" =~ ^[0-9]+$ ]] || usage_err "heavy-acquire: --ticket must be a numeric issue number"
    validate_limits
    worker_live "$holder" \
        || die "heavy-acquire: holder '$holder' is not a live worker (spawning|running) — unknown, crashed, or review-pending rows hold no heavy slots"
    mkdir -p "$(dirname "$HEAVY_REGISTRY")"
    [ -f "$HEAVY_REGISTRY" ] || : > "$HEAVY_REGISTRY"
    with_heavy_lock
    local used existing tmp
    existing="$(heavy_row "$holder")"
    if [ -n "$existing" ]; then
        with_heavy_unlock
        emit_raw "heavy-acquired" "$holder" "" "$ticket" "" "already holds a heavy slot (idempotent)" || true
        printf 'heavy slot already held by %s (idempotent)\n' "$holder"
        return 0
    fi
    used="$(heavy_used)"
    if [ "$used" -ge "$MAX_HEAVY" ]; then
        with_heavy_unlock
        emit_raw "heavy-waiting" "$holder" "" "$ticket" "" "heavy slots full ($used/$MAX_HEAVY) — waiting-resource, not blocked" || true
        die "heavy-acquire: waiting-resource — heavy slots full ($used/$MAX_HEAVY held); '$holder' stays running and retries with backoff (this is NOT semantic blocked-input; do not file needs-info for it)"
    fi
    note="$(printf '%s' "$note" | tr '\t\n' '  ')"
    # Same-dir staging: the atomic rename publishes only complete views.
    tmp="$(mktemp -p "$(dirname "$HEAVY_REGISTRY")" heavy.XXXXXX)"
    cat "$HEAVY_REGISTRY" > "$tmp"
    printf '%s\t%s\t%s\t%s\n' "$holder" "$ticket" "$(date '+%F %T')" "$note" >> "$tmp"
    mv "$tmp" "$HEAVY_REGISTRY"
    with_heavy_unlock
    emit_raw "heavy-acquired" "$holder" "" "$ticket" "" "${note:-slot acquired ($((used + 1))/$MAX_HEAVY)}" || true
    printf 'heavy slot acquired by %s for ticket #%s (%s/%s held)\n' "$holder" "$ticket" "$((used + 1))" "$MAX_HEAVY"
}

cmd_heavy_release() {
    local holder=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --holder) need_arg "$1" "${2:-}"; holder="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "heavy-release: unknown flag $1" ;;
        esac
    done
    valid_name "$holder" || usage_err "heavy-release: invalid holder worker name '${holder:-}'"
    mkdir -p "$(dirname "$HEAVY_REGISTRY")"
    [ -f "$HEAVY_REGISTRY" ] || : > "$HEAVY_REGISTRY"
    with_heavy_lock
    local tmp
    if [ -z "$(heavy_row "$holder")" ]; then
        with_heavy_unlock
        printf 'no heavy slot held by %s (already released)\n' "$holder"
        return 0
    fi
    tmp="$(mktemp -p "$(dirname "$HEAVY_REGISTRY")" heavy.XXXXXX)"
    awk -F'\t' -v h="$holder" '$1 != h' "$HEAVY_REGISTRY" > "$tmp"
    mv "$tmp" "$HEAVY_REGISTRY"
    with_heavy_unlock
    emit_raw "heavy-released" "$holder" "" "" "" "slot released" || true
    printf 'heavy slot released by %s (%s/%s held)\n' "$holder" "$(heavy_used)" "$MAX_HEAVY"
}

cmd_heavy_list() {
    [ $# -eq 0 ] || usage_err "heavy-list takes no flags"
    [ -f "$HEAVY_REGISTRY" ] && cat "$HEAVY_REGISTRY" || true
}

cmd_heavy_reconcile() {
    require_chief "heavy_reconcile"
    [ $# -eq 0 ] || usage_err "heavy-reconcile takes no flags"
    validate_limits
    mkdir -p "$(dirname "$HEAVY_REGISTRY")"
    [ -f "$HEAVY_REGISTRY" ] || : > "$HEAVY_REGISTRY"
    with_heavy_lock
    local holder ticket since note kept=0 released=0 tmp
    tmp="$(mktemp -p "$(dirname "$HEAVY_REGISTRY")" heavy.XXXXXX)"
    : > "$tmp"
    while IFS=$'\t' read -r holder ticket since note; do
        [ -n "$holder" ] || continue
        if worker_live "$holder"; then
            printf '%s\t%s\t%s\t%s\n' "$holder" "$ticket" "$since" "$note" >> "$tmp"
            kept=$((kept + 1))
        else
            released=$((released + 1))
        fi
    done < "$HEAVY_REGISTRY"
    mv "$tmp" "$HEAVY_REGISTRY"
    with_heavy_unlock
    emit_raw "heavy-reconciled" "" "" "" "" "released $released crashed-holder slot(s), kept $kept" || true
    printf 'heavy-reconciled: %d kept, %d released (holders without a live worker row never leak slots)\n' "$kept" "$released"
}

cmd_heavy_status() {
    local machine=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --machine) machine=1; shift ;;
            -h|--help) usage ;;
            *) usage_err "heavy-status: unknown flag $1" ;;
        esac
    done
    validate_limits
    local used free
    used="$(heavy_used)"; free=$((MAX_HEAVY - used))
    if [ -n "$machine" ]; then
        printf 'scope=heavy used=%s max=%s free=%s\n' "$used" "$MAX_HEAVY" "$free"
        [ -f "$HEAVY_REGISTRY" ] && awk -F'\t' 'NF >= 2 { printf "heavy holder=%s ticket=%s since=%s\n", $1, $2, $3 }' "$HEAVY_REGISTRY" || true
        return 0
    fi
    if [ "$used" -eq 0 ]; then
        printf 'heavy: %s/%s used, %s free — no holders\n' "$used" "$MAX_HEAVY" "$free"
        return 0
    fi
    printf 'heavy: %s/%s used, %s free — holders: %s\n' "$used" "$MAX_HEAVY" "$free" \
        "$(awk -F'\t' 'NF >= 2 { printf "%s (#%s) ", $1, $2 }' "$HEAVY_REGISTRY")"
}

gate_readable() { # print the integration-gate reason or nothing when ready.
    if [ -x "$REVIEW" ]; then
        local out
        out="$(WAYFINDER_WORKER_REGISTRY="$WORKER_REGISTRY" WAYFINDER_INTEGRATION_GATE="$GATE_FILE" "$REVIEW" gate-status 2>/dev/null || true)"
        case "$out" in
            gated:*) printf '%s' "${out#gated: }" && return 0 ;;
        esac
        return 0
    fi
    if [ -f "$GATE_FILE" ]; then
        local first
        first="$(head -1 "$GATE_FILE" 2>/dev/null || true)"
        case "$first" in
            blocked*) printf '%s' "${first#blocked }" && return 0 ;;
        esac
    fi
    return 0
}

quiescence_verdict() { # print QUIESCENT... or BLOCKED... (registry-only).
    if [ -x "$RECOVER" ]; then
        WAYFINDER_WORKER_REGISTRY="$WORKER_REGISTRY" "$RECOVER" quiescence 2>/dev/null || true
        return 0
    fi
    printf 'unknown (recovery seam missing)'
}

scout_active() { # print active slice names, one per line.
    [ -x "$SCOUT" ] || return 0
    WAYFINDER_SCOUT_REGISTRY="$SCOUT_REGISTRY" "$SCOUT" status 2>/dev/null \
        | awk -F'\t' '$2 == "active" { print $1 }' || true
}

scout_next_due() { # print the next due slice or nothing.
    [ -x "$SCOUT" ] || return 0
    WAYFINDER_SCOUT_REGISTRY="$SCOUT_REGISTRY" "$SCOUT" next 2>/dev/null || true
}

# band_names <awk-status-test> — worker names in one lifecycle band.
band_where() { # <band> — print "name (role #ticket)" entries, one per line.
    [ -f "$WORKER_REGISTRY" ] || return 0
    case "$1" in
        active) awk -F'\t' '$6 == "spawning" || $6 == "running" { print $1 " (" $2 " #" $3 ")" }' "$WORKER_REGISTRY" ;;
        blocked) awk -F'\t' '$6 == "blocked" { print $1 " (" $2 " #" $3 ")" }' "$WORKER_REGISTRY" ;;
        review) awk -F'\t' '$6 == "done" || $6 == "failed" || $6 == "ready-for-review" || $6 == "revision-requested" || $6 == "accepted-awaiting-integration" { print $1 " (" $6 " " $2 " #" $3 ")" }' "$WORKER_REGISTRY" ;;
        crashed) awk -F'\t' '$6 == "stopped" || $6 == "gone" { print $1 " (" $6 " " $2 " #" $3 ")" }' "$WORKER_REGISTRY" ;;
    esac
}

band_count() { # <band> — print the member count.
    local n
    n="$(band_where "$1" | wc -l)"
    printf '%s' "$n"
}

cmd_status() {
    local machine=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --machine) machine=1; shift ;;
            -h|--help) usage ;;
            *) usage_err "status: unknown flag $1" ;;
        esac
    done
    validate_limits
    local g m s h hu hf
    g="$(live_count all)"; m="$(live_count maintenance)"
    s="$(live_count bug-scout)"; h="$(live_count helper)"
    hu="$(heavy_used)"; hf=$((MAX_HEAVY - hu))
    local active_n blocked_n review_n crashed_n
    active_n="$(band_count active)"; blocked_n="$(band_count blocked)"
    review_n="$(band_count review)"; crashed_n="$(band_count crashed)"
    local gate verdict
    gate="$(gate_readable)"; verdict="$(quiescence_verdict)"
    local scout_list scout_due maint_live
    scout_list="$(scout_active | tr '\n' ' ')"; scout_due="$(scout_next_due)"
    maint_live="$(awk -F'\t' '$2 == "maintenance" && ($6 == "spawning" || $6 == "running") { printf "%s (#%s) ", $1, $3 }' "$WORKER_REGISTRY" 2>/dev/null || true)"
    if [ -n "$machine" ]; then
        printf 'scope=global used=%s max=%s free=%s\n' "$g" "$MAX_WORKERS" "$((MAX_WORKERS - g))"
        printf 'scope=maintenance used=%s max=%s free=%s\n' "$m" "$MAX_MAINT" "$((MAX_MAINT - m))"
        printf 'scope=bug-scout used=%s max=%s free=%s\n' "$s" "$MAX_SCOUTS" "$((MAX_SCOUTS - s))"
        printf 'scope=helper used=%s max=%s free=%s\n' "$h" "$MAX_HELPERS" "$((MAX_HELPERS - h))"
        printf 'scope=heavy used=%s max=%s free=%s\n' "$hu" "$MAX_HEAVY" "$hf"
        [ -f "$WORKER_REGISTRY" ] && awk -F'\t' 'NF >= 6 { printf "worker name=%s role=%s ticket=%s status=%s workspace=%s\n", $1, $2, $3, $6, $4 }' "$WORKER_REGISTRY" || true
        [ -f "$HEAVY_REGISTRY" ] && awk -F'\t' 'NF >= 2 { printf "heavy holder=%s ticket=%s since=%s\n", $1, $2, $3 }' "$HEAVY_REGISTRY" || true
        if [ -n "$gate" ]; then printf 'gate=gated:%s\n' "$gate"; else printf 'gate=ready\n'; fi
        printf 'quiescence=%s\n' "$verdict"
        if [ -n "$scout_list" ]; then printf 'scout_active=%s\n' "$scout_list"; else printf 'scout_active=none\n'; fi
        if [ -n "$scout_due" ]; then printf 'scout_next=%s\n' "$scout_due"; else printf 'scout_next=none\n'; fi
        return 0
    fi
    printf 'capacity: global %s/%s busy, maintenance %s/%s, scouts %s/%s, helpers %s/%s, heavy %s/%s\n' \
        "$g" "$MAX_WORKERS" "$m" "$MAX_MAINT" "$s" "$MAX_SCOUTS" "$h" "$MAX_HELPERS" "$hu" "$MAX_HEAVY"
    printf 'workers: %s active, %s blocked-input, %s review-pending, %s crashed\n' \
        "$active_n" "$blocked_n" "$review_n" "$crashed_n"
    if [ "$active_n" -gt 0 ]; then
        printf 'active: %s\n' "$(band_where active | tr '\n' ' ')"
    else
        printf 'active: none\n'
    fi
    if [ "$blocked_n" -gt 0 ]; then
        printf 'blocked-input: %s\n' "$(band_where blocked | tr '\n' ' ')"
    else
        printf 'blocked-input: none\n'
    fi
    if [ "$hu" -gt 0 ]; then
        printf 'waiting-resource (heavy holders): %s\n' "$(awk -F'\t' 'NF >= 2 { printf "%s (#%s) ", $1, $2 }' "$HEAVY_REGISTRY")"
    else
        printf 'waiting-resource: none (heavy %s/%s free)\n' "$hf" "$MAX_HEAVY"
    fi
    if [ "$review_n" -gt 0 ]; then
        printf 'review-pending: %s\n' "$(band_where review | tr '\n' ' ')"
    else
        printf 'review-pending: none\n'
    fi
    if [ "$crashed_n" -gt 0 ]; then
        printf 'crashed: %s\n' "$(band_where crashed | tr '\n' ' ')"
    else
        printf 'crashed: none\n'
    fi
    if [ -n "$maint_live" ]; then
        printf 'maintenance: live %s(repair in flight; canonical integration gated)\n' "$maint_live"
    else
        printf 'maintenance: idle (no live repair worker)\n'
    fi
    if [ -n "$scout_list" ]; then
        printf 'scout: active slice(s): %s; next due: %s\n' "$scout_list" "${scout_due:-none (all active)}"
    else
        printf 'scout: idle; next due: %s\n' "${scout_due:-none}"
    fi
    if [ -n "$gate" ]; then printf 'integration gate: GATED (%s)\n' "$gate"; else printf 'integration gate: ready\n'; fi
    printf 'quiescence: %s\n' "$verdict"
    if [ "$crashed_n" -gt 0 ] || [ "$review_n" -gt 0 ]; then
        printf 'restart-risk: %s crashed + %s review-pending row(s) — run wayfinder-recover.sh reconcile for Herdr truth before respawning\n' \
            "$crashed_n" "$review_n"
    else
        printf 'restart-risk: low (no crashed or review-pending rows; reconcile still precedes any respawn)\n'
    fi
}

[ $# -ge 1 ] || usage
sub="$1"; shift
case "$sub" in
    limits) cmd_limits "$@" ;;
    check) cmd_check "$@" ;;
    heavy-acquire) cmd_heavy_acquire "$@" ;;
    heavy-release) cmd_heavy_release "$@" ;;
    heavy-list) cmd_heavy_list "$@" ;;
    heavy-reconcile) cmd_heavy_reconcile "$@" ;;
    heavy-status) cmd_heavy_status "$@" ;;
    emit) cmd_emit "$@" ;;
    events) cmd_events "$@" ;;
    status) cmd_status "$@" ;;
    -h|--help|help) usage ;;
    *) usage_err "unknown subcommand '$sub'" ;;
esac
