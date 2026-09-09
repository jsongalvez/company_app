#!/usr/bin/env bash
# wayfinder-chief-test.sh — contract tests for wayfinder-chief.sh (map #697 #735).
# Fake-gh + fake-herdr fixtures only: no real tracker, Herdr, Gradle, DB, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHIEF="$ROOT/tools/wayfinder/wayfinder-chief.sh"
WORKER="$ROOT/tools/wayfinder/wayfinder-worker.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-chief-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$CHIEF" || { echo "FAIL: chief fails bash -n"; exit 1; }
bash -n "$WORKER" || { echo "FAIL: worker fails bash -n"; exit 1; }

mkdir -p "$WORK/ws" "$WORK/bin"
export HERDR_CALLS="$WORK/herdr-calls" GH_CALLS="$WORK/gh-calls" WORK
: > "$HERDR_CALLS"; : > "$GH_CALLS"

cat >"$WORK/bin/herdr" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$HERDR_CALLS"
case "${1:-} ${2:-}" in
    "pane split") printf '{"result":{"pane":{"pane_id":"pane-7"}}}'; exit 0 ;;
    "agent start") if [ -f "$WORK/fail-start-$3" ]; then printf 'injected start failure\n' >&2; exit 1; fi; printf '{"result":{"agent":{"name":"%s","status":"running"}}}' "$3"; exit 0 ;;
    "agent prompt") exit 0 ;;
    "agent list")
        if [ -f "$WORK/list.json" ]; then cat "$WORK/list.json"; else printf '{"result":{"agents":[]}}'; fi
        exit 0 ;;
    "agent send-keys") exit 0 ;;
esac
printf 'unexpected herdr: %s\n' "$*" >&2; exit 1
EOF
chmod +x "$WORK/bin/herdr"

# Fake gh applies the caller's --jq filter to raw fixtures (faithful shape).
cat >"$WORK/bin/gh" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$GH_CALLS"
if [ "${1:-}" = repo ]; then printf 'fixture/repo'; exit 0; fi
endpoint=""; filter=""
prev=""
for arg in "$@"; do
    case "$arg" in repos/*) endpoint="$arg" ;; esac
    if [ "$prev" = "--jq" ]; then filter="$arg"; fi
    prev="$arg"
done
case "$endpoint" in
    */issues/*/sub_issues) map="${endpoint%%/sub_issues}"; map="${map##*/}" ;;
esac
if [[ "$endpoint" == */sub_issues ]]; then
    raw="$WORK/sub-${map}.json"
else
    n="${endpoint##*/}"
    raw="$WORK/issue-${n}.json"
fi
[ -f "$raw" ] || { printf 'missing fixture: %s\n' "$raw" >&2; exit 1; }
if [ -n "$filter" ]; then jq -r "$filter" "$raw"; else cat "$raw"; fi
EOF
chmod +x "$WORK/bin/gh"

export HERDR_BIN="$WORK/bin/herdr" WAYFINDER_GH_BIN="$WORK/bin/gh"
export WAYFINDER_GH_REPO=fixture/repo WAYFINDER_WORKER_KIND=opencode
export WAYFINDER_PARALLEL=on
export PATH="$WORK/bin:$PATH"

mkissue() { # <n> <state> <blocked> <assignees-json> <labels-json>
    cat >"$WORK/issue-$1.json" <<JSON
{"number":$1,"state":"$2","issue_dependencies_summary":{"blocked_by":$3,"blocking":0},"assignees":$4,"labels":$5}
JSON
}
LBL_TASK='[{"name":"wayfinder:task"}]'
chief() { bash "$CHIEF" "$@" >"$WORK/out" 2>"$WORK/err" || { bad "chief failed: $(cat "$WORK/err")"; return 1; }; }
fresh_reg() { export WAYFINDER_WORKER_REGISTRY="$WORK/reg-$1.tsv"; : > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"; : > "$GH_CALLS"; rm -f "$WORK"/list.json; }

echo "1. reconcile runs before any spawn; fill is async (no agent wait)"
fresh_reg 1
printf '[{"number":101}]' >"$WORK/sub-697.json"
mkissue 101 open 0 '[]' "$LBL_TASK"
chief --map 697 --once --max-workers 2
grep -q $'^wf-697-101\tticket\t101\t' "$WAYFINDER_WORKER_REGISTRY" && ok "frontier ticket spawned with map-scoped name" || bad "no spawn: $(cat "$WORK/out" "$WORK/err")"
first_list="$(grep -n 'agent list' "$HERDR_CALLS" | head -1 | cut -d: -f1)"
first_start="$(grep -n 'agent start' "$HERDR_CALLS" | head -1 | cut -d: -f1)"
[ -n "$first_list" ] && [ "$first_list" -lt "$first_start" ] && ok "worker reconcile precedes spawn" || bad "reconcile not first"
grep -q 'agent wait' "$HERDR_CALLS" && bad "chief --once performed a blocking wait" || ok "--once performs no blocking waits"
grep -E 'agent (start|prompt)' "$HERDR_CALLS" | grep -q -- '--wait' && bad "--wait leaked into dispatch" || ok "dispatch async (no --wait)"

echo "2. completed slot refills while unrelated workers run; no duplicates"
fresh_reg 2
printf 'wf-697-101\tticket\t101\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-697-102\tticket\t102\t%s\tpane-7\tdone\tt0\tt0\tfinished\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
printf '[{"number":101},{"number":102},{"number":103}]' >"$WORK/sub-697.json"
mkissue 101 open 0 '[]' "$LBL_TASK"; mkissue 102 open 0 '[]' "$LBL_TASK"; mkissue 103 open 0 '[]' "$LBL_TASK"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-697-101","status":"running"},{"name":"wf-697-102","status":"done"}]}}
JSON
chief --map 697 --once --max-workers 2
grep -q $'^wf-697-102\tticket\t102\t.*\tready-for-review\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "done worker collected to ready-for-review (no git, row kept)" || bad "collect wrong: $(cat "$WAYFINDER_WORKER_REGISTRY")"
grep -q $'^wf-697-103\tticket' "$WAYFINDER_WORKER_REGISTRY" && ok "free slot refilled with ticket 103" || bad "no refill"
[ "$(grep -c '^wf-697-102' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] && ok "no duplicate worker for collected ticket" || bad "duplicate rows"
grep -q $'^wf-697-101\tticket\t101\t.*\trunning\t' "$WAYFINDER_WORKER_REGISTRY" && ok "unrelated running worker untouched" || bad "running worker disturbed"

echo "3. blocked, assigned, and human-deferred tickets never fill"
fresh_reg 3
printf '[{"number":201},{"number":202},{"number":203},{"number":204}]' >"$WORK/sub-697.json"
mkissue 201 open 1 '[]' "$LBL_TASK"
mkissue 202 open 0 '[{"login":"someone"}]' "$LBL_TASK"
mkissue 203 open 0 '[]' '[{"name":"wayfinder:task"},{"name":"needs-info"}]'
mkissue 204 open 0 '[]' "$LBL_TASK"
chief --map 697 --once --max-workers 9
[ "$(grep -c '^wf-697-' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] \
    && grep -q $'^wf-697-204\tticket' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "only the claimable ticket filled" || bad "gate violated: $(cat "$WAYFINDER_WORKER_REGISTRY")"

echo "4. priority wins over number; MAX=1 spawns exactly one"
fresh_reg 4
printf '[{"number":301},{"number":302}]' >"$WORK/sub-697.json"
mkissue 301 open 0 '[]' "$LBL_TASK"
mkissue 302 open 0 '[]' '[{"name":"wayfinder:task"},{"name":"priority:P0"}]'
chief --map 697 --once --max-workers 1
grep -q $'^wf-697-302\tticket' "$WAYFINDER_WORKER_REGISTRY" \
    && [ "$(grep -c '^wf-697-' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] \
    && ok "P0 outranks lower number at MAX=1" || bad "priority/cap wrong: $(cat "$WAYFINDER_WORKER_REGISTRY")"
fresh_reg 4b
mkissue 302 open 0 '[]' "$LBL_TASK"   # same rank now: lowest number must win
chief --map 697 --once --max-workers 1
[ "$(grep -c '^wf-697-' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] \
    && grep -q $'^wf-697-301\tticket' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "sequential fallback takes lowest number first" || bad "order wrong"

echo "5. dry-run is side-effect free"
fresh_reg 5
before="$(sha256sum "$WAYFINDER_WORKER_REGISTRY" | awk '{print $1}')"
chief --map 697 --once --max-workers 2 --dry-run
[ "$(sha256sum "$WAYFINDER_WORKER_REGISTRY" | awk '{print $1}')" = "$before" ] && ok "registry untouched" || bad "dry-run wrote registry"
[ -s "$HERDR_CALLS" ] && bad "dry-run called herdr" || ok "dry-run called no herdr"
grep -q 'would spawn' "$WORK/out" && ok "dry-run logs the plan" || bad "no plan logged"
[ -s "$GH_CALLS" ] && ok "dry-run still reads the frontier" || bad "dry-run skipped frontier read"

echo "6. missing per-ticket workspace waits for the provider instead of creating"
fresh_reg 6
mkdir -p "$WORK/base"
printf '[{"number":205}]' >"$WORK/sub-697.json"
mkissue 205 open 0 '[]' "$LBL_TASK"
chief --map 697 --once --workspace-base "$WORK/base"
grep -q 'awaits workspace provider' "$WORK/out" && ok "provider wait logged" || bad "no provider note"
[ "$(grep -c '^wf-697-' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] && ok "nothing spawned without a workspace" || bad "spawned without workspace"
mkdir -p "$WORK/base/wf-205"
chief --map 697 --once --workspace-base "$WORK/base"
grep -q "wf-697-205"$'\tticket\t205\t'"$WORK/base/wf-205"$'\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "spawned once the provider dir exists" || bad "no spawn after provider: $(cat "$WAYFINDER_WORKER_REGISTRY")"

echo "7. empty frontier exits clean; chief owns no Git integration"
fresh_reg 7
printf '[]' >"$WORK/sub-697.json"
chief --map 697 --once
grep -q 'empty frontier' "$WORK/out" && ok "empty frontier logged, exit 0" || bad "empty frontier mishandled"
if grep -v '^#' "$CHIEF" | grep -w -q 'git'; then bad "chief invokes git"; else ok "chief never invokes git"; fi
if grep -v '^#' "$WORKER" | grep -w -q 'git'; then bad "worker seam invokes git"; else ok "worker seam never invokes git"; fi

echo "8. ticket prompt carries the worker contract (map #697 #737)"
fresh_reg 8
printf '[{"number":801}]' >"$WORK/sub-697.json"
mkissue 801 open 0 '[]' "$LBL_TASK"
chief --map 697 --once --max-workers 1 --base abcdef1234567890
for token in "ROLE:" "TICKET:" "WORKSPACE:" "BASE:" "COMMIT:" "SUMMARY:" "FILES:" "VERIFICATION:" "RISKS" "HELP REQUESTS" "assign-first" "blocked" "exactly one ticket"; do
    grep -q -- "$token" "$HERDR_CALLS" && ok "prompt carries $token" || bad "prompt missing $token"
done
grep -q -- "801" "$HERDR_CALLS" && ok "prompt names the ticket" || bad "prompt missing ticket number"
grep -q -- "abcdef1234567890" "$HERDR_CALLS" && ok "prompt carries dispatched base" || bad "prompt missing --base value"
if grep -- "agent prompt" "$HERDR_CALLS" | grep -q -- '--wait'; then bad "prompt path leaked --wait"; else ok "contract prompt stays async"; fi

echo "9. chief-mediated helper spawn with parent association (map #697 #737)"
fresh_reg 9
printf 'wf-697-901\tticket\t901\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
mkdir -p "$WORK/helper-ws"
if bash "$CHIEF" --map 697 --max-workers 2 --spawn-helper wf-697-901 --scope "backend investigation" --helper-workspace "$WORK/helper-ws" >"$WORK/out" 2>"$WORK/err"; then
    ok "helper spawn accepted"
else bad "helper spawn failed: $(cat "$WORK/err")"; fi
grep -q $'^wf-697-901-h1\thelper\t901\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "helper registered under parent ticket" || bad "helper row missing: $(cat "$WAYFINDER_WORKER_REGISTRY")"
[ "$(awk -F'\t' -v n=wf-697-901-h1 '$1 == n { print $10; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "wf-697-901" ] \
    && ok "helper carries parent association" || bad "helper parent column missing"
grep -q -- "SCOPE: backend investigation" "$HERDR_CALLS" && ok "helper prompt carries bounded scope" || bad "helper prompt missing scope"
grep -q -- "PARENT: wf-697-901" "$HERDR_CALLS" && ok "helper prompt names parent" || bad "helper prompt missing parent"
if bash "$CHIEF" --map 697 --spawn-helper wf-ghost --scope "x" --helper-workspace "$WORK/helper-ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "helper under unknown parent accepted"
else ok "helper under unknown parent refused"; fi
if bash "$CHIEF" --map 697 --spawn-helper wf-697-901 --helper-workspace "$WORK/helper-ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "helper without scope accepted"
else ok "helper without scope refused"; fi
if bash "$CHIEF" --map 697 --spawn-helper wf-697-901-h1 --scope "nested" --helper-workspace "$WORK/helper-ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "nested helper under a helper accepted"
else ok "nested helper refused"; fi
if bash "$CHIEF" --map 697 --spawn-helper wf-697-901 --scope "x" --helper-workspace "$WORK/missing-ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "helper with missing workspace accepted"
else ok "helper with missing workspace refused"; fi
if bash "$CHIEF" --map 697 --once --scope "stray" >"$WORK/out" 2>"$WORK/err"; then
    bad "stray --scope without --spawn-helper accepted"
else ok "stray helper flags without --spawn-helper refused"; fi

echo "10. spawn failure breaks the fill instead of counting as spawned (map #697 #737)"
fresh_reg 10
printf '[{"number":1001},{"number":1002}]' >"$WORK/sub-697.json"
mkissue 1001 open 0 '[]' "$LBL_TASK"; mkissue 1002 open 0 '[]' "$LBL_TASK"
: > "$WORK/fail-start-wf-697-1001"
chief --map 697 --once --max-workers 2
[ "$(grep -c '^wf-697-' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] \
    && ok "failed spawn left no row and filled nothing further" || bad "fill miscounted: $(cat "$WAYFINDER_WORKER_REGISTRY")"
grep -q 'spawn refused for ticket #1001' "$WORK/out" && ok "refusal logged, fill stopped" || bad "no refusal log: $(cat "$WORK/out")"
rm -f "$WORK"/fail-start-*

echo "11. parallel rollout gate: WAYFINDER_PARALLEL=off clamps fill to sequential (map #697 #743)"
fresh_reg 11
printf '[{"number":1101},{"number":1102}]' >"$WORK/sub-697.json"
mkissue 1101 open 0 '[]' "$LBL_TASK"; mkissue 1102 open 0 '[]' "$LBL_TASK"
WAYFINDER_PARALLEL=off bash "$CHIEF" --map 697 --once --max-workers 2 >"$WORK/out" 2>"$WORK/err" \
    || bad "clamped pass failed: $(cat "$WORK/err")"
[ "$(grep -c '^wf-697-' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] \
    && ok "off clamps parallel fill to exactly one worker" || bad "clamp wrong: $(cat "$WAYFINDER_WORKER_REGISTRY")"
grep -q 'sequential fallback' "$WORK/out" && ok "clamp logged with opt-in pointer" || bad "no clamp log: $(cat "$WORK/out")"
if WAYFINDER_PARALLEL=bogus bash "$CHIEF" --map 697 --once >"$WORK/out" 2>"$WORK/err"; then
    bad "bogus WAYFINDER_PARALLEL accepted"
else ok "bogus WAYFINDER_PARALLEL refused"; fi
WAYFINDER_PARALLEL=off bash "$CHIEF" --map 697 --spawn-helper wf-ghost --scope "x" --helper-workspace "$WORK/helper-ws" >"$WORK/out" 2>"$WORK/err" \
    && bad "clamp leaked into single-shot lane validation" || ok "single-shot lanes stay available when parallel is off"

echo "12. unmanaged Wayfinder strays gate the fill; generation is minted once (map #697 #746)"
fresh_reg 12
printf '[{"number":1201}]' >"$WORK/sub-697.json"
mkissue 1201 open 0 '[]' "$LBL_TASK"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-697-999","status":"running"}]}}
JSON
chief --map 697 --once --max-workers 2
[ "$(grep -c '^wf-697-1201' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] \
    && ok "fill skipped while a wf-* stray holds unknown state" || bad "filled around the stray (duplicate risk)"
grep -q "unmanaged Wayfinder agent(s) present:.*wf-697-999" "$WORK/out" \
    && ok "stray gate logged with the stray name" || bad "no stray log: $(cat "$WORK/out")"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"my-own-agent","status":"running"}]}}
JSON
chief --map 697 --once --max-workers 2
grep -q $'^wf-697-1201\tticket' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "non-Wayfinder agents never gate the fill" || bad "foreign agent blocked fill"
gen12="$(awk -F'\t' -v n=wf-697-1201 '$1 == n { print $12; exit }' "$WAYFINDER_WORKER_REGISTRY")"
[ -n "$gen12" ] && ok "chief minted a generation when unset ($gen12)" || bad "no minted generation recorded"
fresh_reg 12b
printf '[{"number":1202}]' >"$WORK/sub-697.json"
mkissue 1202 open 0 '[]' "$LBL_TASK"
WAYFINDER_GENERATION=gen-pinned bash "$CHIEF" --map 697 --once --max-workers 1 >"$WORK/out" 2>"$WORK/err" \
    || bad "pinned-generation pass failed: $(cat "$WORK/err")"
[ "$(awk -F'\t' -v n=wf-697-1202 '$1 == n { print $12; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "gen-pinned" ] \
    && ok "explicit WAYFINDER_GENERATION preserved (mint never overrides)" || bad "explicit generation clobbered"
grep -q "minted chief generation" "$WORK/out" && bad "mint logged despite explicit generation" || ok "no mint log when generation given"

echo
if [ $fail -eq 0 ]; then echo "chief-contract: OK"; else echo "chief-contract: FAILURES PRESENT"; exit 1; fi
