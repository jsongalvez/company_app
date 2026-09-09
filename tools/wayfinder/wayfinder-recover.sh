#!/usr/bin/env bash
# wayfinder-recover.sh — durable parallel worker/workspace registry recovery for Wayfinder (map #697, ticket #741).
#
# Parallel workers create recovery hazards a PID file cannot solve: a daemon or
# chief restart must not duplicate live workers, abandon useful commits, lose
# workspace ownership, or advance to a successor generation while unresolved
# workers still exist. This script is the recovery owner. It joins the two
# durable registries — the worker registry (wayfinder-worker.sh: name, role,
# ticket, workspace, pane, status, map, generation) and the workspace registry
# (wayfinder-workspace.sh: provider, workspace_id, path, base_sha, state) — on
# the workspace path, reconciles both through their seams, and exposes the
# quiescence predicate that gates handoff advancement.
#
# Subcommands:
#
#   status
#             unified operator view: worker counts by lifecycle band, workspace
#             counts, integration gate, orphan summary, quiescence verdict.
#             Read-only (registry + gate files only): no Herdr, no gh, no git.
#   reconcile
#             daemon/chief restart entry: worker reconcile (adopts live Herdr
#             state, re-adopts gone/stopped rows whose agent is live again,
#             marks missing rows gone, reports unmanaged agents) plus workspace
#             reconcile (marks missing paths gone, reports unmanaged worktrees),
#             then prints the quiescence verdict. Never respawns workers and
#             never deletes rows — replacement spawns happen only through the
#             chief fill after the previous owner is confirmed gone and cleaned.
#   quiescence [--strict]
#             handoff-advancement predicate. Prints QUIESCENT or
#             BLOCKED: <reason> (exit 0 / 1). Registry-only and deterministic:
#             writable rows (ticket, maintenance, helper) in any unsafe state
#             block advancement; read-only bug-scout rows are reported but block
#             only with --strict. Handoff advancement (writing or revising a
#             successor packet, and the daemon spawning from it) must refuse
#             while BLOCKED. Read-only: no Herdr, no gh, no git.
#   orphans
#             list crashed workers (stopped/gone) holding tracker claims plus
#             the exact gh release commands that return each ticket to the pool.
#             Read-only: prints guidance, never mutates the tracker (the chief
#             performs the release after confirming no salvageable result needs
#             review first).
#   adopt <name> --role R --ticket N --workspace DIR [--pane P] [--map M]
#             [--generation G] [--parent W --scope TEXT] (helper only)
#             register one unmanaged live Herdr agent as a running worker so a
#             restart adopts it instead of leaving it invisible to the chief
#             fill (which would otherwise spawn a duplicate for the same
#             ticket). Verifies Herdr liveness read-only (agent list), refuses
#             already-registered names and already-held tickets (bug-scout rows
#             excepted: scouts anchor ticket=map and several may share one
#             map; helper rows excepted against their own parent: helpers share
#             the parent ticket by design), and records map/generation recovery
#             identity under the worker seam's charset rules (a forged
#             generation could shadow review note tokens). Never starts agents
#             and never touches the tracker.
#
# Lifecycle bands (quiescence + status share these):
#
#   active    spawning, running (workers executing now)
#   pending   done, blocked, failed (worker-reported, awaiting chief collect),
#             ready-for-review, revision-requested, accepted-awaiting-integration
#             (awaiting chief disposition/integration — ticket #740 queue)
#   crashed   stopped, gone (worker died; workspace result may be salvageable
#             for review — recovery preserves ambiguous state over destroying
#             potentially useful work)
#   terminal  integrated, rejected, cancelled (safe; cleanable)
#
# A generation is quiescent when zero writable rows sit in active, pending, or
# crashed bands. Crashed rows block because their workspace may hold the only
# copy of a reviewable result: disposition (review accept/revision/reject) or
# explicit cleanup must resolve them first. Terminal rows never block; cleaned
# (absent) rows never block. Unmanaged live agents/worktrees reported by
# reconcile must be adopted or stopped before advancing — quiescence trusts the
# registry, so reconcile first, then check quiescence. The daemon gate
# performs that reconcile-then-gate composition itself; bare `quiescence`
# callers must do the same. `--strict` additionally blocks on read-only scout
# activity; it is an operator manual-use knob (the daemon gate uses the
# default: scouts never gate writable generations).
#
# Cleanup ownership: this script never deletes registry rows, workspaces,
# panes, branches, or snapshots. Cleanup happens only after a safe terminal
# disposition, via the worker seam (cleanup) and the workspace provider
# (cleanup) — the same path the review queue's integrate guidance prints.
#
# Boundaries: no Git integration here (no cherry-pick/merge/stage/commit/
# reset/worktree/branch verbs — workspace state is reconciled only through
# wayfinder-workspace.sh); no tracker writes (no gh — orphans prints guidance
# for the chief); review/integration decisions stay chief-owned via
# wayfinder-review.sh; the workspace seam stays provider-neutral (this script
# joins on opaque paths and never branches on provider names).
#
# Env:
#   WAYFINDER_WORKER_REGISTRY    worker registry file (default: <repo>/.wayfinder/workers.tsv)
#   WAYFINDER_WORKSPACE_REGISTRY workspace registry file (default: <repo>/.wayfinder/workspaces.tsv)
#   WAYFINDER_REPO               canonical checkout (default: repo containing this script)
#   WAYFINDER_INTEGRATION_GATE   gate file read by status (default: <repo>/.wayfinder/integration-gate)
#   HERDR_BIN                    herdr executable, adopt liveness only (default: herdr)
#   WAYFINDER_MAP                default owning map recorded by adopt (default: empty)
#   WAYFINDER_GENERATION         default chief generation recorded by adopt (default: empty)
#   WAYFINDER_ROLE               caller role; reconcile/adopt require the chief
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DISCOVERED_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKER="$SCRIPT_DIR/wayfinder-worker.sh"
WORKSPACE="$SCRIPT_DIR/wayfinder-workspace.sh"
REVIEW="$SCRIPT_DIR/wayfinder-review.sh"

CANON_DEFAULT="${WAYFINDER_REPO:-$DISCOVERED_REPO}"
WORKER_REGISTRY="${WAYFINDER_WORKER_REGISTRY:-$DISCOVERED_REPO/.wayfinder/workers.tsv}"
WORKSPACE_REGISTRY="${WAYFINDER_WORKSPACE_REGISTRY:-$DISCOVERED_REPO/.wayfinder/workspaces.tsv}"
GATE_FILE="${WAYFINDER_INTEGRATION_GATE:-$DISCOVERED_REPO/.wayfinder/integration-gate}"
HERDR_BIN="${HERDR_BIN:-herdr}"
CALLER_ROLE="${WAYFINDER_ROLE:-chief}"

LEAF_ROLES="ticket maintenance bug-scout helper"
VALID_ROLES="ticket maintenance bug-scout helper"
WRITABLE_ROLES="ticket maintenance helper"

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

die() { printf 'wayfinder-recover: %s\n' "$*" >&2; exit 1; }
usage_err() { printf 'wayfinder-recover: %s\n' "$*" >&2; exit 2; }
log() { printf '%s [recover] %s\n' "$(date '+%F %T')" "$*"; }

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
valid_writable() { case " $WRITABLE_ROLES " in *" $1 "*) return 0;; esac; return 1; }

worker_row() { # <name> — print the worker TSV row or nothing.
    [ -f "$WORKER_REGISTRY" ] || return 0
    awk -F'\t' -v name="$1" '$1 == name { print; exit }' "$WORKER_REGISTRY"
}

# band semantics live in quiescence_reason/cmd_status below (single source).

gate_readable() { # print the gate reason or nothing when ready.
    if [ -f "$GATE_FILE" ]; then
        local first
        first="$(head -1 "$GATE_FILE" 2>/dev/null || true)"
        case "$first" in
            blocked*)
                printf '%s' "${first#blocked }"
                return 0
                ;;
        esac
    fi
    if [ -f "$WORKER_REGISTRY" ]; then
        local live
        live="$(awk -F'\t' '$2 == "maintenance" && ($6 == "spawning" || $6 == "running") { print $1 }' "$WORKER_REGISTRY" | tr '\n' ' ')"
        if [ -n "$live" ]; then
            printf 'live maintenance worker(s): %s(repair in flight; canonical integration gated)' "$live"
            return 0
        fi
    fi
    return 0
}

# quiescence_reason [--strict] — print QUIESCENT or BLOCKED: <reason>.
# Registry-only: deterministic for handoff-race tests and the daemon gate.
quiescence_reason() {
    local strict=""
    [ "${1:-}" = "--strict" ] && strict=1
    local active=0 pending=0 crashed=0 scout_active=0 scout_pending=0
    local active_names="" pending_names="" crashed_names="" scout_names=""
    if [ -f "$WORKER_REGISTRY" ]; then
        local name role _ticket _ws _pane status
        while IFS=$'\t' read -r name role _ticket _ws _pane status _c _u _note _parent _map _generation _rest; do
            [ -n "$name" ] || continue
            if [ "$role" = "bug-scout" ]; then
                case "$status" in
                    spawning|running) scout_active=$((scout_active + 1)); scout_names="$scout_names $name" ;;
                    done|blocked|failed|ready-for-review|revision-requested|accepted-awaiting-integration|stopped|gone) scout_pending=$((scout_pending + 1)); scout_names="$scout_names $name" ;;
                esac
                continue
            fi
            case "$status" in
                spawning|running) active=$((active + 1)); active_names="$active_names $name" ;;
                done|blocked|failed|ready-for-review|revision-requested|accepted-awaiting-integration) pending=$((pending + 1)); pending_names="$pending_names $name" ;;
                stopped|gone) crashed=$((crashed + 1)); crashed_names="$crashed_names $name" ;;
            esac
        done < "$WORKER_REGISTRY"
    fi
    if [ "$active" -gt 0 ]; then
        printf 'BLOCKED: workers active:%s' "$active_names"
        return 0
    fi
    if [ "$pending" -gt 0 ]; then
        printf 'BLOCKED: results awaiting review:%s' "$pending_names"
        return 0
    fi
    if [ "$crashed" -gt 0 ]; then
        printf 'BLOCKED: crashed workers holding salvageable results:%s (disposition or cleanup first)' "$crashed_names"
        return 0
    fi
    if [ -n "$strict" ] && { [ "$scout_active" -gt 0 ] || [ "$scout_pending" -gt 0 ]; }; then
        printf 'BLOCKED: bug-scout activity (strict):%s' "$scout_names"
        return 0
    fi
    if [ "$scout_active" -gt 0 ] || [ "$scout_pending" -gt 0 ]; then
        printf 'QUIESCENT (writable clear; read-only scout activity:%s)' "$scout_names"
        return 0
    fi
    printf 'QUIESCENT'
}

cmd_quiescence() {
    local strict=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --strict) strict=--strict; shift ;;
            -h|--help) usage ;;
            *) usage_err "quiescence: unknown flag $1" ;;
        esac
    done
    local verdict
    if [ -n "$strict" ]; then
        verdict="$(quiescence_reason --strict)"
    else
        verdict="$(quiescence_reason)"
    fi
    printf '%s\n' "$verdict"
    case "$verdict" in
        QUIESCENT*) return 0 ;;
        *) return 1 ;;
    esac
}

cmd_orphans() {
    [ $# -eq 0 ] || usage_err "orphans takes no flags"
    local found=0
    [ -f "$WORKER_REGISTRY" ] || { printf 'orphans: none (no worker registry)\n'; return 0; }
    local name role ticket _ws _pane status
    while IFS=$'\t' read -r name role ticket _ws _pane status _c _u _note _parent _map _generation _rest; do
        [ -n "$name" ] || continue
        case "$status" in
            stopped|gone) ;;
            *) continue ;;
        esac
        found=$((found + 1))
        printf 'orphan: %s (role=%s ticket=#%s status=%s)\n' "$name" "$role" "$ticket" "$status"
        case "$role" in
            bug-scout)
                printf '  action: scout holds no tracker claim; cleanup the worker row when its findings are filed\n'
                ;;
            *)
                printf '  action: inspect the workspace result first (reviewable commit may be salvageable for the #740 queue)\n'
                printf '  release: gh issue edit %s --remove-assignee @me  # only after the result is dispositioned or confirmed unsalvageable\n' "$ticket"
                ;;
        esac
    done < "$WORKER_REGISTRY"
    [ "$found" -gt 0 ] || printf 'orphans: none\n'
}

cmd_status() {
    [ $# -eq 0 ] || usage_err "status takes no flags"
    local total=0 active=0 pending=0 crashed=0 terminal=0 scouts=0
    if [ -f "$WORKER_REGISTRY" ]; then
        local name role _ticket _ws _pane status
        while IFS=$'\t' read -r name role _ticket _ws _pane status _c _u _note _parent _map _generation _rest; do
            [ -n "$name" ] || continue
            total=$((total + 1))
            if [ "$role" = "bug-scout" ]; then scouts=$((scouts + 1)); continue; fi
            case "$status" in
                spawning|running) active=$((active + 1)) ;;
                done|blocked|failed|ready-for-review|revision-requested|accepted-awaiting-integration) pending=$((pending + 1)) ;;
                stopped|gone) crashed=$((crashed + 1)) ;;
                integrated|rejected|cancelled) terminal=$((terminal + 1)) ;;
            esac
        done < "$WORKER_REGISTRY"
    fi
    local ws_total=0 ws_ready=0 ws_gone=0
    if [ -f "$WORKSPACE_REGISTRY" ]; then
        ws_total="$(awk -F'\t' 'NF >= 5 { n++ } END { print n + 0 }' "$WORKSPACE_REGISTRY")"
        ws_ready="$(awk -F'\t' '$5 == "ready" { n++ } END { print n + 0 }' "$WORKSPACE_REGISTRY")"
        ws_gone="$(awk -F'\t' '$5 == "gone" { n++ } END { print n + 0 }' "$WORKSPACE_REGISTRY")"
    fi
    local gate="ready" reason
    reason="$(gate_readable)"
    [ -z "$reason" ] || gate="GATED ($reason)"
    local verdict
    verdict="$(quiescence_reason)"
    printf 'workers: %d total (%d active, %d review-pending, %d crashed, %d terminal, %d scouts)\n' \
        "$total" "$active" "$pending" "$crashed" "$terminal" "$scouts"
    printf 'workspaces: %d total (%d ready, %d gone)\n' "$ws_total" "$ws_ready" "$ws_gone"
    printf 'integration gate: %s\n' "$gate"
    printf 'quiescence: %s\n' "$verdict"
    cmd_orphans >/dev/null 2>&1 || true
    awk -F'\t' '$6 == "stopped" || $6 == "gone" { n++ } END { if (n + 0 > 0) print "orphans: " n " crashed worker(s) — see `orphans` for release guidance" }' \
        "$WORKER_REGISTRY" 2>/dev/null || true
}

cmd_reconcile() {
    require_chief "recover_reconcile"
    [ $# -eq 0 ] || usage_err "reconcile takes no flags"
    [ -x "$WORKER" ] || die "reconcile: worker seam not executable: $WORKER"
    log "reconcile start: worker registry + workspace provider state"
    "$WORKER" reconcile
    if [ -x "$WORKSPACE" ]; then
        WAYFINDER_REPO="$CANON_DEFAULT" "$WORKSPACE" reconcile || die "reconcile: workspace provider state failed closed — resolve before advancing"
    else
        log "workspace provider seam missing ($WORKSPACE) — worker state only"
    fi
    local verdict
    verdict="$(quiescence_reason)"
    log "reconcile end: $verdict"
    printf '%s\n' "$verdict"
}

herdr_live_status() { # <name> — print the Herdr agent status or nothing.
    local json st
    json="$("$HERDR_BIN" agent list 2>/dev/null || true)"
    [ -n "$json" ] || return 0
    st="$(printf '%s' "$json" | jq -r --arg n "$1" '[.. | objects | select(has("name") and has("status")) | select(.name == $n) | .status] | first // empty' 2>/dev/null || true)"
    printf '%s' "$st"
}

cmd_adopt() {
    require_chief "recover_adopt"
    [ $# -ge 1 ] || usage_err "adopt: worker name is required"
    local name="$1"; shift
    valid_name "$name" || usage_err "adopt: invalid worker name '$name'"
    local role="" ticket="" workspace="" pane="" map="" generation="" parent="" scope=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --role) need_arg "$1" "${2:-}"; role="$2"; shift 2 ;;
            --ticket) need_arg "$1" "${2:-}"; ticket="$2"; shift 2 ;;
            --workspace) need_arg "$1" "${2:-}"; workspace="$2"; shift 2 ;;
            --pane) need_arg "$1" "${2:-}"; pane="$2"; shift 2 ;;
            --map) need_arg "$1" "${2:-}"; map="$2"; shift 2 ;;
            --generation) need_arg "$1" "${2:-}"; generation="$2"; shift 2 ;;
            --parent) need_arg "$1" "${2:-}"; parent="$2"; shift 2 ;;
            --scope) need_arg "$1" "${2:-}"; scope="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "adopt: unknown flag $1" ;;
        esac
    done
    valid_role "$role" || usage_err "adopt: --role must be one of: $VALID_ROLES"
    [[ "$ticket" =~ ^[0-9]+$ ]] || usage_err "adopt: --ticket must be a numeric issue number"
    [ -n "$workspace" ] || usage_err "adopt: --workspace is required (the agent's existing isolated dir; never created here)"
    case "$workspace" in
        *$'\t'*|*$'\n'*) die "adopt: workspace path must not contain tabs or newlines" ;;
    esac
    [ -d "$workspace" ] || die "adopt: workspace does not exist: $workspace"
    [ -n "$map" ] || map="${WAYFINDER_MAP:-}"
    [ -n "$generation" ] || generation="${WAYFINDER_GENERATION:-}"
    case "$map" in
        "") ;;
        *[!0-9]*) usage_err "adopt: --map must be a numeric map issue number" ;;
    esac
    # Same charset as worker spawn (ticket #741): the generation is stored in
    # column 12 AND echoed into the note, and the review queue resolves
    # results by grepping the whole row for note tokens — an unchecked
    # generation could forge reviewed=/result= tokens that shadow legitimate
    # accepts. Refuse structured-token shapes at entry.
    case "$generation" in
        ""|*[!a-zA-Z0-9_:@.-]*)
            [ -z "$generation" ] || usage_err "adopt: --generation must match [a-zA-Z0-9_:@.-]+"
            ;;
    esac
    case "$pane" in
        ''|*[!a-zA-Z0-9_:@-]*)
            [ -z "$pane" ] || die "adopt: invalid pane id '$pane'"
            ;;
    esac
    if [ "$role" = "helper" ]; then
        [ -n "$parent" ] || usage_err "adopt: --role helper requires --parent <parent-worker>"
        [ -n "$scope" ] || usage_err "adopt: --role helper requires --scope <bounded scope note>"
        valid_name "$parent" || usage_err "adopt: invalid parent worker name '$parent'"
        local parent_row
        parent_row="$(worker_row "$parent")"
        [ -n "$parent_row" ] || die "adopt: unknown parent worker '$parent'"
        [ "$(printf '%s' "$parent_row" | cut -f2)" != "helper" ] \
            || die "adopt: parent '$parent' is itself a helper — helpers never nest"
        [ "$ticket" = "$(printf '%s' "$parent_row" | cut -f3)" ] \
            || die "adopt: helper ticket #$ticket must match parent '$parent' ticket"
        [ -z "$map" ] && map="$(printf '%s' "$parent_row" | cut -f11)"
    else
        [ -z "$parent" ] || usage_err "adopt: --parent is helper-only (role $role takes no parent)"
        [ -z "$scope" ] || usage_err "adopt: --scope is helper-only (role $role takes no scope)"
    fi
    [ -z "$(worker_row "$name")" ] \
        || die "adopt: worker name already registered: $name (reconcile or cleanup first — never duplicate)"
    if [ -f "$WORKER_REGISTRY" ]; then
        if [ "$role" = "bug-scout" ]; then
            : # scouts anchor ticket=map; several may share one map.
        elif [ "$role" = "helper" ]; then
            # Helpers share the parent ticket by design, so the ticket guard
            # only refuses a NON-parent holder (a duplicate ticket worker for
            # the same number — pathological, never a second helper).
            local rival
            rival="$(awk -F'\t' -v t="$ticket" -v p="$parent" '$3 == t && $2 != "helper" && $2 != "bug-scout" && $1 != p { print $1; exit }' "$WORKER_REGISTRY")"
            if [ -n "$rival" ]; then
                die "adopt: ticket #$ticket is held by non-parent worker '$rival' — resolve that row first"
            fi
        else
            local holder
            holder="$(awk -F'\t' -v t="$ticket" '$3 == t { print $1; exit }' "$WORKER_REGISTRY")"
            if [ -n "$holder" ]; then
                die "adopt: ticket #$ticket is already held by '$holder' — resolve that row first (never two owners for one ticket)"
            fi
        fi
    fi
    command -v jq >/dev/null 2>&1 || die "adopt: jq is required for Herdr output parsing"
    local herdr_status status note
    herdr_status="$(herdr_live_status "$name")"
    [ -n "$herdr_status" ] || die "adopt: '$name' is not live in herdr agent list — nothing to adopt (spawn a replacement through the chief only after the previous owner is confirmed gone)"
    case "$herdr_status" in
        running|working|idle) status="running" ;;
        done) status="done" ;;
        blocked) status="blocked" ;;
        failed) status="failed" ;;
        *) die "adopt: '$name' reports an unknown Herdr status '$herdr_status' — inspect via herdr agent read first" ;;
    esac
    mkdir -p "$(dirname "$WORKER_REGISTRY")"
    [ -f "$WORKER_REGISTRY" ] || : > "$WORKER_REGISTRY"
    local now tmp
    now="$(date '+%F %T')"
    if [ "$role" = "helper" ]; then
        note="adopted live $herdr_status after restart: helper for $parent: $scope"
    else
        note="adopted live $herdr_status after restart"
    fi
    [ -z "$map" ] || note="$note (map #$map)"
    [ -z "$generation" ] || note="$note generation $generation"
    note="$(printf '%s' "$note" | tr '\t\n' '  ')"
    if command -v flock >/dev/null 2>&1; then
        exec {WAYFINDER_RECOVER_REG_FD}>"$WORKER_REGISTRY.lock"
        flock -w 60 "$WAYFINDER_RECOVER_REG_FD" \
            || die "adopt: cannot lock registry: $WORKER_REGISTRY.lock"
    fi
    tmp="$(mktemp -p "$(dirname "$WORKER_REGISTRY")" reg.XXXXXX)"
    awk -F'\t' -v n="$name" '$1 != n' "$WORKER_REGISTRY" > "$tmp"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$name" "$role" "$ticket" "$workspace" "$pane" "$status" "$now" "$now" "$note" "$parent" "$map" "$generation" >> "$tmp"
    mv "$tmp" "$WORKER_REGISTRY"
    if command -v flock >/dev/null 2>&1; then
        # Value-based close (`exec {VAR}>&-` misbehaves on some bash builds).
        eval "exec $WAYFINDER_RECOVER_REG_FD>&-" 2>/dev/null || true
    fi
    log "adopted $name (role=$role ticket=#$ticket status=$status)"
}

[ $# -ge 1 ] || usage
sub="$1"; shift
case "$sub" in
    status) cmd_status "$@" ;;
    reconcile) cmd_reconcile "$@" ;;
    quiescence) cmd_quiescence "$@" ;;
    orphans) cmd_orphans "$@" ;;
    adopt) cmd_adopt "$@" ;;
    -h|--help|help) usage ;;
    *) usage_err "unknown subcommand '$sub'" ;;
esac
