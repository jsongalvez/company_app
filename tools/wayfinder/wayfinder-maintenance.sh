#!/usr/bin/env bash
# wayfinder-maintenance.sh — dedicated CI/maintenance worker contract for Wayfinder parallel supervision (map #697, ticket #739).
#
# Red hosted CI gets a dedicated operational owner while unrelated safe ticket
# implementation continues in isolated workspaces. The maintenance role is
# writable, isolated, chief-supervised, and distinct from ordinary ticket
# workers. Its durable task is the existing per-SHA repair issue minted by the
# hosted-CI watch (no second invisible task channel); the maintenance worker
# never consumes normal frontier tickets.
#
# Subcommands:
#
#   prompt   <sha> --repair-issue N --map M --workspace DIR [--base SHA]
#                    [--failing TEXT]
#   resolve-base [--base SHA] [--repo DIR]
#                    print the full 40-char base SHA (read-only,
#                    for pre-spawn validation)
#
# Worker contract (enforced at the prompt level, as with ticket/helper/scout
# modes):
# - receives an isolated writable workspace at the failing base SHA and never
#   writes canonical state directly;
# - owns exactly one repair task (the repair issue); assign-first claim with a
#   race fail-safe, no second claim, no frontier pickup;
# - returns root cause, changed files, reviewable commit, verification, and
#   confidence/risk notes via a structured completion report;
# - remains chief-reviewed before integration (`done` means awaiting chief
#   review, never accepted or integrated);
# - does not independently close its repair issue, create handoffs, touch
#   another worker's workspace, or spawn agents (bounded help only via HELP
#   REQUESTS — only the chief spawns helpers).
#
# Scheduling: maintenance workers are chief-spawned via the worker seam
# (--role maintenance) and count toward WAYFINDER_MAX_WORKERS like any worker,
# plus the narrower WAYFINDER_MAX_MAINTENANCE_WORKERS bound enforced by the
# chief lane (default 1: red CI receives prompt attention without a permanently
# occupied idle worker). Pending/unknown/green CI never dispatches maintenance:
# the lane is explicit per red SHA/repair issue, and the watch mints repair
# issues only for red verdicts.
#
# Canonical integration stays gated while the baseline is genuinely red:
# accepted results wait in sibling ticket #740's review/integration queue
# rather than piling onto a broken baseline, while isolated implementation may
# continue. Recovery never duplicates a live repair worker for the same
# task/SHA: the chief lane refuses a second maintenance row for a repair issue
# that already holds one (the registry persists across chief restarts; explicit
# cleanup precedes any redispatch).
#
# Crash safety: the repair issue already lives on GitHub (never lost); the
# worker's reviewable commit lives in its isolated workspace until the chief
# integrates or explicitly cleans up. No mutable maintenance state lives here,
# so every subcommand stays observable to all roles.
#
# Contract notes (ticket #739 acceptance):
# - Do not introduce OpenCode, Rift, or Lane dependencies to satisfy this ticket.
#
# Env:
#   WAYFINDER_REPO            canonical checkout (default: repo containing this script)
#   WAYFINDER_ROLE            caller role (all subcommands observable to all)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DISCOVERED_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

CANON_DEFAULT="${WAYFINDER_REPO:-$DISCOVERED_REPO}"
COMMON="$SCRIPT_DIR/wayfinder-common.sh"

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

die() { printf 'wayfinder-maintenance: %s\n' "$*" >&2; exit 1; }
usage_err() { printf 'wayfinder-maintenance: %s\n' "$*" >&2; exit 2; }

[ -f "$COMMON" ] || die "shared hardening seam missing: $COMMON (ticket #746)"
# shellcheck disable=SC1090
. "$COMMON"

need_arg() { # <flag> <value>
    [ -n "${2:-}" ] || usage_err "$1 requires a value"
}

valid_sha() { [[ "${1:-}" =~ ^[0-9a-f]{40}$ ]]; }

resolve_base() { # <canon> <base-or-empty> — print full 40-char SHA.
    local canon="$1" base="${2:-HEAD}" sha
    sha="$(git -C "$canon" rev-parse --verify "${base}^{commit}" 2>&1)" \
        || die "unknown base revision '$base': $sha"
    printf '%s' "$sha"
}

cmd_resolve_base() {
    local base="" canon="$CANON_DEFAULT"
    while [ $# -gt 0 ]; do
        case "$1" in
            --base) need_arg "$1" "${2:-}"; base="$2"; shift 2 ;;
            --repo) need_arg "$1" "${2:-}"; canon="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "resolve-base: unknown flag $1" ;;
        esac
    done
    resolve_base "$canon" "$base"
}

cmd_prompt() { # <sha> --repair-issue N --map M --workspace DIR [--base SHA] [--failing TEXT]
    local sha="" repair="" map="" workspace="" base="" failing=""
    [ $# -ge 1 ] || usage_err "prompt: failing SHA is required"
    sha="$1"; shift
    valid_sha "$sha" || usage_err "prompt: SHA must be a full 40-char lowercase commit (got '$sha')"
    while [ $# -gt 0 ]; do
        case "$1" in
            --repair-issue) need_arg "$1" "${2:-}"; repair="$2"; shift 2 ;;
            --map) need_arg "$1" "${2:-}"; map="$2"; shift 2 ;;
            --workspace) need_arg "$1" "${2:-}"; workspace="$2"; shift 2 ;;
            --base) need_arg "$1" "${2:-}"; base="$2"; shift 2 ;;
            --failing) need_arg "$1" "${2:-}"; failing="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "prompt: unknown flag $1" ;;
        esac
    done
    [[ "$repair" =~ ^[0-9]+$ ]] || usage_err "prompt: --repair-issue N is required (durable repair task)"
    [[ "$map" =~ ^[0-9]+$ ]] || usage_err "prompt: --map N is required"
    [ -n "$workspace" ] || usage_err "prompt: --workspace is required"
    case "$workspace" in
        *$'\t'*|*$'\n'*) usage_err "prompt: workspace path must not contain tabs or newlines" ;;
    esac
    # Shared forgery guard (ticket #746): --failing text travels into the
    # prompt and the chief lane; refuse separators and structured tokens.
    wf_refuse_forgery "${failing:-}" \
        || usage_err "prompt: --failing text must not contain tabs, newlines, or '|' and must not contain structured note tokens (reviewed=/result=/integrated=/verified=)"
    local base_line="BASE: derive the workspace HEAD revision at start (full 40-char commit) and report it"
    [ -n "$base" ] && base_line="BASE: dispatched at canonical $base — verify the workspace HEAD revision (full 40-char commit) at start and report the actual value"
    local failing_line="FAILING CHECKS: $failing"
    [ -n "$failing" ] || failing_line="FAILING CHECKS: see the repair issue for the failing check names"
    cat <<EOF
ROLE: maintenance worker under Wayfinder map #$map (map chief supervises; you do not).
TASK: repair red hosted CI for failing SHA $sha (repair issue #$repair — read its body and comments chronologically, plus map #$map context, before editing).
MAP: #$map — your durable task is repair issue #$repair; the chief routes normal frontier tickets to ticket workers.
WORKSPACE: $workspace — isolated writable workspace at the failing base. Work only inside this workspace; never modify canonical state directly and never modify another worker's workspace.
$base_line
$failing_line

OWNERSHIP (exactly one repair task):
- Repair only the red-CI scope above (root-cause the failing checks, fix build/tooling/flaky-test/dependency breakage, reconcile the assigned base). Do not absorb unrelated feature work.
- First reserve the claim assign-first: gh issue edit $repair --add-assignee @me
- Then verify the claim is still yours (gh issue view $repair): if another maintenance worker already owns it, STOP before any durable change and report the race as blocked.
- You own this repair task until the chief releases you. Remain available for chief revision requests.
- Do not consume normal frontier tickets: the chief keeps dispatching ticket workers in isolated workspaces while you own red CI.

YOU MUST NOT:
- claim, start, or advance any other ticket or repair task;
- integrate to canonical master yourself, push past chief review, or close repair issue #$repair as done;
- create the Wayfinder successor handoff (the chief owns map advancement);
- modify canonical state directly outside $workspace;
- modify another worker's workspace;
- spawn workers or agents of your own — request bounded help via HELP REQUESTS below; only the chief may spawn helpers.

WORK:
- Diagnose the root cause from the failing checks and the workspace state at the failing base.
- Repair, then verify with the narrowest checks for your change (never a broad sweep); report commands and results.
- Keep the change reviewable: commit in $workspace (reviewable commit the chief can inspect); never writes canonical state directly.

COMPLETION REPORT (via your terminal output — the chief collects it with herdr agent read; no result artifact unless the chief asks):
STATUS: done | blocked | failed
ROLE: maintenance
TASK: #$repair
SHA: $sha
WORKSPACE: $workspace
BASE: <full 40-char HEAD you verified in $workspace>
COMMIT: <sha of your reviewable commit in $workspace, or none>

ROOT CAUSE:
<what broke the baseline and why>

SUMMARY:
<what changed and why>

FILES:
<paths touched>

VERIFICATION:
<commands run + results>

RISKS / REVIEW NOTES:
<conflicts, assumptions, confidence, flaky-vs-real verdict, follow-ups for the chief>

HELP REQUESTS:
<bounded help wanted, or none — the chief decides; helpers are chief-spawned registered siblings>

BLOCKED BEHAVIOR: if you need information, report STATUS blocked with the exact decision/question and enough context for the chief to resolve it. Ticket workers continue meanwhile; genuine user ambiguity follows the map's needs-info / ready-for-human path via the chief.

INTEGRATION GATE: STATUS done means awaiting chief review, not accepted or integrated. The chief integrates accepted repairs through its review/integration queue; canonical integration stays gated while the baseline is genuinely red, and unrelated isolated implementation may continue meanwhile.
EOF
}

[ $# -ge 1 ] || usage
sub="$1"; shift
case "$sub" in
    prompt) cmd_prompt "$@" ;;
    resolve-base) cmd_resolve_base "$@" ;;
    -h|--help|help) usage ;;
    *) usage_err "unknown subcommand '$sub'" ;;
esac
