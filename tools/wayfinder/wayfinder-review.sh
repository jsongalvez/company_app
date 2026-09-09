#!/usr/bin/env bash
# wayfinder-review.sh — chief-owned review/integration queue for Wayfinder parallel workers (map #697, ticket #740).
#
# A worker reporting `done` never means its changes are accepted. Parallel
# workers may overlap semantically, start from different base SHAs, or
# produce individually-correct commits that conflict when combined. This
# script is the durable, inspectable queue where the map chief dispositions
# every writable worker result before anything reaches canonical state:
#
#   working (spawning|running)
#     -> done|blocked|failed (worker-reported, collected by the chief pass)
#     -> ready-for-review (done-awaiting-review; collected, awaiting disposition)
#     -> revision-requested (chief sent concrete findings back to the SAME
#        worker in its SAME workspace; the worker's re-report returns via
#        done -> ready-for-review for a fresh disposition — failed stays a
#        salvageable pending state, never a silent terminal)
#     -> accepted-awaiting-integration (chief accepted; integration may wait
#        for a safe window while isolated implementation continues)
#     -> integrated (single-writer cherry-pick onto canonical + verification)
#     -> rejected | cancelled (explicit terminals, never silent)
#
# `done` is distinct from accepted is distinct from integrated: only
# `integrate` writes canonical state, and tracker tickets close only after
# accepted integration plus the required verification (the chief closes them
# via gh afterwards — this script never touches the tracker).
#
# Subcommands:
#
#   queue [--all] [--role R]
#             list review rows as raw registry TSV (pending by default:
#             ready-for-review, revision-requested,
#             accepted-awaiting-integration, failed; --all adds terminal
#             integrated|rejected|cancelled). Human summary on stderr.
#   status <worker>
#             print one row plus its review-log tail.
#   review <worker> --disposition accept|revision|reject|cancel
#                    [--finding TEXT] [--result-commit SHA]
#             record an explicit chief disposition. revision returns to the
#             same worker/workspace via the worker seam (async prompt, no
#             --wait); the workspace is never recreated or moved.
#   integrate <worker> [--repo DIR] [--allow-gated]
#             single-writer integration of an accepted result into the
#             canonical checkout: serialized, conflict-safe, verified.
#   gate --reason TEXT
#             pause canonical integration (accepted work stays queued while
#             isolated implementation continues).
#   gate-clear
#             resume canonical integration.
#   gate-status
#             print gated/ready plus the reason.
#
# Review flow (per writable result): the chief collects the worker report via
# Herdr read, inspects the recorded result commit/diff and the reported
# verification, checks the change still satisfies the assigned ticket scope,
# then records exactly one disposition here. accepted results wait in
# accepted-awaiting-integration until `integrate` runs in a safe window.
#
# Revision flow: findings travel back to the same worker in its existing
# workspace (worker prompt, async). Context and partial work are preserved;
# the worker re-reports done and the chief collects it again. A revision
# request always carries concrete findings — never a blind restart.
#
# Conflict handling: integration conflicts are expected scheduler events, not
# daemon failures. A conflicting (or empty) cherry-pick is aborted, the
# canonical checkout is left clean, the row keeps
# accepted-awaiting-integration with a conflict note naming the new canonical
# base, and the log prints the exact `review --disposition revision`
# invocation that returns the work with the new base. Small mechanical
# conflicts are therefore still a chief decision, substantive ones go back to
# the originating worker, two workers never race direct canonical writes
# (flock-serialized single writer), and no result is ever silently discarded.
# After any rebase/reconciliation the integration verification re-runs before
# acceptance is recorded as integrated.
#
# Integration gating: `integrate` refuses while gated — a live maintenance
# worker (red-baseline repair in flight) or an explicit `gate` block — unless
# --allow-gated overrides. Gated rows stay queued and observable; ticket
# workers keep implementing in isolated workspaces meanwhile. An unbounded
# pile of stale accepted rows stays visible via `queue` counts (chief pass
# logs them every pass) instead of failing silently.
#
# Ticket closure semantics: STATUS done (or ready-for-review, or even
# accepted) never closes the GitHub issue. Only `integrated` — accepted AND
# applied to canonical AND verified — authorizes the chief to close the
# tracker work. The same applies to maintenance repairs that produce changes.
#
# Cleanup: workspaces/branches/panes holding unreviewed or salvageable
# results are never deleted by this script. `integrate` prints the now-safe
# cleanup instructions only after recording integrated; rejected/cancelled
# rows are terminal and cleanable; the worker seam refuses cleanup of
# review-pending rows without --force. Commits survive chief/daemon crashes
# in their isolated workspaces until reviewed.
#
# Contract notes (ticket #740 acceptance):
# - Only the map chief dispositions and integrates. review/integrate/gate/
#   gate-clear refuse leaf roles (WAYFINDER_ROLE=ticket|maintenance|
#   bug-scout|helper) with a chief pointer; queue/status/gate-status stay
#   observable to all so workers can see where their result stands.
# - No tracker writes here (no gh): closure stays a deliberate chief step
#   after integrated. No Herdr reads/writes except the revision prompt
#   through the worker seam (async — never --wait). Only `integrate`
#   mutates canonical state, and only through the serialized safe path.
# - Review state lives in the shared worker registry (status column plus
#   structured note tokens) so no second registry can drift; whole-file
#   rewrites take the shared registry lock (same `$REGISTRY.lock` convention
#   as the worker seam and the chief collect — single-host mitigation;
#   cross-chief registry hardening is sibling ticket #741). The per-worker
#   append-only log under .wayfinder/reviews/ keeps every disposition,
#   finding, and integration SHA inspectable across restarts.
# - bug-scout rows are never reviewable (read-only output is tickets);
#   helper rows integrate via their parent ticket's result, so direct helper
#   review is refused with a pointer at the parent worker.
# - Do not introduce OpenCode, Rift, or Lane dependencies to satisfy this ticket.
#
# Env:
#   WAYFINDER_WORKER_REGISTRY  worker registry file (default: <repo>/.wayfinder/workers.tsv)
#   WAYFINDER_REPO             canonical checkout (default: repo containing this script)
#   WAYFINDER_INTEGRATION_GATE gate file (default: <repo>/.wayfinder/integration-gate)
#   WAYFINDER_REVIEW_LOG_DIR   review-log dir (default: <repo>/.wayfinder/reviews)
#   WAYFINDER_VERIFY_CMD       optional shell command run in the canonical
#                              checkout after applying a result (must exit 0);
#                              unset means diff-check only (recorded as such).
#                              Operators should set a narrow targeted check
#                              (e.g. bash tools/quality/validate.sh with
#                              focused args) rather than relying on
#                              diff-check-only for consequential integrations.
#   WAYFINDER_ROLE             caller role; leaf roles cannot disposition
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DISCOVERED_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKER="$SCRIPT_DIR/wayfinder-worker.sh"

CANON_DEFAULT="${WAYFINDER_REPO:-$DISCOVERED_REPO}"
REGISTRY="${WAYFINDER_WORKER_REGISTRY:-$DISCOVERED_REPO/.wayfinder/workers.tsv}"
GATE_FILE="${WAYFINDER_INTEGRATION_GATE:-$DISCOVERED_REPO/.wayfinder/integration-gate}"
LOG_DIR="${WAYFINDER_REVIEW_LOG_DIR:-$DISCOVERED_REPO/.wayfinder/reviews}"
VERIFY_CMD="${WAYFINDER_VERIFY_CMD:-}"
CALLER_ROLE="${WAYFINDER_ROLE:-chief}"

LEAF_ROLES="ticket maintenance bug-scout helper"
PENDING_STATES="ready-for-review revision-requested accepted-awaiting-integration failed"
TERMINAL_STATES="integrated rejected cancelled"

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

die() { printf 'wayfinder-review: %s\n' "$*" >&2; exit 1; }
usage_err() { printf 'wayfinder-review: %s\n' "$*" >&2; exit 2; }
log() { printf '%s [review] %s\n' "$(date '+%F %T')" "$*"; }

require_chief() { # <op>
    case " $LEAF_ROLES " in
        *" $CALLER_ROLE "*) die "$1 requires the map chief (WAYFINDER_ROLE=$CALLER_ROLE is a leaf role — request help via the chief instead)" ;;
    esac
}

need_arg() { # <flag> <value>
    [ -n "${2:-}" ] || usage_err "$1 requires a value"
}

valid_name() { [[ "${1:-}" =~ ^[a-z][a-z0-9_-]{0,31}$ ]]; }
valid_sha() { [[ "${1:-}" =~ ^[0-9a-f]{40}$ ]]; }

reg_row() { # <name> — print the TSV row or nothing.
    [ -f "$REGISTRY" ] || return 0
    awk -F'\t' -v name="$1" '$1 == name { print; exit }' "$REGISTRY"
}

# with_registry_lock / with_registry_unlock — serialize whole-file registry
# rewrites across local writers (review dispositions here, worker seam
# upserts, chief collect). Self-contained per call: never held across
# subprocess invocations. flock releases on process exit, so a killed writer
# never wedges the queue; without flock the single-chief assumption applies.
with_registry_lock() {
    if command -v flock >/dev/null 2>&1; then
        mkdir -p "$(dirname "$REGISTRY")"
        exec {WAYFINDER_REVIEW_REG_FD}>"$REGISTRY.lock"
        flock -w 60 "$WAYFINDER_REVIEW_REG_FD" \
            || die "cannot lock registry: $REGISTRY.lock"
    fi
}

with_registry_unlock() {
    # Value-based close: `exec {VAR}>&-` misbehaves on some bash builds
    # (observed: stderr closed as a side effect), so close the numeric fd.
    if command -v flock >/dev/null 2>&1; then
        eval "exec $WAYFINDER_REVIEW_REG_FD>&-" 2>/dev/null || true
    fi
}

row_field() { # <row> <n> — print TSV field n.
    printf '%s' "$1" | cut -f"$2"
}

# reg_set <name> <status> <note-append> — move one row, appending to its note.
reg_set() {
    local name="$1" status="$2" append="$3" tmp
    append="$(printf '%s' "$append" | tr '\t\n' '  ')"
    with_registry_lock
    tmp="$(mktemp)"
    awk -F'\t' -v n="$name" -v s="$status" -v a="$append" -v now="$(date '+%F %T')" \
        'BEGIN { OFS = "\t" } $1 == n { $6 = s; $8 = now; $9 = ($9 == "" ? a : $9 " | " a) } { print }' \
        "$REGISTRY" > "$tmp"
    mv "$tmp" "$REGISTRY"
    with_registry_unlock
}

review_log() { # <name> <text> — append one audit line.
    local name="$1" text="$2"
    mkdir -p "$LOG_DIR"
    text="$(printf '%s' "$text" | tr '\t\n' '  ')"
    printf '%s %s\n' "$(date '+%F %T')" "$text" >> "$LOG_DIR/$name.log"
}

result_of() { # <row> — print the latest accepted result= SHA or nothing.
    # Anchored on the accept token and last-match: a re-accept after rework
    # appends a newer token, and finding/reason text cannot forge one (the
    # review/gate entry points refuse reviewed=/result=/integrated= and `|`).
    printf '%s' "$1" | grep -oE 'reviewed=accept result=[0-9a-f]{40}' | tail -1 | grep -oE '[0-9a-f]{40}'
}

# refuse_forgery <text> — reject note-token separators and structured-token
# forgery in operator finding/reason text (prompt-forgery guard, same class
# as the maintenance lane's --failing check).
refuse_forgery() { # <flag> <text>
    case "$1" in
        *$'\t'*|*$'\n'*|*'|'*) die "review: $2 must not contain tabs, newlines, or '|' (note-token guard)" ;;
    esac
    case "$1" in
        *reviewed=*|*result=*|*integrated=*|*verified=*) die "review: $2 must not contain structured note tokens (reviewed=/result=/integrated=/verified=)" ;;
    esac
}

is_pending() { # <status> — true for actionable review states.
    case " $PENDING_STATES " in *" $1 "*) return 0;; esac; return 1
}

is_terminal() { # <status> — true for terminal review states.
    case " $TERMINAL_STATES " in *" $1 "*) return 0;; esac; return 1
}

# gate_reason — print why integration is paused, or nothing when ready.
gate_reason() {
    if [ -f "$GATE_FILE" ]; then
        local first
        first="$(head -1 "$GATE_FILE" 2>/dev/null || true)"
        case "$first" in
            blocked*)
                printf '%s' "${first#blocked }"
                return 0
                ;;
            "")
                ;;
            *)
                printf 'stale gate file ignored (first line must start with "blocked"): %s\n' "$GATE_FILE" >&2
                ;;
        esac
    fi
    if [ -f "$REGISTRY" ]; then
        local live
        live="$(awk -F'\t' '$2 == "maintenance" && ($6 == "spawning" || $6 == "running") { print $1 }' "$REGISTRY" | tr '\n' ' ')"
        if [ -n "$live" ]; then
            printf 'live maintenance worker(s): %s(repair in flight; canonical integration gated)' "$live"
            return 0
        fi
    fi
    return 0
}

cmd_queue() {
    local show_all=0 role="" st row
    while [ $# -gt 0 ]; do
        case "$1" in
            --all) show_all=1; shift ;;
            --role) need_arg "$1" "${2:-}"; role="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "queue: unknown flag $1" ;;
        esac
    done
    case "$role" in
        ""|ticket|maintenance|bug-scout|helper) ;;
        *) usage_err "queue: --role must be ticket|maintenance|bug-scout|helper (got '$role')" ;;
    esac
    local pending=0 accepted=0 revision=0 failed=0 terminal=0
    [ -f "$REGISTRY" ] || { log "queue: empty (no worker registry)"; return 0; }
    while IFS= read -r row; do
        [ -n "$row" ] || continue
        st="$(row_field "$row" 6)"
        if [ -n "$role" ] && [ "$(row_field "$row" 2)" != "$role" ]; then continue; fi
        if is_pending "$st"; then
            printf '%s\n' "$row"
            case "$st" in
                ready-for-review) pending=$((pending + 1)) ;;
                accepted-awaiting-integration) accepted=$((accepted + 1)) ;;
                revision-requested) revision=$((revision + 1)) ;;
                failed) failed=$((failed + 1)) ;;
            esac
        elif [ "$show_all" -eq 1 ] && is_terminal "$st"; then
            printf '%s\n' "$row"
            terminal=$((terminal + 1))
        fi
    done < "$REGISTRY"
    local gate="ready" reason
    reason="$(gate_reason)"
    [ -z "$reason" ] || gate="GATED ($reason)"
    log "queue: $pending ready-for-review, $accepted accepted-awaiting-integration, $revision revision-requested, $failed failed${show_all:+", $terminal terminal shown"} (integration gate: $gate)" >&2
}

cmd_status() {
    [ $# -ge 1 ] || usage_err "status: worker name is required"
    local name="$1"
    valid_name "$name" || usage_err "status: invalid worker name '$name'"
    local row
    row="$(reg_row "$name")"
    [ -n "$row" ] || die "status: unknown worker '$name' (not in registry)"
    printf 'name=%s\nrole=%s\nticket=%s\nworkspace=%s\nstatus=%s\nupdated=%s\nnote=%s\nparent=%s\n' \
        "$(row_field "$row" 1)" "$(row_field "$row" 2)" "$(row_field "$row" 3)" \
        "$(row_field "$row" 4)" "$(row_field "$row" 6)" "$(row_field "$row" 8)" \
        "$(row_field "$row" 9)" "$(row_field "$row" 10)"
    printf -- '--- review log (tail) ---\n'
    if [ -f "$LOG_DIR/$name.log" ]; then tail -20 "$LOG_DIR/$name.log"; else printf '(no review log)\n'; fi
}

revision_prompt() { # <name> <ticket> <workspace> <finding> — print the revision prompt.
    local name="$1" ticket="$2" ws="$3" finding="$4"
    cat <<EOF
CHIEF REVISION REQUEST for $name (ticket #$ticket).
FINDING: $finding
WORKSPACE: $ws — revise in this SAME workspace; do not start a new checkout and do not touch another worker's workspace.

Rework the finding above, commit the revision in $ws, and re-run the narrowest checks for your change (never a broad sweep).

RE-REPORT (via your terminal output — the chief collects it with herdr agent read):
STATUS: done | blocked | failed
ROLE: (your role)
TICKET: #$ticket
WORKSPACE: $ws
BASE: <full 40-char HEAD you verify in $ws>
COMMIT: <sha of your new reviewable commit in $ws, or none>

SUMMARY:
<what changed in this revision>

VERIFICATION:
<commands run + results>
EOF
}

cmd_review() {
    require_chief "review_disposition"
    [ $# -ge 1 ] || usage_err "review: worker name is required"
    local name="$1"; shift
    valid_name "$name" || usage_err "review: invalid worker name '$name'"
    local disp="" finding="" result=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --disposition) need_arg "$1" "${2:-}"; disp="$2"; shift 2 ;;
            --finding) need_arg "$1" "${2:-}"; finding="$2"; shift 2 ;;
            --result-commit) need_arg "$1" "${2:-}"; result="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "review: unknown flag $1" ;;
        esac
    done
    case "$disp" in
        accept|revision|reject|cancel) ;;
        *) usage_err "review: --disposition must be accept|revision|reject|cancel (got '${disp:-}')" ;;
    esac
    [ -z "$finding" ] || refuse_forgery "$finding" "--finding"
    if [ -n "$result" ]; then
        valid_sha "$result" || die "review: --result-commit must be a full 40-char lowercase commit (got '$result')"
    fi
    [ -x "$WORKER" ] || die "review: worker seam not executable: $WORKER"
    local row role ticket ws status parent
    row="$(reg_row "$name")"
    [ -n "$row" ] || die "review: unknown worker '$name' (not in registry)"
    role="$(row_field "$row" 2)"; ticket="$(row_field "$row" 3)"
    ws="$(row_field "$row" 4)"; status="$(row_field "$row" 6)"
    parent="$(row_field "$row" 10)"
    case "$role" in
        ticket|maintenance) ;;
        helper) die "review: helper '$name' integrates via its parent worker '${parent:-unknown}' — disposition the parent ticket's result instead" ;;
        bug-scout) die "review: bug-scout '$name' is read-only (durable output is tickets) — nothing to disposition" ;;
        *) die "review: worker '$name' carries an unknown role '$role'" ;;
    esac
    case "$disp" in
        accept)
            case "$status" in
                ready-for-review|failed) ;;
                *) die "review: cannot accept '$name' while $status (want ready-for-review or failed with a salvageable commit)" ;;
            esac
            [ -n "$result" ] || die "review: accept requires --result-commit <reviewed 40-char SHA> (inspect the commit before accepting)"
            reg_set "$name" "accepted-awaiting-integration" "reviewed=accept result=$result${finding:+ finding: $finding}"
            review_log "$name" "accept ticket=#$ticket result=$result${finding:+ finding: $finding}"
            log "accepted $name (ticket #$ticket, result $(printf '%s' "$result" | cut -c1-7)) — awaiting integration window"
            ;;
        revision)
            case "$status" in
                ready-for-review|failed|accepted-awaiting-integration|revision-requested) ;;
                *) die "review: cannot request revision from '$name' while $status" ;;
            esac
            [ -n "$finding" ] || die "review: revision requires --finding <concrete findings> (never a blind restart)"
            [ -d "$ws" ] || die "review: worker '$name' workspace is gone ($ws) — result may still be salvageable from its commit, but revision needs a live workspace"
            local prompt_file text rc=0
            prompt_file="$(mktemp)"
            revision_prompt "$name" "$ticket" "$ws" "$finding" > "$prompt_file"
            text="$(cat "$prompt_file")"
            rm -f "$prompt_file"
            export WAYFINDER_WORKER_REGISTRY="$REGISTRY"
            "$WORKER" prompt "$name" --text "$text" || rc=$?
            [ "$rc" -eq 0 ] || die "review: revision prompt failed for '$name' — state kept as $status (worker not notified)"
            # Re-read after the slow external prompt: a concurrent
            # disposition/integration may have moved the row; never clobber it.
            local fresh fresh_status
            fresh="$(reg_row "$name")"
            fresh_status="$(row_field "$fresh" 6)"
            [ "$fresh_status" = "$status" ] \
                || die "review: '$name' moved to $fresh_status during the prompt — revision NOT recorded, re-issue against the new state"
            reg_set "$name" "revision-requested" "reviewed=revision finding: $finding"
            review_log "$name" "revision ticket=#$ticket finding: $finding"
            log "revision requested from $name in its workspace $ws (state kept, context preserved)"
            ;;
        reject|cancel)
            case "$status" in
                integrated|rejected|cancelled) die "review: '$name' is already $status — $disp is not applicable" ;;
                revision-requested|spawning|running)
                    die "review: '$name' is live ($status) — stop the worker first (worker stop), then $disp from stopped; never orphan a running agent" ;;
                done|blocked) die "review: '$name' is $status (awaiting chief collection) — collect first, then $disp from ready-for-review" ;;
                ready-for-review|failed|accepted-awaiting-integration|stopped|gone) ;;
                *) die "review: cannot $disp '$name' while $status" ;;
            esac
            [ -n "$finding" ] || die "review: $disp requires --finding <reason> (explicit, never silent)"
            local terminal="$disp"
            [ "$disp" = "cancel" ] && terminal="cancelled"
            [ "$disp" = "reject" ] && terminal="rejected"
            reg_set "$name" "$terminal" "reviewed=$disp finding: $finding"
            review_log "$name" "$disp ticket=#$ticket finding: $finding"
            log "$disp recorded for $name (ticket #$ticket) — workspace preserved until explicit cleanup"
            if [ "$disp" = "cancel" ]; then
                log "release the tracker claim for ticket #$ticket (remove the worker assignee) so it re-enters the pool instead of pinning the chain"
            fi
            ;;
    esac
}

cmd_gate() {
    require_chief "review_gate"
    local reason=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --reason) need_arg "$1" "${2:-}"; reason="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "gate: unknown flag $1" ;;
        esac
    done
    [ -n "$reason" ] || usage_err "gate: --reason TEXT is required"
    refuse_forgery "$reason" "--reason"
    mkdir -p "$(dirname "$GATE_FILE")"
    printf 'blocked %s %s\n' "$(date '+%F %T')" "$reason" > "$GATE_FILE"
    log "integration gated: $reason (accepted work stays queued; isolated implementation continues)"
}

cmd_gate_clear() {
    require_chief "review_gate-clear"
    [ $# -eq 0 ] || usage_err "gate-clear takes no flags"
    rm -f "$GATE_FILE"
    log "integration gate cleared — chief may resume integrations in a controlled order"
}

cmd_gate_status() {
    [ $# -eq 0 ] || usage_err "gate-status takes no flags"
    local reason
    reason="$(gate_reason)"
    if [ -n "$reason" ]; then
        printf 'gated: %s\n' "$reason"
    else
        printf 'ready\n'
    fi
}

cmd_integrate() {
    require_chief "review_integrate"
    [ $# -ge 1 ] || usage_err "integrate: worker name is required"
    local name="$1"; shift
    valid_name "$name" || usage_err "integrate: invalid worker name '$name'"
    local repo="$CANON_DEFAULT" allow_gated=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --repo) need_arg "$1" "${2:-}"; repo="$2"; shift 2 ;;
            --allow-gated) allow_gated=1; shift ;;
            -h|--help) usage ;;
            *) usage_err "integrate: unknown flag $1" ;;
        esac
    done
    case "$repo" in
        -*) usage_err "integrate: --repo must be a directory, not a flag (got '$repo')" ;;
    esac
    local row role ticket ws status result short
    row="$(reg_row "$name")"
    [ -n "$row" ] || die "integrate: unknown worker '$name' (not in registry)"
    role="$(row_field "$row" 2)"; ticket="$(row_field "$row" 3)"
    ws="$(row_field "$row" 4)"; status="$(row_field "$row" 6)"
    case "$role" in
        ticket|maintenance) ;;
        *) die "integrate: worker '$name' has role '$role' — only accepted ticket/maintenance results integrate" ;;
    esac
    [ "$status" = "accepted-awaiting-integration" ] \
        || die "integrate: worker '$name' is $status (want accepted-awaiting-integration — record review accept first)"
    result="$(result_of "$row")"
    [ -n "$result" ] || die "integrate: worker '$name' records no result commit — review accept must carry --result-commit"
    short="$(printf '%s' "$result" | cut -c1-7)"
    [ -d "$repo" ] || die "integrate: canonical repo not found: $repo (set WAYFINDER_REPO)"
    git -C "$repo" rev-parse --git-dir >/dev/null 2>&1 || die "integrate: not a checkout: $repo"
    repo="$(cd "$repo" && pwd -P)" # physical path: one lock identity per
    # canonical checkout regardless of symlinks/relative spellings.
    if [ -z "$allow_gated" ]; then
        local reason
        reason="$(gate_reason)"
        if [ -n "$reason" ]; then
            die "integrate: gated ($reason) — '$name' stays accepted-awaiting-integration; rerun with --allow-gated to override"
        fi
    fi
    # Single-writer: concurrent integrates serialize here, so two workers
    # never race direct canonical writes. flock releases on process exit, so
    # a killed integrate never wedges the queue. Without flock the
    # single-chief assumption applies (loud warning below); the in-lock
    # re-validation still turns a lost race into a refusal, never corruption.
    local lock="$repo/.wayfinder/integrate.lock" lock_fd
    mkdir -p "$(dirname "$lock")"
    if command -v flock >/dev/null 2>&1; then
        exec {lock_fd}>"$lock"
        flock -w 60 "$lock_fd" || die "integrate: cannot lock $lock"
    else
        log "warning: flock unavailable — integrating without serialization (single-chief assumption)"
    fi
    # Re-validate inside the lock: the pre-lock snapshot may be stale (a
    # concurrent disposition, revision, or integrate may have landed). A lost
    # race refuses here instead of applying a superseded result.
    local fresh fresh_status fresh_result
    fresh="$(reg_row "$name")"
    [ -n "$fresh" ] || die "integrate: worker '$name' vanished from the registry while waiting for the lock"
    fresh_status="$(row_field "$fresh" 6)"
    [ "$fresh_status" = "accepted-awaiting-integration" ] \
        || die "integrate: worker '$name' is now $fresh_status (was $status) — re-disposition before integrating"
    fresh_result="$(result_of "$fresh")"
    [ "$fresh_result" = "$result" ] \
        || die "integrate: worker '$name' records a newer accepted result — refusing the superseded $short"
    if [ -z "$allow_gated" ]; then
        local reason_inside
        reason_inside="$(gate_reason)"
        if [ -n "$reason_inside" ]; then
            die "integrate: gated inside lock ($reason_inside) — '$name' stays accepted-awaiting-integration"
        fi
    fi
    local gated_note=""
    if [ -n "$allow_gated" ]; then
        local overridden
        overridden="$(gate_reason)"
        [ -z "$overridden" ] || gated_note=" allow-gated override (gate was: $overridden)"
    fi
    local porcelain
    # The runtime state dir (.wayfinder/) is excluded: it holds the
    # integration lock plus registry/log state (gitignored in production)
    # and must never read as a dirty checkout.
    porcelain="$(git -C "$repo" status --porcelain -- . ':!.wayfinder' 2>&1)" \
        || die "integrate: cannot read canonical status: $porcelain"
    [ -z "$porcelain" ] || die "integrate: canonical checkout is dirty — refusing to pile '$name' onto uncommitted state"
    git -C "$repo" cat-file -e "${result}^{commit}" 2>/dev/null \
        || die "integrate: result $short is missing from the canonical object store — nothing discarded, '$name' kept as accepted-awaiting-integration"
    local parents
    parents="$(git -C "$repo" rev-list --parents -n 1 "$result" 2>/dev/null)" \
        || die "integrate: cannot inspect result $short"
    [ "$(printf '%s' "$parents" | awk '{ print NF - 1 }')" -le 1 ] \
        || die "integrate: result $short is a merge commit — request a single reviewable commit via revision"
    local newbase verified out
    newbase="$(git -C "$repo" rev-parse HEAD)"
    if git -C "$repo" merge-base --is-ancestor "$result" HEAD 2>/dev/null; then
        # Already present (sequential fallback or an equivalent change
        # landed first): no new commit, but verification still runs before
        # the row may claim integrated.
        verified="$(verify_result "$repo" "$result" || true)"
        [ -n "$verified" ] || die "integrate: verification failed for already-present $short — '$name' kept as accepted-awaiting-integration"
        reg_set "$name" "integrated" "integrated=$newbase result=$result already-present verified=$verified$gated_note"
        review_log "$name" "integrate ticket=#$ticket result=$result already-present integrated=$newbase verified=$verified$gated_note"
        log "integrated $name (ticket #$ticket): result $short already present at $(printf '%s' "$newbase" | cut -c1-7) — no new commit$gated_note"
        integrate_done "$name" "$ticket"
        return 0
    fi
    if [ -d "$ws" ]; then
        if [ -n "$(git -C "$ws" status --porcelain 2>/dev/null || true)" ]; then
            log "warning: worker workspace $ws holds uncommitted state — integrating recorded result $short only"
        fi
    else
        log "warning: worker workspace $ws is missing — integrating recorded result $short from the object store"
    fi
    if out="$(git -C "$repo" cherry-pick -x "$result" 2>&1)"; then
        local newhead
        newhead="$(git -C "$repo" rev-parse HEAD)"
        verified="$(verify_result "$repo" "$newhead" || true)"
        if [ -z "$verified" ]; then
            # Restore the exact pre-integration base (never HEAD~1: under a
            # lost race that could remove another worker's commit). A failed
            # restore is loud, never claimed.
            git -C "$repo" reset --hard "$newbase" >/dev/null 2>&1 \
                || die "integrate: verification failed AND restore of $newbase failed — operator must inspect $repo; '$name' kept as accepted-awaiting-integration"
            if [ -n "$(git -C "$repo" status --porcelain -- . ':!.wayfinder' 2>/dev/null)" ]; then
                die "integrate: verification failed; base restored but the checkout is dirty (verification droppings?) — operator must clean $repo; '$name' kept as accepted-awaiting-integration"
            fi
            reg_set "$name" "accepted-awaiting-integration" "integrate verification-failed at $(printf '%s' "$newbase" | cut -c1-7): ${VERIFY_CMD:-diff-check}$gated_note"
            review_log "$name" "integrate ticket=#$ticket result=$result verification FAILED at $(printf '%s' "$newbase" | cut -c1-7) — canonical restored, row kept$gated_note"
            die "integrate: verification failed for '$name' — applied commit removed, canonical restored, row kept as accepted-awaiting-integration"
        fi
        reg_set "$name" "integrated" "integrated=$newhead result=$result verified=$verified$gated_note"
        review_log "$name" "integrate ticket=#$ticket result=$result integrated=$newhead verified=$verified$gated_note"
        log "integrated $name (ticket #$ticket): $short -> $(printf '%s' "$newhead" | cut -c1-7)$gated_note"
        integrate_done "$name" "$ticket"
        return 0
    fi
    git -C "$repo" cherry-pick --abort >/dev/null 2>&1 || true
    if [ -n "$(git -C "$repo" status --porcelain -- . ':!.wayfinder' 2>/dev/null)" ]; then
        die "integrate: conflict abort left canonical state dirty — operator must inspect $repo; '$name' kept as accepted-awaiting-integration, nothing discarded"
    fi
    newbase="$(git -C "$repo" rev-parse HEAD)"
    local kind="conflict" first
    first="$(printf '%s' "$out" | head -1 | tr '\t\n' '  ')"
    case "$out" in
        *"nothing to commit"*|*"empty"*|*"already exists"*) kind="empty (content may already be present)" ;;
    esac
    reg_set "$name" "accepted-awaiting-integration" "integrate $kind at $(printf '%s' "$newbase" | cut -c1-7): $first"
    review_log "$name" "integrate ticket=#$ticket result=$result $kind at $newbase — canonical clean, row kept"
    log "integrate $kind for '$name' at $(printf '%s' "$newbase" | cut -c1-7) — canonical left clean, nothing discarded"
    printf 'recover with: wayfinder-review.sh review %s --disposition revision --finding "rebase onto %s: %s" [--result-commit <new-sha>]\n' \
        "$name" "$(printf '%s' "$newbase" | cut -c1-7)" "$first" >&2
    return 1
}

# verify_result <repo> <commit> — print the verification tag or nothing on failure.
verify_result() {
    local repo="$1" commit="$2" tag="diff-check-only"
    if git -C "$repo" rev-parse "${commit}~1" >/dev/null 2>&1; then
        git -C "$repo" diff --check "${commit}~1" "$commit" 2>/dev/null || return 1
    else
        tag="diff-check-na-root"
    fi
    if [ -n "$VERIFY_CMD" ]; then
        (cd "$repo" && sh -c "$VERIFY_CMD" >/dev/null 2>&1) || return 1
        tag="verified:$VERIFY_CMD"
    fi
    printf '%s' "$tag" | tr '|' '/'
}

# integrate_done <name> <ticket> — closure/cleanup instructions (never automatic).
integrate_done() {
    local name="$1" ticket="$2"
    log "ticket #$ticket may now be closed by the chief — closure only after this integrated state"
    log "workspace for '$name' is now safe to release: worker cleanup + provider cleanup"
}

[ $# -ge 1 ] || usage
sub="$1"; shift
case "$sub" in
    queue) cmd_queue "$@" ;;
    status) cmd_status "$@" ;;
    review) cmd_review "$@" ;;
    integrate) cmd_integrate "$@" ;;
    gate) cmd_gate "$@" ;;
    gate-clear) cmd_gate_clear "$@" ;;
    gate-status) cmd_gate_status "$@" ;;
    -h|--help|help) usage ;;
    *) usage_err "unknown subcommand '$sub'" ;;
esac
