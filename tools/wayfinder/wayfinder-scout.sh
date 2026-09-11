#!/usr/bin/env bash
# wayfinder-scout.sh — repository-wide read-only bug-scout role for Wayfinder parallel supervision (map #697, ticket #738).
#
# A dedicated bug scout audits the whole repository in bounded slices while
# ticket implementation proceeds, and emits evidence-backed GitHub issues into
# the normal tracker/frontier flow. The scout is logically read-only: its
# durable output is tickets, never implementation.
#
# Subcommands:
#
#   slices                    list the auditable slice taxonomy, one slug per line
#   next                      print the next slice to audit (first pending in
#                             taxonomy order, else the stalest done slice;
#                             exits 1 with no output while every slice is
#                             active — nothing is due)
#   resolve-base [--base SHA] [--repo DIR]
#                             print the full 40-char base SHA (read-only,
#                             for pre-spawn validation)
#   start    <slice> [--base SHA] [--repo DIR]
#   complete <slice> [--note TEXT]
#   note-finding <slice> <issue-number>
#   status
#   prompt   <slice> --map N --workspace DIR [--base SHA]
#
# Slice/progress state (durable, inspectable TSV under gitignored .wayfinder/):
#
#   slice, status (active|done), base_sha, issues, updated, note
#
# Slices with no row are pending. start records the audited base SHA (full
# 40-char commit, default canonical HEAD). complete marks a slice done.
# note-finding appends a filed issue number to the slice row (deduped), so
# which findings came from which slice/run survives restarts. next answers
# which slice is due: pending first, otherwise the stalest done slice for a
# deliberate revisit. Staleness beyond that is a chief scheduling decision
# (sibling ticket #742 owns role-aware priority).
#
# Read-only contract (enforced at the prompt level, as with helper modes):
# the scout may read/search files, inspect issues/PR history for duplication
# checks, run non-mutating analysis, and create/update GitHub issues. It must
# not edit source/config/docs/tests in canonical state, commit or integrate
# fixes, close a bug it merely understands, hand private tasks to a worker,
# claim tickets, create handoffs, or spawn agents. Genuinely destructive
# experiments belong only in a chief-supplied disposable isolated workspace
# (workspace provider purpose scout-<slice>) whose mutations are experimental
# and never eligible for integration (sibling #740 owns the review gate).
#
# Scheduling: scouts are chief-spawned via the worker seam
# (--role bug-scout) and count toward WAYFINDER_MAX_WORKERS like any worker,
# so scouting runs concurrently with implementation under the same global
# bound. The chief never auto-fills scouts in its ticket pass; scout dispatch
# is an explicit --spawn-scout lane (one slice per spawn), and role-aware
# priority/caps belong to sibling ticket #742.
#
# Crash safety: filed issues already live on GitHub (never lost); slice rows
# are published by atomic rename, so a crash loses at most the in-flight
# slice, which re-audits cleanly. Canonical state is untouched because the
# scout holds no writable checkout by default.
#
# Contract notes (ticket #738 acceptance):
# - Only the map chief mutates scout progress (start/complete/note-finding
#   refuse leaf roles with a chief pointer); slices/next/status/prompt stay
#   observable to all so a scout can read its assignment.
# - Slice start/complete emit structured lifecycle events best-effort (ticket
#   #742 observability); a failed emit never breaks the progress update.
# - Every finding must pass the prompt's duplication check and ticket-quality
#   template before filing; speculative low-signal tickets are out of scope.
# - Do not introduce OpenCode, Rift, or Lane dependencies to satisfy this ticket.
#
# Env:
#   WAYFINDER_SCOUT_REGISTRY  state file (default: <repo>/.wayfinder/scout.tsv)
#   WAYFINDER_REPO            canonical checkout (default: repo containing this script)
#   WAYFINDER_ROLE            caller role; leaf roles cannot mutate progress
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DISCOVERED_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

CANON_DEFAULT="${WAYFINDER_REPO:-$DISCOVERED_REPO}"
REGISTRY_DEFAULT="${WAYFINDER_SCOUT_REGISTRY:-$DISCOVERED_REPO/.wayfinder/scout.tsv}"
CALLER_ROLE="${WAYFINDER_ROLE:-chief}"
CAPACITY="$SCRIPT_DIR/wayfinder-capacity.sh"

LEAF_ROLES="ticket maintenance bug-scout helper"

SLICES="backend-domain persistence-data-integrity api-routes-auth frontend-ui-state cross-platform-shared tests-fixtures ci-build-tooling security-permissions migrations-schema operational-recovery"

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

die() { printf 'wayfinder-scout: %s\n' "$*" >&2; exit 1; }
usage_err() { printf 'wayfinder-scout: %s\n' "$*" >&2; exit 2; }

require_chief() { # <op>
    case " $LEAF_ROLES " in
        *" $CALLER_ROLE "*) die "$1 requires the map chief (WAYFINDER_ROLE=$CALLER_ROLE is a leaf role — request help via the chief instead)" ;;
    esac
}

need_arg() { # <flag> <value>
    [ -n "${2:-}" ] || usage_err "$1 requires a value"
}

valid_slice() { local s; for s in $SLICES; do [ "$s" = "${1:-}" ] && return 0; done; return 1; }

resolve_registry() {
    if [ -n "${WAYFINDER_SCOUT_REGISTRY:-}" ]; then
        printf '%s' "$WAYFINDER_SCOUT_REGISTRY"
    else
        printf '%s' "$REGISTRY_DEFAULT"
    fi
}

reg_init() { # <registry>
    mkdir -p "$(dirname "$1")"
    [ -f "$1" ] || : > "$1"
}

reg_row() { # <registry> <slice>
    [ -f "$1" ] || return 0
    awk -F'\t' -v s="$2" '$1 == s { print; exit }' "$1"
}

# scout_emit <registry> <event> [emit-flags...] — best-effort structured
# lifecycle event (ticket #742 observability); never breaks progress updates.
scout_emit() { # <registry> <event> [emit-flags...]
    local registry="$1"; shift
    [ -x "$CAPACITY" ] || return 0
    WAYFINDER_EVENTS_LOG="${WAYFINDER_EVENTS_LOG:-$(dirname "$registry")/events.log}" \
        "$CAPACITY" emit "$@" >/dev/null 2>&1 || true
}

# reg_upsert <registry> <slice> <status> <base> <issues> <note>
reg_upsert() {
    local registry="$1" slice="$2" status="$3" base="$4" issues="$5" note="$6"
    local now existing updated tmp
    now="$(date '+%F %T')"
    issues="$(printf '%s' "$issues" | tr '\t\n\r' '  ')"
    note="$(printf '%s' "$note" | tr '\t\n\r' '  ')"
    reg_init "$registry"
    existing="$(reg_row "$registry" "$slice")"
    tmp="$(mktemp -p "$(dirname "$registry")" scout.XXXXXX)"
    awk -F'\t' -v s="$slice" '$1 != s' "$registry" > "$tmp"
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$slice" "$status" "$base" "$issues" "$now" "$note" >> "$tmp"
    mv "$tmp" "$registry"
}

resolve_base() { # <canon> <base-or-empty> — print full 40-char SHA.
    local canon="$1" base="${2:-HEAD}" sha
    sha="$(git -C "$canon" rev-parse --verify "${base}^{commit}" 2>&1)" \
        || die "unknown base revision '$base': $sha"
    printf '%s' "$sha"
}

slice_scope() { # <slice> — one-line audit scope for the prompt.
    case "$1" in
        backend-domain) printf 'domain services, engines, and business-rule enforcement' ;;
        persistence-data-integrity) printf 'entities, repositories, transactions, and data-integrity paths' ;;
        api-routes-auth) printf 'routes, before-filters, auth/capability gates, and DTO contracts' ;;
        frontend-ui-state) printf 'Compose screens, navigation, ViewModels, and UI state handling' ;;
        cross-platform-shared) printf 'shared-module domain types, serialization, and expect/actual seams' ;;
        tests-fixtures) printf 'test helpers, fixtures, seeders, and coverage gaps' ;;
        ci-build-tooling) printf 'Gradle config, hooks, scripts, CI workflows, and tooling contracts' ;;
        security-permissions) printf 'permission checks, PII handling, anonymization, and audit trails' ;;
        migrations-schema) printf 'Flyway migrations, schema evolution, and version-mismatch handling' ;;
        operational-recovery) printf 'recovery paths, daemon/tooling crash safety, and operational runbooks' ;;
        *) printf 'repository slice %s' "$1" ;;
    esac
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

cmd_slices() {
    [ $# -eq 0 ] || usage_err "slices takes no arguments"
    for s in $SLICES; do printf '%s\n' "$s"; done
}

cmd_next() {
    [ $# -eq 0 ] || usage_err "next takes no arguments"
    local registry s row status oldest_slice="" oldest_updated=""
    registry="$(resolve_registry)"
    for s in $SLICES; do
        row="$(reg_row "$registry" "$s")"
        [ -z "$row" ] && { printf '%s\n' "$s"; return 0; }
        status="$(printf '%s' "$row" | cut -f2)"
        [ "$status" != "active" ] && [ "$status" != "done" ] && { printf '%s\n' "$s"; return 0; }
    done
    # No pending slice: revisit the stalest done slice (lexicographically
    # oldest timestamp; equal timestamps fall back to taxonomy order).
    for s in $SLICES; do
        row="$(reg_row "$registry" "$s")"
        status="$(printf '%s' "$row" | cut -f2)"
        [ "$status" = "done" ] || continue
        updated="$(printf '%s' "$row" | cut -f5)"
        if [ -z "$oldest_slice" ] || [[ "$updated" < "$oldest_updated" ]]; then
            oldest_slice="$s"; oldest_updated="$updated"
        fi
    done
    if [ -n "$oldest_slice" ]; then printf '%s\n' "$oldest_slice"; return 0; fi
    # Every slice active (or registry holds only active rows): no next slice.
    return 1
}

cmd_start() {
    require_chief "scout_start"
    local slice="" base="" canon="$CANON_DEFAULT"
    [ $# -ge 1 ] || usage_err "start: slice is required"
    slice="$1"; shift
    valid_slice "$slice" || usage_err "start: unknown slice '$slice' (see slices)"
    while [ $# -gt 0 ]; do
        case "$1" in
            --base) need_arg "$1" "${2:-}"; base="$2"; shift 2 ;;
            --repo) need_arg "$1" "${2:-}"; canon="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "start: unknown flag $1" ;;
        esac
    done
    local registry sha row issues=""
    registry="$(resolve_registry)"
    sha="$(resolve_base "$canon" "$base")"
    row="$(reg_row "$registry" "$slice")"
    if [ -n "$row" ]; then
        issues="$(printf '%s' "$row" | cut -f4)"
        [ "$(printf '%s' "$row" | cut -f2)" = "active" ] \
            && die "start: slice '$slice' is already active (complete it first)"
    else
        issues=""
    fi
    reg_upsert "$registry" "$slice" "active" "$sha" "$issues" "auditing at $sha"
    scout_emit "$registry" scout-slice-started --detail "slice=$slice base=$sha"
    printf 'started %s base=%s\n' "$slice" "$sha"
}

cmd_complete() {
    require_chief "scout_complete"
    local slice="" note=""
    [ $# -ge 1 ] || usage_err "complete: slice is required"
    slice="$1"; shift
    valid_slice "$slice" || usage_err "complete: unknown slice '$slice' (see slices)"
    while [ $# -gt 0 ]; do
        case "$1" in
            --note) need_arg "$1" "${2:-}"; note="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "complete: unknown flag $1" ;;
        esac
    done
    local registry row base issues
    registry="$(resolve_registry)"
    row="$(reg_row "$registry" "$slice")"
    [ -n "$row" ] || die "complete: slice '$slice' was never started"
    [ "$(printf '%s' "$row" | cut -f2)" = "active" ] \
        || die "complete: slice '$slice' is not active (status: $(printf '%s' "$row" | cut -f2))"
    base="$(printf '%s' "$row" | cut -f3)"
    issues="$(printf '%s' "$row" | cut -f4)"
    [ -n "$note" ] || note="audited at $base"
    reg_upsert "$registry" "$slice" "done" "$base" "$issues" "$note"
    scout_emit "$registry" scout-slice-completed --detail "slice=$slice base=$base issues=${issues:-none}"
    printf 'completed %s base=%s issues=%s\n' "$slice" "$(printf '%s' "$base" | cut -c1-7)" "${issues:-none}"
}

cmd_note_finding() {
    require_chief "scout_note-finding"
    local slice="" issue=""
    [ $# -ge 2 ] || usage_err "note-finding: slice and issue number are required"
    slice="$1"; issue="$2"; shift 2
    [ $# -eq 0 ] || usage_err "note-finding takes exactly two arguments"
    valid_slice "$slice" || usage_err "note-finding: unknown slice '$slice' (see slices)"
    [[ "$issue" =~ ^[0-9]+$ ]] || usage_err "note-finding: issue must be numeric (got '$issue')"
    issue="$(printf '%s' "$issue" | sed 's/^0*//')"
    [ -n "$issue" ] || die "note-finding: issue must be a positive number (GitHub has no issue #0)"
    local registry row status base issues
    registry="$(resolve_registry)"
    row="$(reg_row "$registry" "$slice")"
    [ -n "$row" ] || die "note-finding: slice '$slice' was never started (start it first)"
    status="$(printf '%s' "$row" | cut -f2)"
    base="$(printf '%s' "$row" | cut -f3)"
    issues="$(printf '%s' "$row" | cut -f4)"
    case ",$issues," in
        *",$issue,"*) die "note-finding: issue #$issue already recorded for slice '$slice'" ;;
    esac
    if [ -n "$issues" ]; then issues="$issues,$issue"; else issues="$issue"; fi
    reg_upsert "$registry" "$slice" "$status" "$base" "$issues" "finding #$issue filed from $slice"
    printf 'recorded #%s for %s\n' "$issue" "$slice"
}

cmd_status() {
    local registry s row
    [ $# -eq 0 ] || usage_err "status takes no arguments"
    registry="$(resolve_registry)"
    for s in $SLICES; do
        row="$(reg_row "$registry" "$s")"
        if [ -n "$row" ]; then
            printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
                "$(printf '%s' "$row" | cut -f1)" "$(printf '%s' "$row" | cut -f2)" \
                "$(printf '%s' "$row" | cut -f3)" "$(printf '%s' "$row" | cut -f4)" \
                "$(printf '%s' "$row" | cut -f5)" "$(printf '%s' "$row" | cut -f6)"
        else
            printf '%s\tpending\t\t\t\t\n' "$s"
        fi
    done
}

cmd_prompt() { # <slice> --map N --workspace DIR [--base SHA]
    local slice="" map="" workspace="" base=""
    [ $# -ge 1 ] || usage_err "prompt: slice is required"
    slice="$1"; shift
    valid_slice "$slice" || usage_err "prompt: unknown slice '$slice' (see slices)"
    while [ $# -gt 0 ]; do
        case "$1" in
            --map) need_arg "$1" "${2:-}"; map="$2"; shift 2 ;;
            --workspace) need_arg "$1" "${2:-}"; workspace="$2"; shift 2 ;;
            --base) need_arg "$1" "${2:-}"; base="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "prompt: unknown flag $1" ;;
        esac
    done
    [[ "$map" =~ ^[0-9]+$ ]] || usage_err "prompt: --map N is required"
    [ -n "$workspace" ] || usage_err "prompt: --workspace is required"
    local base_line="BASE: derive the workspace HEAD revision at start (full 40-char commit) and report it"
    [ -n "$base" ] && base_line="BASE: dispatched at canonical $base — verify the workspace HEAD revision (full 40-char commit) at start and report the actual value"
    cat <<EOF
ROLE: bug-scout worker under Wayfinder map #$map (chief-spawned; you are not an orchestrator).
SLICE: $slice — $(slice_scope "$slice").
MAP: #$map (supervision anchor) — file findings as native sub-issues of bug map #863 via tools/wayfinder/wayfinder-create-child.sh 863 task <title> <body-file>; map #755 is at GitHub's 100-child cap and rejects new links, so nothing files there.
SCOPE: master tree at BASE only. Prototype branches (prototype/*) are out of scope — do not audit them, do not chase their commits, do not file on them.
WORKSPACE: $workspace — read-only. Work only inside this workspace and never modify another worker's workspace.
$base_line

READ-ONLY CONTRACT:
You may: read/search repository files; inspect existing issues/PR history for duplication checks; run tests or analysis commands that do not mutate durable repository state; inspect logs/results/artifacts; create/update GitHub issues as your deliverable.
You must not: edit source/config/docs/tests in canonical state; commit implementation changes; integrate fixes; close a discovered bug merely because you understand the fix; privately hand a task to a specific implementation worker (file it on the tracker instead); claim, start, or advance any ticket; create the Wayfinder successor handoff; spawn workers or agents of your own (request bounded help via HELP REQUESTS below — only the chief may spawn helpers).

SLICE DISCIPLINE (bounded, not whole-repo browsing):
- Audit only SLICE above in this run. Do not re-audit slices the chief did not assign.
- Record the base revision you actually audited and the slice you covered in your report.

DUPLICATION CHECK (before filing anything):
- Search open and closed issues for the same defect (gh issue list --search, gh issue view).
- Check the shared findings registry FIRST: tools/wayfinder/wayfinder-findings.sh check <area:short-kebab> — a filed row means stop (note it as duplicate), an investigating row by a live scout means pick a different suspect or note the overlap in your report instead of double-diving.
- Before deep-diving a suspect, claim it: tools/wayfinder/wayfinder-findings.sh claim <fingerprint> <your-agent-name> "<note>" (exit 3 with the existing row means someone owns it — back off).
- After filing: tools/wayfinder/wayfinder-findings.sh filed <fingerprint> <issue-number> <your-agent-name>. If the issue already exists: tools/wayfinder/wayfinder-findings.sh dupe <fingerprint> <issue-number> <your-agent-name> and do not file.
- A duplicate is never re-filed: note the existing issue number in your report instead.

TICKET QUALITY (every filed issue must carry):
- user/system impact; concrete evidence with file/symbol/route references; why the behavior is wrong or risky; reproduction/falsification where practical; scope boundaries; acceptance criteria; targeted verification guidance; duplication-check note; dependency/blocker notes when known.
- Do not file speculative low-signal tickets to maximize count.
- File with tools/wayfinder/wayfinder-create-child.sh 863 task <title> <body-file> so the issue attaches as a native sub-issue of bug map #863: the chief's frontier only sees native sub-issues, and a plain unlinked issue never reaches a ticket worker. (Map #755 is at the 100-child cap — never file there.)

EXPERIMENTAL MUTATION (only if an investigation genuinely requires it):
- Ask the chief for a disposable isolated workspace (provider purpose scout-$slice). Mutations there are experimental only and are never integrated. Your durable output remains the ticket.

COMPLETION REPORT (via your terminal output — the chief collects it with herdr agent read; filed GitHub issues persist independently):
STATUS: done | blocked | failed
ROLE: bug-scout
SLICE: $slice
WORKSPACE: $workspace
BASE: <full 40-char HEAD you verified in $workspace>

FINDINGS:
<issue numbers filed this run, or none with the areas covered>

COVERAGE:
<slices/paths audited, duplication checks performed>

VERIFICATION:
<read-only commands run + results>

RISKS / REVIEW NOTES:
<uncertainty, follow-up slices, stale areas for the chief>

HELP REQUESTS:
<bounded help wanted, or none — the chief decides; helpers are chief-spawned registered siblings>

BLOCKED BEHAVIOR: if you need information, report STATUS blocked with the exact decision/question and enough context for the chief to resolve it. Other workers continue meanwhile.
EOF
}

[ $# -ge 1 ] || usage
sub="$1"; shift
case "$sub" in
    slices) cmd_slices "$@" ;;
    next) cmd_next "$@" ;;
    resolve-base) cmd_resolve_base "$@" ;;
    start) cmd_start "$@" ;;
    complete) cmd_complete "$@" ;;
    note-finding) cmd_note_finding "$@" ;;
    status) cmd_status "$@" ;;
    prompt) cmd_prompt "$@" ;;
    -h|--help|help) usage ;;
    *) usage_err "unknown subcommand '$sub'" ;;
esac
