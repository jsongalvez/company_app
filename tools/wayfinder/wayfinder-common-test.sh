#!/usr/bin/env bash
# wayfinder-common-test.sh — contract tests for wayfinder-common.sh (map #697, ticket #746).
# Plain bash — no Herdr, Gradle, database, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMMON="$ROOT/tools/wayfinder/wayfinder-common.sh"
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$COMMON" || { echo "FAIL: common fails bash -n"; exit 1; }
# Sourcing must be side-effect free (no output, no commands beyond defs).
[ -z "$(bash -c ". \"$COMMON\"" 2>&1)" ] && ok "sourcing is side-effect free" || bad "sourcing produced output"
# shellcheck disable=SC1090
. "$COMMON"

echo "1. forgery refusal: separators and structured tokens refused, plain text clean"
wf_refuse_forgery "backend investigation" && ok "plain scope clean" || bad "plain scope refused"
wf_refuse_forgery "scope with spaces, commas (mode=writable)" && ok "mode-suffixed scope clean" || bad "mode scope refused"
wf_refuse_forgery "pipe | trick" && bad "pipe accepted" || ok "pipe refused"
wf_refuse_forgery "$(printf 'tab\there')" && bad "tab accepted" || ok "tab refused"
wf_refuse_forgery "$(printf 'two\nlines')" && bad "newline accepted" || ok "newline refused"
wf_refuse_forgery "reviewed=accept result=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
    && bad "forged accept accepted" || ok "forged accept refused"
wf_refuse_forgery "note result=deadbeef" && bad "result= accepted" || ok "result= refused"
wf_refuse_forgery "integrated=abc" && bad "integrated= accepted" || ok "integrated= refused"
wf_refuse_forgery "verified=yes" && bad "verified= accepted" || ok "verified= refused"
wf_refuse_forgery "" && ok "empty text clean" || bad "empty text refused"

echo "2. TSV field split preserves empty fields (no read-style collapse)"
line="$(printf 'wf-x\tticket\t101\t/ws\t\tgone\tt0\tt0\tnote\t\t697\tgen-a')"
[ "$(wf_tsv_field "$line" 1)" = "wf-x" ] && ok "field 1 (name)" || bad "field 1 wrong"
[ "$(wf_tsv_field "$line" 5)" = "" ] && ok "field 5 (empty pane stays empty)" || bad "field 5 shifted: '$(wf_tsv_field "$line" 5)'"
[ "$(wf_tsv_field "$line" 6)" = "gone" ] && ok "field 6 (status aligned past the hole)" || bad "field 6 shifted: '$(wf_tsv_field "$line" 6)'"
[ "$(wf_tsv_field "$line" 10)" = "" ] && ok "field 10 (empty parent stays empty)" || bad "field 10 shifted"
[ "$(wf_tsv_field "$line" 11)" = "697" ] && ok "field 11 (map aligned)" || bad "field 11 shifted"
legacy="$(printf 'wf-old\tticket\t131\t/ws\tpane-7\trunning\tt0\tt0\tlegacy note')"
[ "$(wf_tsv_field "$legacy" 11)" = "" ] && ok "short legacy rows read as empty (no error)" || bad "legacy row misread"
[ "$(wf_tsv_field "$legacy" 6)" = "running" ] && ok "legacy status aligned" || bad "legacy status wrong"

echo "3. no-flock warning is loud and names the operation"
out="$(wf_warn_no_flock "worker registry rewrite" 2>&1)"
case "$out" in
    *flock*unavailable*worker*registry*rewrite*) ok "warning names flock + operation" ;;
    *) bad "warning unclear: $out" ;;
esac

echo
if [ $fail -eq 0 ]; then echo "common-contract: OK"; else echo "common-contract: FAILURES PRESENT"; exit 1; fi
