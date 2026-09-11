#!/usr/bin/env bash
# wayfinder-worker.sh — Herdr worker-control seam for the Wayfinder map chief (map #697, tickets #735 #737 #741 #742).
#
# Hides direct Herdr CLI details behind one small boundary so the chief
# scheduler (wayfinder-chief.sh) and later tickets never parse Herdr output
# themselves. Subcommands:
#
#   spawn     --role R --ticket N --workspace DIR [--name N] [--pane P]
#             [--kind K] [--prompt-file F | --prompt TEXT]
#             [--parent WORKER --scope TEXT] (helper only)
#             [--map M] [--generation G] (recovery identity, ticket #741)
#   prompt    <name> [--text-file F | --text TEXT]
#   status    <name>
#   wait      <name> [--timeout MS] [--until STATE]...
#   read      <name> [--lines N] [--source SRC]
#   stop      <name>
#   cleanup   <name> [--force]
#   reconcile
#   harvest   (idle-finisher adoption: report files + idle+branch evidence)
#
# Contract notes (tickets #735 #737 acceptance):
# - Only the map chief creates/schedules workers. spawn/prompt/stop/cleanup/
#   reconcile refuse leaf roles: when WAYFINDER_ROLE is ticket, maintenance,
#   bug-scout, or helper the call fails with a "request help via the chief"
#   message instead of touching Herdr. status/wait/read stay observable to all.
# - Ticket-worker ownership (ticket #737): each ticket worker owns exactly one
#   assigned ticket. The worker prompt (built by the chief) carries role,
#   ticket, map context, and workspace/base identity; the worker must verify
#   the assign-first claim before editing (a lost race stops before durable
#   changes), implement only the assigned scope, and never claim a second
#   ticket, integrate to master, close its own ticket, create the successor
#   handoff, touch another worker's workspace, or spawn recursive agents.
#   Completion is a structured report (STATUS/ROLE/TICKET/WORKSPACE/BASE/
#   COMMIT/SUMMARY/FILES/VERIFICATION/RISKS/HELP REQUESTS) collected via
#   `read`; `blocked` names the exact decision/question without stalling
#   unrelated workers.
# - Helper mediation (ticket #737): a ticket worker may request bounded help
#   via HELP REQUESTS, but only the chief spawns helpers — as registered,
#   capacity-counted siblings with a parent association (--parent names the
#   existing parent worker, --scope records the non-overlapping bounded
#   scope). Helpers never nest (a helper cannot parent another helper).
#   Writable helpers receive their own isolated workspace like any worker.
# - Dispatch is asynchronous by default: spawn and prompt never pass --wait
#   (nor --until/--timeout) to `herdr agent prompt`. worker_wait is the only
#   blocking call and always carries an explicit check-in --timeout
#   (default 300000ms) — a timeout is a check-in interval, never a deadline.
# - Worker role/class is explicit: every registry row carries one of
#   ticket, maintenance, bug-scout, helper.
# - Concurrency is bounded: spawn refuses when running rows reach
#   WAYFINDER_MAX_WORKERS (default 1 — the sequential fallback). The
#   pre-spawn capacity/name checks are re-validated under the registry
#   lock on insert (ticket #746 reg_insert): a lost race refuses instead
#   of overwriting a concurrent spawn's row, and the just-started agent
#   is torn down so no live worker leaks.
#   Narrower role ceilings (maintenance, bug-scout, helper) and the
#   independent heavyweight-job bound live in sibling ticket #742's capacity
#   seam (wayfinder-capacity.sh), which chief dispatch lanes check before
#   spawning here.
# - Heavyweight work (full Gradle suites, load runs) coordinates through the
#   capacity seam's heavy-acquire/heavy-release: a refused acquire is
#   waiting-resource, never semantic blocked-input — the worker stays running
#   and retries with backoff. Heavy slots held by crashed workers are
#   released by the capacity seam's chief-only heavy-reconcile, never leaked.
# - Stable identity for crash recovery (tickets #735 #741): the Herdr agent
#   name is the registry key — chief-driven spawns name it deterministically
#   per map+ticket (wf-<map>-<ticket>) so a daemon/chief restart reconciles
#   the same name instead of spawning a duplicate (spawn refuses an
#   already-registered name; replacement spawns only after the previous row
#   is cleaned). reconcile adopts live Herdr state, re-adopts gone/stopped
#   rows whose agent is live again, and marks missing rows gone without
#   deleting salvageable rows (an unrecognizable listing fails closed instead
#   of mass-marking gone). Worker-reported done|blocked|failed never move
#   backwards on Herdr lag; an orphaned revision (agent gone mid-rework)
#   converges to failed with the workspace preserved. Every spawn records the
#   owning map and chief generation (columns 11-12, diagnostic identity —
#   nothing filters on them; duplication safety comes from the name key, not
#   the generation); unmanaged live agents are reported, never auto-adopted —
#   explicit adoption lives in wayfinder-recover.sh.
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
#   WAYFINDER_MAP            owning map number recorded on spawn (default: empty)
#   WAYFINDER_GENERATION     chief session/generation recorded on spawn (default: empty)
#
# Registry (TSV, runtime state under gitignored .wayfinder/):
#   name role ticket workspace pane status created updated note parent map generation
# Parent (column 10, ticket #737) names the parent worker for helper rows and
# is empty for all other roles. Older 9-column rows read as parent-empty.
# Map (column 11, ticket #741) records the owning Wayfinder map number so a
# restarted daemon/chief can reconstruct which map each worker belongs to
# without re-querying the tracker. Generation (column 12, ticket #741)
# records the chief session/generation that spawned the worker
# (WAYFINDER_GENERATION, e.g. a chief start timestamp; empty stays legal for
# the sequential fallback — duplication safety comes from the deterministic
# name key, generation is diagnostic identity for operators). Older rows
# without map/generation read as empty and are adopted on next reconcile.
# Status flow: spawning -> running -> done|blocked|failed -> ready-for-review
# (-> revision-requested: live rework, still stoppable; the worker's
#  re-report returns via done|blocked|failed -> ready-for-review for a fresh
#  disposition -> accepted-awaiting-integration -> integrated;
#  -> rejected|cancelled) in ticket #740's chief-owned review queue.
# reconcile adopts worker re-reports from revision-requested but never
# rewrites a waiting disposition (ready-for-review,
# accepted-awaiting-integration) or a terminal (integrated, rejected,
# cancelled): a missing pane never strands the workspace result. cleanup
# removes terminal rows (integrated|rejected|cancelled|stopped|gone) and
# then tears down the Herdr agent/pane best-effort (ticket #746: a
# cleaned row must not leave a live agent that reappears as `unmanaged`
# and blocks the next successor gate);
# unreviewed or salvageable rows need --force. stop refuses non-live review
# state (ready-for-review, accepted-awaiting-integration, integrated,
# rejected, cancelled) but stays available for live rework
# (revision-requested): stopping a reworking worker keeps its revision
# findings in the review log, not the overwritten row note.
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

COMMON="$SCRIPT_DIR/wayfinder-common.sh"
[ -f "$COMMON" ] || die "shared hardening seam missing: $COMMON (ticket #746)"
# shellcheck disable=SC1090
. "$COMMON"

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

# with_registry_lock / with_registry_unlock — serialize whole-file registry
# rewrites across local writers (spawn/prompt/stop/reconcile here, chief
# collect, review dispositions). Self-contained per call: never held across
# subprocess invocations. flock releases on process exit; without flock the
# single-chief assumption applies (cross-chief hardening is ticket #741).
with_registry_lock() {
    if command -v flock >/dev/null 2>&1; then
        mkdir -p "$(dirname "$REGISTRY")"
        exec {WAYFINDER_WORKER_REG_FD}>"$REGISTRY.lock"
        flock -w 60 "$WAYFINDER_WORKER_REG_FD" \
            || die "cannot lock registry: $REGISTRY.lock"
    else
        wf_warn_no_flock "worker registry rewrite"
    fi
}

with_registry_unlock() {
    # Value-based close: `exec {VAR}>&-` misbehaves on some bash builds
    # (observed: stderr closed as a side effect), so close the numeric fd.
    if command -v flock >/dev/null 2>&1; then
        eval "exec $WAYFINDER_WORKER_REG_FD>&-" 2>/dev/null || true
    fi
}

# reg_upsert <name> <role> <ticket> <workspace> <pane> <status> <note> [parent [map [generation]]]
# The optional parent (column 10, helper rows) defaults to preserving the
# existing row's parent so status updates (stop/reconcile) never drop the
# helper association; new rows default to empty (non-helper). Map (column 11)
# and generation (column 12, ticket #741) likewise preserve the existing row
# when the caller passes empty, so reconcile/stop never drop recovery
# identity; new rows default to empty (pre-#741 rows).
reg_upsert() {
    local name="$1" role="$2" ticket="$3" workspace="$4" pane="$5" status="$6" note="$7"
    local parent="${8:-}" map="${9:-}" generation="${10:-}"
    local now existing created tmp
    now="$(date '+%F %T')"
    note="$(printf '%s' "$note" | tr '\t\n' '  ')"
    parent="$(printf '%s' "$parent" | tr '\t\n' '  ')"
    map="$(printf '%s' "$map" | tr '\t\n' '  ')"
    generation="$(printf '%s' "$generation" | tr '\t\n' '  ')"
    reg_init
    existing="$(reg_row "$name")"
    if [ -n "$existing" ]; then
        created="$(printf '%s' "$existing" | cut -f7)"
        [ -n "$created" ] || created="$now"
        if [ -z "$parent" ]; then
            parent="$(printf '%s' "$existing" | awk -F'\t' '{ print $10 }')"
        fi
        if [ -z "$map" ]; then
            map="$(printf '%s' "$existing" | awk -F'\t' '{ print $11 }')"
        fi
        if [ -z "$generation" ]; then
            generation="$(printf '%s' "$existing" | awk -F'\t' '{ print $12 }')"
        fi
    else
        created="$now"
    fi
    # Same-dir staging (ticket #741): the atomic rename publishes only
    # complete views to lock-free readers (quiescence, counts). A bare mktemp
    # in $TMPDIR risks a cross-filesystem copy+unlink that can tear the
    # recovery-critical registry on crash.
    tmp="$(mktemp -p "$(dirname "$REGISTRY")" reg.XXXXXX)"
    with_registry_lock
    awk -F'\t' -v name="$name" '$1 != name' "$REGISTRY" > "$tmp"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$name" "$role" "$ticket" "$workspace" "$pane" "$status" "$created" "$now" "$note" "$parent" "$map" "$generation" >> "$tmp"
    mv "$tmp" "$REGISTRY"
    with_registry_unlock
}

# reg_insert <name> <role> <ticket> <workspace> <pane> <status> <note> [parent [map [generation]]]
# Insert-only variant of reg_upsert for spawn paths (ticket #746): the
# duplicate-name and capacity guards that cmd_spawn checks before
# allocating Herdr resources are re-validated under the registry lock, so
# two concurrent spawns cannot both slip past the pre-checks and silently
# overwrite each other or exceed WAYFINDER_MAX_WORKERS. Refuses (prints to
# stderr, returns 1) instead of overwriting; the caller tears down the
# just-started agent so a lost race leaks no live worker.
reg_insert() {
    local name="$1" role="$2" ticket="$3" workspace="$4" pane="$5" status="$6" note="$7"
    local parent="${8:-}" map="${9:-}" generation="${10:-}"
    local now created running tmp
    now="$(date '+%F %T')"
    created="$now"
    note="$(printf '%s' "$note" | tr '\t\n' '  ')"
    parent="$(printf '%s' "$parent" | tr '\t\n' '  ')"
    map="$(printf '%s' "$map" | tr '\t\n' '  ')"
    generation="$(printf '%s' "$generation" | tr '\t\n' '  ')"
    reg_init
    with_registry_lock
    if [ -n "$(awk -F'\t' -v name="$name" '$1 == name { print; exit }' "$REGISTRY")" ]; then
        with_registry_unlock
        printf 'wayfinder-worker: spawn race: worker name already registered: %s (a concurrent spawn won — refusing instead of overwriting)\n' "$name" >&2
        return 1
    fi
    case "$status" in
        spawning|running)
            running="$(awk -F'\t' '$6 == "spawning" || $6 == "running" { n++ } END { print n + 0 }' "$REGISTRY")"
            if [ "$running" -ge "$MAX_WORKERS" ]; then
                with_registry_unlock
                printf 'wayfinder-worker: spawn race: at capacity (%s/%s running) — refusing %s (a concurrent spawn filled the last slot)\n' "$running" "$MAX_WORKERS" "$name" >&2
                return 1
            fi
            ;;
    esac
    # Same-dir staging (ticket #741): the atomic rename publishes only
    # complete views to lock-free readers (quiescence, counts).
    tmp="$(mktemp -p "$(dirname "$REGISTRY")" reg.XXXXXX)"
    awk -F'\t' -v name="$name" '$1 != name' "$REGISTRY" > "$tmp"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$name" "$role" "$ticket" "$workspace" "$pane" "$status" "$created" "$now" "$note" "$parent" "$map" "$generation" >> "$tmp"
    mv "$tmp" "$REGISTRY"
    with_registry_unlock
}

# teardown_agent <name> <pane> — best-effort Herdr release after a safe
# terminal disposition or a refused spawn (ticket #746). A cleaned row
# must not leave a live agent that reappears as `unmanaged` and blocks
# the next successor gate. The delete/kill verbs are best-effort: an
# unknown verb or an already-gone agent warns on stderr and never fails
# the caller (cleanup of a crashed worker whose agent is already gone is
# the normal case, not an error). The warning names the exact manual
# equivalent so an unrecognized runtime stays actionable.
teardown_agent() {
    local name="${1:-}" pane="${2:-}" out
    [ -n "$name" ] || return 0
    command -v "$HERDR_BIN" >/dev/null 2>&1 || {
        printf 'wayfinder-worker: warning: herdr binary not found (%s) — agent %s left for the operator (herdr agent delete %s)\n' "$HERDR_BIN" "$name" "$name" >&2
        return 0
    }
    if out="$("$HERDR_BIN" agent delete "$name" 2>&1)"; then
        printf 'wayfinder-worker: tore down agent %s\n' "$name" >&2
    else
        printf 'wayfinder-worker: warning: agent teardown for %s reported: %s (operator equivalent: herdr agent delete %s)\n' "$name" "$out" "$name" >&2
    fi
    if [ -n "$pane" ]; then
        if out="$("$HERDR_BIN" pane kill "$pane" 2>&1)"; then
            printf 'wayfinder-worker: tore down pane %s\n' "$pane" >&2
        else
            printf 'wayfinder-worker: warning: pane teardown for %s reported: %s (operator equivalent: herdr pane kill %s)\n' "$pane" "$out" "$pane" >&2
        fi
    fi
    return 0
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
    local prompt_text="" prompt_file="" parent="" scope="" map="" generation=""
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
            --parent) parent="${2:-}"; shift 2 ;;
            --scope) scope="${2:-}"; shift 2 ;;
            --map) map="${2:-}"; shift 2 ;;
            --generation) generation="${2:-}"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "spawn: unknown flag $1" ;;
        esac
    done
    valid_role "$role" || usage_err "spawn: --role must be one of: $VALID_ROLES"
    [[ "$ticket" =~ ^[0-9]+$ ]] || usage_err "spawn: --ticket must be a numeric issue number"
    [ -n "$map" ] || map="${WAYFINDER_MAP:-}"
    [ -n "$generation" ] || generation="${WAYFINDER_GENERATION:-}"
    case "$map" in
        "") ;;
        *[!0-9]*) usage_err "spawn: --map must be a numeric map issue number" ;;
    esac
    case "$generation" in
        ""|*[!a-zA-Z0-9_:@.-]*)
            [ -z "$generation" ] || usage_err "spawn: --generation must match [a-zA-Z0-9_:@.-]+"
            ;;
    esac
    [ -n "$workspace" ] || usage_err "spawn: --workspace is required (provider-owned dir; never created here)"
    case "$workspace" in
        *$'\t'*|*$'\n'*) die "spawn: workspace path must not contain tabs or newlines" ;;
    esac
    [ -d "$workspace" ] || die "spawn: workspace does not exist: $workspace (ticket #736 owns creation)"
    if [ "$role" = "helper" ]; then
        [ -n "$parent" ] || usage_err "spawn: --role helper requires --parent <parent-worker> (chief-mediated only)"
        [ -n "$scope" ] || usage_err "spawn: --role helper requires --scope <bounded non-overlapping scope>"
        # Shared forgery guard (ticket #746): the scope lands in the
        # registry note, which the review queue greps for result tokens.
        wf_refuse_forgery "$scope" \
            || die "spawn: --scope must not contain tabs, newlines, or '|' and must not contain structured note tokens (reviewed=/result=/integrated=/verified=)"
        valid_name "$parent" || usage_err "spawn: invalid parent worker name '$parent'"
        local parent_row parent_role parent_ticket parent_workspace
        parent_row="$(reg_row "$parent")"
        [ -n "$parent_row" ] || die "spawn: unknown parent worker '$parent' (helpers attach to a registered worker)"
        parent_role="$(printf '%s' "$parent_row" | cut -f2)"
        [ "$parent_role" != "helper" ] || die "spawn: parent '$parent' is itself a helper — helpers never nest (request via the chief)"
        parent_ticket="$(printf '%s' "$parent_row" | cut -f3)"
        [ "$ticket" = "$parent_ticket" ] || die "spawn: helper ticket #$ticket must match parent '$parent' ticket #$parent_ticket"
        parent_workspace="$(printf '%s' "$parent_row" | cut -f4)"
        [ "$workspace" != "$parent_workspace" ] || die "spawn: helper workspace must differ from parent '$parent' workspace (writable helpers need their own isolated workspace)"
        if [ -z "$map" ]; then
            map="$(printf '%s' "$parent_row" | cut -f11)"
        fi
        if [ -z "$name" ]; then
            local n=1
            name="${parent}-h${n}"
            while [ -n "$(reg_row "$name")" ]; do
                n=$((n + 1))
                name="${parent}-h${n}"
                [ "$n" -lt 1000 ] || die "spawn: cannot allocate a helper name under '$parent'"
            done
        fi
    else
        [ -z "$parent" ] || usage_err "spawn: --parent is helper-only (role $role takes no parent)"
        [ -z "$scope" ] || usage_err "spawn: --scope is helper-only (role $role takes no scope)"
    fi
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
            if [ "$role" = "helper" ]; then
                reg_insert "$name" "$role" "$ticket" "$workspace" "$pane" "running" "helper for $parent: $scope; initial prompt failed: $out" "$parent" "$map" "$generation" \
                    || { teardown_agent "$name" "$pane"; die "spawn: lost a concurrent insert race for $name after a failed initial prompt (worker not registered): $out"; }
            else
                reg_insert "$name" "$role" "$ticket" "$workspace" "$pane" "running" "started; initial prompt failed: $out" "$parent" "$map" "$generation" \
                    || { teardown_agent "$name" "$pane"; die "spawn: lost a concurrent insert race for $name after a failed initial prompt (worker not registered): $out"; }
            fi
            die "agent prompt failed for $name (worker kept as running): $out"
        fi
    fi
    if [ "$role" = "helper" ]; then
        reg_insert "$name" "$role" "$ticket" "$workspace" "$pane" "running" "helper for $parent: $scope (kind=$kind)" "$parent" "$map" "$generation" \
            || { teardown_agent "$name" "$pane"; die "spawn: worker name already registered or at capacity (a concurrent spawn won) — started agent torn down: $name"; }
    else
        reg_insert "$name" "$role" "$ticket" "$workspace" "$pane" "running" "spawned kind=$kind" "$parent" "$map" "$generation" \
            || { teardown_agent "$name" "$pane"; die "spawn: worker name already registered or at capacity (a concurrent spawn won) — started agent torn down: $name"; }
    fi
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
    state="$(herdr_field "$out" '.result.agent.agent_status // .result.agent.status // .result.status // .status // empty')"
    printf 'worker=%s registry=%s herdr=%s\n' "$name" "$(printf '%s' "$row" | cut -f6)" "${state:-unknown}"
}

# cmd_harvest — adopt finished workers Herdr reports as merely idle (map #755).
#
# opencode leaf workers end their turn `idle` with the completion report in
# their transcript; Herdr almost never reports them `done`, so the chief's
# collect lane (done|blocked|failed -> ready-for-review) waits forever while
# execution slots stay occupied. Harvest converts workspace evidence into
# registry motion without parsing transcripts (alternate-screen scrollback is
# unreliable):
#
# - Signal A (strong): `$workspace/.wayfinder/report-<ticket>.md` carrying a
#   `STATUS: done|blocked|failed` line (plus `COMMIT:`) — written by the
#   worker per its ticket prompt. Adopted immediately.
# - Signal B (fallback): Herdr named status `idle` persisted across passes
#   (>=120s, tracked in harvest.tsv beside the registry) AND the workspace
#   sits on a `prototype/*` branch (never master) AND holds commits ahead of
#   its recorded base AND has a clean tree. The commit+clean gates carry the
#   safety (a mid-build worker has no commit or a dirty tree); the 120s timer
#   is only flicker margin. A worker idle mid-build with no commit is never
#   harvested; a worker that committed to master is never harvested (left for
#   the operator).
#
# Chief-only like reconcile; safe no-op when nothing qualifies.
cmd_harvest() {
    require_chief "worker_harvest"
    need_herdr
    reg_init
    local wsreg="$REPO/.wayfinder/workspaces.tsv"
    local state_file
    state_file="$(dirname "$REGISTRY")/harvest.tsv"
    [ -f "$state_file" ] || : > "$state_file"
    local now out live harvested=0 skipped=0
    now="$(date +%s)"
    out="$("$HERDR_BIN" agent list 2>&1)" \
        || die "agent list failed: $out"
    live="$(herdr_field "$out" '[.. | objects | select(has("name")) | "\(.name)\t\(.status // .agent_status // empty)" | select(test("\t.+"))] | unique | .[]' || true)"
    local line name role ticket ws pane status
    while IFS= read -r line; do
        name="$(wf_tsv_field "$line" 1)"
        role="$(wf_tsv_field "$line" 2)"
        ticket="$(wf_tsv_field "$line" 3)"
        ws="$(wf_tsv_field "$line" 4)"
        pane="$(wf_tsv_field "$line" 5)"
        status="$(wf_tsv_field "$line" 6)"
        [ -n "$name" ] || continue
        case "$status" in spawning|running) ;; *) continue ;; esac
        report="$ws/.wayfinder/report-$ticket.md"
        if [ -f "$report" ]; then
            st="$(grep -a -m1 -i '^STATUS:' "$report" 2>/dev/null | sed 's/^[^:]*:[[:space:]]*//' | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
            cm="$(grep -a -m1 -i '^COMMIT:' "$report" 2>/dev/null | sed 's/^[^:]*:[[:space:]]*//' | tr -d '[:space:]')"
            case "$st" in
                done|blocked|failed)
                    reg_upsert "$name" "$role" "$ticket" "$ws" "$pane" "$st" "harvested report file (commit ${cm:-none})"
                    printf 'harvested %s via report file -> %s\n' "$name" "$st"
                    harvested=$((harvested + 1))
                    continue
                    ;;
            esac
        fi
        herdr_status="$(printf '%s' "$live" | awk -F'\t' -v n="$name" '$1 == n { print $2; exit }')"
        case "$herdr_status" in
            idle|done) ;;
            *)
                if awk -F'\t' -v n="$name" '$1 == n { found = 1; exit } END { exit !found }' "$state_file" 2>/dev/null; then
                    awk -F'\t' -v n="$name" '$1 != n' "$state_file" > "$state_file.tmp" && mv "$state_file.tmp" "$state_file"
                fi
                continue
                ;;
        esac
        first="$(awk -F'\t' -v n="$name" '$1 == n { print $2; exit }' "$state_file")"
        case "$first" in
            ''|*[!0-9]*)
                awk -F'\t' -v n="$name" '$1 != n' "$state_file" > "$state_file.tmp" && mv "$state_file.tmp" "$state_file"
                printf '%s\t%s\n' "$name" "$now" >> "$state_file"
                continue
                ;;
        esac
        if [ $((now - first)) -lt 120 ]; then continue; fi
        if [ ! -d "$ws" ]; then skipped=$((skipped + 1)); continue; fi
        br="$(git -C "$ws" branch --show-current 2>/dev/null || true)"
        rowmap="$(wf_tsv_field "$line" 11)"
        if [ "$rowmap" = "755" ]; then
            case "$br" in
                prototype/*) ;;
                *) skipped=$((skipped + 1)); continue ;;
            esac
        else
            # Fix lanes (e.g. bug map #863): any branch except master —
            # review/integration stays chief-owned either way.
            case "$br" in ''|master) skipped=$((skipped + 1)); continue ;; esac
        fi
        if [ -n "$(git -C "$ws" status --porcelain 2>/dev/null | head -n 1)" ]; then skipped=$((skipped + 1)); continue; fi
        wid="wf-$ticket"
        base="$(awk -F'\t' -v w="$wid" '$1 == w { print $4; exit }' "$wsreg" 2>/dev/null)"
        if [ -z "$base" ]; then skipped=$((skipped + 1)); continue; fi
        ahead="$(git -C "$ws" rev-list --count "$base"..HEAD 2>/dev/null || printf '0')"
        case "$ahead" in ''|*[!0-9]*) ahead=0 ;; esac
        if [ "$ahead" -le 0 ]; then skipped=$((skipped + 1)); continue; fi
        headsha="$(git -C "$ws" rev-parse HEAD 2>/dev/null || printf 'none')"
        reg_upsert "$name" "$role" "$ticket" "$ws" "$pane" "done" "harvested: idle 120s+ with $ahead commit(s) on $br ($headsha)"
        printf 'harvested %s via idle+branch evidence -> done (%s %s)\n' "$name" "$br" "$headsha"
        harvested=$((harvested + 1))
    done < "$REGISTRY"
    if [ -s "$state_file" ]; then
        awk -F'\t' 'NR == FNR { seen[$1] = 1; next } $1 in seen' "$REGISTRY" "$state_file" > "$state_file.tmp" && mv "$state_file.tmp" "$state_file"
    fi
    printf 'harvest: %d harvested, %d skipped\n' "$harvested" "$skipped"
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
    case "$(printf '%s' "$row" | cut -f6)" in
        ready-for-review|accepted-awaiting-integration|integrated|rejected|cancelled)
            die "stop: worker $name is $(printf '%s' "$row" | cut -f6) — not a live worker; use review dispositions and cleanup instead"
            ;;
    esac
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
    local row status pane tmp
    row="$(reg_row "$name")" || true
    [ -n "$row" ] || die "cleanup: unknown worker '$name' (not in registry)"
    status="$(printf '%s' "$row" | cut -f6)"
    pane="$(printf '%s' "$row" | cut -f5)"
    case "$status" in
        spawning|running)
            [ "$force" -eq 1 ] || die "cleanup: worker $name is $status — refuse to orphan a live agent (stop it first or pass --force)"
            ;;
        done|blocked|failed|ready-for-review|revision-requested|accepted-awaiting-integration)
            # Unreviewed or salvageable result (ticket #740): an explicit
            # chief disposition (review accept/revision/reject, then
            # integrate) precedes cleanup — never drop reviewable work
            # silently. Terminal integrated/rejected/cancelled/stopped/gone
            # rows clean freely; --force overrides for operator recovery.
            [ "$force" -eq 1 ] || die "cleanup: worker $name is $status — unreviewed or salvageable result; record a review disposition first (pass --force to override)"
            ;;
        integrated|rejected|cancelled|stopped|gone) ;;
        *) die "cleanup: worker $name carries an unknown status '$status' — refusing (inspect the registry before removing)" ;;
    esac
    tmp="$(mktemp -p "$(dirname "$REGISTRY")" reg.XXXXXX)"
    with_registry_lock
    awk -F'\t' -v name="$name" '$1 != name' "$REGISTRY" > "$tmp"
    mv "$tmp" "$REGISTRY"
    with_registry_unlock
    # Pane/agent release (ticket #746): the row is gone, so the Herdr agent
    # must go too — otherwise it reappears as `unmanaged` and blocks the
    # next successor gate. Best-effort: a crashed worker whose agent is
    # already gone warns and still cleans up.
    teardown_agent "$name" "$pane"
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
    # Herdr 0.9.0 uses `name` (named workers only) + `agent_status`
    # (working/idle/blocked/done/unknown); unnamed sessions carry only
    # `agent` (kind) with no `name` and are not workers. The `|| true`
    # keeps the shape guard below reachable: under `set -e`, a failing
    # jq would abort with a bare code before any diagnostic.
    live="$(herdr_field "$out" '[.. | objects | select(has("name")) | "\(.name)\t\(.status // .agent_status // empty)" | select(test("\t.+"))] | unique | .[]' || true)"
    if [ -z "$live" ]; then
        # Fail closed (ticket #741): an empty parsed set is ambiguous between
        # "no live agents" and "unrecognized/broken listing". Only a listing
        # that positively carries agent arrays holding zero entries may mark
        # rows gone: stray "agents" strings and unparseable output refuse
        # instead of mass-marking live workers gone (a lying registry invites
        # cleanup of workers that may be alive). Entries without a `name`
        # are unnamed non-worker sessions (Herdr 0.9.0 chief panes) — when
        # the registry holds no running rows there is nothing to mark gone,
        # so succeed instead of blocking chief dispatch (map #755).
        agent_counts="$(printf '%s' "$out" | jq -r '[.. | objects | select(has("agents") and (.agents | type == "array")) | .agents] | {lists: length, entries: ([.[].[]?] | length)} | "\(.lists)/\(.entries)"' 2>/dev/null || printf 'INVALID')"
        case "$agent_counts" in
            INVALID) die "agent list returned unparseable output — refusing to mark workers gone" ;;
            0/*) die "agent list returned no recognizable agent list — refusing to mark workers gone" ;;
            */0) ;;
            *)
                if [ -f "$REGISTRY" ] && grep -qP '^(?:[^\t]*\t){5}(?:spawning|running)(?:\t|$)' "$REGISTRY" 2>/dev/null; then
                    die "agent list holds entries without usable identity ($agent_counts) — refusing to mark workers gone"
                fi
                ;;
        esac
    fi
    names="$(printf '%s' "$live" | cut -f1)"
    local kept=0 gone=0 updated=0 readopted=0
    # Empty-safe field split (ticket #746): `read` with a tab IFS
    # collapses empty fields (tab is IFS whitespace), shifting sparse
    # rows such as empty-pane adopted rows so the status check reads the
    # wrong column. Whole-line reads plus positional split keep every
    # band decision aligned.
    local line
    while IFS= read -r line; do
        name="$(wf_tsv_field "$line" 1)"
        _role="$(wf_tsv_field "$line" 2)"
        _ticket="$(wf_tsv_field "$line" 3)"
        _ws="$(wf_tsv_field "$line" 4)"
        _pane="$(wf_tsv_field "$line" 5)"
        status="$(wf_tsv_field "$line" 6)"
        [ -n "$name" ] || continue
        # Re-adopt (ticket #741): a row previously marked gone/stopped whose
        # Herdr agent is live again is adopted back instead of respawned — a
        # daemon/chief restart must adopt live workers, never duplicate them.
        # The workspace result is preserved; the note records the adoption.
        case "$status" in
            gone|stopped)
                herdr_status="$(printf '%s' "$live" | awk -F'\t' -v n="$name" '$1 == n { print $2; exit }')"
                case "$herdr_status" in
                    running|working|idle)
                        reg_upsert "$name" "$_role" "$_ticket" "$_ws" "$_pane" "running" "re-adopted live worker after restart (was $status)"
                        readopted=$((readopted + 1))
                        ;;
                    done|blocked|failed)
                        reg_upsert "$name" "$_role" "$_ticket" "$_ws" "$_pane" "$herdr_status" "re-adopted reported $herdr_status after restart (was $status)"
                        readopted=$((readopted + 1))
                        ;;
                    *) kept=$((kept + 1)) ;;
                esac
                continue
                ;;
        esac
        case "$status" in
            ready-for-review|accepted-awaiting-integration|integrated|rejected|cancelled)
                # Waiting disposition or terminal (ticket #740): Herdr
                # liveness never rewrites a disposition, and a missing pane
                # never strands the workspace result — cleanup follows the
                # terminal disposition explicitly.
                kept=$((kept + 1))
                continue
                ;;
        esac
        herdr_status="$(printf '%s' "$live" | awk -F'\t' -v n="$name" '$1 == n { print $2; exit }')"
        if [ "$status" = "revision-requested" ]; then
            # Live rework: adopt the worker's re-report so the revision
            # round-trips via done|blocked|failed back to the chief collect
            # for a fresh disposition. A vanished agent can never re-report,
            # so the orphaned rework converges to failed (workspace preserved
            # for review) instead of stranding every later disposition.
            case "$herdr_status" in
                done|blocked|failed)
                    reg_upsert "$name" "$_role" "$_ticket" "$_ws" "$_pane" "$herdr_status" "rework reported $herdr_status — back to chief review"
                    updated=$((updated + 1))
                    ;;
                "")
                    reg_upsert "$name" "$_role" "$_ticket" "$_ws" "$_pane" "failed" "rework orphaned: agent gone mid-revision — workspace preserved for review"
                    updated=$((updated + 1))
                    ;;
                *) kept=$((kept + 1)) ;;
            esac
            continue
        fi
        case "$status" in
            done|blocked|failed)
                # Worker-reported states never move backwards (ticket #741):
                # a lagging Herdr listing that still says running must not
                # erase a pending report — forward motion belongs to the chief
                # collect into ready-for-review.
                kept=$((kept + 1))
                continue
                ;;
        esac
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
                failed) herdr_status="failed" ;;
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
    # Live agents the registry never saw are reported, never auto-adopted here:
    # explicit adoption is the recover lane's job (wayfinder-recover.sh adopt),
    # so a restart can never silently attach an unrelated live agent to a
    # ticket. Advancement discipline is reconcile-first: the daemon gate
    # reconciles before reading the verdict and treats reported unmanaged
    # agents as blocking until an operator adopts or stops them.
    if [ -n "$names" ]; then
        while IFS= read -r name; do
            [ -n "$name" ] || continue
            if [ -z "$(reg_row "$name")" ]; then
                herdr_status="$(printf '%s' "$live" | awk -F'\t' -v n="$name" '$1 == n { print $2; exit }')"
                printf 'unmanaged: %s (%s)\n' "$name" "$herdr_status"
            fi
        done <<< "$names"
    fi
    if [ "$readopted" -gt 0 ]; then
        printf 'reconciled: %d kept, %d updated, %d gone, %d re-adopted\n' "$kept" "$updated" "$gone" "$readopted"
    else
        printf 'reconciled: %d kept, %d updated, %d gone\n' "$kept" "$updated" "$gone"
    fi
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
    harvest) cmd_harvest "$@" ;;
    -h|--help|help) usage ;;
    *) usage_err "unknown subcommand '$sub'" ;;
esac
