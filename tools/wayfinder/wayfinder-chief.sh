#!/usr/bin/env bash
# wayfinder-chief.sh — map chief scheduler for Wayfinder parallel supervision (map #697, tickets #735 #737).
#
# One orchestration layer per active map: the chief continuously schedules,
# reconciles, reviews-queues, and refills bounded worker capacity through the
# worker-control seam (wayfinder-worker.sh). Leaf workers never advance the
# map themselves.
#
# Ticket-worker contract (ticket #737): every ticket prompt carries role,
# ticket, map context, and workspace/base identity, plus the ownership
# boundary (exactly one ticket; assign-first claim with race fail-safe; no
# second claim, no self-integration, no handoff, no foreign workspaces, no
# recursive agents) and the structured completion report collected via
# `read` (STATUS/ROLE/TICKET/WORKSPACE/BASE/COMMIT/SUMMARY/FILES/
# VERIFICATION/RISKS/HELP REQUESTS). `blocked` names the exact decision
# without stalling unrelated workers; HELP REQUESTS is the only helper
# channel — only the chief spawns helpers (--spawn-helper) as registered,
# capacity-counted siblings with a parent association.
#
# Control loop (asynchronous — never spawn-N-then-wait-for-all-N):
#
#   reconcile workers -> collect completed/blocked -> queue ready results
#     -> query authoritative frontier -> fill free capacity
#     -> perform useful chief work -> repeat
#
# A completed worker slot is refillable while unrelated workers still run:
# collection uses non-blocking status, never a batch wait. worker_wait with a
# bounded check-in timeout is only for a sequential (MAX_WORKERS=1) chief that
# chooses to sit with its single worker; --once (the default for tests and
# the sequential fallback step) performs no blocking waits at all.
#
# Frontier authority stays with the tracker: native open blockers
# (issue_dependencies_summary.blocked_by), claim/assignment rules, and
# human-deferred states (needs-info / ready-for-human) gate every pickup, in
# priority order (priority:P0, then P1, then P2/untagged; lowest number breaks
# ties). The chief never assigns tickets — workers claim assign-first on start
# per docs/agents/issue-tracker.md — and never performs Git integration:
# collected done/blocked workers become ready-for-review rows for sibling
# ticket #740's review/integration queue. Workspace dirs are never created
# here (sibling ticket #736 owns the WorkspaceProvider): with --workspace-base
# a missing per-ticket dir skips with an awaiting-provider note; the default
# single --workspace (the repo checkout) preserves the sequential fallback.
#
# Usage: wayfinder-chief.sh --map N [--once] [--max-workers N] [--dry-run]
#          [--workspace DIR] [--workspace-base DIR] [--pane PANE]
#          [--registry PATH] [--interval SECS] [--base SHA]
#          [--spawn-helper PARENT --scope TEXT --helper-workspace DIR
#            [--helper-name NAME] [--helper-mode read-only|writable]]
#
# --base records the known canonical revision in ticket prompts (no git is
# run here; the worker verifies `git rev-parse HEAD` in its workspace and
# reports the actual BASE). --spawn-helper performs one chief-mediated
# helper spawn for an existing worker and exits (helpers never nest; the
# helper ticket inherits the parent ticket; writable helpers need their own
# isolated workspace with an explicit non-overlapping scope).
#
# Env: WAYFINDER_MAX_WORKERS (default 1), WAYFINDER_WORKER_KIND,
#   WAYFINDER_WORKER_ARGS, WAYFINDER_GH_BIN (default gh), WAYFINDER_GH_REPO
#   (default from origin), HERDR_BIN, WAYFINDER_ROLE=chief (set automatically).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKER="$SCRIPT_DIR/wayfinder-worker.sh"

MAP="" ONCE=0 DRY_RUN=""
MAX="${WAYFINDER_MAX_WORKERS:-1}"
WORKSPACE="$REPO" WORKSPACE_BASE="" PANE=""
REGISTRY="${WAYFINDER_WORKER_REGISTRY:-$REPO/.wayfinder/workers.tsv}"
INTERVAL=15
GH_BIN="${WAYFINDER_GH_BIN:-gh}"
GH_REPO="${WAYFINDER_GH_REPO:-}"
BASE_SHA=""
SPAWN_HELPER="" HELPER_SCOPE="" HELPER_WORKSPACE="" HELPER_NAME="" HELPER_MODE="read-only"

usage() { sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
log() { printf '%s [chief] %s\n' "$(date '+%F %T')" "$*"; }
die() { printf 'wayfinder-chief: %s\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
    case "$1" in
        --map) MAP="${2:-}"; shift 2 ;;
        --once) ONCE=1; shift ;;
        --max-workers) MAX="${2:-}"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        --workspace) WORKSPACE="${2:-}"; shift 2 ;;
        --workspace-base) WORKSPACE_BASE="${2:-}"; shift 2 ;;
        --pane) PANE="${2:-}"; shift 2 ;;
        --registry) REGISTRY="${2:-}"; shift 2 ;;
        --interval) INTERVAL="${2:-}"; shift 2 ;;
        --base) BASE_SHA="${2:-}"; shift 2 ;;
        --spawn-helper) SPAWN_HELPER="${2:-}"; shift 2 ;;
        --scope) HELPER_SCOPE="${2:-}"; shift 2 ;;
        --helper-workspace) HELPER_WORKSPACE="${2:-}"; shift 2 ;;
        --helper-name) HELPER_NAME="${2:-}"; shift 2 ;;
        --helper-mode) HELPER_MODE="${2:-}"; shift 2 ;;
        -h|--help) usage ;;
        *) die "unknown flag $1" ;;
    esac
done
[[ "$MAP" =~ ^[0-9]+$ ]] || { printf 'wayfinder-chief: --map N is required\n' >&2; exit 2; }
[[ "$MAX" =~ ^[0-9]+$ ]] && [ "$MAX" -ge 1 ] || die "--max-workers must be >= 1 (got '$MAX')"
if [ -z "$SPAWN_HELPER" ] && { [ -n "$HELPER_SCOPE" ] || [ -n "$HELPER_WORKSPACE" ] || [ -n "$HELPER_NAME" ]; }; then
    die "--scope/--helper-workspace/--helper-name require --spawn-helper <parent-worker>"
fi
export WAYFINDER_MAX_WORKERS="$MAX"
export WAYFINDER_WORKER_REGISTRY="$REGISTRY"
export WAYFINDER_ROLE=chief
[ -x "$WORKER" ] || die "worker seam not executable: $WORKER"
command -v "$GH_BIN" >/dev/null 2>&1 || die "gh binary not found: $GH_BIN (set WAYFINDER_GH_BIN)"
command -v jq >/dev/null 2>&1 || die "jq is required for frontier queries"
if [ -z "$GH_REPO" ]; then
    GH_REPO="$("$GH_BIN" repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null)" \
        || die "cannot determine repo slug (set WAYFINDER_GH_REPO)"
fi

registry_tickets() { # every ticket with a registry row, one per line.
    [ -f "$REGISTRY" ] || return 0
    awk -F'\t' 'NF >= 6 { print $3 }' "$REGISTRY"
}

running_count() {
    [ -f "$REGISTRY" ] || { printf '0'; return; }
    awk -F'\t' '$6 == "spawning" || $6 == "running" { n++ } END { print n + 0 }' "$REGISTRY"
}

# collect_results — mark done/blocked rows ready-for-review (sibling #740's
# queue). Frees the execution slot without touching Git or deleting rows.
collect_results() {
    [ -f "$REGISTRY" ] || return 0
    local changed=0
    local rows
    rows="$(awk -F'\t' '$6 == "done" || $6 == "blocked" { print $1 }' "$REGISTRY")"
    [ -n "$rows" ] || return 0
    local name row tmp
    while IFS= read -r name; do
        [ -n "$name" ] || continue
        row="$(awk -F'\t' -v n="$name" '$1 == n { print; exit }' "$REGISTRY")"
        tmp="$(mktemp)"
        awk -F'\t' -v n="$name" -v now="$(date '+%F %T')" 'BEGIN { OFS = "\t" } $1 == n { $6 = "ready-for-review"; $8 = now; $9 = $9 " | awaiting chief review (#740 queue)" } { print }' \
            "$REGISTRY" > "$tmp"
        mv "$tmp" "$REGISTRY"
        log "collected $name (was $(printf '%s' "$row" | cut -f6), ticket #$(printf '%s' "$row" | cut -f3)) -> ready-for-review"
        changed=$((changed + 1))
    done <<< "$rows"
    printf '%d' "$changed" > /dev/null
}

# query_frontier — ordered open/unblocked/unclaimed/non-deferred child numbers.
query_frontier() {
    local numbers n payload state blocked assignees labels rank
    numbers="$("$GH_BIN" api "repos/$GH_REPO/issues/$MAP/sub_issues" --paginate --jq '.[].number' 2>/dev/null)" || \
        die "sub-issues query failed for map #$MAP"
    [ -n "$numbers" ] || return 0
    local ranked
    ranked=""
    for n in $numbers; do
        payload="$("$GH_BIN" api "repos/$GH_REPO/issues/$n" \
            --jq '{state: .state, blocked: .issue_dependencies_summary.blocked_by, assignees: [.assignees[].login], labels: [.labels[].name]}' 2>/dev/null)" || \
            die "issue query failed for #$n"
        state="$(printf '%s' "$payload" | jq -r '.state')"
        blocked="$(printf '%s' "$payload" | jq -r '.blocked // 0')"
        assignees="$(printf '%s' "$payload" | jq -r '.assignees | length')"
        labels="$(printf '%s' "$payload" | jq -r '.labels | join(" ")')"
        [ "$state" = "open" ] || continue
        [ "${blocked:-0}" -eq 0 ] || continue
        [ "$assignees" -eq 0 ] || continue
        case " $labels " in
            *" needs-info "*|*" ready-for-human "*) continue ;;
        esac
        # A registry row in any status means the ticket is already handled
        # or in flight — never spawn a duplicate (retry prompts go through
        # worker_prompt for the existing row).
        if registry_tickets | grep -qx "$n"; then continue; fi
        case " $labels " in
            *" priority:P0 "*) rank=0 ;;
            *" priority:P1 "*) rank=1 ;;
            *) rank=2 ;;
        esac
        ranked="$(printf '%s\n%s %s' "$ranked" "$rank" "$n")"
    done
    printf '%s' "$ranked" | awk 'NF == 2' | sort -k1,1n -k2,2n | awk '{ print $2 }'
}

ticket_workspace() { # <ticket> — print dir or nothing when awaiting provider.
    local ticket="$1" dir
    if [ -n "$WORKSPACE_BASE" ]; then
        dir="$WORKSPACE_BASE/wf-$ticket"
        [ -d "$dir" ] || return 0
        printf '%s' "$dir"
    else
        [ -d "$WORKSPACE" ] || die "workspace does not exist: $WORKSPACE"
        printf '%s' "$WORKSPACE"
    fi
}

chief_idle_work() {
    # Hook for useful chief work between fills (future: scout supervision,
    # doc refresh). Today: a heartbeat line so supervision can see liveness.
    log "chief idle work: none pending (map #$MAP)"
}

ticket_prompt() { # <ticket> <workspace> — print the ticket-worker prompt.
    local ticket="$1" ws="$2" base_line="BASE: derive the workspace HEAD revision at start (full 40-char commit) and report it"
    [ -n "$BASE_SHA" ] && base_line="BASE: dispatched at canonical $BASE_SHA — verify the workspace HEAD revision (full 40-char commit) at start and report the actual value"
    cat <<EOF
ROLE: ticket worker under Wayfinder map #$MAP (map chief supervises; you do not).
TICKET: #$ticket — read its issue body and comments chronologically, plus map #$MAP context, before editing.
WORKSPACE: $ws — work only inside this isolated workspace; never modify another worker's workspace.
$base_line

OWNERSHIP (exactly one ticket):
- Implement only ticket #$ticket's scope. Do not absorb unrelated cleanup.
- First reserve the claim assign-first: gh issue edit $ticket --add-assignee @me
- Then verify the claim is still yours (gh issue view $ticket): if another worker already owns it, STOP before any durable change and report the race as blocked.
- You own this ticket until the chief releases you. Remain available for chief revision requests.

YOU MUST NOT:
- claim, start, or advance any other ticket;
- integrate to canonical master yourself, push past chief review, or close ticket #$ticket as done;
- create the Wayfinder successor handoff (the chief owns map advancement);
- modify another worker's workspace;
- spawn workers or agents of your own — request bounded help via HELP REQUESTS below; only the chief may spawn helpers.

VERIFICATION: run the narrowest checks for your change (never a broad sweep); report commands and results.

COMPLETION REPORT (via your terminal output — the chief collects it with herdr agent read; no result artifact unless the chief asks):
STATUS: done | blocked | failed
ROLE: ticket
TICKET: #$ticket
WORKSPACE: $ws
BASE: <full 40-char HEAD you verified in $ws>
COMMIT: <sha of your reviewable commit in $ws, or none>

SUMMARY:
<what changed and why>

FILES:
<paths touched>

VERIFICATION:
<commands run + results>

RISKS / REVIEW NOTES:
<conflicts, assumptions, follow-ups for the chief>

HELP REQUESTS:
<bounded help wanted, or none — the chief decides; helpers are chief-spawned registered siblings>

BLOCKED BEHAVIOR: if you need information, report STATUS blocked with the exact decision/question and enough context for the chief to resolve it. Other workers continue meanwhile; genuine user ambiguity follows the map's needs-info / ready-for-human path via the chief.
EOF
}

spawn_for_ticket() { # <ticket> <workspace>
    local ticket="$1" ws="$2" prompt_file name rc=0
    name="wf-${MAP}-${ticket}"
    prompt_file="$(mktemp)"
    ticket_prompt "$ticket" "$ws" > "$prompt_file"
    if [ -n "$PANE" ]; then
        "$WORKER" spawn --role ticket --ticket "$ticket" --workspace "$ws" --name "$name" --pane "$PANE" --prompt-file "$prompt_file" || rc=$?
    else
        "$WORKER" spawn --role ticket --ticket "$ticket" --workspace "$ws" --name "$name" --prompt-file "$prompt_file" || rc=$?
    fi
    rm -f "$prompt_file"
    return "$rc"
}

helper_prompt() { # <parent> <ticket> <scope> <workspace> <mode> — print helper prompt.
    local parent="$1" ticket="$2" scope="$3" ws="$4" mode="$5"
    cat <<EOF
ROLE: helper worker under Wayfinder map #$MAP, parent worker $parent (chief-spawned registered sibling; you are not an orchestrator).
PARENT TICKET: #$ticket — the parent owns the ticket; you own only the bounded scope below.
SCOPE: $scope
MODE: $mode (read-only default; writable work stays inside the non-overlapping scope above)
WORKSPACE: $ws — work only inside this isolated workspace; never modify the parent's or another worker's workspace.

OWNERSHIP:
- You do not own ticket #$ticket. Do not claim tickets, integrate to master, close tickets, create handoffs, or spawn further workers/agents (helpers never nest — send follow-up needs back through the parent/chief).
- Writable output must be a reviewable commit in $ws confined to SCOPE; the chief integrates via the parent.

COMPLETION REPORT (collected with herdr agent read):
STATUS: done | blocked | failed
ROLE: helper
PARENT: $parent
TICKET: #$ticket
WORKSPACE: $ws
BASE: <full 40-char HEAD you verified in $ws>
COMMIT: <sha or none>

SUMMARY:
...

FILES:
...

VERIFICATION:
...

RISKS / REVIEW NOTES:
...

HELP REQUESTS:
<none — helpers do not request further helpers>
EOF
}

spawn_helper_for() { # chief-mediated helper spawn; exits 0 on success.
    [ -n "$SPAWN_HELPER" ] || die "--spawn-helper requires a parent worker name"
    [ -n "$HELPER_SCOPE" ] || die "--spawn-helper requires --scope <bounded non-overlapping scope>"
    [ -n "$HELPER_WORKSPACE" ] || die "--spawn-helper requires --helper-workspace <isolated dir>"
    case "$HELPER_MODE" in
        read-only|writable) ;;
        *) die "--helper-mode must be read-only or writable (got '$HELPER_MODE')" ;;
    esac
    [[ "$SPAWN_HELPER" =~ ^[a-z][a-z0-9_-]{0,31}$ ]] || die "--spawn-helper: invalid parent worker name '$SPAWN_HELPER'"
    [ -f "$REGISTRY" ] || die "--spawn-helper: unknown parent '$SPAWN_HELPER' (no worker registry)"
    local parent_row parent_ticket
    parent_row="$(awk -F'\t' -v n="$SPAWN_HELPER" '$1 == n { print; exit }' "$REGISTRY")"
    [ -n "$parent_row" ] || die "--spawn-helper: unknown parent worker '$SPAWN_HELPER'"
    [ "$(printf '%s' "$parent_row" | cut -f2)" != "helper" ] \
        || die "--spawn-helper: parent '$SPAWN_HELPER' is itself a helper — helpers never nest"
    parent_ticket="$(printf '%s' "$parent_row" | cut -f3)"
    [[ "$parent_ticket" =~ ^[0-9]+$ ]] || die "--spawn-helper: parent '$SPAWN_HELPER' carries no ticket"
    [ -d "$HELPER_WORKSPACE" ] || die "--spawn-helper: workspace does not exist: $HELPER_WORKSPACE"
    local prompt_file="" args=() rc=0
    prompt_file="$(mktemp)"
    helper_prompt "$SPAWN_HELPER" "$parent_ticket" "$HELPER_SCOPE" "$HELPER_WORKSPACE" "$HELPER_MODE" > "$prompt_file"
    args=(spawn --role helper --ticket "$parent_ticket" --workspace "$HELPER_WORKSPACE" --parent "$SPAWN_HELPER" --scope "$HELPER_SCOPE (mode=$HELPER_MODE)" --prompt-file "$prompt_file")
    [ -z "$HELPER_NAME" ] || args+=(--name "$HELPER_NAME")
    [ -z "$PANE" ] || args+=(--pane "$PANE")
    if [ -n "$DRY_RUN" ]; then
        log "dry-run: would spawn helper for $SPAWN_HELPER (ticket #$parent_ticket, scope: $HELPER_SCOPE, workspace=$HELPER_WORKSPACE, mode=$HELPER_MODE)"
        rm -f "$prompt_file"
        return 0
    fi
    "$WORKER" "${args[@]}" || rc=$?
    rm -f "$prompt_file"
    return "$rc"
}

pass() {
    log "pass start (map #$MAP, max=$MAX)"
    if [ -z "$DRY_RUN" ]; then
        "$WORKER" reconcile
    else
        log "dry-run: skip worker reconcile"
    fi
    if [ -z "$DRY_RUN" ]; then
        collect_results
    fi
    local free frontier ticket ws spawned=0
    free=$((MAX - $(running_count)))
    if [ "$free" -le 0 ]; then
        log "at capacity ($(running_count)/$MAX running) — no fill this pass"
        chief_idle_work
        return 0
    fi
    frontier="$(query_frontier)"
    if [ -z "$frontier" ]; then
        log "empty frontier for map #$MAP — nothing to fill"
        chief_idle_work
        return 0
    fi
    while IFS= read -r ticket; do
        [ -n "$ticket" ] || continue
        [ "$spawned" -lt "$free" ] || break
        ws="$(ticket_workspace "$ticket")"
        if [ -z "$ws" ]; then
            log "ticket #$ticket awaits workspace provider (#736) — skip"
            continue
        fi
        if [ -n "$DRY_RUN" ]; then
            log "dry-run: would spawn ticket #$ticket (role=ticket, workspace=$ws)"
            spawned=$((spawned + 1))
            continue
        fi
        if spawn_for_ticket "$ticket" "$ws"; then
            spawned=$((spawned + 1))
        else
            log "spawn refused for ticket #$ticket — stop fill this pass"
            break
        fi
    done <<< "$frontier"
    log "pass end: spawned $spawned (free slots were $free)"
    chief_idle_work
}

if [ -n "$SPAWN_HELPER" ]; then
    spawn_helper_for || exit $?
    exit 0
fi

if [ "$ONCE" -eq 1 ]; then
    pass
    exit 0
fi
trap 'log "chief stopping"; exit 0' TERM INT
while :; do
    pass
    sleep "$INTERVAL"
done
