#!/usr/bin/env bash
# wayfinder-capacity-test.sh — contract tests for wayfinder-capacity.sh + chief
# role-aware lanes (map #697 #742).
# Fake-herdr fixtures + real git fixture SHAs only: no real Herdr, tracker,
# Gradle, database, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CAPACITY="$ROOT/tools/wayfinder/wayfinder-capacity.sh"
CHIEF="$ROOT/tools/wayfinder/wayfinder-chief.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-capacity-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$CAPACITY" || { echo "FAIL: capacity fails bash -n"; exit 1; }
bash -n "$CHIEF" || { echo "FAIL: chief fails bash -n"; exit 1; }

mkdir -p "$WORK/ws" "$WORK/ws2" "$WORK/bin"
export HERDR_CALLS="$WORK/herdr-calls"
: > "$HERDR_CALLS"

cat >"$WORK/bin/herdr" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$HERDR_CALLS"
case "${1:-} ${2:-}" in
    "pane split") printf '{"result":{"pane":{"pane_id":"pane-7"}}}'; exit 0 ;;
    "agent start") printf '{"result":{"agent":{"name":"%s","status":"running"}}}' "$3"; exit 0 ;;
    "agent prompt") exit 0 ;;
    "agent list") printf '{"result":{"agents":[]}}'; exit 0 ;;
    "agent send-keys") exit 0 ;;
esac
printf 'unexpected herdr: %s\n' "$*" >&2; exit 1
EOF
chmod +x "$WORK/bin/herdr"

export HERDR_BIN="$WORK/bin/herdr" PATH="$WORK/bin:$PATH" WORK
export WAYFINDER_GH_REPO=fixture/repo WAYFINDER_WORKER_KIND=opencode
export WAYFINDER_ROLE=chief

fresh() { # fresh <tag> — isolated registries for one case.
    export WAYFINDER_WORKER_REGISTRY="$WORK/workers-$1.tsv"
    export WAYFINDER_SCOUT_REGISTRY="$WORK/scout-$1.tsv"
    export WAYFINDER_HEAVY_REGISTRY="$WORK/heavy-$1.tsv"
    export WAYFINDER_EVENTS_LOG="$WORK/events-$1.log"
    : > "$WAYFINDER_WORKER_REGISTRY"; : > "$WAYFINDER_SCOUT_REGISTRY"
    : > "$WAYFINDER_HEAVY_REGISTRY"; rm -f "$WAYFINDER_EVENTS_LOG"
    : > "$HERDR_CALLS"
    export WAYFINDER_MAX_WORKERS=6 WAYFINDER_MAX_MAINTENANCE_WORKERS=1
    export WAYFINDER_MAX_BUG_SCOUTS=1 WAYFINDER_MAX_HELPERS=2 WAYFINDER_MAX_HEAVY_JOBS=2
}

run() { local rc=0; bash "$CAPACITY" "$@" >"$WORK/out" 2>"$WORK/err" || rc=$?; printf "%s" "$rc" >"$WORK/rc"; }
rc() { cat "$WORK/rc"; }
wrow() { # wrow <name> <role> <ticket> <status>
    printf '%s\t%s\t%s\t%s\tpane-7\t%s\tt0\tt0\tnote\t\t697\tg1\n' "$1" "$2" "$3" "$WORK/ws" "$4" >> "$WAYFINDER_WORKER_REGISTRY"
}

echo "1. limits report effective bounds with live usage"
fresh 1
wrow wf-a ticket 101 running
run limits
[ "$(rc)" -eq 0 ] || bad "limits failed: $(cat "$WORK/err")"
grep -q 'global 1/6 busy' "$WORK/out" && ok "global usage shown" || bad "global line wrong: $(cat "$WORK/out")"
grep -q 'heavy 0/2' "$WORK/out" && ok "heavy bound shown" || bad "heavy line wrong"
run limits --machine
grep -q '^scope=heavy used=0 max=2 free=2$' "$WORK/out" && ok "machine limits parseable" || bad "machine limits wrong"

echo "2. check gates global and narrow ceilings"
fresh 2
run check --role ticket
[ "$(rc)" -eq 0 ] && ok "ticket allowed under capacity" || bad "ticket refused: $(cat "$WORK/err")"
export WAYFINDER_MAX_WORKERS=1
wrow wf-a ticket 101 running
if run check --role ticket; [ "$(rc)" -ne 0 ]; then ok "global ceiling refuses"; else bad "over-global check accepted"; fi
grep -q 'global capacity' "$WORK/err" && ok "refusal names the global bound" || bad "refusal unclear"
export WAYFINDER_MAX_WORKERS=6
wrow wf-m maintenance 900 running
if run check --role maintenance; [ "$(rc)" -ne 0 ]; then ok "narrow maintenance ceiling refuses"; else bad "over-maintenance check accepted"; fi
run check --role bug-scout
[ "$(rc)" -eq 0 ] && ok "scout allowed under its ceiling" || bad "scout refused: $(cat "$WORK/err")"
export WAYFINDER_MAX_BUG_SCOUTS=0
if run check --role bug-scout; [ "$(rc)" -ne 0 ]; then ok "scout disable (0) refuses"; else bad "disabled scout accepted"; fi
grep -q 'disabled' "$WORK/err" && ok "disable message explicit" || bad "disable message unclear"
export WAYFINDER_MAX_BUG_SCOUTS=1
if run check --role bogus; [ "$(rc)" -eq 2 ]; then ok "unknown role rejected"; else bad "unknown role accepted"; fi

echo "3. heavy acquire/release is worker-coordinated and idempotent"
fresh 3
wrow wf-a ticket 101 running
run heavy-acquire --holder wf-a --ticket 101 --note "gradle suite"
[ "$(rc)" -eq 0 ] || bad "acquire failed: $(cat "$WORK/err")"
grep -q $'^wf-a\t101\t' "$WAYFINDER_HEAVY_REGISTRY" \
    && ok "heavy row recorded" || bad "heavy row missing"
run heavy-acquire --holder wf-a --ticket 101
[ "$(rc)" -eq 0 ] && ok "duplicate acquire idempotent" || bad "duplicate acquire failed"
[ "$(grep -c '^wf-a' "$WAYFINDER_HEAVY_REGISTRY")" -eq 1 ] && ok "no duplicate heavy row" || bad "duplicate heavy rows"
run heavy-release --holder wf-a
[ "$(rc)" -eq 0 ] || bad "release failed"
[ ! -s "$WAYFINDER_HEAVY_REGISTRY" ] && ok "slot freed" || bad "slot survived release"
run heavy-release --holder wf-a
[ "$(rc)" -eq 0 ] && ok "absent release idempotent" || bad "absent release failed"
if run heavy-acquire --holder wf-ghost --ticket 101; [ "$(rc)" -ne 0 ]; then ok "unknown holder refused"; else bad "ghost acquire accepted"; fi

echo "4. heavy full is waiting-resource, never blocked-input"
fresh 4
export WAYFINDER_MAX_HEAVY_JOBS=1
wrow wf-a ticket 101 running
wrow wf-b ticket 102 running
bash "$CAPACITY" heavy-acquire --holder wf-a --ticket 101 >/dev/null 2>&1 || bad "first acquire failed"
if run heavy-acquire --holder wf-b --ticket 102; [ "$(rc)" -ne 0 ]; then ok "second acquire refused at 1/1"; else bad "over-heavy acquire accepted"; fi
grep -q 'waiting-resource' "$WORK/err" && ok "refusal says waiting-resource" || bad "refusal missing waiting-resource: $(cat "$WORK/err")"
grep -q 'NOT semantic blocked' "$WORK/err" && ok "refusal distinguishes blocked-input" || bad "blocked distinction missing"
grep -q $'^wf-b\t.*\tblocked\t' "$WAYFINDER_WORKER_REGISTRY" && bad "waiter row flipped to blocked" || ok "waiter stays running (not blocked)"
run heavy-status
grep -q 'heavy: 1/1 used, 0 free' "$WORK/out" && ok "heavy usage shown" || bad "heavy status wrong: $(cat "$WORK/out")"
export WAYFINDER_MAX_HEAVY_JOBS=2

echo "5. heavy-reconcile never leaks crashed-holder slots"
fresh 5
wrow wf-live ticket 101 running
printf 'wf-dead\tticket\t102\t%s\tpane-7\tgone\tt0\tt0\tcrashed\t\t697\tg1\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
bash "$CAPACITY" heavy-acquire --holder wf-live --ticket 101 >/dev/null 2>&1 || bad "live acquire failed"
printf 'wf-dead\t102\t2026-01-01 00:00\tstale hold\n' >> "$WAYFINDER_HEAVY_REGISTRY"
run heavy-reconcile
[ "$(rc)" -eq 0 ] || bad "reconcile failed: $(cat "$WORK/err")"
grep -q 'kept, 1 released' "$WORK/out" && ok "crashed holder released, live kept" || bad "reconcile verdict wrong: $(cat "$WORK/out")"
grep -q '^wf-live' "$WAYFINDER_HEAVY_REGISTRY" && ok "live holder survives" || bad "live holder dropped"
grep -q '^wf-dead' "$WAYFINDER_HEAVY_REGISTRY" && bad "crashed holder leaked" || ok "crashed holder gone"
if WAYFINDER_ROLE=ticket bash "$CAPACITY" heavy-reconcile >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf heavy-reconcile accepted"
else ok "heavy-reconcile requires the chief"; fi

echo "6. status answers the operator questions without Herdr/gh/git"
fresh 6
wrow wf-impl ticket 101 running
printf 'wf-block\tmaintenance\t900\t%s\tpane-7\tblocked\tt0\tt0\tneeds decision\t\t697\tg1\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-rev\tticket\t102\t%s\tpane-7\tready-for-review\tt0\tt0\tawaiting chief review\t\t697\tg1\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
bash "$CAPACITY" heavy-acquire --holder wf-impl --ticket 101 >/dev/null 2>&1 || bad "status fixture acquire failed"
run status
[ "$(rc)" -eq 0 ] || bad "status failed: $(cat "$WORK/err")"
for token in "blocked-input: wf-block" "waiting-resource (heavy holders): wf-impl" "review-pending: wf-rev" "integration gate:" "quiescence:" "restart-risk:" "scout:" "maintenance:"; do
    grep -q -- "$token" "$WORK/out" && ok "status shows $token" || bad "status missing $token: $(cat "$WORK/out")"
done
run status --machine
grep -q '^worker name=wf-block role=maintenance ticket=900 status=blocked' "$WORK/out" && ok "machine worker rows carry identity" || bad "machine rows wrong"
grep -q '^scope=heavy used=1 max=2 free=1$' "$WORK/out" && ok "machine heavy free shown" || bad "machine heavy wrong"
grep -q '^quiescence=' "$WORK/out" && ok "machine quiescence shown" || bad "machine quiescence missing"
if grep -v '^#' "$CAPACITY" | grep -w -q 'git'; then bad "capacity invokes git"; else ok "capacity never invokes git"; fi

echo "7. lifecycle events carry role/task/worker/workspace identity"
fresh 7
run emit worker-dispatched --worker wf-697-101 --role ticket --ticket 101 --workspace "$WORK/ws" --detail "spawned"
[ "$(rc)" -eq 0 ] || bad "emit failed: $(cat "$WORK/err")"
run events --lines 5
grep -q $'worker-dispatched\twf-697-101\tticket\t101\t'"$WORK/ws" "$WORK/out" \
    && ok "event carries worker/role/ticket/workspace" || bad "event identity wrong: $(cat "$WORK/out")"
bash "$CAPACITY" heavy-acquire --holder wf-697-101 --ticket 101 >/dev/null 2>&1 || {
    printf 'wf-697-101\tticket\t101\t%s\tpane-7\trunning\tt0\tt0\tnote\t\t697\tg1\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
    bash "$CAPACITY" heavy-acquire --holder wf-697-101 --ticket 101 >/dev/null 2>&1 || bad "acquire for event check failed"
}
bash "$CAPACITY" events --lines 3 | grep -q 'heavy-acquired' && ok "heavy acquire emits" || bad "heavy-acquired event missing"

echo "8. chief scout/helper lanes enforce narrow ceilings (map #697 #742)"
fresh 8
wrow wf-697-901 ticket 901 running
if bash "$CHIEF" --map 697 --spawn-scout backend-domain --scout-workspace "$WORK/ws" >"$WORK/out" 2>"$WORK/err"; then
    ok "first scout dispatched"
else bad "first scout refused: $(cat "$WORK/err")"; fi
grep -q $'bug-scout\t697\t' "$WAYFINDER_WORKER_REGISTRY" && ok "scout row anchors ticket=map" || bad "scout row missing"
if bash "$CHIEF" --map 697 --spawn-scout api-routes-auth --scout-workspace "$WORK/ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "second scout past 1/1 accepted"
elif grep -q -i 'scout capacity' "$WORK/err"; then ok "second scout refused at ceiling"; else bad "wrong scout refusal: $(cat "$WORK/err")"; fi
export WAYFINDER_MAX_HELPERS=1
if bash "$CHIEF" --map 697 --spawn-helper wf-697-901 --scope "backend investigation" --helper-workspace "$WORK/ws2" >"$WORK/out" 2>"$WORK/err"; then
    ok "first helper dispatched"
else bad "first helper refused: $(cat "$WORK/err")"; fi
mkdir -p "$WORK/ws3"
if bash "$CHIEF" --map 697 --spawn-helper wf-697-901 --scope "second scope" --helper-workspace "$WORK/ws3" >"$WORK/out" 2>"$WORK/err"; then
    bad "second helper past 1/1 accepted"
elif grep -q -i 'helper capacity' "$WORK/err"; then ok "second helper refused at ceiling"; else bad "wrong helper refusal: $(cat "$WORK/err")"; fi
export WAYFINDER_MAX_HELPERS=2

echo "9. sequential fallback: MAX_WORKERS=1 stays valid"
fresh 9
export WAYFINDER_MAX_WORKERS=1
run check --role ticket
[ "$(rc)" -eq 0 ] && ok "single slot allowed" || bad "MAX=1 refused"
wrow wf-a ticket 101 running
if run check --role ticket; [ "$(rc)" -ne 0 ]; then ok "second unit refused at MAX=1"; else bad "MAX=1 overfill accepted"; fi
export WAYFINDER_MAX_WORKERS=6

echo "10. chief --status delegates to the capacity view"
fresh 10
wrow wf-a ticket 101 running
if bash "$CHIEF" --map 697 --status >"$WORK/out" 2>"$WORK/err"; then
    ok "chief --status exits 0"
else bad "chief --status failed: $(cat "$WORK/err")"; fi
grep -q 'capacity:' "$WORK/out" && ok "chief status shows capacity" || bad "no capacity in chief status"

echo "11. chief runs emit structured events; dry-run stays silent"
fresh 11
cat >"$WORK/bin/gh" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = repo ]; then printf 'fixture/repo'; exit 0; fi
endpoint=""; filter=""; prev=""
for arg in "$@"; do
    case "$arg" in repos/*) endpoint="$arg" ;; esac
    if [ "$prev" = "--jq" ]; then filter="$arg"; fi
    prev="$arg"
done
if [[ "$endpoint" == */sub_issues ]]; then raw="$WORK/sub-697.json";
else n="${endpoint##*/}"; raw="$WORK/issue-$n.json"; fi
[ -f "$raw" ] || { printf 'missing fixture: %s\n' "$raw" >&2; exit 1; }
if [ -n "$filter" ]; then jq -r "$filter" "$raw"; else cat "$raw"; fi
EOF
chmod +x "$WORK/bin/gh"
export WAYFINDER_GH_BIN="$WORK/bin/gh"
printf '[{"number":1101}]' >"$WORK/sub-697.json"
cat >"$WORK/issue-1101.json" <<'JSON'
{"number":1101,"state":"open","issue_dependencies_summary":{"blocked_by":0,"blocking":0},"assignees":[],"labels":[{"name":"wayfinder:task"}]}
JSON
mkdir -p "$WORK/base/wf-1101"
if bash "$CHIEF" --map 697 --once --max-workers 2 --workspace-base "$WORK/base" >"$WORK/out" 2>"$WORK/err"; then
    ok "chief --once exits 0"
else bad "chief --once failed: $(cat "$WORK/err")"; fi
grep -q 'chief-started' "$WAYFINDER_EVENTS_LOG" && ok "chief-started emitted" || bad "no chief-started: $(cat "$WAYFINDER_EVENTS_LOG" 2>/dev/null)"
grep -q 'worker-dispatched' "$WAYFINDER_EVENTS_LOG" && ok "worker-dispatched emitted on fill" || bad "no worker-dispatched"
: > "$WAYFINDER_EVENTS_LOG"
if bash "$CHIEF" --map 697 --once --max-workers 2 --workspace-base "$WORK/base" --dry-run >"$WORK/out" 2>"$WORK/err"; then
    ok "dry-run exits 0"
else bad "dry-run failed: $(cat "$WORK/err")"; fi
[ -s "$WAYFINDER_EVENTS_LOG" ] && bad "dry-run emitted events" || ok "dry-run emitted nothing"

echo
if [ $fail -eq 0 ]; then echo "capacity-contract: OK"; else echo "capacity-contract: FAILURES PRESENT"; exit 1; fi
