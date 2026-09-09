#!/usr/bin/env bash
# wayfinder-worker.sh — Herdr worker-control seam for the Wayfinder map chief (map #697, ticket #735).
#
# Hides direct Herdr CLI details behind one small boundary so the chief
# scheduler (wayfinder-chief.sh) and later tickets never parse Herdr output
# themselves. Subcommands:
#
#   spawn     --role R --ticket N --workspace DIR [--name N] [--pane P]
#             [--kind K] [--prompt-file F | --prompt TEXT]
#   prompt    <name> [--text-file F | --text TEXT]
#   status    <name>
#   wait      <name> [--timeout MS] [--until STATE]...
#   read      <name> [--lines N] [--source SRC]
#   stop      <name>
#   cleanup   <name> [--force]
#   reconcile
#
# Contract notes (ticket #735 acceptance):
# - Only the map chief creates/schedules workers. spawn/prompt/stop/cleanup/
#   reconcile refuse leaf roles: when WAYFINDER_ROLE is ticket, maintenance,
#   bug-scout, or helper the call fails with a "request help via the chief"
#   message instead of touching Herdr. status/wait/read stay observable to all.
# - Dispatch is asynchronous by default: spawn and prompt never pass --wait
#   (nor --until/--timeout) to `herdr agent prompt`. worker_wait is the only
#   blocking call and always carries an explicit check-in --timeout
#   (default 300000ms) — a timeout is a check-in interval, never a deadline.
# - Worker role/class is explicit: every registry row carries one of
#   ticket, maintenance, bug-scout, helper.
# - Concurrency is bounded: spawn refuses when running rows reach
#   WAYFINDER_MAX_WORKERS (default 1 — the sequential fallback).
# - Stable identity for crash recovery (sibling ticket #741): the Herdr agent
#   name is the registry key; reconcile adopts live state and marks missing
#   rows gone without deleting salvageable rows.
# - No Git integration lives here: no merge/cherry-pick/stage/commit, and no
#   workspace creation (sibling ticket #736 owns the WorkspaceProvider — the
#   workspace dir must already exist and is only recorded).
#
# Env:
#   HERDR_BIN              herdr executable (default: herdr)
#   WAYFINDER_MAX_WORKERS  concurrency bound, >=1 (default: 1)
#   WAYFINDER_WORKER_KIND  Herdr agent kind (default: opencode)
#   WAYFINDER_WORKER_ARGS  extra args appended after -- on agent start
#   WAYFINDER_ROLE         caller role; leaf roles cannot orchestrate
#   WAYFINDER_WORKER_REGISTRY  registry file (default: <repo>/.wayfinder/workers.tsv)
#
# Registry (TSV, runtime state under gitignored .wayfinder/):
#   name role ticket workspace pane status created updated note
# Status flow: spawning -> running -> done|blocked -> ready-for-review
# (-> accepted/revision/rejected in ticket #740) -> cleanup removes the row.
# stop marks stopped; reconcile marks missing agents gone.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

HERDR_BIN="${HERDR_BIN:-herdr}"
MAX_WORKERS="${WAYFINDER_MAX_WORKERS:-1}"
WORKER_KIND_DEFAULT="${WAYFINDER_WORKER_KIND:-opencode}"
REGISTRY="${WAYFINDER_WORKER_REGISTRY:-$REPO/.wayfinder/workers.tsv}"
CALLER_ROLE="${WAYFINDER_ROLE:-chief}"

VALID_ROLES="ticket maintenance bug-scout helper"
LEAF_ROLES="ticket maintenance bug-scout helper"

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

die() { printf 'wayfinder-worker: %s\n' "$*" >&2; exit 1; }
usage_err() { printf 'wayfinder-worker: %s\n' "$*" >&2; exit 2; }

need_herdr() {
    command -v "$HERDR_BIN" >/dev/null 2>&1 || die "herdr binary not found: $HERDR_BIN (set HERDR_BIN)"
    command -v jq >/dev/null 2>&1 || die "jq is required for Herdr output parsing"
}

require_chief() { # <op>
    case " $LEAF_ROLES " in
        *" $CALLER_ROLE "*) die "$1 requires the map chief (WAYFINDER_ROLE=$CALLER_ROLE is a leaf role — request help via the chief instead)" ;;
    esac
}

valid_name() { [[ "${1:-}" =~ ^[a-z][a-z0-9_-]{0,31}$ ]]; }
valid_role() { case " $VALID_ROLES " in *" $1 "*) return 0;; esac; return 1; }

reg_init() {
    mkdir -p "$(dirname "$REGISTRY")"
    [ -f "$REGISTRY" ] || : > "$REGISTRY"
}

# running_count — rows occupying execution slots (spawning + running).
running_count() {
    [ -f "$REGISTRY" ] || { printf '0'; return; }
    awk -F'\t' '$6 == "spawning" || $6 == "running" { n++ } END { print n + 0 }' "$REGISTRY"
}

reg_row() { # <name> — print the TSV row or nothing.
    [ -f "$REGISTRY" ] || return 0
    awk -F'\t' -v name="$1" '$1 == name { print; exit }' "$REGISTRY"
}

# reg_upsert <name> <role> <ticket> <workspace> <pane> <status> <note>
reg_upsert() {
    local name="$1" role="$2" ticket="$3" workspace="$4" pane="$5" status="$6" note="$7"
    local now existing created tmp
    now="$(date '+%F %T')"
    note="$(printf '%s' "$note" | tr '\t\n' '  ')"
    reg_init
    existing="$(reg_row "$name")"
    if [ -n "$existing" ]; then
        created="$(printf '%s' "$existing" | cut -f7)"
        [ -n "$created" ] || created="$now"
    else
        created="$now"
    fi
    tmp="$(mktemp)"
    awk -F'\t' -v name="$name" '$1 != name' "$REGISTRY" > "$tmp"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$name" "$role" "$ticket" "$workspace" "$pane" "$status" "$created" "$now" "$note" >> "$tmp"
    mv "$tmp" "$REGISTRY"
}

# herdr_json <jq-filter> — tolerant field walk for Herdr response shapes.
herdr_field() { # <json> <filter...>
    local json="$1"; shift
    printf '%s' "$json" | jq -r "$@" 2>/dev/null
}

alloc_pane() {
    local out pane
    out="$("$HERDR_BIN" pane split --current --direction right --no-focus 2>&1)" \
        || die "pane allocation failed: $out"
    pane="$(herdr_field "$out" '.result.pane.pane_id // .result.pane_id // .pane_id // empty')"
    [ -n "$pane" ] || die "pane allocation returned no pane id: $out"
    printf '%s' "$pane"
}

cmd_spawn() {
    require_chief "worker_spawn"
    local role="" ticket="" workspace="" name="" pane="" kind="$WORKER_KIND_DEFAULT"
    local prompt_text="" prompt_file=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --role) role="${2:-}"; shift 2 ;;
            --ticket) ticket="${2:-}"; shift 2 ;;
            --workspace) workspace="${2:-}"; shift 2 ;;
            --name) name="${2:-}"; shift 2 ;;
            --pane) pane="${2:-}"; shift 2 ;;
            --kind) kind="${2:-}"; shift 2 ;;
            --prompt) prompt_text="${2:-}"; shift 2 ;;
            --prompt-file) prompt_file="${2:-}"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "spawn: unknown flag $1" ;;
        esac
    done
    valid_role "$role" || usage_err "spawn: --role must be one of: $VALID_ROLES"
    [[ "$ticket" =~ ^[0-9]+$ ]] || usage_err "spawn: --ticket must be a numeric issue number"
    [ -n "$workspace" ] || usage_err "spawn: --workspace is required (provider-owned dir; never created here)"
    [ -d "$workspace" ] || die "spawn: workspace does not exist: $workspace (ticket #736 owns creation)"
    [ -z "$name" ] && name="wf-${ticket}-${role}"
    valid_name "$name" || die "spawn: invalid Herdr agent name '$name' (want [a-z][a-z0-9_-]{0,31})"
    case "$pane" in ''|*[!a-zA-Z0-9_:@-]* ) [ -z "$pane" ] || die "spawn: invalid pane id '$pane'" ;; esac
    [ -n "$prompt_file" ] || true
    if [ -n "$prompt_file" ] && [ ! -f "$prompt_file" ]; then die "spawn: prompt file not found: $prompt_file"; fi
    [[ "$MAX_WORKERS" =~ ^[0-9]+$ ]] && [ "$MAX_WORKERS" -ge 1 ] || die "spawn: WAYFINDER_MAX_WORKERS must be >= 1 (got '$MAX_WORKERS')"
    if [ "$(running_count)" -ge "$MAX_WORKERS" ]; then
        die "spawn: at capacity ($(running_count)/$MAX_WORKERS running) — refuse to exceed WAYFINDER_MAX_WORKERS"
    fi
    if [ -n "$(reg_row "$name")" ]; then
        die "spawn: worker name already registered: $name (cleanup or reconcile first)"
    fi
    need_herdr
    if [ -z "$pane" ]; then pane="$(alloc_pane)"; fi
    local out
    if [ -n "${WAYFINDER_WORKER_ARGS:-}" ]; then
        # shellcheck disable=SC2206
        local extra=($WAYFINDER_WORKER_ARGS)
        out="$("$HERDR_BIN" agent start "$name" --kind "$kind" --pane "$pane" -- "${extra[@]}" 2>&1)" \
            || die "agent start failed for $name: $out"
    else
        out="$("$HERDR_BIN" agent start "$name" --kind "$kind" --pane "$pane" 2>&1)" \
            || die "agent start failed for $name: $out"
    fi
    # Initial prompt is asynchronous by construction: never --wait here.
    if [ -n "$prompt_file" ]; then
        prompt_text="$(cat "$prompt_file")"
    fi
    if [ -n "$prompt_text" ]; then
        if ! out="$("$HERDR_BIN" agent prompt "$name" "$prompt_text" 2>&1)"; then
            reg_upsert "$name" "$role" "$ticket" "$workspace" "$pane" "running" "started; initial prompt failed: $out"
            die "agent prompt failed for $name (worker kept as running): $out"
        fi
    fi
    reg_upsert "$name" "$role" "$ticket" "$workspace" "$pane" "running" "spawned kind=$kind"
    printf 'spawned %s (role=%s ticket=#%s pane=%s)\n' "$name" "$role" "$ticket" "$pane"
}

cmd_prompt() {
    require_chief "worker_prompt"
    local name="${1:-}" text="" text_file=""
    shift || usage_err "prompt: worker name is required"
    while [ $# -gt 0 ]; do
        case "$1" in
            --text) text="${2:-}"; shift 2 ;;
            --text-file) text_file="${2:-}"; shift 2 ;;
            *) usage_err "prompt: unknown flag $1" ;;
        esac
    done
    valid_name "$name" || usage_err "prompt: invalid worker name '$name'"
    [ -z "$text_file" ] || [ -f "$text_file" ] || die "prompt: text file not found: $text_file"
    [ -n "$text_file" ] && text="$(cat "$text_file")"
    [ -n "$text" ] || usage_err "prompt: --text or --text-file is required"
    [ -n "$(reg_row "$name")" ] || die "prompt: unknown worker '$name' (not in registry)"
    need_herdr
    # Asynchronous: no --wait, no --until, no --timeout — success acknowledges
    # the write, not the turn (Herdr agent-prompt semantics).
    local out
    out="$("$HERDR_BIN" agent prompt "$name" "$text" 2>&1)" \
        || die "agent prompt failed for $name: $out"
    printf 'prompted %s\n' "$name"
}

cmd_status() {
    local name="${1:-}"
    valid_name "$name" || usage_err "status: invalid worker name '${1:-}'"
    local row
    row="$(reg_row "$name")"
    [ -n "$row" ] || die "status: unknown worker '$name' (not in registry)"
    need_herdr
    local out state
    out="$("$HERDR_BIN" agent get "$name" 2>&1)" \
        || die "agent get failed for $name: $out"
    state="$(herdr_field "$out" '.result.agent.status // .result.status // .status // empty')"
    printf 'worker=%s registry=%s herdr=%s\n' "$name" "$(printf '%s' "$row" | cut -f6)" "${state:-unknown}"
}

cmd_wait() {
    local name="${1:-}" timeout_ms=300000
    shift || usage_err "wait: worker name is required"
    local until_args=()
    while [ $# -gt 0 ]; do
        case "$1" in
            --timeout) timeout_ms="${2:-}"; shift 2 ;;
            --until) until_args+=(--until "${2:-}"); shift 2 ;;
            *) usage_err "wait: unknown flag $1" ;;
        esac
    done
    valid_name "$name" || usage_err "wait: invalid worker name '$name'"
    [[ "$timeout_ms" =~ ^[0-9]+$ ]] || usage_err "wait: --timeout must be milliseconds"
    [ -n "$(reg_row "$name")" ] || die "wait: unknown worker '$name' (not in registry)"
    need_herdr
    # The single blocking call in the seam: a bounded check-in wait. Callers
    # re-enter on timeout — expiry is a check-in, never a worker deadline.
    "$HERDR_BIN" agent wait "$name" "${until_args[@]}" --timeout "$timeout_ms"
}

cmd_read() {
    local name="${1:-}"
    shift || usage_err "read: worker name is required"
    local lines=120 source="recent-unwrapped"
    while [ $# -gt 0 ]; do
        case "$1" in
            --lines) lines="${2:-}"; shift 2 ;;
            --source) source="${2:-}"; shift 2 ;;
            *) usage_err "read: unknown flag $1" ;;
        esac
    done
    valid_name "$name" || usage_err "read: invalid worker name '$name'"
    [ -n "$(reg_row "$name")" ] || die "read: unknown worker '$name' (not in registry)"
    need_herdr
    "$HERDR_BIN" agent read "$name" --source "$source" --lines "$lines"
}

cmd_stop() {
    require_chief "worker_stop"
    local name="${1:-}"
    valid_name "$name" || usage_err "stop: invalid worker name '${1:-}'"
    local row
    row="$(reg_row "$name")" || true
    [ -n "$row" ] || die "stop: unknown worker '$name' (not in registry)"
    need_herdr
    local out
    out="$("$HERDR_BIN" agent send-keys "$name" ctrl+c 2>&1)" \
        || die "agent send-keys failed for $name: $out"
    reg_upsert "$name" "$(printf '%s' "$row" | cut -f2)" "$(printf '%s' "$row" | cut -f3)" \
        "$(printf '%s' "$row" | cut -f4)" "$(printf '%s' "$row" | cut -f5)" "stopped" "interrupted via send-keys"
    printf 'stopped %s\n' "$name"
}

cmd_cleanup() {
    require_chief "worker_cleanup"
    local name="${1:-}" force=0
    shift || usage_err "cleanup: worker name is required"
    while [ $# -gt 0 ]; do
        case "$1" in
            --force) force=1; shift ;;
            *) usage_err "cleanup: unknown flag $1" ;;
        esac
    done
    valid_name "$name" || usage_err "cleanup: invalid worker name '$name'"
    local row status tmp
    row="$(reg_row "$name")" || true
    [ -n "$row" ] || die "cleanup: unknown worker '$name' (not in registry)"
    status="$(printf '%s' "$row" | cut -f6)"
    case "$status" in
        spawning|running)
            [ "$force" -eq 1 ] || die "cleanup: worker $name is $status — refuse to orphan a live agent (stop it first or pass --force)"
            ;;
    esac
    tmp="$(mktemp)"
    awk -F'\t' -v name="$name" '$1 != name' "$REGISTRY" > "$tmp"
    mv "$tmp" "$REGISTRY"
    printf 'cleaned %s (was %s)\n' "$name" "$status"
}

cmd_reconcile() {
    require_chief "worker_reconcile"
    need_herdr
    reg_init
    local out live names row name status herdr_status
    out="$("$HERDR_BIN" agent list 2>&1)" \
        || die "agent list failed: $out"
    # Tolerant across Herdr response shapes: collect name/status pairs.
    live="$(herdr_field "$out" '[.. | objects | select(has("name") and has("status")) | "\(.name)\t\(.status)"] | unique | .[]')"
    names="$(printf '%s' "$live" | cut -f1)"
    local kept=0 gone=0 updated=0
    while IFS=$'\t' read -r name _role _ticket _ws _pane status _c _u _note; do
        [ -n "$name" ] || continue
        herdr_status="$(printf '%s' "$live" | awk -F'\t' -v n="$name" '$1 == n { print $2; exit }')"
        if [ -z "$herdr_status" ]; then
            case "$status" in
                spawning|running)
                    reg_upsert "$name" "$_role" "$_ticket" "$_ws" "$_pane" "gone" "missing from herdr agent list — result may be salvageable"
                    gone=$((gone + 1))
                    ;;
                *) kept=$((kept + 1)) ;;
            esac
        else
            case "$herdr_status" in
                running|working|idle) herdr_status="running" ;;
                done) herdr_status="done" ;;
                blocked) herdr_status="blocked" ;;
                *) herdr_status="$status" ;;
            esac
            if [ "$herdr_status" != "$status" ]; then
                reg_upsert "$name" "$_role" "$_ticket" "$_ws" "$_pane" "$herdr_status" "adopted from herdr agent list"
                updated=$((updated + 1))
            else
                kept=$((kept + 1))
            fi
        fi
    done < "$REGISTRY"
    # Live agents the registry never saw are reported, never adopted here —
    # adoption across a chief restart is sibling ticket #741's recovery path.
    if [ -n "$names" ]; then
        while IFS= read -r name; do
            [ -n "$name" ] || continue
            if [ -z "$(reg_row "$name")" ]; then
                herdr_status="$(printf '%s' "$live" | awk -F'\t' -v n="$name" '$1 == n { print $2; exit }')"
                printf 'unmanaged: %s (%s)\n' "$name" "$herdr_status"
            fi
        done <<< "$names"
    fi
    printf 'reconciled: %d kept, %d updated, %d gone\n' "$kept" "$updated" "$gone"
}

[ $# -ge 1 ] || usage
sub="$1"; shift
case "$sub" in
    spawn) cmd_spawn "$@" ;;
    prompt) cmd_prompt "$@" ;;
    status) cmd_status "$@" ;;
    wait) cmd_wait "$@" ;;
    read) cmd_read "$@" ;;
    stop) cmd_stop "$@" ;;
    cleanup) cmd_cleanup "$@" ;;
    reconcile) cmd_reconcile "$@" ;;
    -h|--help|help) usage ;;
    *) usage_err "unknown subcommand '$sub'" ;;
esac
