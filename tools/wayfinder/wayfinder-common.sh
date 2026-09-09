#!/usr/bin/env bash
# wayfinder-common.sh — shared hardening helpers for Wayfinder parallel seams (map #697, ticket #746).
#
# Sourced (never executed) by the worker/review/recover/chief/capacity/
# maintenance seams. Pure predicates and formatting helpers with no side
# effects beyond an optional stderr warning — no Herdr, no gh, no git, no
# registry writes, so sourcing it never changes a caller's authority:
#
#   wf_refuse_forgery <text>
#             return 1 when <text> carries note-token separators (tab,
#             newline, `|`) or structured note tokens (reviewed=/result=/
#             integrated=/verified=) that could shadow the review queue's
#             grepped result tokens (result_of tails the last
#             `reviewed=accept result=` match); return 0 when clean.
#             Every free-text entry point into a registry note or a
#             chief-mediated prompt (helper --scope, maintenance --failing,
#             review --finding/--reason, gate --reason, adopt --scope/
#             --generation) routes through this one predicate. Callers die
#             with their own script-prefixed message:
#               wf_refuse_forgery "$scope" \
#                 || die "spawn: --scope must not contain tabs, newlines, '|' or structured note tokens"
#   wf_tsv_field <line> <n>
#             print TSV field n (1-based), preserving empty fields. `read`
#             with a tab IFS collapses empty fields (tab counts as IFS
#             whitespace), shifting sparse rows such as empty-pane adopted
#             worker rows so status checks read the wrong column. Whole-line
#             reads plus this positional split keep every column aligned.
#   wf_warn_no_flock <what>
#             warn that <what> proceeds without serialization
#             (single-chief assumption, last-writer-wins). Every registry
#             lock's no-flock fallback calls this — unlocked rewrites are
#             always loud, never silent (macOS ships no flock).
#
# Self-test: wayfinder-common-test.sh (same ok/bad convention as the
# sibling seam self-tests). Plain bash — no Gradle, database, or network.

# wf_refuse_forgery <text> — see header. No output; status only.
wf_refuse_forgery() {
    case "${1:-}" in
        *$'\t'*|*$'\n'*|*'|'*) return 1 ;;
    esac
    case "${1:-}" in
        *reviewed=*|*result=*|*integrated=*|*verified=*) return 1 ;;
    esac
    return 0
}

# wf_tsv_field <line> <n> — see header.
wf_tsv_field() {
    printf '%s' "${1:-}" | awk -F'\t' -v n="${2:-1}" '{ print $n }'
}

# wf_warn_no_flock <what> — see header.
wf_warn_no_flock() {
    printf 'wayfinder: warning: flock unavailable — %s without serialization (single-chief assumption, last-writer-wins)\n' "${1:-registry rewrite}" >&2
}
