#!/usr/bin/env bash
# wayfinder-findings.sh — shared bug-finding registry for concurrent scouts.
#
# Issue search only dedupes against FILED issues. Parallel scouts also need
# to see each other's in-flight suspects, or two scouts deep-dive and file
# the same defect (cap-outage dupes #861/#862 vs #865). This registry is
# that shared document: claim-before-dive, filed-on-issue, dupe-on-existing.
#
# Registry (TSV, gitignored runtime state beside other .wayfinder files):
#   fingerprint, status (investigating|filed|duplicate), ref (issue number or
#   empty), scout, updated, note
# Fingerprint convention: <area>:<short-kebab> (e.g.
# remittance:session-line-status). Coarse on purpose — a collision forces a
# human/scout look instead of a silent double-file.
#
# Subcommands (all roles may read; scouts write their own rows):
#   check <fp>            print matching row or nothing (exit 0 either way)
#   claim <fp> <scout> [note]   claim a suspect (refuses when already claimed/filed)
#   filed <fp> <issue> [scout]  mark filed (creates row when absent)
#   dupe <fp> <issue> [scout]   mark duplicate-of (creates row when absent)
#   list                  dump the registry
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
REGISTRY="${WAYFINDER_FINDINGS_REGISTRY:-$REPO/.wayfinder/scout-findings.tsv}"

die() { printf 'wayfinder-findings: %s\n' "$*" >&2; exit 1; }
reg_init() { mkdir -p "$(dirname "$REGISTRY")"; [ -f "$REGISTRY" ] || : > "$REGISTRY"; }
lock() {
    mkdir -p "$(dirname "$REGISTRY")"
    if command -v flock >/dev/null 2>&1; then
        exec {FINDINGS_FD}>"$REGISTRY.lock"
        flock -w 60 "$FINDINGS_FD" || die "cannot lock registry"
    fi
}
unlock() { eval "exec ${FINDINGS_FD:-9}>&-" 2>/dev/null || true; }
now() { date '+%F %T'; }

[ $# -ge 1 ] || { printf 'Usage: %s check|claim|filed|dupe|list ...\n' "$0" >&2; exit 2; }
sub="$1"; shift
case "$sub" in
    check)
        [ $# -ge 1 ] || die "check: fingerprint required"
        reg_init
        awk -F'\t' -v f="$1" '$1 == f { print; exit }' "$REGISTRY"
        ;;
    claim)
        [ $# -ge 2 ] || die "claim: fingerprint and scout required"
        reg_init; lock
        if awk -F'\t' -v f="$1" '$1 == f { found = 1; exit } END { exit !found }' "$REGISTRY"; then
            unlock
            printf 'already: %s\n' "$(awk -F'\t' -v f="$1" '$1 == f { print; exit }' "$REGISTRY")"
            exit 3
        fi
        printf '%s\tinvestigating\t\t%s\t%s\t%s\n' "$1" "$2" "$(now)" "${3:-}" >> "$REGISTRY"
        unlock
        printf 'claimed %s\n' "$1"
        ;;
    filed|dupe)
        [ $# -ge 2 ] || die "$sub: fingerprint and issue required"
        status=filed; [ "$sub" = "dupe" ] && status=duplicate
        reg_init; lock
        tmp="$(mktemp -p "$(dirname "$REGISTRY")" findings.XXXXXX)"
        awk -F'\t' -v f="$1" '$1 != f' "$REGISTRY" > "$tmp"
        printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$status" "$2" "${3:-}" "$(now)" "" >> "$tmp"
        mv "$tmp" "$REGISTRY"
        unlock
        printf '%s %s -> %s #%s\n' "$1" "$status" "$sub" "$2"
        ;;
    list)
        reg_init
        cat "$REGISTRY"
        ;;
    *) die "unknown subcommand '$sub'" ;;
esac
