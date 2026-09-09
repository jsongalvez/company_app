#!/usr/bin/env bash
# wayfinder-worker-test.sh — contract tests for wayfinder-worker.sh (map #697 #735).
# Fake-herdr fixtures only: no real Herdr, Gradle, database, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORKER="$ROOT/tools/wayfinder/wayfinder-worker.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-worker-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$WORKER" || { echo "FAIL: worker fails bash -n"; exit 1; }

mkdir -p "$WORK/ws" "$WORK/bin"
export HERDR_CALLS="$WORK/herdr-calls" WAYFINDER_WORKER_REGISTRY="$WORK/workers.tsv"
export WAYFINDER_ROLE=chief WAYFINDER_MAX_WORKERS=5 WAYFINDER_WORKER_KIND=opencode
: > "$HERDR_CALLS"

# Fake herdr: records argv, serves canned agent states.
cat >"$WORK/bin/herdr" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$HERDR_CALLS"
case "${1:-} ${2:-}" in
    "pane split") printf '{"result":{"pane":{"pane_id":"pane-7"}}}'; exit 0 ;;
    "agent start") printf '{"result":{"agent":{"name":"%s","status":"running"}}}' "$3"; exit 0 ;;
    "agent prompt") exit 0 ;;
    "agent get")
        st="running"; [ -f "$WORK/status-$3" ] && st="$(cat "$WORK/status-$3")"
        printf '{"result":{"agent":{"name":"%s","status":"%s"}}}' "$3" "$st"; exit 0 ;;
    "agent list")
        if [ -f "$WORK/list.json" ]; then cat "$WORK/list.json"; else printf '{"result":{"agents":[]}}'; fi
        exit 0 ;;
    "agent wait") printf '{"result":{"agent":{"name":"%s","status":"done"}}}' "$3"; exit 0 ;;
    "agent read") printf 'screen-output\n'; exit 0 ;;
    "agent send-keys") exit 0 ;;
esac
printf 'unexpected: %s\n' "$*" >&2; exit 1
EOF
chmod +x "$WORK/bin/herdr"
export HERDR_BIN="$WORK/bin/herdr" PATH="$WORK/bin:$PATH" WORK

run() { local rc=0; bash "$WORKER" "$@" >"$WORK/out" 2>"$WORK/err" || rc=$?; printf "%s" "$rc" >"$WORK/rc"; }
rc() { cat "$WORK/rc"; }

echo "1. spawn happy path records a running row with explicit role"
run spawn --role ticket --ticket 101 --workspace "$WORK/ws" --name wf-a
[ "$(rc)" -eq 0 ] || bad "spawn failed: $(cat "$WORK/err")"
grep -q $'^wf-a\tticket\t101\t'"$WORK/ws"$'\tpane-7\trunning\t' "$WORK/workers.tsv" \
    && ok "registry row carries name/role/ticket/workspace/pane/running" \
    || bad "registry row wrong: $(cat "$WORK/workers.tsv")"

echo "2. dispatch is asynchronous: spawn/prompt never pass --wait"
if grep -E 'agent (start|prompt)' "$HERDR_CALLS" | grep -q -- '--wait'; then
    bad "a spawn/prompt call carried --wait"
else ok "no --wait on spawn/prompt herdr calls"; fi
if grep -v '^#' "$WORKER" | grep -n 'agent prompt' | grep -q -- '--wait'; then
    bad "worker script hard-codes --wait on agent prompt"
else ok "script never hard-codes --wait on agent prompt"; fi

echo "3. role is explicit and validated"
run spawn --ticket 102 --workspace "$WORK/ws"; [ "$(rc)" -eq 2 ] && ok "missing role rejected" || bad "missing role accepted"
run spawn --role bogus --ticket 102 --workspace "$WORK/ws"; [ "$(rc)" -eq 2 ] && ok "bad role rejected" || bad "bad role accepted"
for r in maintenance bug-scout; do
    run spawn --role "$r" --ticket 102 --workspace "$WORK/ws" --name "wf-$r"
    [ "$(rc)" -eq 0 ] && ok "role $r accepted" || bad "role $r rejected: $(cat "$WORK/err")"
done
# Helper spawns only through chief mediation: parent + scope required (map #697 #737).
run spawn --role helper --ticket 102 --workspace "$WORK/ws" --name wf-helper
[ "$(rc)" -eq 2 ] && ok "bare helper spawn rejected (needs parent+scope)" || bad "bare helper spawn accepted"
run spawn --role ticket --ticket 102 --workspace "$WORK/ws" --name wf-parflag --parent wf-a --scope x
[ "$(rc)" -eq 2 ] && ok "--parent on non-helper rejected" || bad "--parent on non-helper accepted"

echo "4. leaf roles cannot orchestrate through the normal contract"
if WAYFINDER_ROLE=ticket bash "$WORKER" spawn --role ticket --ticket 103 --workspace "$WORK/ws" --name wf-leaf >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf spawn accepted"
elif grep -q "chief" "$WORK"/err; then ok "leaf spawn refused with chief pointer"; else bad "leaf refusal unclear: $(cat "$WORK/err")"; fi
[ "$(grep -c "agent start wf-leaf" "$HERDR_CALLS")" -eq 0 ] && ok "refused spawn touched no Herdr" || bad "refused spawn called Herdr"
if WAYFINDER_ROLE=helper bash "$WORKER" prompt wf-a --text hi >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf prompt accepted"
else ok "leaf prompt refused"; fi
WAYFINDER_ROLE=ticket bash "$WORKER" status wf-a >/dev/null 2>&1 && ok "leaf status stays observable" || bad "leaf status blocked"

echo "5. concurrency is bounded"
export WAYFINDER_MAX_WORKERS=1 WAYFINDER_WORKER_REGISTRY="$WORK/cap.tsv"
: > "$WORK/cap.tsv"
bash "$WORKER" spawn --role ticket --ticket 201 --workspace "$WORK/ws" --name wf-cap1 >/dev/null 2>&1 \
    || bad "first spawn at MAX=1 failed"
if bash "$WORKER" spawn --role ticket --ticket 202 --workspace "$WORK/ws" --name wf-cap2 >"$WORK/out" 2>"$WORK/err"; then
    bad "over-capacity spawn accepted"
elif grep -q "capacity" "$WORK/err"; then ok "over-capacity refused with capacity message"; else bad "wrong refusal: $(cat "$WORK/err")"; fi
export WAYFINDER_MAX_WORKERS=5 WAYFINDER_WORKER_REGISTRY="$WORK/workers.tsv"

echo "6. workspace must exist; names are validated"
run spawn --role ticket --ticket 104 --workspace "$WORK/missing" --name wf-miss
[ "$(rc)" -ne 0 ] && ok "missing workspace refused" || bad "missing workspace accepted"
run spawn --role ticket --ticket 104 --workspace "$WORK/ws" --name 'Bad; rm -rf'
[ "$(rc)" -ne 0 ] && ok "injection name refused" || bad "injection name accepted"
run prompt wf-unknown --text hi; [ "$(rc)" -ne 0 ] && ok "prompt of unknown worker refused" || bad "prompt of unknown worker accepted"

echo "7. wait is the bounded check-in call"
run wait wf-a --timeout 60000
[ "$(rc)" -eq 0 ] || bad "wait failed"
grep -q "agent wait wf-a --timeout 60000" "$HERDR_CALLS" && ok "wait forwards explicit timeout" || bad "wait timeout not forwarded"
run wait wf-a
grep -q "agent wait wf-a --timeout 300000" "$HERDR_CALLS" && ok "wait defaults to bounded 300s check-in" || bad "wait default timeout missing"

echo "8. stop/cleanup lifecycle with live-agent guard"
run stop wf-a; [ "$(rc)" -eq 0 ] && ok "stop ok" || bad "stop failed: $(cat "$WORK/err")"
grep -q "agent send-keys wf-a ctrl+c" "$HERDR_CALLS" && ok "stop interrupts via send-keys" || bad "stop used wrong herdr call"
run cleanup wf-maintenance; [ "$(rc)" -ne 0 ] && ok "cleanup of running worker refused" || bad "cleanup of running worker accepted"
bash "$WORKER" cleanup wf-a >/dev/null 2>&1 && ok "cleanup of stopped worker ok" || bad "cleanup of stopped worker failed"
[ "$(grep -c '^wf-a' "$WORK/workers.tsv" || true)" -eq 0 ] \
    && ok "cleanup removed the row" || bad "row survived cleanup"

echo "9. reconcile adopts live state, marks missing gone, reports unmanaged"
printf 'wf-r1\tticket\t301\t%s\tpane-7\trunning\tt0\tt0\tnote\n' "$WORK/ws" > "$WORK/workers.tsv"
printf 'wf-r2\tticket\t302\t%s\tpane-7\trunning\tt0\tt0\tnote\n' "$WORK/ws" >> "$WORK/workers.tsv"
printf 'done' > "$WORK/status-wf-r1"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-r1","status":"done"},{"name":"wf-stray","status":"running"}]}}
JSON
bash "$WORKER" reconcile >"$WORK/out" 2>"$WORK/err" || bad "reconcile failed: $(cat "$WORK/err")"
grep -q $'^wf-r1\tticket\t301\t.*\tdone\t' "$WORK/workers.tsv" && ok "live done adopted" || bad "done not adopted"
grep -q $'^wf-r2\tticket\t302\t.*\tgone\t' "$WORK/workers.tsv" && ok "missing worker marked gone, row kept" || bad "gone handling wrong"
grep -q "unmanaged: wf-stray" "$WORK/out" && ok "unmanaged live agent reported, not adopted" || bad "unmanaged not reported"
grep -q "reconciled:" "$WORK/out" && ok "reconcile summary printed" || bad "no summary"

echo "10. WAYFINDER_MAX_WORKERS=1 stays valid; 0 is rejected"
export WAYFINDER_WORKER_REGISTRY="$WORK/seq.tsv" WAYFINDER_MAX_WORKERS=1
: > "$WORK/seq.tsv"
bash "$WORKER" spawn --role ticket --ticket 401 --workspace "$WORK/ws" --name wf-seq >/dev/null 2>&1 \
    && ok "MAX=1 sequential spawn valid" || bad "MAX=1 spawn failed"
export WAYFINDER_MAX_WORKERS=0
bash "$WORKER" spawn --role ticket --ticket 402 --workspace "$WORK/ws" --name wf-seq2 >/dev/null 2>&1 \
    && bad "MAX=0 accepted" || ok "MAX=0 rejected"
export WAYFINDER_MAX_WORKERS=5

echo "11. no Git integration and no workspace creation in the seam"
if grep -v '^#' "$WORKER" | grep -w -q 'git'; then bad "seam invokes git"; else ok "seam never invokes git"; fi
if grep -v '^#' "$WORKER" | grep -Eq 'git worktree|mkdir.*[Ww]orkspace'; then bad "seam creates workspaces"; else ok "seam never creates workspaces"; fi

echo "12. helper mediation: parent association, no nesting, ticket match (map #697 #737)"
export WAYFINDER_WORKER_REGISTRY="$WORK/helpers.tsv" WAYFINDER_MAX_WORKERS=5
: > "$WORK/helpers.tsv"
mkdir -p "$WORK/ws2"
bash "$WORKER" spawn --role ticket --ticket 501 --workspace "$WORK/ws" --name wf-par >/dev/null 2>&1 \
    || bad "parent ticket spawn failed"
run spawn --role helper --ticket 501 --workspace "$WORK/ws2" --parent wf-par --scope "backend investigation" --name wf-h1
[ "$(rc)" -eq 0 ] && ok "chief-mediated helper accepted" || bad "helper rejected: $(cat "$WORK/err")"
grep -q $'^wf-h1\thelper\t501\t.*\trunning\t.*helper for wf-par: backend investigation' "$WORK/helpers.tsv" \
    && ok "helper row records scope note" || bad "helper note wrong: $(cat "$WORK/helpers.tsv")"
[ "$(awk -F'\t' -v n=wf-h1 '$1 == n { print $10; exit }' "$WORK/helpers.tsv")" = "wf-par" ] \
    && ok "helper row carries parent association" || bad "parent column missing"
run spawn --role helper --ticket 501 --workspace "$WORK/ws2" --parent wf-h1 --scope "nested" --name wf-nest
[ "$(rc)" -ne 0 ] && ok "nested helper under a helper refused" || bad "nested helper accepted"
run spawn --role helper --ticket 999 --workspace "$WORK/ws2" --parent wf-par --scope "wrong ticket" --name wf-wrong
[ "$(rc)" -ne 0 ] && ok "helper ticket mismatch refused" || bad "helper ticket mismatch accepted"
run spawn --role helper --ticket 501 --workspace "$WORK/ws2" --parent wf-ghost --scope "ghost" --name wf-ghost-h
[ "$(rc)" -ne 0 ] && ok "helper under unknown parent refused" || bad "helper under unknown parent accepted"
run spawn --role helper --ticket 501 --workspace "$WORK/ws2" --parent wf-par --name wf-noscope
[ "$(rc)" -eq 2 ] && ok "helper without scope rejected" || bad "helper without scope accepted"
run spawn --role helper --ticket 501 --workspace "$WORK/ws" --parent wf-par --scope "shared checkout" --name wf-shared
[ "$(rc)" -ne 0 ] && ok "helper sharing the parent workspace refused" || bad "helper sharing parent workspace accepted"

echo "13. helpers count toward capacity and survive status updates with parent intact"
export WAYFINDER_WORKER_REGISTRY="$WORK/hcap.tsv" WAYFINDER_MAX_WORKERS=2
: > "$WORK/hcap.tsv"
bash "$WORKER" spawn --role ticket --ticket 601 --workspace "$WORK/ws" --name wf-hcpar >/dev/null 2>&1 \
    || bad "cap parent spawn failed"
bash "$WORKER" spawn --role helper --ticket 601 --workspace "$WORK/ws2" --parent wf-hcpar --scope "s1" --name wf-hc1 >/dev/null 2>&1 \
    || bad "cap helper spawn failed"
if bash "$WORKER" spawn --role ticket --ticket 602 --workspace "$WORK/ws" --name wf-hc2 >"$WORK/out" 2>"$WORK/err"; then
    bad "third spawn past helper-occupied capacity accepted"
elif grep -q "capacity" "$WORK/err"; then ok "helper occupies a capacity slot"; else bad "wrong refusal: $(cat "$WORK/err")"; fi
bash "$WORKER" stop wf-hc1 >/dev/null 2>&1 || bad "helper stop failed"
[ "$(awk -F'\t' -v n=wf-hc1 '$1 == n { print $10; exit }' "$WORK/hcap.tsv")" = "wf-hcpar" ] \
    && ok "parent survives stop" || bad "parent dropped by stop"
printf 'done' > "$WORK/status-wf-hcpar"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-hcpar","status":"done"},{"name":"wf-hc1","status":"idle"}]}}
JSON
bash "$WORKER" reconcile >/dev/null 2>&1 || bad "reconcile with helper failed"
[ "$(awk -F'\t' -v n=wf-hc1 '$1 == n { print $10; exit }' "$WORK/hcap.tsv")" = "wf-hcpar" ] \
    && ok "parent survives reconcile" || bad "parent dropped by reconcile"
export WAYFINDER_WORKER_REGISTRY="$WORK/workers.tsv" WAYFINDER_MAX_WORKERS=5
rm -f "$WORK"/list.json "$WORK"/status-*

echo
if [ $fail -eq 0 ]; then echo "worker-contract: OK"; else echo "worker-contract: FAILURES PRESENT"; exit 1; fi
