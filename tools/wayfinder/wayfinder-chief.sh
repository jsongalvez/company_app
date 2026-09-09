#!/usr/bin/env bash
# wayfinder-chief.sh — map chief scheduler for Wayfinder parallel supervision (map #697, tickets #735 #737 #738 #739 #740 #741 #742).
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
#   reconcile workers -> collect completed/blocked/failed -> queue ready results
#     -> refuse fill while unmanaged wf-* strays exist (ticket #746: a stray
#        may hold a frontier claim outside the registry — adopt or stop it
#        first, never spawn a duplicate owner around it)
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
# ties). Repair issues (body carries the wayfinder-ci-repair marker) are the
# maintenance lane's durable tasks and never enter the ticket fill — the chief
# dispatches them explicitly via --spawn-maintenance. The chief never assigns
# tickets — workers claim assign-first on start
# per docs/agents/issue-tracker.md — and never writes canonical state except
# through sibling ticket #740's serialized review/integration queue
# (wayfinder-review.sh --review/--integrate, the single-writer lane): the
# daemon never touches canonical state at all, and isolated-workspace
# workers never write outside their own workspace (the sequential fallback,
# where the workspace IS the repo checkout, integrates via the queue's
# already-present path instead of a fresh commit). Collected
# done/blocked/failed workers become ready-for-review rows for that queue. Workspace dirs are never created
# here (sibling ticket #736 owns the WorkspaceProvider): with --workspace-base
# a missing per-ticket dir skips with an awaiting-provider note; the default
# single --workspace (the repo checkout) preserves the sequential fallback.
#
# --base records the known canonical revision in ticket prompts (no git is
# run here; the worker verifies `git rev-parse HEAD` in its workspace and
# reports the actual BASE). --spawn-helper performs one chief-mediated
# helper spawn for an existing worker and exits (helpers never nest; the
# helper ticket inherits the parent ticket; writable helpers need their own
# isolated workspace with an explicit non-overlapping scope).
#
# Bug-scout lane (ticket #738): --spawn-scout performs one chief-mediated
# scout spawn for a bounded audit slice and exits. The scout prompt (built by
# wayfinder-scout.sh, the single source of truth) carries the read-only
# contract, slice scope, duplication check, ticket-quality template, and the
# disposable-workspace note. The worker row carries --role bug-scout with the
# map number as its supervision anchor (scouts are repo-scoped, not bound to
# one implementation ticket); slice linkage lives in scout state and the
# chief log. Slice progress is recorded in scout state on success
# (--base pre-validated before any spawn; a post-spawn record failure other
# than an already-active re-audit reports nonzero). Scout dispatch stays
# explicit — the normal pass below still fills just the ticket frontier — and
# the scout ceiling (WAYFINDER_MAX_BUG_SCOUTS, default 1) keeps long-lived
# audit from starving implementation (sibling ticket #742 owns the full
# role-aware capacity model).
#
# Maintenance lane (ticket #739): --spawn-maintenance performs one
# chief-mediated maintenance spawn for a red hosted-CI SHA and exits. The
# prompt (built by wayfinder-maintenance.sh, the single source of truth)
# carries the writable-but-isolated contract: exactly one repair task (the
# durable per-SHA repair issue minted by the hosted-CI watch — reused here,
# never a second invisible channel), assign-first repair claim with race
# fail-safe, no frontier pickup, no direct canonical writes, no
# self-integration, and the structured completion report with root cause. The
# worker row carries --role maintenance with the repair issue as its ticket.
# Dispatch stays explicit — the normal pass below still fills the ticket
# frontier (unrelated isolated implementation continues while the baseline is
# red); canonical integration stays gated until the repair lands (sibling
# ticket #740's queue). Maintenance capacity is bounded by
# WAYFINDER_MAX_MAINTENANCE_WORKERS (default 1, live rows only — no permanently
# occupied idle worker) inside the shared WAYFINDER_MAX_WORKERS bound; a
# repair issue that already holds a maintenance row is never double-dispatched
# (registry persists across chief restarts; explicit cleanup precedes any
# redispatch). Pending/unknown/green CI never dispatches: the lane requires an
# explicit red SHA and the watch mints repair issues only for red verdicts.
#
# Review/integration queue (ticket #740): every writable worker result
# receives an explicit chief disposition through wayfinder-review.sh — accept
# (records the reviewed result commit, awaits a safe integration window),
# revision (concrete findings back to the SAME worker in its SAME workspace),
# reject/cancel (explicit, never silent) — then single-writer `integrate`
# (serialized, conflict-safe, verified) before any tracker closure or
# workspace release. `done` stays distinct from accepted/integrated; gated
# integrations wait while isolated implementation continues. Lanes below:
# --queue (inspectable queue + gate state), --review (disposition),
# --integrate (single-writer apply). The chief pass only collects
# done/blocked/failed into ready-for-review and logs queue depth — it never
# auto-integrates, so the sequential fallback keeps working unchanged.
#
# Crash recovery (ticket #741): every spawn records the owning map and the
# chief generation (WAYFINDER_GENERATION) in the worker registry, so a
# restarted daemon/chief reconciles the same deterministic worker names
# instead of duplicating live workers. A recovered chief runs --recover first:
# worker + workspace-provider reconcile through their seams, then the
# quiescence verdict and orphan release guidance — before any new dispatch.
# Successor handoff generations must not advance while quiescence reports
# BLOCKED (see wayfinder-recover.sh quiescence).
#
# Role-aware capacity + observability (ticket #742): the hard global worker
# ceiling (WAYFINDER_MAX_WORKERS, default 1 — the sequential fallback) is
# enforced by the worker seam on every spawn; narrower ceilings for
# maintenance (WAYFINDER_MAX_MAINTENANCE_WORKERS, default 1), scouts
# (WAYFINDER_MAX_BUG_SCOUTS, default 1, 0 disables), and helpers
# (WAYFINDER_MAX_HELPERS, default 2, 0 disables) are enforced here via
# wayfinder-capacity.sh check before each mediated spawn. Heavyweight
# build/test concurrency is bounded independently (WAYFINDER_MAX_HEAVY_JOBS,
# default 2): workers coordinate slots through the capacity seam's
# heavy-acquire/heavy-release, and a refused acquire is waiting-resource —
# never semantic blocked-input. Deterministic priority under constraint:
# preserve running work, then maintenance repair, ticket frontier
# (priority:P0 > P1 > P2/untagged, lowest number first), helpers, scouts
# last. --status prints the compact operator view (active, blocked-input vs
# resource-waiting, review-pending, free capacity) without entering panes.
# Dispatch/collect/disposition milestones emit structured events best-effort
# (a failed emit never breaks scheduling; --dry-run emits nothing).
#
# Parallel rollout (ticket #743): parallel dispatch is gated behind the
# explicit WAYFINDER_PARALLEL=on switch (default off = sequential fallback:
# fill width clamped to 1 with a log line). Enabling parallel mode is a
# deliberate configuration change (set WAYFINDER_PARALLEL=on alongside
# WAYFINDER_MAX_WORKERS>1 in the chief/daemon environment, then restart the
# daemon so new sessions pick it up); single-shot lanes (--spawn-helper,
# --spawn-scout, --spawn-maintenance, --queue/--review/--integrate,
# --recover, --status) stay available in both modes as explicit bounded
# actions. Rollback is the same knob in reverse (unset or =off + restart):
# no registry/state surgery — worker rows keep their map/generation
# recovery identity and quiescence still gates handoffs. This script never
# restarts the running daemon itself.
#
# Usage: wayfinder-chief.sh --map N [--once] [--max-workers N] [--dry-run]
#          [--workspace DIR] [--workspace-base DIR] [--pane PANE]
#          [--registry PATH] [--interval SECS] [--base SHA]
#          [--spawn-helper PARENT --scope TEXT --helper-workspace DIR
#            [--helper-name NAME] [--helper-mode read-only|writable]]
#          [--spawn-scout SLICE --scout-workspace DIR [--scout-name NAME]]
#          [--spawn-maintenance SHA --maintenance-workspace DIR
#            --repair-issue N [--maintenance-name NAME] [--failing TEXT]]
#          [--queue [--all]]
#          [--review WORKER --disposition accept|revision|reject|cancel
#            [--finding TEXT] [--result-commit SHA]]
#          [--integrate WORKER [--allow-gated]]
#          [--recover]
#          [--status [--machine]]
#
# Env: WAYFINDER_PARALLEL (on|off, default off — the explicit parallel-mode
#   switch; off clamps fill width to the sequential fallback of 1),
#   WAYFINDER_MAX_WORKERS (default 1), WAYFINDER_MAX_MAINTENANCE_WORKERS
#   (default 1, live maintenance rows only), WAYFINDER_MAX_BUG_SCOUTS
#   (default 1, live scout rows only, 0 disables), WAYFINDER_MAX_HELPERS
#   (default 2, live helper rows only, 0 disables), WAYFINDER_MAX_HEAVY_JOBS
#   (default 2, heavyweight slots — reported, not enforced, here),
#   WAYFINDER_EVENTS_LOG (structured events file, default: beside the worker
#   registry), WAYFINDER_WORKER_KIND,
#   WAYFINDER_WORKER_ARGS, WAYFINDER_GH_BIN (default gh), WAYFINDER_GH_REPO
#   (default from origin), HERDR_BIN, WAYFINDER_GENERATION (recorded on spawn —
#   minted once per chief session when unset, ticket #746; explicit values
#   preserved), WAYFINDER_ROLE=chief (set automatically).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKER="$SCRIPT_DIR/wayfinder-worker.sh"
SCOUT="$SCRIPT_DIR/wayfinder-scout.sh"
MAINT="$SCRIPT_DIR/wayfinder-maintenance.sh"
REVIEW="$SCRIPT_DIR/wayfinder-review.sh"
RECOVER="$SCRIPT_DIR/wayfinder-recover.sh"
CAPACITY="$SCRIPT_DIR/wayfinder-capacity.sh"
COMMON="$SCRIPT_DIR/wayfinder-common.sh"

MAP="" ONCE=0 DRY_RUN=""
MAX="${WAYFINDER_MAX_WORKERS:-1}"
PARALLEL="${WAYFINDER_PARALLEL:-off}"
MAX_MAINT="${WAYFINDER_MAX_MAINTENANCE_WORKERS:-1}"
MAX_SCOUTS="${WAYFINDER_MAX_BUG_SCOUTS:-1}"
MAX_HELPERS="${WAYFINDER_MAX_HELPERS:-2}"
MAX_HEAVY="${WAYFINDER_MAX_HEAVY_JOBS:-2}"
WORKSPACE="$REPO" WORKSPACE_BASE="" PANE=""
REGISTRY="${WAYFINDER_WORKER_REGISTRY:-$REPO/.wayfinder/workers.tsv}"
INTERVAL=15
GH_BIN="${WAYFINDER_GH_BIN:-gh}"
GH_REPO="${WAYFINDER_GH_REPO:-}"
BASE_SHA=""
SPAWN_HELPER="" HELPER_SCOPE="" HELPER_WORKSPACE="" HELPER_NAME="" HELPER_MODE="read-only"
SPAWN_SCOUT="" SCOUT_WORKSPACE="" SCOUT_NAME=""
SPAWN_MAINT="" MAINT_WORKSPACE="" MAINT_NAME="" MAINT_REPAIR="" MAINT_FAILING=""
QUEUE="" QUEUE_ALL=""
REVIEW_WORKER="" REVIEW_DISP="" REVIEW_FINDING="" REVIEW_COMMIT=""
INTEGRATE_WORKER="" INTEGRATE_GATED=""
RECOVER_LANE=""
STATUS_LANE="" STATUS_MACHINE=""

usage() { sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
log() { printf '%s [chief] %s\n' "$(date '+%F %T')" "$*"; }
die() { printf 'wayfinder-chief: %s\n' "$*" >&2; exit 1; }

[ -f "$COMMON" ] || die "shared hardening seam missing: $COMMON (ticket #746)"
# shellcheck disable=SC1090
. "$COMMON"

# capacity_emit <event> [emit-flags...] — best-effort structured lifecycle
# event (ticket #742). Never breaks scheduling: a missing seam or a failed
# write is silent. --dry-run callers never reach here (dry-run emits nothing).
capacity_emit() {
    [ -x "$CAPACITY" ] || return 0
    WAYFINDER_WORKER_REGISTRY="$REGISTRY" \
    WAYFINDER_EVENTS_LOG="${WAYFINDER_EVENTS_LOG:-$(dirname "$REGISTRY")/events.log}" \
        "$CAPACITY" emit "$@" >/dev/null 2>&1 || true
}

# capacity_check <role> — refuse dispatch past the global or role ceiling.
capacity_check() {
    [ -x "$CAPACITY" ] || return 0
    WAYFINDER_WORKER_REGISTRY="$REGISTRY" \
    WAYFINDER_MAX_WORKERS="$MAX" \
    WAYFINDER_MAX_MAINTENANCE_WORKERS="$MAX_MAINT" \
    WAYFINDER_MAX_BUG_SCOUTS="$MAX_SCOUTS" \
    WAYFINDER_MAX_HELPERS="$MAX_HELPERS" \
    WAYFINDER_MAX_HEAVY_JOBS="$MAX_HEAVY" \
        "$CAPACITY" check --role "$1"
}

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
        --spawn-scout) SPAWN_SCOUT="${2:-}"; shift 2 ;;
        --scout-workspace) SCOUT_WORKSPACE="${2:-}"; shift 2 ;;
        --scout-name) SCOUT_NAME="${2:-}"; shift 2 ;;
        --spawn-maintenance) SPAWN_MAINT="${2:-}"; shift 2 ;;
        --maintenance-workspace) MAINT_WORKSPACE="${2:-}"; shift 2 ;;
        --maintenance-name) MAINT_NAME="${2:-}"; shift 2 ;;
        --repair-issue) MAINT_REPAIR="${2:-}"; shift 2 ;;
        --failing) MAINT_FAILING="${2:-}"; shift 2 ;;
        --queue) QUEUE=1; shift ;;
        --all) QUEUE_ALL=1; shift ;;
        --review) REVIEW_WORKER="${2:-}"; shift 2 ;;
        --disposition) REVIEW_DISP="${2:-}"; shift 2 ;;
        --finding) REVIEW_FINDING="${2:-}"; shift 2 ;;
        --result-commit) REVIEW_COMMIT="${2:-}"; shift 2 ;;
        --integrate) INTEGRATE_WORKER="${2:-}"; shift 2 ;;
        --allow-gated) INTEGRATE_GATED=1; shift ;;
        --recover) RECOVER_LANE=1; shift ;;
        --status) STATUS_LANE=1; shift ;;
        --machine) STATUS_MACHINE=1; shift ;;
        -h|--help) usage ;;
        *) die "unknown flag $1" ;;
    esac
done
[[ "$MAP" =~ ^[0-9]+$ ]] || { printf 'wayfinder-chief: --map N is required\n' >&2; exit 2; }
[[ "$MAX" =~ ^[0-9]+$ ]] && [ "$MAX" -ge 1 ] || die "--max-workers must be >= 1 (got '$MAX')"
case "$PARALLEL" in
    on|off) ;;
    *) die "WAYFINDER_PARALLEL must be on or off (got '$PARALLEL')" ;;
esac
if [ "$PARALLEL" != "on" ] && [ "$MAX" -gt 1 ]; then
    printf '%s [chief] sequential fallback (WAYFINDER_PARALLEL=%s): clamping --max-workers %s to 1 — set WAYFINDER_PARALLEL=on for parallel dispatch\n' "$(date '+%F %T')" "$PARALLEL" "$MAX"
    MAX=1
fi
[[ "$MAX_MAINT" =~ ^[0-9]+$ ]] && [ "$MAX_MAINT" -ge 1 ] || die "WAYFINDER_MAX_MAINTENANCE_WORKERS must be >= 1 (got '$MAX_MAINT')"
[[ "$MAX_SCOUTS" =~ ^[0-9]+$ ]] || die "WAYFINDER_MAX_BUG_SCOUTS must be >= 0 (got '$MAX_SCOUTS')"
[[ "$MAX_HELPERS" =~ ^[0-9]+$ ]] || die "WAYFINDER_MAX_HELPERS must be >= 0 (got '$MAX_HELPERS')"
[[ "$MAX_HEAVY" =~ ^[0-9]+$ ]] || die "WAYFINDER_MAX_HEAVY_JOBS must be >= 0 (got '$MAX_HEAVY')"
lanes=0
[ -n "$SPAWN_HELPER" ] && lanes=$((lanes + 1))
[ -n "$SPAWN_SCOUT" ] && lanes=$((lanes + 1))
[ -n "$SPAWN_MAINT" ] && lanes=$((lanes + 1))
[ -n "$QUEUE" ] && lanes=$((lanes + 1))
[ -n "$REVIEW_WORKER" ] && lanes=$((lanes + 1))
[ -n "$INTEGRATE_WORKER" ] && lanes=$((lanes + 1))
[ -n "$RECOVER_LANE" ] && lanes=$((lanes + 1))
[ -n "$STATUS_LANE" ] && lanes=$((lanes + 1))
if [ "$lanes" -gt 1 ]; then
    die "--spawn-helper, --spawn-scout, --spawn-maintenance, --queue, --review, --integrate, --recover, and --status are separate single-shot lanes (one per invocation)"
fi
if [ -n "$SPAWN_MAINT" ] && [ "$HELPER_MODE" != "read-only" ]; then
    die "--helper-mode does not apply to --spawn-maintenance (helpers only)"
fi
if [ -n "$SPAWN_SCOUT" ] && [ "$HELPER_MODE" != "read-only" ]; then
    die "--helper-mode does not apply to --spawn-scout (helpers only)"
fi
if [ -z "$SPAWN_HELPER" ] && [ -z "$SPAWN_SCOUT" ] && [ -z "$SPAWN_MAINT" ] && [ "$HELPER_MODE" != "read-only" ]; then
    die "--helper-mode requires --spawn-helper <parent-worker>"
fi
if [ -z "$SPAWN_HELPER" ] && { [ -n "$HELPER_SCOPE" ] || [ -n "$HELPER_WORKSPACE" ] || [ -n "$HELPER_NAME" ]; }; then
    die "--scope/--helper-workspace/--helper-name require --spawn-helper <parent-worker>"
fi
if [ -z "$SPAWN_SCOUT" ] && { [ -n "$SCOUT_WORKSPACE" ] || [ -n "$SCOUT_NAME" ]; }; then
    die "--scout-workspace/--scout-name require --spawn-scout <slice>"
fi
if [ -z "$SPAWN_MAINT" ] && { [ -n "$MAINT_WORKSPACE" ] || [ -n "$MAINT_NAME" ] || [ -n "$MAINT_REPAIR" ] || [ -n "$MAINT_FAILING" ]; }; then
    die "--maintenance-workspace/--maintenance-name/--repair-issue/--failing require --spawn-maintenance <sha>"
fi
if [ -z "$QUEUE" ] && [ -n "$QUEUE_ALL" ]; then
    die "--all requires --queue"
fi
if [ -z "$STATUS_LANE" ] && [ -n "$STATUS_MACHINE" ]; then
    die "--machine requires --status"
fi
if [ -z "$REVIEW_WORKER" ] && { [ -n "$REVIEW_DISP" ] || [ -n "$REVIEW_FINDING" ] || [ -n "$REVIEW_COMMIT" ]; }; then
    die "--disposition/--finding/--result-commit require --review <worker>"
fi
if [ -z "$INTEGRATE_WORKER" ] && [ -n "$INTEGRATE_GATED" ]; then
    die "--allow-gated requires --integrate <worker>"
fi
export WAYFINDER_MAX_WORKERS="$MAX"
export WAYFINDER_WORKER_REGISTRY="$REGISTRY"
export WAYFINDER_ROLE=chief
# Recovery identity (ticket #741, minting knob ticket #746): every worker
# spawn records the owning map (always) and the chief generation. Parallel
# deployments set WAYFINDER_GENERATION per chief session; when unset, the
# chief mints one stable stamp for its own lifetime so a restarted chief
# never reuses its predecessor's generation (empty stays legal for the
# sequential fallback — duplication safety comes from the deterministic
# worker name key, generation is diagnostic identity for operators). The
# worker seam defaults from these env values.
export WAYFINDER_MAP="$MAP"
if [ -z "${WAYFINDER_GENERATION:-}" ]; then
    WAYFINDER_GENERATION="chief-$(date '+%Y%m%d-%H%M%S')-$$"
    export WAYFINDER_GENERATION
    log "minted chief generation $WAYFINDER_GENERATION (recorded on every spawn; set WAYFINDER_GENERATION to override)"
fi
[ -x "$WORKER" ] || die "worker seam not executable: $WORKER"
[ -x "$SCOUT" ] || die "scout contract not executable: $SCOUT"
[ -x "$MAINT" ] || die "maintenance contract not executable: $MAINT"
[ -x "$REVIEW" ] || die "review queue not executable: $REVIEW"
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

# collect_results — mark done/blocked/failed rows ready-for-review (sibling
# #740's queue). Frees the execution slot without touching canonical state
# or deleting rows. failed rows keep their origin in the note so the chief
# can tell salvageable results from clean completions at disposition time.
collect_results() {
    [ -f "$REGISTRY" ] || return 0
    local changed=0
    local rows
    rows="$(awk -F'\t' '$6 == "done" || $6 == "blocked" || $6 == "failed" { print $1 }' "$REGISTRY")"
    [ -n "$rows" ] || return 0
    local name row tmp
    # Same-dir staging (ticket #741): the atomic rename publishes only
    # complete views (see the worker seam).
    mkdir -p "$(dirname "$REGISTRY")"
    if command -v flock >/dev/null 2>&1; then
        exec {WAYFINDER_CHIEF_REG_FD}>"$REGISTRY.lock"
        flock -w 60 "$WAYFINDER_CHIEF_REG_FD" \
            || { printf 'wayfinder-chief: cannot lock registry: %s.lock\n' "$REGISTRY" >&2; return 1; }
    else
        wf_warn_no_flock "chief collect rewrite"
    fi
    while IFS= read -r name; do
        [ -n "$name" ] || continue
        row="$(awk -F'\t' -v n="$name" '$1 == n { print; exit }' "$REGISTRY")"
        # Re-validate under the lock (ticket #746): the snapshot above
        # raced review dispositions and integrations — a row that moved
        # past done|blocked|failed (reviewed, integrated, cleaned) is
        # skipped, never clobbered back to ready-for-review.
        if [ -z "$row" ]; then
            log "collect: $name vanished since the snapshot (cleaned concurrently) — skipping"
            continue
        fi
        case "$(printf '%s' "$row" | cut -f6)" in
            done|blocked|failed) ;;
            *) log "collect: $name is now $(printf '%s' "$row" | cut -f6) — skipping (moved since the snapshot)"; continue ;;
        esac
        tmp="$(mktemp -p "$(dirname "$REGISTRY")" reg.XXXXXX)"
        awk -F'\t' -v n="$name" -v now="$(date '+%F %T')" 'BEGIN { OFS = "\t" } $1 == n { $6 = "ready-for-review"; $8 = now; $9 = ($9 == "" ? "awaiting chief review (#740 queue)" : $9 " | awaiting chief review (#740 queue)") } { print }' \
            "$REGISTRY" > "$tmp"
        mv "$tmp" "$REGISTRY"
        log "collected $name (was $(printf '%s' "$row" | cut -f6), ticket #$(printf '%s' "$row" | cut -f3)) -> ready-for-review"
        case "$(printf '%s' "$row" | cut -f6)" in
            blocked) capacity_emit worker-blocked-input --worker "$name" --role "$(printf '%s' "$row" | cut -f2)" --ticket "$(printf '%s' "$row" | cut -f3)" --workspace "$(printf '%s' "$row" | cut -f4)" --detail "collected to ready-for-review" ;;
            failed) capacity_emit worker-failed --worker "$name" --role "$(printf '%s' "$row" | cut -f2)" --ticket "$(printf '%s' "$row" | cut -f3)" --workspace "$(printf '%s' "$row" | cut -f4)" --detail "collected to ready-for-review" ;;
            *) capacity_emit worker-collected --worker "$name" --role "$(printf '%s' "$row" | cut -f2)" --ticket "$(printf '%s' "$row" | cut -f3)" --workspace "$(printf '%s' "$row" | cut -f4)" --detail "done-awaiting-review" ;;
        esac
        changed=$((changed + 1))
    done <<< "$rows"
    if command -v flock >/dev/null 2>&1; then
        # Value-based close (`exec {VAR}>&-` misbehaves on some bash builds).
        eval "exec $WAYFINDER_CHIEF_REG_FD>&-" 2>/dev/null || true
    fi
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
            --jq '{state: .state, blocked: .issue_dependencies_summary.blocked_by, assignees: [.assignees[].login], labels: [.labels[].name], repair: ((.body // "") | contains("wayfinder-ci-repair"))}' 2>/dev/null)" || \
            die "issue query failed for #$n"
        state="$(printf '%s' "$payload" | jq -r '.state')"
        blocked="$(printf '%s' "$payload" | jq -r '.blocked // 0')"
        assignees="$(printf '%s' "$payload" | jq -r '.assignees | length')"
        labels="$(printf '%s' "$payload" | jq -r '.labels | join(" ")')"
        repair="$(printf '%s' "$payload" | jq -r '.repair')"
        [ "$state" = "open" ] || continue
        [ "${blocked:-0}" -eq 0 ] || continue
        [ "$assignees" -eq 0 ] || continue
        case " $labels " in
            *" needs-info "*|*" ready-for-human "*) continue ;;
        esac
        # Repair issues are the maintenance lane's durable tasks (ticket
        # #739; marker literal mirrors REPAIR_MARKER in wayfinder-loop.sh),
        # not ordinary frontier tickets: the chief dispatches them explicitly
        # via --spawn-maintenance, never through the ticket fill.
        [ "$repair" = "true" ] && continue
        # A registry row in any status means the ticket is already handled
        # or in flight — never spawn a duplicate (retry prompts go through
        # worker_prompt for the existing row). Full-input grep (no -q): an
        # early-exiting grep would SIGPIPE the producer mid-list under
        # pipefail and flake the gate into duplicate spawns.
        if registry_tickets | grep -x "$n" >/dev/null; then continue; fi
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
    # Hook for useful chief work between fills (future: automatic scout
    # supervision via --spawn-scout, doc refresh). Today: a heartbeat line
    # so supervision can see liveness.
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
    [ "$rc" -eq 0 ] || return "$rc"
    [ -z "$DRY_RUN" ] && capacity_emit worker-dispatched --worker "$name" --role ticket --ticket "$ticket" --workspace "$ws" --detail "map #$MAP fill"
    return 0
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
    # Shared forgery guard (ticket #746): the scope lands in the worker
    # registry note via the worker seam.
    wf_refuse_forgery "$HELPER_SCOPE" \
        || die "--spawn-helper: --scope must not contain tabs, newlines, or '|' and must not contain structured note tokens (reviewed=/result=/integrated=/verified=)"
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
    # Narrow helper ceiling (ticket #742): helpers count toward the global
    # bound (worker seam) and additionally cap here so helper bursts cannot
    # starve ticket implementation. Check-then-act assumes the single-chief
    # model shared with the ticket fill path.
    capacity_check helper || exit $?
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
    [ "$rc" -eq 0 ] || return "$rc"
    if [ -z "$DRY_RUN" ]; then
        local helper_name="$HELPER_NAME"
        if [ -z "$helper_name" ]; then
            helper_name="$(awk -F'\t' -v p="$SPAWN_HELPER" '$2 == "helper" && $10 == p { n = $1 } END { print n }' "$REGISTRY")"
        fi
        capacity_emit helper-dispatched --worker "${helper_name:-unknown}" --role helper --ticket "$parent_ticket" --workspace "$HELPER_WORKSPACE" --detail "parent $SPAWN_HELPER scope: $HELPER_SCOPE"
    fi
    return 0
}

scout_name_taken() { # <name> — true when the worker registry holds the name.
    [ -f "$REGISTRY" ] || return 1
    awk -F'\t' -v n="$1" '$1 == n { found=1; exit } END { exit !found }' "$REGISTRY"
}

spawn_scout_for() { # chief-mediated bug-scout spawn for one slice; exits 0 on success.
    [ -n "$SPAWN_SCOUT" ] || die "--spawn-scout requires a slice (see wayfinder-scout.sh slices)"
    # Full-input grep (no -q): an early-exiting grep would SIGPIPE the
    # producer mid-list under pipefail and flake slice validation.
    "$SCOUT" slices | grep -xF "$SPAWN_SCOUT" >/dev/null \
        || die "--spawn-scout: unknown slice '$SPAWN_SCOUT' (see wayfinder-scout.sh slices)"
    [ -n "$SCOUT_WORKSPACE" ] || die "--spawn-scout requires --scout-workspace <read-only dir>"
    [ -d "$SCOUT_WORKSPACE" ] || die "--spawn-scout: workspace does not exist: $SCOUT_WORKSPACE"
    case "$SCOUT_WORKSPACE" in
        *$'\t'*|*$'\n'*) die "--spawn-scout: workspace path must not contain tabs or newlines" ;;
    esac
    # Pre-validate the base before spending capacity: an unresolvable --base
    # must refuse here, not orphan a spawned worker (also runs in dry-run —
    # resolve-base is read-only).
    if [ -n "$BASE_SHA" ]; then
        "$SCOUT" resolve-base --base "$BASE_SHA" >/dev/null \
            || die "--spawn-scout: unknown base revision '$BASE_SHA'"
    fi
    # Narrow scout ceiling (ticket #742): long-lived audit cannot starve
    # implementation. Single-chief check-then-act like the helper lane.
    capacity_check bug-scout || exit $?
    local name="$SCOUT_NAME" prompt_file="" args=() rc=0 n start_err start_rc=0
    if [ -z "$name" ]; then
        name="wf-${MAP}-scout" n=1
        while scout_name_taken "$name"; do
            n=$((n + 1))
            name="wf-${MAP}-scout-${n}"
            [ "$n" -lt 1000 ] || die "--spawn-scout: cannot allocate a free scout name under 'wf-${MAP}-scout'"
        done
    fi
    [[ "$name" =~ ^[a-z][a-z0-9_-]{0,31}$ ]] || die "--spawn-scout: invalid scout name '$name'"
    prompt_file="$(mktemp)"
    if [ -n "$BASE_SHA" ]; then
        "$SCOUT" prompt "$SPAWN_SCOUT" --map "$MAP" --workspace "$SCOUT_WORKSPACE" --base "$BASE_SHA" > "$prompt_file" \
            || { rm -f "$prompt_file"; die "--spawn-scout: cannot build the scout prompt for '$SPAWN_SCOUT'"; }
    else
        "$SCOUT" prompt "$SPAWN_SCOUT" --map "$MAP" --workspace "$SCOUT_WORKSPACE" > "$prompt_file" \
            || { rm -f "$prompt_file"; die "--spawn-scout: cannot build the scout prompt for '$SPAWN_SCOUT'"; }
    fi
    args=(spawn --role bug-scout --ticket "$MAP" --workspace "$SCOUT_WORKSPACE" --name "$name" --prompt-file "$prompt_file")
    [ -z "$PANE" ] || args+=(--pane "$PANE")
    if [ -n "$DRY_RUN" ]; then
        log "dry-run: would spawn scout for slice $SPAWN_SCOUT (role=bug-scout, workspace=$SCOUT_WORKSPACE, name=$name)"
        rm -f "$prompt_file"
        return 0
    fi
    "$WORKER" "${args[@]}" || rc=$?
    rm -f "$prompt_file"
    [ "$rc" -eq 0 ] || return "$rc"
    [ -z "$DRY_RUN" ] && capacity_emit scout-dispatched --worker "$name" --role bug-scout --ticket "$MAP" --workspace "$SCOUT_WORKSPACE" --detail "slice $SPAWN_SCOUT"
    log "spawned scout $name for slice $SPAWN_SCOUT (role=bug-scout, ticket #$MAP, workspace=$SCOUT_WORKSPACE)"
    # Record slice progress only after the worker exists: a refused spawn
    # (capacity, duplicate name) leaves scout state untouched. An already
    # active slice is a deliberate re-audit, not a failure; any other
    # record failure is reported nonzero so the divergence stays visible
    # (the worker still runs, but complete/note-finding would refuse a
    # never-started slice).
    start_err="$(mktemp)"
    if [ -n "$BASE_SHA" ]; then
        "$SCOUT" start "$SPAWN_SCOUT" --base "$BASE_SHA" 2>"$start_err" || start_rc=$?
    else
        "$SCOUT" start "$SPAWN_SCOUT" 2>"$start_err" || start_rc=$?
    fi
    if [ "$start_rc" -eq 0 ]; then
        rm -f "$start_err"
        return 0
    fi
    if [ "$(scout_slice_status)" = "active" ]; then
        log "scout $name spawned; slice $SPAWN_SCOUT already active (re-audit)"
        rm -f "$start_err"
        return 0
    fi
    log "warning: scout $name spawned but slice-state start failed: $(cat "$start_err")"
    rm -f "$start_err"
    return 1
}

scout_slice_status() { # print the scout-state status for the spawn slice, or nothing.
    "$SCOUT" status 2>/dev/null | awk -F'\t' -v s="$SPAWN_SCOUT" '$1 == s { print $2; exit }'
}

maintenance_name_taken() { # <name> — true when the worker registry holds the name.
    [ -f "$REGISTRY" ] || return 1
    awk -F'\t' -v n="$1" '$1 == n { found=1; exit } END { exit !found }' "$REGISTRY"
}

maintenance_ticket_row() { # <repair-issue> — print the maintenance row for the repair task, or nothing.
    [ -f "$REGISTRY" ] || return 0
    awk -F'\t' -v t="$1" '$2 == "maintenance" && $3 == t { print; exit }' "$REGISTRY"
}

spawn_maintenance_for() { # chief-mediated maintenance spawn for one red SHA; exits 0 on success.
    [[ "$SPAWN_MAINT" =~ ^[0-9a-f]{40}$ ]] \
        || die "--spawn-maintenance: SHA must be a full 40-char lowercase commit (got '$SPAWN_MAINT')"
    [[ "$MAINT_REPAIR" =~ ^[0-9]+$ ]] \
        || die "--spawn-maintenance requires --repair-issue <durable repair task> (reuse the hosted-CI repair issue)"
    [ -n "$MAINT_WORKSPACE" ] || die "--spawn-maintenance requires --maintenance-workspace <isolated dir>"
    # Shared forgery guard (ticket #746): --failing text travels into the
    # maintenance prompt; refuse separators and structured note tokens.
    wf_refuse_forgery "${MAINT_FAILING:-}" \
        || die "--spawn-maintenance: --failing text must not contain tabs, newlines, or '|' and must not contain structured note tokens (reviewed=/result=/integrated=/verified=)"
    [ -d "$MAINT_WORKSPACE" ] || die "--spawn-maintenance: workspace does not exist: $MAINT_WORKSPACE"
    case "$MAINT_WORKSPACE" in
        *$'\t'*|*$'\n'*) die "--spawn-maintenance: workspace path must not contain tabs or newlines" ;;
    esac
    # Recovery guard first: a repair issue that already holds a maintenance
    # row is never double-dispatched (the registry persists across chief
    # restarts; explicit cleanup precedes any redispatch). The message names
    # the owner so the operator can reconcile instead of respawning.
    local existing existing_status
    existing="$(maintenance_ticket_row "$MAINT_REPAIR")"
    if [ -n "$existing" ]; then
        existing_status="$(printf '%s' "$existing" | cut -f6)"
        die "--spawn-maintenance: repair #$MAINT_REPAIR already holds maintenance worker '$(printf '%s' "$existing" | cut -f1)' ($existing_status) — reconcile or cleanup first, never duplicate"
    fi
    # Pre-validate revisions before spending capacity: an unresolvable SHA
    # must refuse here, not orphan a spawned worker (also runs in dry-run —
    # resolve-base is read-only). The failing SHA is mandatory; --base is
    # validated too when the operator passes one (scout-lane parity).
    "$MAINT" resolve-base --base "$SPAWN_MAINT" >/dev/null \
        || die "--spawn-maintenance: unknown base revision '$SPAWN_MAINT'"
    if [ -n "$BASE_SHA" ]; then
        "$MAINT" resolve-base --base "$BASE_SHA" >/dev/null \
            || die "--spawn-maintenance: unknown base revision '$BASE_SHA'"
    fi
    # Narrow maintenance bound (ticket #742, live rows only — no permanently
    # occupied idle worker). The shared global bound is still enforced by the
    # worker seam. Check-then-act assumes the single-chief model shared with
    # the ticket fill path (cf. WorkspaceProvider flock note); cross-chief
    # registry hardening belongs to sibling ticket #741's recovery work.
    capacity_check maintenance || exit $?
    local name="$MAINT_NAME" prompt_file="" args=() rc=0 n
    if [ -z "$name" ]; then
        name="wf-${MAP}-maintenance" n=1
        while maintenance_name_taken "$name"; do
            n=$((n + 1))
            name="wf-${MAP}-maintenance-${n}"
            [ "$n" -lt 1000 ] || die "--spawn-maintenance: cannot allocate a free maintenance name under 'wf-${MAP}-maintenance'"
        done
    fi
    [[ "$name" =~ ^[a-z][a-z0-9_-]{0,31}$ ]] || die "--spawn-maintenance: invalid maintenance name '$name'"
    prompt_file="$(mktemp)"
    if [ -n "$BASE_SHA" ]; then
        if [ -n "$MAINT_FAILING" ]; then
            "$MAINT" prompt "$SPAWN_MAINT" --repair-issue "$MAINT_REPAIR" --map "$MAP" --workspace "$MAINT_WORKSPACE" --base "$BASE_SHA" --failing "$MAINT_FAILING" > "$prompt_file" \
                || { rm -f "$prompt_file"; die "--spawn-maintenance: cannot build the maintenance prompt for '$SPAWN_MAINT'"; }
        else
            "$MAINT" prompt "$SPAWN_MAINT" --repair-issue "$MAINT_REPAIR" --map "$MAP" --workspace "$MAINT_WORKSPACE" --base "$BASE_SHA" > "$prompt_file" \
                || { rm -f "$prompt_file"; die "--spawn-maintenance: cannot build the maintenance prompt for '$SPAWN_MAINT'"; }
        fi
    else
        if [ -n "$MAINT_FAILING" ]; then
            "$MAINT" prompt "$SPAWN_MAINT" --repair-issue "$MAINT_REPAIR" --map "$MAP" --workspace "$MAINT_WORKSPACE" --failing "$MAINT_FAILING" > "$prompt_file" \
                || { rm -f "$prompt_file"; die "--spawn-maintenance: cannot build the maintenance prompt for '$SPAWN_MAINT'"; }
        else
            "$MAINT" prompt "$SPAWN_MAINT" --repair-issue "$MAINT_REPAIR" --map "$MAP" --workspace "$MAINT_WORKSPACE" > "$prompt_file" \
                || { rm -f "$prompt_file"; die "--spawn-maintenance: cannot build the maintenance prompt for '$SPAWN_MAINT'"; }
        fi
    fi
    args=(spawn --role maintenance --ticket "$MAINT_REPAIR" --workspace "$MAINT_WORKSPACE" --name "$name" --prompt-file "$prompt_file")
    [ -z "$PANE" ] || args+=(--pane "$PANE")
    if [ -n "$DRY_RUN" ]; then
        log "dry-run: would spawn maintenance for red $SPAWN_MAINT (repair #$MAINT_REPAIR, role=maintenance, workspace=$MAINT_WORKSPACE, name=$name)"
        rm -f "$prompt_file"
        return 0
    fi
    "$WORKER" "${args[@]}" || rc=$?
    rm -f "$prompt_file"
    [ "$rc" -eq 0 ] || return "$rc"
    [ -z "$DRY_RUN" ] && capacity_emit maintenance-dispatched --worker "$name" --role maintenance --ticket "$MAINT_REPAIR" --workspace "$MAINT_WORKSPACE" --detail "red $SPAWN_MAINT"
    log "spawned maintenance $name for red $SPAWN_MAINT (repair #$MAINT_REPAIR, role=maintenance, workspace=$MAINT_WORKSPACE)"
    log "canonical integration gated until repair #$MAINT_REPAIR lands; ticket workers may continue in isolated workspaces (#740 queue)"
}

# queue_depths — cheap registry counts for the #740 review queue (no tracker,
# no Herdr, no canonical reads): keeps an unbounded pile of stale accepted
# rows visible instead of silent. Runs in every pass, including dry-run. The
# gate reading reuses the queue's own gate-status so the pass log and
# `integrate` can never disagree (same predicate, same live-maintenance view).
queue_depths() {
    local ready accepted revision failed gate="ready" gate_out
    if [ ! -f "$REGISTRY" ]; then
        log "review queue: empty (no worker registry)"
        return 0
    fi
    ready="$(awk -F'\t' '$6 == "ready-for-review" { n++ } END { print n + 0 }' "$REGISTRY")"
    accepted="$(awk -F'\t' '$6 == "accepted-awaiting-integration" { n++ } END { print n + 0 }' "$REGISTRY")"
    revision="$(awk -F'\t' '$6 == "revision-requested" { n++ } END { print n + 0 }' "$REGISTRY")"
    failed="$(awk -F'\t' '$6 == "failed" { n++ } END { print n + 0 }' "$REGISTRY")"
    gate_out="$(WAYFINDER_WORKER_REGISTRY="$REGISTRY" "$REVIEW" gate-status 2>/dev/null || printf 'ready')"
    case "$gate_out" in
        gated:*) gate="GATED (${gate_out#gated: })" ;;
    esac
    log "review queue: $ready ready-for-review, $accepted accepted-awaiting-integration, $revision revision-requested, $failed failed (integration gate: $gate)"
}

queue_lane() { # inspectable review queue + gate state; single-shot, no fill.
    local args=(queue)
    [ -z "$QUEUE_ALL" ] || args+=(--all)
    "$REVIEW" "${args[@]}"
}

review_lane() { # explicit chief disposition; single-shot, no fill.
    [ -n "$REVIEW_DISP" ] || die "--review requires --disposition accept|revision|reject|cancel"
    local args=(review "$REVIEW_WORKER" --disposition "$REVIEW_DISP")
    [ -z "$REVIEW_FINDING" ] || args+=(--finding "$REVIEW_FINDING")
    [ -z "$REVIEW_COMMIT" ] || args+=(--result-commit "$REVIEW_COMMIT")
    "$REVIEW" "${args[@]}"
}

integrate_lane() { # single-writer integration; single-shot, no fill.
    local args=(integrate "$INTEGRATE_WORKER")
    [ -z "$INTEGRATE_GATED" ] || args+=(--allow-gated)
    "$REVIEW" "${args[@]}"
}

recover_lane() { # crash-restart recovery; single-shot, no fill.
    # A recovered chief reconciles existing workers BEFORE dispatching new
    # work (ticket #741): live workers survive the chief crash, salvageable
    # results stay queued for review, and the quiescence verdict tells the
    # operator whether a successor generation may advance. Never fills.
    [ -x "$RECOVER" ] || die "recovery lane not executable: $RECOVER"
    WAYFINDER_WORKER_REGISTRY="$REGISTRY" "$RECOVER" reconcile
    WAYFINDER_WORKER_REGISTRY="$REGISTRY" "$RECOVER" orphans || true
    [ -z "$DRY_RUN" ] && capacity_emit chief-recovered --detail "map #$MAP worker+provider reconcile before dispatch"
}

status_lane() { # compact operator view; single-shot, no fill.
    [ -x "$CAPACITY" ] || die "capacity seam not executable: $CAPACITY"
    local args=(status)
    [ -z "$STATUS_MACHINE" ] || args+=(--machine)
    WAYFINDER_WORKER_REGISTRY="$REGISTRY" \
    WAYFINDER_MAX_WORKERS="$MAX" \
    WAYFINDER_MAX_MAINTENANCE_WORKERS="$MAX_MAINT" \
    WAYFINDER_MAX_BUG_SCOUTS="$MAX_SCOUTS" \
    WAYFINDER_MAX_HELPERS="$MAX_HELPERS" \
    WAYFINDER_MAX_HEAVY_JOBS="$MAX_HEAVY" \
        "$CAPACITY" "${args[@]}"
}

pass() {
    log "pass start (map #$MAP, max=$MAX)"
    local reconcile_out=""
    if [ -z "$DRY_RUN" ]; then
        reconcile_out="$("$WORKER" reconcile 2>&1)" || { printf '%s\n' "$reconcile_out"; return 1; }
        printf '%s\n' "$reconcile_out"
    else
        log "dry-run: skip worker reconcile"
    fi
    if [ -z "$DRY_RUN" ]; then
        collect_results
    fi
    queue_depths
    # Unmanaged-stray gate (ticket #746): reconcile reports live agents
    # the registry never saw. A wf-* stray may hold a frontier ticket's
    # claim outside the registry — filling around it would spawn a
    # duplicate owner. Skip the fill (collection above still ran) until
    # an operator adopts or stops the stray; non-Wayfinder agents never
    # gate the fill. Mirrors the daemon's reconcile-then-gate discipline.
    local strays=""
    if [ -z "$DRY_RUN" ]; then
        strays="$(printf '%s\n' "$reconcile_out" | awk '/^unmanaged: wf-/ { print $2 }' | tr '\n' ' ')"
        if [ -n "$strays" ]; then
            log "unmanaged Wayfinder agent(s) present: $strays — adopt (recover adopt) or stop them before dispatch; skip fill this pass"
            chief_idle_work
            return 0
        fi
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
    if [ -z "$DRY_RUN" ] && [ "$spawned" -gt 0 ]; then
        capacity_emit slot-refilled --detail "map #$MAP spawned $spawned (free slots were $free)"
    fi
    chief_idle_work
}

if [ -n "$SPAWN_HELPER" ]; then
    spawn_helper_for || exit $?
    exit 0
fi

if [ -n "$SPAWN_SCOUT" ]; then
    spawn_scout_for || exit $?
    exit 0
fi

if [ -n "$SPAWN_MAINT" ]; then
    spawn_maintenance_for || exit $?
    exit 0
fi

if [ -n "$QUEUE" ]; then
    queue_lane || exit $?
    exit 0
fi

if [ -n "$REVIEW_WORKER" ]; then
    review_lane || exit $?
    exit 0
fi

if [ -n "$INTEGRATE_WORKER" ]; then
    integrate_lane || exit $?
    exit 0
fi

if [ -n "$RECOVER_LANE" ]; then
    recover_lane || exit $?
    exit 0
fi

if [ -n "$STATUS_LANE" ]; then
    status_lane || exit $?
    exit 0
fi

# Scheduling entry (ticket #742): single-shot lanes above never reach here,
# so this marks real chief runs (daemon start, --once steps) — never dry-run.
if [ "$ONCE" -eq 1 ]; then
    [ -z "$DRY_RUN" ] && capacity_emit chief-started --detail "map #$MAP max=$MAX once"
    pass
    exit 0
fi
[ -z "$DRY_RUN" ] && capacity_emit chief-started --detail "map #$MAP max=$MAX loop"
trap 'log "chief stopping"; exit 0' TERM INT
while :; do
    pass
    sleep "$INTERVAL"
done
