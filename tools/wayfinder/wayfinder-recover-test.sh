#!/usr/bin/env bash
# wayfinder-recover-test.sh — contract tests for wayfinder-recover.sh + worker
# recovery identity + chief --recover lane + loop quiescence gate (map #697 #741).
# Fake-herdr/fake-gh fixtures + real git fixture repos only: no real Herdr,
# tracker, Gradle, DB, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RECOVER="$ROOT/tools/wayfinder/wayfinder-recover.sh"
WORKER="$ROOT/tools/wayfinder/wayfinder-worker.sh"
CHIEF="$ROOT/tools/wayfinder/wayfinder-chief.sh"
WORKSPACE="$ROOT/tools/wayfinder/wayfinder-workspace.sh"
LOOP="$ROOT/tools/wayfinder/wayfinder-loop.sh"
REVIEW="$ROOT/tools/wayfinder/wayfinder-review.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-recover-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$RECOVER" || { echo "FAIL: recover fails bash -n"; exit 1; }
bash -n "$WORKER" || { echo "FAIL: worker fails bash -n"; exit 1; }
bash -n "$CHIEF" || { echo "FAIL: chief fails bash -n"; exit 1; }
bash -n "$LOOP" || { echo "FAIL: loop fails bash -n"; exit 1; }

mkdir -p "$WORK/ws" "$WORK/ws2" "$WORK/bin"
export HERDR_CALLS="$WORK/herdr-calls" GH_CALLS="$WORK/gh-calls" WORK
: > "$HERDR_CALLS"; : > "$GH_CALLS"

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
    "agent send-keys") exit 0 ;;
esac
printf 'unexpected herdr: %s\n' "$*" >&2; exit 1
EOF
chmod +x "$WORK/bin/herdr"

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
if [[ "$endpoint" == */sub_issues ]]; then
    raw="$WORK/sub-697.json"
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
export PATH="$WORK/bin:$PATH"
export WAYFINDER_WORKER_REGISTRY="$WORK/workers.tsv"
export WAYFINDER_WORKSPACE_REGISTRY="$WORK/workspaces.tsv"
export WAYFINDER_INTEGRATION_GATE="$WORK/integration-gate"
export WAYFINDER_ROLE=chief WAYFINDER_MAX_WORKERS=5
: > "$WAYFINDER_WORKER_REGISTRY"; : > "$WORK/workspaces.tsv"

mkfixture() { # <name> — init a git repo with one commit, print path.
    local dir="$WORK/$1"
    mkdir -p "$dir"
    git init -q "$dir"
    git -C "$dir" config user.email test@test.test
    git -C "$dir" config user.name test
    echo "seed $1" > "$dir/seed.txt"
    git -C "$dir" add seed.txt
    git -C "$dir" commit -qm "seed $1"
    printf '%s' "$dir"
}

FIX="$(mkfixture repo)"
export WAYFINDER_REPO="$FIX"

mkrow() { # <name> <role> <ticket> <workspace> <status> [note] [parent [map [generation]]]
    printf '%s\t%s\t%s\t%s\tpane-7\t%s\tt0\tt0\t%s\t%s\t%s\t%s\n' \
        "$1" "$2" "$3" "$4" "$5" "${6:-reported}" "${7:-}" "${8:-}" "${9:-}" >> "$WAYFINDER_WORKER_REGISTRY"
}

recover() { bash "$RECOVER" "$@" >"$WORK/out" 2>"$WORK/err"; }
recover_rc() { bash "$RECOVER" "$@" >"$WORK/out" 2>"$WORK/err" || printf "%s" "$?"; }

echo "1. empty or missing registry is quiescent (sequential chain unaffected)"
: > "$WAYFINDER_WORKER_REGISTRY"
recover quiescence
grep -q "^QUIESCENT$" "$WORK/out" && ok "empty registry quiescent" || bad "empty not quiescent: $(cat "$WORK/out")"
rm -f "$WAYFINDER_WORKER_REGISTRY"
recover quiescence
grep -q "^QUIESCENT$" "$WORK/out" && ok "missing registry quiescent" || bad "missing not quiescent"
: > "$WAYFINDER_WORKER_REGISTRY"

echo "2. active writable workers block; terminal rows never block"
mkrow wf-a1 ticket 101 "$WORK/ws" running
if bash "$RECOVER" quiescence >"$WORK/out" 2>"$WORK/err"; then
    bad "active worker read quiescent"
else
    grep -q "BLOCKED: workers active:.*wf-a1" "$WORK/out" && ok "active worker blocks with name" || bad "wrong block reason: $(cat "$WORK/out")"
fi
: > "$WAYFINDER_WORKER_REGISTRY"
mkrow wf-t1 ticket 102 "$WORK/ws" integrated
mkrow wf-t2 ticket 103 "$WORK/ws" rejected
mkrow wf-t3 ticket 104 "$WORK/ws" cancelled
recover quiescence
grep -q "^QUIESCENT$" "$WORK/out" && ok "terminal rows quiescent" || bad "terminals block: $(cat "$WORK/out")"
: > "$WAYFINDER_WORKER_REGISTRY"

echo "3. review-pending results block advancement (done != accepted != integrated)"
for st in done blocked failed ready-for-review revision-requested accepted-awaiting-integration; do
    : > "$WAYFINDER_WORKER_REGISTRY"
    mkrow "wf-p" ticket 110 "$WORK/ws" "$st"
    if bash "$RECOVER" quiescence >"$WORK/out" 2>"$WORK/err"; then
        bad "status $st read quiescent"
    elif grep -q "BLOCKED: results awaiting review:.*wf-p" "$WORK/out"; then
        ok "$st blocks as awaiting review"
    else
        bad "$st wrong reason: $(cat "$WORK/out")"
    fi
done
: > "$WAYFINDER_WORKER_REGISTRY"

echo "4. crashed workers block and surface orphan release guidance (tracker untouched)"
mkrow wf-g1 ticket 120 "$WORK/ws" gone
if bash "$RECOVER" quiescence >"$WORK/out" 2>"$WORK/err"; then
    bad "gone worker read quiescent"
else
    grep -q "BLOCKED: crashed workers holding salvageable results:.*wf-g1" "$WORK/out" \
        && ok "crashed worker blocks as salvageable" || bad "wrong crash reason: $(cat "$WORK/out")"
fi
recover orphans
grep -q "orphan: wf-g1 (role=ticket ticket=#120 status=gone)" "$WORK/out" \
    && ok "orphan names worker/role/ticket/status" || bad "orphan line wrong: $(cat "$WORK/out")"
grep -q "gh issue edit 120 --remove-assignee" "$WORK/out" \
    && ok "orphan prints claim-release guidance" || bad "no release guidance"
[ -s "$GH_CALLS" ] && bad "orphans called gh (must stay tracker read-only)" || ok "orphans never touches the tracker"
: > "$WAYFINDER_WORKER_REGISTRY"
recover orphans
grep -q "orphans: none" "$WORK/out" && ok "no orphans reported cleanly" || bad "empty orphans wrong"

echo "5. read-only scouts never block writable quiescence (strict mode excepted)"
mkrow wf-sc1 bug-scout 697 "$WORK/ws" running
recover quiescence
grep -q "^QUIESCENT (writable clear; read-only scout activity:.*wf-sc1)" "$WORK/out" \
    && ok "scout reported without blocking" || bad "scout blocked or hidden: $(cat "$WORK/out")"
if bash "$RECOVER" quiescence --strict >"$WORK/out" 2>"$WORK/err"; then
    bad "strict mode ignored scout activity"
else
    grep -q "BLOCKED: bug-scout activity (strict):.*wf-sc1" "$WORK/out" \
        && ok "strict mode blocks on scouts" || bad "strict reason wrong: $(cat "$WORK/out")"
fi
: > "$WAYFINDER_WORKER_REGISTRY"

echo "6. spawn records map+generation recovery identity; old rows stay readable"
WAYFINDER_MAP=697 WAYFINDER_GENERATION=gen-test-1 \
    bash "$WORKER" spawn --role ticket --ticket 130 --workspace "$WORK/ws" --name wf-id1 >/dev/null 2>&1 \
    || bad "spawn with identity failed: $(cat "$WORK/err" 2>/dev/null)"
[ "$(awk -F'\t' -v n=wf-id1 '$1 == n { print $11; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "697" ] \
    && ok "map recorded in column 11" || bad "map missing: $(cat "$WAYFINDER_WORKER_REGISTRY")"
[ "$(awk -F'\t' -v n=wf-id1 '$1 == n { print $12; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "gen-test-1" ] \
    && ok "generation recorded in column 12" || bad "generation missing"
printf 'wf-old\tticket\t131\t%s\tpane-7\trunning\tt0\tt0\tlegacy note\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
rm -f "$WORK/list.json"
bash "$WORKER" reconcile >/dev/null 2>&1 || bad "reconcile with legacy row failed"
grep -q $'^wf-old\tticket\t131\t' "$WAYFINDER_WORKER_REGISTRY" && ok "legacy 9-col row survives reconcile" || bad "legacy row lost"
: > "$WAYFINDER_WORKER_REGISTRY"; rm -f "$WORK"/list.json "$WORK"/status-*

echo "7. restart re-adopts gone workers instead of duplicating them"
printf 'wf-re\tticket\t140\t%s\tpane-7\tgone\tt0\tt0\tmissing from herdr agent list\t\t697\tgen-a\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-re","status":"running"}]}}
JSON
bash "$WORKER" reconcile >"$WORK/out" 2>&1 || bad "reconcile failed"
grep -q $'^wf-re\tticket\t140\t.*\trunning\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "gone row re-adopted to running (no duplicate spawn)" || bad "no re-adoption: $(cat "$WAYFINDER_WORKER_REGISTRY")"
grep -q "re-adopted" "$WORK/out" && ok "re-adoption summarized" || bad "no re-adopt summary: $(cat "$WORK/out")"
[ "$(awk -F'\t' -v n=wf-re '$1 == n { print $11; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "697" ] \
    && ok "map survives re-adoption" || bad "map dropped by reconcile"
[ "$(grep -c '^wf-re' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] && ok "exactly one row (no duplicate)" || bad "duplicate rows"
: > "$WAYFINDER_WORKER_REGISTRY"; rm -f "$WORK"/list.json

echo "8. unmanaged live agents are reported, never auto-adopted (daemon gate blocks on them)"
: > "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-stray","status":"running"}]}}
JSON
bash "$WORKER" reconcile >"$WORK/out" 2>&1 || bad "reconcile failed"
grep -q "unmanaged: wf-stray" "$WORK/out" && ok "unmanaged reported" || bad "unmanaged not reported"
[ "$(grep -c '^wf-stray' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] \
    && ok "unmanaged never auto-adopted into the registry" || bad "unmanaged auto-adopted"
recover quiescence
grep -q "^QUIESCENT$" "$WORK/out" && ok "bare quiescence trusts the registry (reconcile-first discipline documented)" || bad "quiescence misread"
if grep -q 'unmanaged' "$LOOP" && grep -q '"$recover" reconcile' "$LOOP"; then
    ok "daemon gate reconciles first and blocks on unmanaged agents"
else bad "daemon gate ignores unmanaged agents"; fi
rm -f "$WORK"/list.json

echo "9. adopt registers one unmanaged live agent with duplicate guards"
: > "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-new","status":"running"}]}}
JSON
WAYFINDER_MAP=697 WAYFINDER_GENERATION=gen-b \
    bash "$RECOVER" adopt wf-new --role ticket --ticket 150 --workspace "$WORK/ws" --pane pane-9 >"$WORK/out" 2>"$WORK/err" \
    || bad "adopt failed: $(cat "$WORK/err")"
grep -q $'^wf-new\tticket\t150\t'"$WORK/ws"$'\tpane-9\trunning\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "adopted row carries role/ticket/workspace/pane/running" || bad "adopted row wrong: $(cat "$WAYFINDER_WORKER_REGISTRY")"
[ "$(awk -F'\t' -v n=wf-new '$1 == n { print $11; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "697" ] \
    && ok "adopt records map identity" || bad "adopt map missing"
if WAYFINDER_MAP=697 bash "$RECOVER" adopt wf-new --role ticket --ticket 151 --workspace "$WORK/ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "duplicate name adopted"
else ok "duplicate name refused"; fi
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-second","status":"running"}]}}
JSON
if WAYFINDER_MAP=697 bash "$RECOVER" adopt wf-second --role ticket --ticket 150 --workspace "$WORK/ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "second owner for ticket #150 adopted"
else grep -q "already held by 'wf-new'" "$WORK/err" && ok "duplicate ticket refused with owner pointer" || bad "wrong refusal: $(cat "$WORK/err")"; fi
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[]}}
JSON
if WAYFINDER_MAP=697 bash "$RECOVER" adopt wf-ghost --role ticket --ticket 152 --workspace "$WORK/ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "non-live agent adopted"
else grep -q "not live in herdr agent list" "$WORK/err" && ok "non-live adoption refused (spawn replacement only after confirmed gone)" || bad "wrong refusal: $(cat "$WORK/err")"; fi
rm -f "$WORK"/list.json
: > "$WAYFINDER_WORKER_REGISTRY"

echo "10. handoff race: successor waits while BLOCKED, advances when QUIESCENT"
mkdir -p "$WORK/handoffs"
printf '# packet gen-1\n' > "$WORK/handoffs/wayfinder-1-from-0-handoff.md"
mkrow wf-race ticket 160 "$WORK/ws" running
if bash "$RECOVER" quiescence >"$WORK/out" 2>"$WORK/err"; then
    bad "successor would advance over an active worker"
else
    grep -q "BLOCKED: workers active" "$WORK/out" \
        && ok "successor gated while workers active (packet waits)" || bad "wrong gate: $(cat "$WORK/out")"
fi
printf 'wf-race\tticket\t160\t%s\tpane-7\tintegrated\tt0\tt0\treviewed\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
bash "$WORKER" cleanup wf-race >/dev/null 2>&1 || bad "terminal cleanup failed"
recover quiescence
grep -q "^QUIESCENT$" "$WORK/out" && ok "successor may advance after integrate+cleanup" || bad "still blocked: $(cat "$WORK/out")"
: > "$WAYFINDER_WORKER_REGISTRY"

echo "11. reconcile joins worker + workspace provider state, never respawns or deletes"
bash "$WORKSPACE" create --provider fake --purpose ticket-170 --id wf-ws1 --root "$WORK/ws-root" >/dev/null 2>&1 \
    || bad "workspace fixture create failed"
mkrow wf-ws-row ticket 170 "$(bash "$WORKSPACE" path wf-ws1)" running
rm -rf "$(bash "$WORKSPACE" path wf-ws1)"
printf 'wf-ws-row\tticket\t170\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
rm -f "$WORK/list.json"
bash "$RECOVER" reconcile >"$WORK/out" 2>"$WORK/err" || bad "recover reconcile failed: $(cat "$WORK/err")"
grep -q $'^wf-ws-row\t.*\tgone\t' "$WAYFINDER_WORKER_REGISTRY" && ok "missing worker marked gone, row kept" || bad "worker gone handling wrong"
grep -q "reconciled:" "$WORK/out" && ok "worker reconcile ran through the seam" || bad "no worker summary"
grep -q "reconcile end:" "$WORK/out" && ok "quiescence verdict closes reconcile" || bad "no closing verdict"
[ "$(grep -c '^wf-ws-row' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] && ok "reconcile never deletes rows" || bad "row deleted"
bash "$WORKSPACE" cleanup wf-ws1 >/dev/null 2>&1 || true
: > "$WAYFINDER_WORKER_REGISTRY"

echo "12. leaf roles cannot reconcile or adopt; read lanes stay observable"
: > "$WAYFINDER_WORKER_REGISTRY"
mkrow wf-leaf ticket 180 "$WORK/ws" running
if WAYFINDER_ROLE=ticket bash "$RECOVER" reconcile >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf reconcile accepted"
elif grep -q "chief" "$WORK/err"; then ok "leaf reconcile refused with chief pointer"; else bad "leaf refusal unclear"; fi
if WAYFINDER_ROLE=helper bash "$RECOVER" adopt wf-x --role ticket --ticket 181 --workspace "$WORK/ws" >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf adopt accepted"
else ok "leaf adopt refused"; fi
# Quiescence exits 1 while BLOCKED by design — observability means the lane
# runs and reports, not that it exits 0.
WAYFINDER_ROLE=ticket bash "$RECOVER" quiescence >"$WORK/out" 2>&1 \
    && bad "leaf quiescence misread an active worker as quiescent" \
    || grep -q "BLOCKED: workers active:.*wf-leaf" "$WORK/out" && ok "leaf quiescence observable (reports BLOCKED)" || bad "leaf quiescence blocked: $(cat "$WORK/out")"
WAYFINDER_ROLE=bug-scout bash "$RECOVER" status >/dev/null 2>&1 && ok "leaf status observable" || bad "leaf status blocked"
WAYFINDER_ROLE=maintenance bash "$RECOVER" orphans >/dev/null 2>&1 && ok "leaf orphans observable" || bad "leaf orphans blocked"
: > "$WAYFINDER_WORKER_REGISTRY"

echo "13. chief records map identity and offers a no-fill --recover lane"
printf '[{"number":190}]' >"$WORK/sub-697.json"
cat >"$WORK/issue-190.json" <<'JSON'
{"number":190,"state":"open","issue_dependencies_summary":{"blocked_by":0,"blocking":0},"assignees":[],"labels":[{"name":"wayfinder:task"}]}
JSON
rm -f "$WORK/list.json"
WAYFINDER_GENERATION=gen-chief-1 bash "$CHIEF" --map 697 --once --max-workers 1 --workspace "$WORK/ws" >"$WORK/out" 2>"$WORK/err" \
    || bad "chief pass failed: $(cat "$WORK/err")"
[ "$(awk -F'\t' -v n=wf-697-190 '$1 == n { print $11; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "697" ] \
    && ok "chief spawn records map #697" || bad "chief spawn map missing: $(cat "$WAYFINDER_WORKER_REGISTRY")"
[ "$(awk -F'\t' -v n=wf-697-190 '$1 == n { print $12; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "gen-chief-1" ] \
    && ok "chief spawn records generation" || bad "chief spawn generation missing"
bash "$CHIEF" --map 697 --recover >"$WORK/out" 2>"$WORK/err" \
    || bad "chief --recover failed: $(cat "$WORK/err")"
grep -q "reconcile" "$WORK/out" && ok "--recover reconciles before any dispatch" || bad "no reconcile in --recover: $(cat "$WORK/out")"
[ "$(grep -c '^wf-697-190' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] && ok "--recover fills nothing (single-shot)" || bad "--recover spawned work"
if bash "$CHIEF" --map 697 --recover --queue >"$WORK/out" 2>"$WORK/err"; then
    bad "combined --recover --queue accepted"
else ok "lanes stay one-per-invocation"; fi
: > "$WAYFINDER_WORKER_REGISTRY"

echo "14. structural guards: no daemon-side integration, no tracker writes, provider-neutral join"
if [ "$(grep -v '^#' "$RECOVER" | grep -Ec '(^|[^a-zA-Z-])(git -C|git checkout|git cherry-pick|git merge|git reset|git commit|git push|git pull|git rebase|git stash|git worktree|git branch)( |$)')" -gt 0 ]; then
    bad "recover invokes git directly (workspace state goes through the provider seam)"
else ok "recover never invokes git (no cherry-pick/merge/commit/worktree verbs)"; fi
if [ "$(grep -v '^#' "$RECOVER" | grep -Ec 'gh (issue (create|comment|close)|api --method POST)|\$GH_BIN|GH_CALLS')" -gt 0 ]; then
    bad "recover writes to the tracker"
else ok "recover never writes the tracker (orphans prints release guidance only)"; fi
if [ "$(grep -E '^\s*"\$HERDR_BIN" agent (start|prompt|wait|send-keys)' "$RECOVER" | grep -c .)" -gt 0 ]; then
    bad "recover drives agents (must stay read-only list)"
else ok "recover Herdr use stays read-only (agent list for adopt only)"; fi
if [ "$(grep -v '^#' "$RECOVER" | grep -Ec 'WAYFINDER_WORKSPACE_PROVIDER| Cow |cow\)')" -gt 0 ]; then
    bad "recover branches on provider names"
else ok "recover joins on opaque paths (provider-neutral)"; fi
if [ "$(grep -v '^#' "$WORKER" | grep -Ec 'WAYFINDER_WORKSPACE_PROVIDER|workspaces\.tsv')" -gt 0 ]; then
    bad "worker seam branches on provider state"
else ok "worker seam still treats workspaces as opaque dirs"; fi
if [ "$(grep -v '^#' "$CHIEF" | grep -Ec 'WAYFINDER_WORKSPACE_PROVIDER|workspaces\.tsv')" -gt 0 ]; then
    bad "chief branches on provider state"
else ok "chief stays provider-agnostic"; fi
if grep -q 'quiescence_gate' "$LOOP" && grep -q '"$recover" quiescence' "$LOOP"; then
    ok "daemon gates successor spawns on the quiescence predicate"
else bad "loop never consults quiescence"; fi
# Full-input counts (no -q downstream): an early-exiting grep would SIGPIPE
# the producer mid-list under pipefail and flip the verdict.
if [ "$(grep -vE '^\s*#' "$LOOP" | grep -c 'WAYFINDER_QUIESCENCE_GATE')" -gt 0 ]; then
    ok "daemon gate carries an explicit bypass switch"
else bad "no gate bypass switch"; fi
if [ "$(grep -vE '^\s*#' "$LOOP" | grep -Ec 'wayfinder-review\.sh|cherry-pick|agent start')" -gt 0 ]; then
    bad "daemon loop invokes the review/integrate/worker-spawn path"
else ok "daemon still never performs integration (supervisor only)"; fi

echo "15. fail-closed reconcile, forward-only reports, forgery refusal, atomic staging"
: > "$WAYFINDER_WORKER_REGISTRY"
mkrow wf-fc ticket 190 "$WORK/ws" running
printf '{"result":{}}' > "$WORK/list.json"
if bash "$WORKER" reconcile >"$WORK/out" 2>"$WORK/err"; then
    bad "shape-mismatched listing accepted"
else
    grep -q "refusing to mark workers gone" "$WORK/err" \
        && ok "unrecognizable listing fails closed" || bad "wrong refusal: $(cat "$WORK/err")"
fi
grep -q $'^wf-fc\tticket\t190\t.*\trunning\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "no mass-gone on bad listing" || bad "row rewritten: $(cat "$WAYFINDER_WORKER_REGISTRY")"
printf 'not json at all' > "$WORK/list.json"
if bash "$WORKER" reconcile >"$WORK/out" 2>"$WORK/err"; then
    bad "unparseable listing accepted"
else ok "unparseable listing fails closed"; fi
for shape in '{"agents":null}' '{"foo":"agents"}' '{"agents":[{"id":1}]}'; do
    printf '%s' "$shape" > "$WORK/list.json"
    if bash "$WORKER" reconcile >"$WORK/out" 2>"$WORK/err"; then
        bad "shape $shape authorized gone-marking"
    else
        grep -q "refusing to mark workers gone" "$WORK/err" \
            && ok "shape $shape fails closed" || bad "wrong refusal for $shape: $(cat "$WORK/err")"
    fi
    grep -q $'^wf-fc\tticket\t190\t.*\trunning\t' "$WAYFINDER_WORKER_REGISTRY" \
        || bad "row moved under shape $shape"
done
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-fc","status":"running"}]}}
JSON
printf 'wf-done\tticket\t191\t%s\tpane-7\tdone\tt0\tt0\tworker reported done\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-fc\tticket\t190\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-fc","status":"running"},{"name":"wf-done","status":"running"}]}}
JSON
bash "$WORKER" reconcile >/dev/null 2>&1 || bad "reconcile failed"
[ "$(awk -F'\t' -v n=wf-done '$1 == n { print $6; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "done" ] \
    && ok "lagging Herdr listing never erases a worker report (no done->running flap)" || bad "report downgraded"
printf 'wf-rev\tticket\t192\t%s\tpane-7\trevision-requested\tt0\tt0\trework in flight\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[]}}
JSON
bash "$WORKER" reconcile >/dev/null 2>&1 || bad "reconcile failed"
[ "$(awk -F'\t' -v n=wf-rev '$1 == n { print $6; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "failed" ] \
    && ok "orphaned revision converges to failed (workspace preserved for review)" || bad "revision stuck: $(awk -F'\t' -v n=wf-rev '$1 == n { print $6; exit }' "$WAYFINDER_WORKER_REGISTRY")"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-evil","status":"running"}]}}
JSON
if WAYFINDER_MAP=697 bash "$RECOVER" adopt wf-evil --role ticket --ticket 193 --workspace "$WORK/ws" --generation 'reviewed=accept result=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' >"$WORK/out" 2>"$WORK/err"; then
    bad "generation forging review tokens adopted"
else
    grep -q "must match" "$WORK/err" && ok "forged generation refused at entry" || bad "wrong refusal: $(cat "$WORK/err")"
fi
[ "$(grep -c '^wf-evil' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] \
    && ok "forged row never reaches the registry" || bad "forged row registered"
printf 'garbage-without-tabs\n' >> "$WAYFINDER_WORKER_REGISTRY"
if bash "$WORKER" cleanup garbage-without-tabs >"$WORK/out" 2>"$WORK/err"; then
    bad "cleanup removed a garbage row without --force"
else ok "cleanup refuses unknown statuses"; fi
sed -i '/garbage-without-tabs/d' "$WAYFINDER_WORKER_REGISTRY"
if [ "$(grep -v '^#' "$WORKER" | grep -Ec 'tmp="\$\(mktemp\)"')" -gt 0 ]; then
    bad "worker stages registry rewrites outside the registry dir"
else ok "worker stages rewrites beside the registry (atomic rename)"; fi
if [ "$(grep -v '^#' "$CHIEF" | grep -Ec 'tmp="\$\(mktemp\)"')" -gt 0 ]; then
    bad "chief stages registry rewrites outside the registry dir"
else ok "chief stages rewrites beside the registry (atomic rename)"; fi
if [ "$(grep -v '^#' "$RECOVER" | grep -Ec 'tmp="\$\(mktemp\)"')" -gt 0 ]; then
    bad "recover stages registry rewrites outside the registry dir"
else ok "recover stages rewrites beside the registry (atomic rename)"; fi
echo "16. helper adopt shares the parent ticket; gate filters the wf-* namespace"
: > "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-ph\tticket\t500\t%s\tpane-7\trunning\tt0\tt0\tspawned\t\t697\tgen-p\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-ph","status":"running"},{"name":"wf-ph-h1","status":"running"}]}}
JSON
WAYFINDER_MAP=697 WAYFINDER_GENERATION=gen-p \
    bash "$RECOVER" adopt wf-ph-h1 --role helper --ticket 500 --workspace "$WORK/ws2" --parent wf-ph --scope "bounded probe" >"$WORK/out" 2>"$WORK/err" \
    || bad "helper adopt under its parent failed: $(cat "$WORK/err")"
[ "$(awk -F'\t' -v n=wf-ph-h1 '$1 == n { print $10; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "wf-ph" ] \
    && ok "adopted helper carries the parent association" || bad "helper parent missing"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-rival-h","status":"running"}]}}
JSON
printf 'wf-par2\tticket\t502\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-other\tticket\t502\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
if WAYFINDER_MAP=697 bash "$RECOVER" adopt wf-rival-h --role helper --ticket 502 --workspace "$WORK/ws2" --parent wf-par2 --scope "x" >"$WORK/out" 2>"$WORK/err"; then
    bad "helper adopt beside a non-parent ticket holder accepted"
else
    grep -q "non-parent worker 'wf-other'" "$WORK/err" \
        && ok "helper adopt refuses non-parent ticket holders" || bad "wrong refusal: $(cat "$WORK/err")"
fi
if grep -q "unmanaged: wf-" "$LOOP" && grep -q '"$recover" reconcile' "$LOOP"; then
    ok "daemon gate reconciles and blocks on wf-* unmanaged agents"
else bad "daemon gate misses unmanaged Wayfinder agents"; fi
if grep -q 'herdr_present' "$LOOP"; then
    ok "daemon gate degrades without a Herdr runtime (sequential unaffected)"
else bad "no Herdr-absent fallback in the gate"; fi
if [ "$(grep -v '^#' "$REVIEW" | grep -Ec 'tmp="\$\(mktemp\)"')" -eq 0 ]; then
    ok "review stages rewrites beside the registry (atomic rename)"
else bad "review stages registry rewrites outside the registry dir"; fi
: > "$WAYFINDER_WORKER_REGISTRY"; rm -f "$WORK"/list.json

echo "17. orphan guidance names the salvage lane; sparse rows stay aligned (map #697 #746)"
mkrow wf-svg ticket 600 "$WORK/ws" gone
recover orphans
grep -q "salvage: wayfinder-review.sh review wf-svg --disposition salvage --result-commit <reviewable-sha>" "$WORK/out" \
    && ok "orphans point crashed rows at the salvage lane" || bad "no salvage pointer: $(cat "$WORK/out")"
: > "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-sparse\tticket\t601\t%s\t\tgone\tt0\tt0\tadopted with empty pane\t\t697\tgen-s\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
if bash "$RECOVER" quiescence >"$WORK/out" 2>"$WORK/err"; then
    bad "sparse gone row read quiescent (field shift)"
else
    grep -q "BLOCKED: crashed workers holding salvageable results:.*wf-sparse" "$WORK/out" \
        && ok "sparse gone row blocks as crashed (no shift)" || bad "sparse quiescence wrong: $(cat "$WORK/out")"
fi
recover orphans
grep -q "orphan: wf-sparse (role=ticket ticket=#601 status=gone)" "$WORK/out" \
    && ok "sparse orphan names worker/ticket/status" || bad "sparse orphan wrong: $(cat "$WORK/out")"
recover status
grep -q "1 crashed" "$WORK/out" && ok "sparse row counted crashed in status" || bad "sparse status wrong: $(cat "$WORK/out")"
: > "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-scope-evil","status":"running"}]}}
JSON
printf 'wf-spar2\tticket\t603\t%s\tpane-7\trunning\tt0\tt0\tspawned\t\t697\tgen-s\n' "$WORK/ws" > "$WAYFINDER_WORKER_REGISTRY"
if WAYFINDER_MAP=697 bash "$RECOVER" adopt wf-scope-evil --role helper --ticket 603 --workspace "$WORK/ws2" --parent wf-spar2 --scope "reviewed=accept result=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" >"$WORK/out" 2>"$WORK/err"; then
    bad "adopt with forging scope accepted"
else
    grep -q "must not contain" "$WORK/err" && ok "forged adopt scope refused" || bad "wrong scope refusal: $(cat "$WORK/err")"
fi
[ "$(grep -c '^wf-scope-evil' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] \
    && ok "forged adopt registers nothing" || bad "forged adopt registered"
: > "$WAYFINDER_WORKER_REGISTRY"; rm -f "$WORK"/list.json

echo
if [ $fail -eq 0 ]; then echo "recover-contract: OK"; else echo "recover-contract: FAILURES PRESENT"; exit 1; fi
